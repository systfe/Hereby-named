package dev.herebynamed;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.herebynamed.domain.*;
import dev.herebynamed.net.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Server entrypoint: deliberately has no client/Xaero/Traveler class references. */
public final class HerebyNamed implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("hereby_named");
    private static final Map<MinecraftServer, ServerData> SERVERS = new IdentityHashMap<>();
    private static ServerData data(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, s -> new ServerData(s.getSavePath(WorldSavePath.ROOT).resolve("hereby-named")));
    }
    @Override public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(Wire.CHANNEL, (server, player, handler, buf, response) -> {
            try {
                Wire.Fragment fragment = Wire.Fragment.read(buf);
                server.execute(() -> receive(server, player, fragment));
            } catch (RuntimeException ex) { LOG.warn("Rejected invalid region packet from {}", player.getUuid()); }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                send(server, handler.player, handler.player.getWorld().getRegistryKey().getValue().toString(), ""));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerData d = data(server); d.assemblers.remove(handler.player.getUuid());
            d.readAt.remove(handler.player.getUuid());
            for (String dimension : d.authority.releaseAll(handler.player.getUuid())) {
                RegionBook book = d.books.get(dimension); if (book != null) book.clearHistory();
                broadcast(server, dimension);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SERVERS.remove(server));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                CommandManager.literal("herebynamed").requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("grant").then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> authorize(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true))))
                    .then(CommandManager.literal("revoke").then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(ctx -> authorize(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false))))));
    }
    private static int authorize(ServerCommandSource source, String name, boolean grant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(name);
        if (player == null) { source.sendError(Text.literal("玩家需要在线")); return 0; }
        ServerData d = data(source.getServer());
        Set<UUID> next = new HashSet<>(d.authorized);
        if (grant) next.add(player.getUuid()); else next.remove(player.getUuid());
        try {
            Files.createDirectories(d.root);
            Path temporary = d.root.resolve("editors.tmp");
            Files.write(temporary, next.stream().map(UUID::toString).sorted().toList());
            try { Files.move(temporary, d.root.resolve("editors.txt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, d.root.resolve("editors.txt"), StandardCopyOption.REPLACE_EXISTING); }
            d.authorized.clear(); d.authorized.addAll(next);
            if (!grant) for (String dim : d.authority.releaseAll(player.getUuid())) {
                d.books.get(dim).clearHistory(); broadcast(source.getServer(), dim);
            }
            for (String dim : List.copyOf(d.books.keySet())) send(source.getServer(), player, dim, "编辑权限已更新");
            source.sendFeedback(() -> Text.literal("此地名曰：已" + (grant ? "授权 " : "撤销授权 ") + name), true); return 1;
        } catch (IOException ex) { LOG.error("Cannot persist region editors", ex); source.sendError(Text.literal("授权保存失败")); return 0; }
    }
    private static void receive(MinecraftServer server, ServerPlayerEntity player, Wire.Fragment fragment) {
        if (player.isDisconnected()) return;
        ServerData d = data(server);
        String dimension = null;
        try {
            Optional<byte[]> bytes = d.assemblers.computeIfAbsent(player.getUuid(), id -> new Wire.Assembler()).accept(fragment);
            if (bytes.isEmpty()) return;
            RegionEdit edit = RegionEdit.decode(bytes.get());
            if (!validDimension(server, edit.dimension())) return;
            dimension = edit.dimension();
            if (edit.action() == RegionEdit.Action.READ) {
                long now = System.nanoTime(), last = d.readAt.getOrDefault(player.getUuid(), 0L);
                if (now - last < 100_000_000L) return;
                d.readAt.put(player.getUuid(), now); send(server, player, edit.dimension(), ""); return;
            }
            RegionBook book = d.book(edit.dimension());
            if (edit.action() == RegionEdit.Action.RELEASE) {
                if (d.authority.canEdit(edit.dimension(), player.getUuid(), true)) {
                    d.authority.release(edit.dimension(), player.getUuid()); book.clearHistory(); broadcast(server, edit.dimension());
                }
                return;
            }
            boolean authorized = authorized(d, player);
            if (edit.action() == RegionEdit.Action.ACQUIRE) {
                boolean already = d.authority.canEdit(edit.dimension(), player.getUuid(), authorized);
                if (!d.authority.acquire(edit.dimension(), player.getUuid(), authorized)) {
                    send(server, player, edit.dimension(), authorized ? "该维度正由其他玩家编辑" : "需要 OP 或编辑授权"); return;
                }
                if (!already) book.clearHistory(); broadcast(server, edit.dimension()); return;
            }
            if (!d.authority.canEdit(edit.dimension(), player.getUuid(), authorized)) {
                send(server, player, edit.dimension(), "当前没有此维度的编辑权"); return;
            }
            if (edit.revision() != d.revisions.getOrDefault(edit.dimension(), 0L)) {
                send(server, player, edit.dimension(), "数据已更新，请重试本次操作"); return;
            }
            RegionBook.State before = book.state();
            try { edit.apply(book); d.store.save(edit.dimension(), book); }
            catch (IOException ex) { book.replace(before); throw ex; }
            d.revisions.merge(edit.dimension(), 1L, Long::sum); broadcast(server, edit.dimension());
        } catch (Exception ex) {
            LOG.warn("Region operation failed for {}", player.getUuid(), ex);
            if (dimension != null) send(server, player, dimension, Objects.toString(ex.getMessage(), "操作失败"));
            else player.sendMessage(Text.literal("此地名曰：无效区域请求"), false);
        }
    }
    private static boolean validDimension(MinecraftServer server, String dimension) {
        return server.getWorldRegistryKeys().stream().anyMatch(key -> key.getValue().toString().equals(dimension));
    }
    private static boolean authorized(ServerData d, ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) || d.authorized.contains(player.getUuid())
                || (player.getServer().isSingleplayer() && player.getServer().isHost(player.getGameProfile()));
    }
    private static void broadcast(MinecraftServer server, String dimension) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) send(server, player, dimension, "");
    }
    private static void send(MinecraftServer server, ServerPlayerEntity player, String dimension, String message) {
        if (!ServerPlayNetworking.canSend(player, Wire.CHANNEL)) return;
        ServerData d = data(server);
        try {
            Snapshot snapshot = new Snapshot(dimension, d.revisions.getOrDefault(dimension, 0L),
                    d.authority.holder(dimension), authorized(d, player), message, d.book(dimension));
            Wire.send(snapshot.encode(), packet -> ServerPlayNetworking.send(player, Wire.CHANNEL, packet));
        } catch (IOException ex) {
            LOG.error("Cannot load/sync named regions", ex);
            player.sendMessage(Text.literal("此地名曰：区域存档读取或同步失败，已保留原文件"), false);
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
}
