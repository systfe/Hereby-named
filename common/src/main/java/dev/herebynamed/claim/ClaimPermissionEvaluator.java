package dev.herebynamed.claim;

import dev.herebynamed.claim.model.Claim;
import dev.herebynamed.claim.model.ClaimRole;
import dev.herebynamed.domain.ChunkCell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class ClaimPermissionEvaluator {
    public static boolean checkPermission(ServerPlayer player, ChunkCell cell, int flagBit, ClaimManager manager) {
        if (manager == null || player == null) return true;
        Claim claim = manager.getClaimAt(player.serverLevel().dimension().location().toString(), cell);
        if (claim == null) return true; // Wilderness allows all
        if (player.hasPermissions(2)) return true; // OP bypass
        if (player.getUUID().equals(claim.owner())) return true; // Owner has all permissions

        ClaimRole role = claim.members().getOrDefault(player.getUUID(), ClaimRole.VISITOR);
        if (role == ClaimRole.ADMIN) return true;
        if (role == ClaimRole.MEMBER) return (claim.memberFlags() & flagBit) != 0;
        return (claim.visitorFlags() & flagBit) != 0;
    }

    public static boolean isEnvironmentProtected(Level level, ChunkCell cell, int flagBit, ClaimManager manager) {
        if (manager == null || level == null) return false;
        Claim claim = manager.getClaimAt(level.dimension().location().toString(), cell);
        if (claim == null) return false;
        return (claim.environmentFlags() & flagBit) != 0;
    }

    private ClaimPermissionEvaluator() {}
}
