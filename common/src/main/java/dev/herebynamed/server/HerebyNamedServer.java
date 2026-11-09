package dev.herebynamed.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.herebynamed.auto.AutoWork;
import dev.herebynamed.domain.*;
import dev.herebynamed.net.Snapshot;
import dev.herebynamed.net.Wire;
import dev.herebynamed.platform.PlatformHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class HerebyNamedServer {
    public static final Logger LOG = LoggerFactory.getLogger("hereby_named");
    private static final Map<MinecraftServer, ServerData> SERVERS = new IdentityHashMap<>();

    private static ServerData data(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, s -> new ServerData(s.getWorldPath(LevelResource.ROOT).resolve("hereby-named")));
    }

    public static void onJoin(MinecraftServer server, ServerPlayer player) {
        send(server, player, player.serverLevel().dimension().location().toString(), "");
    }

    public static void onDisconnect(MinecraftServer server, ServerPlayer player) {
        ServerData d = data(server);
        d.assemblers.remove(player.getUUID());
        d.readAt.remove(player.getUUID());
        d.automaticAt.remove(player.getUUID());
        d.incoming.remove(player.getUUID());
        d.outgoing.keySet().removeIf(key -> key.player().equals(player.getUUID()));
        for (String dimension : d.authority.releaseAll(player.getUUID())) {
            RegionBook book = d.books.get(dimension);
            if (book != null) book.clearHistory();
            broadcast(server, dimension);
        }
    }

    public static void onStopping(MinecraftServer server) {
        ServerData d = SERVERS.get(server);
        if (d != null) {
            d.writes.close();
            d.stopping = true;
        }
    }

    public static void onStopped(MinecraftServer server) {
        SERVERS.remove(server);
    }

    public static void onServerTick(MinecraftServer server) {
        pump(server, SERVERS.get(server));
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("herebynamed").requires(source -> source.hasPermission(2))
                .then(Commands.literal("grant").then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> authorize(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true))))
                .then(Commands.literal("revoke").then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> authorize(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false)))));
    }

    private static int authorize(CommandSourceStack source, String name, boolean grant) {
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(name);
        if (player == null) { source.sendFailure(Component.literal("玩家需要在线")); return 0; }
        ServerData d = data(source.getServer());
        Set<UUID> next = new HashSet<>(d.authorized);
        if (grant) next.add(player.getUUID()); else next.remove(player.getUUID());
        try {
            Files.createDirectories(d.root);
            Path temporary = d.root.resolve("editors.tmp");
            Files.write(temporary, next.stream().map(UUID::toString).sorted().toList());
            try { Files.move(temporary, d.root.resolve("editors.txt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, d.root.resolve("editors.txt"), StandardCopyOption.REPLACE_EXISTING); }
            d.authorized.clear(); d.authorized.addAll(next);
            if (!grant) for (String dim : d.authority.releaseAll(player.getUUID())) {
                d.books.get(dim).clearHistory(); broadcast(source.getServer(), dim);
            }
            for (String dim : List.copyOf(d.books.keySet())) send(source.getServer(), player, dim, "编辑权限已更新");
            source.sendSuccess(() -> Component.literal("此地名曰：已" + (grant ? "授权 " : "撤销授权 ") + name), true); return 1;
        } catch (IOException ex) { LOG.error("Cannot persist region editors", ex); source.sendFailure(Component.literal("授权保存失败")); return 0; }
    }

    public static void receive(MinecraftServer server, ServerPlayer player, Wire.Fragment fragment) {
        if (player.hasDisconnected()) return;
        ServerData d = data(server);
        try {
            Optional<byte[]> bytes = d.assemblers.computeIfAbsent(player.getUUID(), id -> new Wire.Assembler()).accept(fragment);
            if (bytes.isEmpty()) return;
            var queue = d.incoming.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
            if (queue.size() < 4) queue.addLast(new Incoming(player, bytes.get()));
            pump(server, d);
        } catch (Exception ex) { LOG.warn("Invalid region fragment", ex); }
    }

    private static void pump(MinecraftServer server, ServerData d) {
        if (d == null || d.stopping || d.pumping) return;
        d.pumping = true;
        try {
            for (var entry : List.copyOf(d.incoming.entrySet())) {
                if (!AutoWork.hasCapacity()) break;
                var queue = entry.getValue();
                if (queue.isEmpty()) { d.incoming.remove(entry.getKey()); continue; }
                Incoming incoming = queue.peekFirst();
                var decoding = AutoWork.submit(() -> RegionEdit.decode(incoming.bytes()));
                if (AutoWork.rejected(decoding)) break;
                queue.removeFirst();
                decoding.whenComplete((edit, error) -> server.execute(() -> {
                    if (SERVERS.get(server) != d || d.stopping) return;
                    if (!incoming.player().hasDisconnected()) {
                        if (error != null) LOG.warn("Invalid region request", error);
                        else handle(server, incoming.player(), edit);
                    }
                    pump(server, d);
                }));
            }
            for (int count = 0; count < 4 && !d.outgoing.isEmpty() && AutoWork.hasCapacity(); count++) {
                var entry = d.outgoing.entrySet().iterator().next();
                Outgoing outgoing = entry.getValue();
                d.outgoing.remove(entry.getKey());
                encode(server, d, outgoing);
            }
        } finally { d.pumping = false; }
        if (!d.retryScheduled && (!d.outgoing.isEmpty() || d.incoming.values().stream().anyMatch(q -> !q.isEmpty()))) {
            d.retryScheduled = true;
            java.util.concurrent.CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() ->
                server.execute(() -> {
                    if (SERVERS.get(server) != d || d.stopping) return;
                    d.retryScheduled = false;
                    pump(server, d);
                }));
        }
    }

    private static void handle(MinecraftServer server, ServerPlayer player, RegionEdit edit) {
        if (player.hasDisconnected()) return;
        ServerData d = data(server);
        String dimension = null;
        long requestId = edit.requestId();
        try {
            if (!validDimension(server, edit.dimension())) return;
            dimension = edit.dimension();
            if (edit.action() == RegionEdit.Action.READ) {
                long now = System.nanoTime(), last = d.readAt.getOrDefault(player.getUUID(), 0L);
                if (now - last < 100_000_000L) return;
                d.readAt.put(player.getUUID(), now); send(server, player, edit.dimension(), "", requestId); return;
            }
            RegionBook book = d.book(edit.dimension());
            if (edit.action() == RegionEdit.Action.RELEASE) {
                if (d.authority.canEdit(edit.dimension(), player.getUUID(), true)) {
                    d.authority.release(edit.dimension(), player.getUUID()); book.clearHistory(); broadcast(server, edit.dimension());
                }
                return;
            }
            boolean authorized = authorized(d, player);
            if (edit.action() == RegionEdit.Action.AUTO_APPLY || edit.action() == RegionEdit.Action.AUTO_ALLOW_AGAIN || edit.action() == RegionEdit.Action.AUTO_PRUNE) {
                automatic(server, player, edit, d, book, authorized); return;
            }
            if (d.automaticBusy.contains(edit.dimension())) {
                send(server, player, edit.dimension(), "区域更新中，请稍后重试", requestId); return;
            }
            if (edit.action() == RegionEdit.Action.ACQUIRE) {
                boolean already = d.authority.canEdit(edit.dimension(), player.getUUID(), authorized);
                if (!d.authority.acquire(edit.dimension(), player.getUUID(), authorized)) {
                    send(server, player, edit.dimension(), authorized ? "该维度正由其他玩家编辑" : "需要 OP 或编辑授权", requestId); return;
                }
                if (!already) book.clearHistory(); broadcast(server, edit.dimension(), player.getUUID(), requestId); return;
            }
            if (!d.authority.canEdit(edit.dimension(), player.getUUID(), authorized)) {
                send(server, player, edit.dimension(), "当前没有此维度的编辑权", requestId); return;
            }
            if (edit.revision() != d.revisions.getOrDefault(edit.dimension(), 0L)) {
                send(server, player, edit.dimension(), "数据已更新，请重试本次操作", requestId); return;
            }
            RegionBook.State before = book.state();
            try { edit.apply(book); d.store.save(edit.dimension(), book); }
            catch (IOException ex) { book.replace(before); throw ex; }
            d.revisions.merge(edit.dimension(), 1L, Long::sum); broadcast(server, edit.dimension(), player.getUUID(), requestId);
        } catch (Exception ex) {
            LOG.warn("Region operation failed for {}", player.getUUID(), ex);
            if (dimension != null) send(server, player, dimension, Objects.toString(ex.getMessage(), "操作失败"), requestId);
            else player.sendSystemMessage(Component.literal("此地名曰：无效区域请求"));
        }
    }

    private static void automatic(MinecraftServer server, ServerPlayer player, RegionEdit edit, ServerData d, RegionBook book, boolean authorized) {
        String dim = edit.dimension();
        long now = System.nanoTime();
        if (!d.authority.canAutomate(dim, authorized) || !player.serverLevel().dimension().location().toString().equals(dim)
                || d.automaticBusy.contains(dim)
                || edit.revision() != d.revisions.getOrDefault(dim, 0L)
                || now - d.automaticAt.getOrDefault(player.getUUID(), 0L) < 2_000_000_000L) {
            send(server, player, dim, "", edit.requestId()); return;
        }
        d.automaticAt.put(player.getUUID(), now); d.automaticBusy.add(dim);
        RegionBook next = book.snapshot(); RegionBook.State before = book.state();
        d.writes.submit(() -> {
            edit.apply(next);
            if (!before.equals(next.state())) d.store.save(dim, next);
            return next;
        }).whenComplete((result, error) -> server.execute(() -> {
            if (SERVERS.get(server) != d) return;
            d.automaticBusy.remove(dim);
            if (error != null) LOG.warn("Automatic region transaction failed", error);
            else if (!before.equals(result.state())) { d.books.put(dim, result); d.revisions.merge(dim, 1L, Long::sum); }
            broadcast(server, dim, player.getUUID(), edit.requestId());
        }));
    }

    private static boolean validDimension(MinecraftServer server, String dimension) {
        return server.levelKeys().stream().anyMatch(key -> key.location().toString().equals(dimension));
    }

    private static boolean authorized(ServerData d, ServerPlayer player) {
        return player.hasPermissions(2) || d.authorized.contains(player.getUUID())
                || (player.getServer().isSingleplayer() && player.getServer().isSingleplayerOwner(player.getGameProfile()));
    }

    public static void broadcast(MinecraftServer server, String dimension) {
        broadcast(server, dimension, null, 0);
    }

    public static void broadcast(MinecraftServer server, String dimension, UUID requester, long requestId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            send(server, player, dimension, "", player.getUUID().equals(requester) ? requestId : 0);
    }

    public static void send(MinecraftServer server, ServerPlayer player, String dimension, String message) {
        send(server, player, dimension, message, 0);
    }

    public static void send(MinecraftServer server, ServerPlayer player, String dimension, String message, long responseTo) {
        if (!PlatformHelper.get().canSendToPlayer(player)) return;
        ServerData d = data(server);
        try {
            if (d.stopping || player.hasDisconnected()) return;
            d.outgoing.put(new Delivery(player.getUUID(), dimension, responseTo), new Outgoing(player, dimension, message, responseTo));
            pump(server, d);
        } catch (RuntimeException ex) { LOG.error("Cannot queue named regions", ex); }
    }

    private record Delivery(UUID player, String dimension, long responseTo) { }
    private record Incoming(ServerPlayer player, byte[] bytes) { }
    private record Outgoing(ServerPlayer player, String dimension, String message, long responseTo) { }

    private static void encode(MinecraftServer server, ServerData d, Outgoing outgoing) {
        var player = outgoing.player(); String dimension = outgoing.dimension(), message = outgoing.message(); long responseTo = outgoing.responseTo();
        if (player.hasDisconnected() || d.stopping) return;
        try {
            Snapshot snapshot = new Snapshot(dimension, d.revisions.getOrDefault(dimension, 0L), responseTo,
                    d.authority.holder(dimension), authorized(d, player), message, d.book(dimension).snapshot());
            AutoWork.submit(snapshot::encode).whenComplete((bytes, error) -> server.execute(() -> {
                if (SERVERS.get(server) != d) return;
                if (player.hasDisconnected()) return;
                if (error != null) {
                    if (error instanceof java.util.concurrent.RejectedExecutionException)
                        send(server, player, dimension, message, responseTo);
                    else LOG.error("Cannot encode named regions", error);
                    return;
                }
                if (snapshot.revision() != d.revisions.getOrDefault(dimension, 0L) || !Objects.equals(snapshot.holder(), d.authority.holder(dimension))
                        || snapshot.authorized() != authorized(d, player)) { send(server, player, dimension, message, responseTo); return; }
                Wire.send(bytes, packet -> PlatformHelper.get().sendToPlayer(player, packet));
            }));
        } catch (IOException ex) {
            LOG.error("Cannot load/sync named regions", ex);
            player.sendSystemMessage(Component.literal("此地名曰：区域存档读取或同步失败，已保留原文件"));
        }
    }

    private static final class ServerData {
        final Path root;
        final RegionStore store;
        final EditAuthority authority = new EditAuthority();
        final Set<UUID> authorized = new HashSet<>();
        final Map<String, RegionBook> books = new HashMap<>();
        final Map<String, Long> revisions = new HashMap<>();
        final Map<UUID, Wire.Assembler> assemblers = new HashMap<>();
        final Map<UUID, Long> readAt = new HashMap<>();
        final Map<UUID, Long> automaticAt = new HashMap<>();
        final Set<String> automaticBusy = new HashSet<>();
        final AutoWork.Session writes = new AutoWork.Session();
        final Map<Delivery, Outgoing> outgoing = new LinkedHashMap<>();
        final Map<UUID, ArrayDeque<Incoming>> incoming = new LinkedHashMap<>();
        boolean stopping, pumping, retryScheduled;
        ServerData(Path root) {
            this.root = root; store = new RegionStore(root.resolve("regions"));
            Path file = root.resolve("editors.txt");
            if (Files.exists(file)) try { for (String line : Files.readAllLines(file)) authorized.add(UUID.fromString(line)); }
            catch (Exception ex) { LOG.error("Cannot read region editor grants", ex); }
        }
        RegionBook book(String dim) throws IOException {
            RegionBook book = books.get(dim);
            if (book == null) { book = store.load(dim); books.put(dim, book); }
            return book;
        }
    }

    private HerebyNamedServer() {}
}
