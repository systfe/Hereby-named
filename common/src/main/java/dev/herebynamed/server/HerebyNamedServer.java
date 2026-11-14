package dev.herebynamed.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.herebynamed.auto.AutoWork;
import dev.herebynamed.claim.ClaimManager;
import dev.herebynamed.claim.ChunkLoaderManager;
import dev.herebynamed.claim.PlayerClaimBorderTracker;
import dev.herebynamed.claim.model.Claim;
import dev.herebynamed.claim.model.ClaimFlag;
import dev.herebynamed.claim.model.ClaimRole;
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

    public static ClaimManager getClaimManager(MinecraftServer server) {
        if (server == null) return null;
        ServerData d = SERVERS.get(server);
        return d != null ? d.claimManager : null;
    }

    public static ClaimManager getActiveClaimManager() {
        for (ServerData d : SERVERS.values()) {
            if (d != null && d.claimManager != null) return d.claimManager;
        }
        return null;
    }

    private static ServerData data(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, s -> new ServerData(s.getWorldPath(LevelResource.ROOT).resolve("hereby-named"), s));
    }

    public static void onJoin(MinecraftServer server, ServerPlayer player) {
        ChunkLoaderManager.onPlayerReconnect(server, player.getUUID());
        send(server, player, player.serverLevel().dimension().location().toString(), "");
    }

    public static void onDisconnect(MinecraftServer server, ServerPlayer player) {
        PlayerClaimBorderTracker.onPlayerDisconnect(player.getUUID());
        ChunkLoaderManager.onPlayerDisconnect(server, player.getUUID());
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
            d.claimStore.save(d.claimManager, d.claimManager.getDimensions());
            ChunkLoaderManager.clearAll();
        }
    }

    public static void onStopped(MinecraftServer server) {
        SERVERS.remove(server);
    }

    public static void onServerTick(MinecraftServer server) {
        ServerData d = SERVERS.get(server);
        if (d != null) {
            pump(server, d);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerClaimBorderTracker.onPlayerTick(player, d.claimManager);
            }
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("herebynamed").requires(source -> source.hasPermission(2))
                .then(Commands.literal("grant").then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> authorize(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true))))
                .then(Commands.literal("revoke").then(Commands.argument("player", StringArgumentType.word())
                    .executes(ctx -> authorize(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false)))));

        registerClaimCommands(dispatcher);
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

    private static void registerClaimCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("claim")
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> createClaim(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("claim")
                    .executes(ctx -> claimCurrentChunk(ctx.getSource())))
                .then(Commands.literal("info")
                    .executes(ctx -> claimInfo(ctx.getSource())))
                .then(Commands.literal("remove")
                    .executes(ctx -> removeClaim(ctx.getSource())))
                .then(Commands.literal("forceload")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> toggleForceLoad(ctx.getSource(), StringArgumentType.getString(ctx, "value")))
                        .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                            .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .executes(ctx -> toggleForceLoadCoord(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "value"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z")))))))
                .then(Commands.literal("flag")
                    .then(Commands.argument("flag", StringArgumentType.word())
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> toggleFlag(ctx.getSource(), StringArgumentType.getString(ctx, "flag"), BoolArgumentType.getBool(ctx, "value"))))))
                .then(Commands.literal("member")
                    .then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> addMember(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                    .then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> removeMember(ctx.getSource(), StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("config")
                    .then(Commands.literal("reload")
                        .executes(ctx -> reloadClaimConfig(ctx.getSource())))
                    .then(Commands.literal("cost")
                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                            .executes(ctx -> setClaimCost(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"), "minecraft:emerald"))
                            .then(Commands.argument("item", StringArgumentType.word())
                                .executes(ctx -> setClaimCost(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"),
                                        StringArgumentType.getString(ctx, "item"))))))
                    .then(Commands.literal("forceload_cost")
                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                            .executes(ctx -> setForceLoadCost(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"), "minecraft:diamond"))
                            .then(Commands.argument("item", StringArgumentType.word())
                                .executes(ctx -> setForceLoadCost(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"),
                                        StringArgumentType.getString(ctx, "item")))))))
        );
    }

    private static int createClaim(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("仅玩家可执行此命令")); return 0; }
        ServerData d = data(source.getServer());
        ClaimManager mgr = d.claimManager;
        String dim = player.serverLevel().dimension().location().toString();
        ChunkCell cell = new ChunkCell(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        if (mgr.getClaimAt(dim, cell) != null) {
            source.sendFailure(Component.literal("§c该区块已有领地归属！"));
            return 0;
        }
        int playerChunks = mgr.getClaimsByOwner(player.getUUID()).stream().mapToInt(c -> c.chunks().size()).sum();
        if (playerChunks >= d.economyConfig.limits().maxClaimChunksPerPlayer() && !player.hasPermissions(2)) {
            source.sendFailure(Component.literal("§c你的领地区块总数已达上限 (" + d.economyConfig.limits().maxClaimChunksPerPlayer() + ")"));
            return 0;
        }
        if (!dev.herebynamed.claim.ClaimEconomyHelper.checkAndDeduct(player, 1, d.economyConfig.claimCost())) {
            return 0;
        }
        Claim claim = Claim.create(player.getUUID(), name, 0x33aa33, Set.of(cell));
        boolean success = mgr.registerClaim(dim, claim);
        if (success) {
            d.claimStore.save(mgr, mgr.getDimensions());
            syncClaimToBook(source.getServer(), dim, claim);
            source.sendSuccess(() -> Component.literal("§a成功创建领地: §f" + name + " §7(区块: " + cell.x() + ", " + cell.z() + ")"), true);
            return 1;
        } else {
            dev.herebynamed.claim.ClaimEconomyHelper.refund(player, 1, d.economyConfig.claimCost());
            source.sendFailure(Component.literal("§c领地创建失败：区块已被占用"));
            return 0;
        }
    }

    private static int claimCurrentChunk(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("仅玩家可执行此命令")); return 0; }
        ServerData d = data(source.getServer());
        String dim = player.serverLevel().dimension().location().toString();
        ChunkCell cell = new ChunkCell(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        if (d.claimManager.getClaimAt(dim, cell) != null) {
            source.sendFailure(Component.literal("§c该区块已有领地归属！"));
            return 0;
        }
        List<Claim> playerClaims = d.claimManager.getClaimsByOwner(player.getUUID());
        if (playerClaims.isEmpty()) {
            source.sendFailure(Component.literal("§c你尚未创建任何领地，请先使用 /claim create <名称>"));
            return 0;
        }
        Claim targetClaim = playerClaims.get(playerClaims.size() - 1);
        int totalLoaded = playerClaims.stream().mapToInt(c -> c.chunks().size()).sum();
        if (totalLoaded >= d.economyConfig.limits().maxClaimChunksPerPlayer() && !player.hasPermissions(2)) {
            source.sendFailure(Component.literal("§c你拥有的领地区块总数已达上限 (" + d.economyConfig.limits().maxClaimChunksPerPlayer() + ")"));
            return 0;
        }
        if (!dev.herebynamed.claim.ClaimEconomyHelper.checkAndDeduct(player, 1, d.economyConfig.claimCost())) {
            return 0;
        }
        Set<ChunkCell> newChunks = new HashSet<>(targetClaim.chunks());
        newChunks.add(cell);
        Claim updated = targetClaim.withChunks(newChunks);
        boolean success = d.claimManager.registerClaim(dim, updated);
        if (success) {
            d.claimStore.save(d.claimManager, d.claimManager.getDimensions());
            syncClaimToBook(source.getServer(), dim, updated);
            source.sendSuccess(() -> Component.literal("§a成功将区块 (" + cell.x() + ", " + cell.z() + ") 划入领地: §f" + targetClaim.name()), true);
            return 1;
        } else {
            dev.herebynamed.claim.ClaimEconomyHelper.refund(player, 1, d.economyConfig.claimCost());
            source.sendFailure(Component.literal("§c领地扩张失败：区块冲突"));
            return 0;
        }
    }

    private static int claimInfo(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("仅玩家可执行此命令")); return 0; }
        ClaimManager mgr = getClaimManager(source.getServer());
        if (mgr == null) return 0;
        String dim = player.serverLevel().dimension().location().toString();
        ChunkCell cell = new ChunkCell(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        Claim claim = mgr.getClaimAt(dim, cell);
        ServerData d = data(source.getServer());
        if (claim == null) {
            String costMsg = d.economyConfig.claimCost().enabled()
                    ? ("§e圈地/扩张费用: §f每区块 " + d.economyConfig.claimCost().amountPerChunk() + " 个 " + d.economyConfig.claimCost().item())
                    : "§7圈地费用: §a免费";
            source.sendSuccess(() -> Component.literal("§7当前所在位置属于无主荒野 (区块: " + cell.x() + ", " + cell.z() + ") · " + costMsg), false);
            return 1;
        }
        String ownerName = mgr.resolvePlayerName(source.getServer(), claim.owner());
        source.sendSuccess(() -> Component.literal("§6=== 领地信息 ==="), false);
        source.sendSuccess(() -> Component.literal("§e名称: §f" + claim.name() + " §7| §e领主: §a" + ownerName), false);
        source.sendSuccess(() -> Component.literal("§e涵盖区块: §f" + claim.chunks().size() + " §7| §e信任成员: §f" + claim.members().size()), false);
        source.sendSuccess(() -> Component.literal("§e访客权限: §7" + formatFlags(claim.visitorFlags())), false);
        source.sendSuccess(() -> Component.literal("§e常加载: §f" + (claim.forceLoaded() ? "§a开启" : "§c关闭")), false);
        return 1;
    }

    private static int reloadClaimConfig(CommandSourceStack source) {
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("§c只有管理员可以重载领地经济配置！"));
            return 0;
        }
        ServerData d = data(source.getServer());
        d.economyConfig = d.claimStore.loadConfig();
        source.sendSuccess(() -> Component.literal("§a已从 claims_config.json 重新加载领地与经济配置！"), true);
        return 1;
    }

    private static int setClaimCost(CommandSourceStack source, int amount, String itemId) {
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("§c只有管理员可以修改圈地费用！"));
            return 0;
        }
        ServerData d = data(source.getServer());
        boolean enabled = amount > 0;
        dev.herebynamed.claim.model.ClaimEconomyConfig.ClaimCost nextCost = new dev.herebynamed.claim.model.ClaimEconomyConfig.ClaimCost(
                enabled,
                itemId,
                amount,
                d.economyConfig.claimCost().adminBypass(),
                d.economyConfig.claimCost().refundOnUnclaim()
        );
        d.economyConfig = new dev.herebynamed.claim.model.ClaimEconomyConfig(
                nextCost,
                d.economyConfig.limits(),
                d.economyConfig.forceLoad()
        );
        d.claimStore.saveConfig(d.economyConfig);
        source.sendSuccess(() -> Component.literal("§a已将领地圈地/扩张费用设置为每个区块 " + amount + " 个 " + itemId + (enabled ? "（已启用）" : "（已禁用）")), true);
        return 1;
    }

    private static int setForceLoadCost(CommandSourceStack source, int amount, String itemId) {
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("§c只有管理员可以修改常加载费用！"));
            return 0;
        }
        ServerData d = data(source.getServer());
        boolean enabled = amount > 0;
        dev.herebynamed.claim.model.ClaimEconomyConfig.ForceLoad nextForce = new dev.herebynamed.claim.model.ClaimEconomyConfig.ForceLoad(
                enabled,
                itemId,
                amount,
                d.economyConfig.forceLoad().onlyWhileOnline()
        );
        d.economyConfig = new dev.herebynamed.claim.model.ClaimEconomyConfig(
                d.economyConfig.claimCost(),
                d.economyConfig.limits(),
                nextForce
        );
        d.claimStore.saveConfig(d.economyConfig);
        source.sendSuccess(() -> Component.literal("§a已将常加载费用设置为每个区块 " + amount + " 个 " + itemId + (enabled ? "（已启用）" : "（已禁用）")), true);
        return 1;
    }

    private static String formatFlags(int flags) {
        List<String> active = new ArrayList<>();
        if ((flags & ClaimFlag.BREAK) != 0) active.add("破坏");
        if ((flags & ClaimFlag.PLACE) != 0) active.add("放置");
        if ((flags & ClaimFlag.INTERACT) != 0) active.add("交互");
        if ((flags & ClaimFlag.CONTAINER) != 0) active.add("容器");
        if ((flags & ClaimFlag.PVP) != 0) active.add("PvP");
        if ((flags & ClaimFlag.ANIMAL_PROTECT) != 0) active.add("保护动物");
        if ((flags & ClaimFlag.EXPLOSION_PROOF) != 0) active.add("防爆");
        if ((flags & ClaimFlag.FIRE_SPREAD) != 0) active.add("防火");
        if ((flags & ClaimFlag.ENTER) != 0) active.add("允许进入");
        return active.isEmpty() ? "全禁用" : String.join(", ", active);
    }

    private static int removeClaim(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("仅玩家可执行此命令")); return 0; }
        ServerData d = data(source.getServer());
        ClaimManager mgr = d.claimManager;
        String dim = player.serverLevel().dimension().location().toString();
        ChunkCell cell = new ChunkCell(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        Claim claim = mgr.getClaimAt(dim, cell);
        if (claim == null) { source.sendFailure(Component.literal("§c当前位置没有领地！")); return 0; }
        if (!player.getUUID().equals(claim.owner()) && !player.hasPermissions(2)) {
            source.sendFailure(Component.literal("§c只有领主或OP管理员可以解散领地！")); return 0;
        }
        for (ChunkCell c : claim.chunks()) {
            dev.herebynamed.claim.ChunkLoaderManager.setForceLoaded(player.serverLevel(), claim.owner(), c, false);
        }
        mgr.evictClaim(claim, dim);
        removeClaimFromBook(source.getServer(), dim, claim);
        dev.herebynamed.claim.ClaimEconomyHelper.refund(player, claim.chunks().size(), d.economyConfig.claimCost());
        d.claimStore.save(mgr, mgr.getDimensions());
        source.sendSuccess(() -> Component.literal("§a已解散领地: §f" + claim.name()), true);
        return 1;
    }

    private static int toggleForceLoad(CommandSourceStack source, String valStr) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("仅玩家可执行此命令")); return 0; }
        return toggleForceLoadCoord(source, valStr, player.getBlockX() >> 4, player.getBlockZ() >> 4);
    }

    private static int toggleForceLoadCoord(CommandSourceStack source, String valStr, int chunkX, int chunkZ) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("仅玩家可执行此命令")); return 0; }
        ServerData d = data(source.getServer());
        String dim = player.serverLevel().dimension().location().toString();
        ChunkCell cell = new ChunkCell(chunkX, chunkZ);
        Claim claim = d.claimManager.getClaimAt(dim, cell);
        if (claim != null && !player.getUUID().equals(claim.owner()) && !player.hasPermissions(2)) {
            source.sendFailure(Component.literal("§c该区块属于他人领地，只有领主可以配置常加载！")); return 0;
        }
        boolean isCurrentlyLoaded = dev.herebynamed.claim.ChunkLoaderManager.isForceLoaded(player.getUUID(), dim, cell);
        boolean targetVal = "toggle".equalsIgnoreCase(valStr) ? !isCurrentlyLoaded : Boolean.parseBoolean(valStr);

        if (targetVal == isCurrentlyLoaded) {
            source.sendSuccess(() -> Component.literal("§7区块 (" + chunkX + ", " + chunkZ + ") 常加载状态未改变"), false);
            return 1;
        }

        if (targetVal) {
            if (!d.economyConfig.forceLoad().enabled() && !player.hasPermissions(2)) {
                source.sendFailure(Component.literal("§c常加载功能已被服务器禁用！"));
                return 0;
            }
            int playerLoaded = dev.herebynamed.claim.ChunkLoaderManager.getPlayerLoadedCount(player.getUUID());
            if (playerLoaded >= d.economyConfig.limits().maxForceLoadedChunksPerPlayer() && !player.hasPermissions(2)) {
                source.sendFailure(Component.literal("§c你的常加载区块配额已达上限 (" + d.economyConfig.limits().maxForceLoadedChunksPerPlayer() + ")"));
                return 0;
            }
            int globalLoaded = dev.herebynamed.claim.ChunkLoaderManager.getGlobalLoadedCount();
            if (globalLoaded >= d.economyConfig.limits().maxGlobalForceLoadedChunks() && !player.hasPermissions(2)) {
                source.sendFailure(Component.literal("§c全服常加载区块总数已达上限 (" + d.economyConfig.limits().maxGlobalForceLoadedChunks() + ")"));
                return 0;
            }
            if (!dev.herebynamed.claim.ClaimEconomyHelper.checkAndDeduct(player, 1, new dev.herebynamed.claim.model.ClaimEconomyConfig.ClaimCost(
                    d.economyConfig.forceLoad().enabled(),
                    d.economyConfig.forceLoad().costItem(),
                    d.economyConfig.forceLoad().costAmountPerChunk(),
                    true, false))) {
                return 0;
            }
        }
        dev.herebynamed.claim.ChunkLoaderManager.setForceLoaded(player.serverLevel(), player.getUUID(), cell, targetVal);
        dev.herebynamed.claim.ChunkLoaderManager.save(d.root);
        if (claim != null) {
            Claim updated = claim.withForceLoaded(targetVal);
            d.claimManager.registerClaim(dim, updated);
            d.claimStore.save(d.claimManager, d.claimManager.getDimensions());
            syncClaimToBook(source.getServer(), dim, updated);
        } else {
            syncWildForceLoadsToBook(source.getServer(), dim, player.getUUID());
        }
        source.sendSuccess(() -> Component.literal("§a已" + (targetVal ? "开启" : "关闭") + "区块 (" + chunkX + ", " + chunkZ + ") 常加载！"), true);
        return 1;
    }

    private static int toggleFlag(CommandSourceStack source, String flagName, boolean value) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("仅玩家可执行此命令")); return 0; }
        ServerData d = data(source.getServer());
        ClaimManager mgr = d.claimManager;
        String dim = player.serverLevel().dimension().location().toString();
        ChunkCell cell = new ChunkCell(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        Claim claim = mgr.getClaimAt(dim, cell);
        if (claim == null) { source.sendFailure(Component.literal("§c当前位置没有领地！")); return 0; }
        if (!player.getUUID().equals(claim.owner()) && !player.hasPermissions(2)) {
            source.sendFailure(Component.literal("§c只有领主可以修改领地标志位！")); return 0;
        }
        int bit = ClaimFlag.parseFlag(flagName);
        if (bit == 0) {
            source.sendFailure(Component.literal("§c未知标志位！可用：break, place, interact, container, pvp, animal, explosion, fire, enter"));
            return 0;
        }
        int cur = claim.visitorFlags();
        int next = value ? (cur | bit) : (cur & ~bit);
        Claim updated = claim.withVisitorFlags(next);
        if (bit == ClaimFlag.EXPLOSION_PROOF || bit == ClaimFlag.FIRE_SPREAD) {
            int envCur = claim.environmentFlags();
            int envNext = value ? (envCur | bit) : (envCur & ~bit);
            updated = updated.withEnvironmentFlags(envNext);
        }
        mgr.registerClaim(dim, updated);
        d.claimStore.save(mgr, mgr.getDimensions());
        syncClaimToBook(source.getServer(), dim, updated);
        source.sendSuccess(() -> Component.literal("§a已更新标志位 §e" + flagName + " §a为 §f" + value), true);
        return 1;
    }

    private static int addMember(CommandSourceStack source, String targetName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("仅玩家可执行此命令")); return 0; }
        ServerData d = data(source.getServer());
        ClaimManager mgr = d.claimManager;
        String dim = player.serverLevel().dimension().location().toString();
        ChunkCell cell = new ChunkCell(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        Claim claim = mgr.getClaimAt(dim, cell);
        if (claim == null) { source.sendFailure(Component.literal("§c当前位置没有领地！")); return 0; }
        if (!player.getUUID().equals(claim.owner()) && !player.hasPermissions(2)) {
            source.sendFailure(Component.literal("§c只有领主可以添加成员！")); return 0;
        }
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) { source.sendFailure(Component.literal("§c目标玩家需要在线！")); return 0; }
        Claim updated = claim.withMember(target.getUUID(), ClaimRole.MEMBER);
        mgr.registerClaim(dim, updated);
        d.claimStore.save(mgr, mgr.getDimensions());
        syncClaimToBook(source.getServer(), dim, updated);
        source.sendSuccess(() -> Component.literal("§a已将 §f" + targetName + " §a添加为领地信任成员"), true);
        return 1;
    }

    private static int removeMember(CommandSourceStack source, String targetName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("仅玩家可执行此命令")); return 0; }
        ServerData d = data(source.getServer());
        ClaimManager mgr = d.claimManager;
        String dim = player.serverLevel().dimension().location().toString();
        ChunkCell cell = new ChunkCell(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        Claim claim = mgr.getClaimAt(dim, cell);
        if (claim == null) { source.sendFailure(Component.literal("§c当前位置没有领地！")); return 0; }
        if (!player.getUUID().equals(claim.owner()) && !player.hasPermissions(2)) {
            source.sendFailure(Component.literal("§c只有领主可以移除成员！")); return 0;
        }
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        UUID targetId = target != null ? target.getUUID() : null;
        if (targetId == null) {
            for (UUID id : claim.members().keySet()) {
                if (mgr.resolvePlayerName(source.getServer(), id).equalsIgnoreCase(targetName)) {
                    targetId = id;
                    break;
                }
            }
        }
        if (targetId == null) { source.sendFailure(Component.literal("§c未找到该成员！")); return 0; }
        Claim updated = claim.withoutMember(targetId);
        mgr.registerClaim(dim, updated);
        d.claimStore.save(mgr, mgr.getDimensions());
        syncClaimToBook(source.getServer(), dim, updated);
        source.sendSuccess(() -> Component.literal("§a已将 §f" + targetName + " §a移出领地成员"), true);
        return 1;
    }

    public static void syncClaimToBook(MinecraftServer server, String dim, Claim claim) {
        if (server == null || dim == null || claim == null) return;
        ServerData d = data(server);
        try {
            RegionBook book = d.book(dim);
            String regionId = "claim-" + claim.id().toString();
            String ownerName = d.claimManager.resolvePlayerName(server, claim.owner());
            String title = claim.name() + " [领主: " + ownerName + "]";
            var existing = book.region(regionId);
            if (existing.isPresent()) {
                book.renameRegion(regionId, title, claim.color());
                book.paint(regionId, claim.chunks(), true);
            } else {
                List<RegionBook.Region> regs = new ArrayList<>(book.regions());
                regs.add(new RegionBook.Region(regionId, 1, title, claim.color(), claim.chunks(), null));
                book.replace(new RegionBook.State(book.state().highestLayer(), book.state().layers(), regs, book.state().suppressed()));
            }
            d.revisions.put(dim, d.revisions.getOrDefault(dim, 0L) + 1);
            broadcast(server, dim);
        } catch (Exception ex) {
            LOG.error("Failed to sync claim to book", ex);
        }
    }

    public static void removeClaimFromBook(MinecraftServer server, String dim, Claim claim) {
        if (server == null || dim == null || claim == null) return;
        ServerData d = data(server);
        try {
            RegionBook book = d.book(dim);
            String regionId = "claim-" + claim.id().toString();
            book.deleteRegion(regionId, false);
            d.revisions.put(dim, d.revisions.getOrDefault(dim, 0L) + 1);
            broadcast(server, dim);
        } catch (Exception ex) {
            LOG.error("Failed to remove claim from book", ex);
        }
    }

    public static void syncWildForceLoadsToBook(MinecraftServer server, String dim, UUID playerUuid) {
        if (server == null || dim == null || playerUuid == null) return;
        ServerData d = data(server);
        try {
            RegionBook book = d.book(dim);
            String regionId = "loader-" + playerUuid.toString();
            Set<ChunkCell> allPlayerCells = dev.herebynamed.claim.ChunkLoaderManager.getPlayerCells(playerUuid, dim);
            Set<ChunkCell> wildCells = new HashSet<>();
            for (ChunkCell c : allPlayerCells) {
                if (d.claimManager.getClaimAt(dim, c) == null) {
                    wildCells.add(c);
                }
            }
            if (wildCells.isEmpty()) {
                book.deleteRegion(regionId, false);
            } else {
                String playerName = d.claimManager.resolvePlayerName(server, playerUuid);
                String title = "常加载区块 [玩家: " + playerName + "]";
                int color = 0x00bcd4;
                var existing = book.region(regionId);
                if (existing.isPresent()) {
                    book.renameRegion(regionId, title, color);
                    book.paint(regionId, wildCells, true);
                } else {
                    List<RegionBook.Region> regs = new ArrayList<>(book.regions());
                    regs.add(new RegionBook.Region(regionId, 1, title, color, wildCells, null));
                    book.replace(new RegionBook.State(book.state().highestLayer(), book.state().layers(), regs, book.state().suppressed()));
                }
            }
            d.revisions.put(dim, d.revisions.getOrDefault(dim, 0L) + 1);
            broadcast(server, dim);
        } catch (Exception ex) {
            LOG.error("Failed to sync wild chunkloaders to book", ex);
        }
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
        final dev.herebynamed.claim.ClaimStore claimStore;
        volatile dev.herebynamed.claim.model.ClaimEconomyConfig economyConfig;
        final EditAuthority authority = new EditAuthority();
        final ClaimManager claimManager = new ClaimManager();
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
        ServerData(Path root, MinecraftServer server) {
            this.root = root;
            this.store = new RegionStore(root.resolve("regions"));
            this.claimStore = new dev.herebynamed.claim.ClaimStore(root);
            this.claimStore.load(this.claimManager);
            this.economyConfig = this.claimStore.loadConfig();
            dev.herebynamed.claim.ChunkLoaderManager.load(root, server);
            if (!this.economyConfig.forceLoad().onlyWhileOnline()) {
                dev.herebynamed.claim.ChunkLoaderManager.activateAll(server);
            }
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
