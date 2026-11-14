package dev.herebynamed.claim;

import dev.herebynamed.claim.model.Claim;
import dev.herebynamed.claim.model.ClaimFlag;
import dev.herebynamed.domain.ChunkCell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerClaimBorderTracker {
    private static final Map<UUID, ChunkCell> LAST_CHUNKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_NOTICE_TICKS = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_TICK_WINDOW = 60L; // 3 seconds debounce

    public static void onPlayerTick(ServerPlayer player, ClaimManager manager) {
        if (player == null || manager == null) return;
        int currentChunkX = player.getBlockX() >> 4;
        int currentChunkZ = player.getBlockZ() >> 4;
        ChunkCell currentCell = new ChunkCell(currentChunkX, currentChunkZ);
        ChunkCell previousCell = LAST_CHUNKS.get(player.getUUID());

        if (currentCell.equals(previousCell)) return;

        String dimension = player.serverLevel().dimension().location().toString();
        Claim toClaim = manager.getClaimAt(dimension, currentCell);

        // Check ENTER permission
        if (toClaim != null && !ClaimPermissionEvaluator.checkPermission(player, currentCell, ClaimFlag.ENTER, manager)) {
            ChunkCell fallback = previousCell != null ? previousCell : new ChunkCell(player.getBlockX() >> 4, player.getBlockZ() >> 4);
            double backX = (fallback.x() << 4) + 8.0;
            double backZ = (fallback.z() << 4) + 8.0;
            player.teleportTo(backX, player.getY(), backZ);
            player.setDeltaMovement(Vec3.ZERO);
            player.sendSystemMessage(Component.literal("§c[领地] 该领地禁止外来访客进入！"), true);
            return;
        }

        LAST_CHUNKS.put(player.getUUID(), currentCell);

        Claim fromClaim = previousCell != null ? manager.getClaimAt(dimension, previousCell) : null;
        UUID fromId = fromClaim != null ? fromClaim.id() : null;
        UUID toId = toClaim != null ? toClaim.id() : null;
        if (Objects.equals(fromId, toId)) return;

        long currentTick = player.server.getTickCount();
        long lastTick = LAST_NOTICE_TICKS.getOrDefault(player.getUUID(), 0L);
        if (currentTick - lastTick < DEBOUNCE_TICK_WINDOW) {
            return;
        }
        LAST_NOTICE_TICKS.put(player.getUUID(), currentTick);

        Component message;
        if (toClaim != null) {
            String ownerName = manager.resolvePlayerName(player.server, toClaim.owner());
            message = Component.literal("§6[领地] §f进入 §e" + toClaim.name() + " §7(领主: §a" + ownerName + "§7)");
        } else {
            message = Component.literal("§6[领地] §f离开 §e" + (fromClaim != null ? fromClaim.name() : "") + "§7，进入荒野");
        }

        player.sendSystemMessage(message, true);
    }

    public static void onPlayerDisconnect(UUID playerId) {
        LAST_CHUNKS.remove(playerId);
        LAST_NOTICE_TICKS.remove(playerId);
    }

    private PlayerClaimBorderTracker() {}
}
