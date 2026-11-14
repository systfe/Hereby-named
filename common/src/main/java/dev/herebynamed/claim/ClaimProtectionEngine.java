package dev.herebynamed.claim;

import dev.herebynamed.claim.model.ClaimFlag;
import dev.herebynamed.domain.ChunkCell;
import dev.herebynamed.server.HerebyNamedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public final class ClaimProtectionEngine {
    private static void notifyDenied(ServerPlayer player, String action) {
        if (player != null) {
            player.sendSystemMessage(Component.literal("§c[领地] 你没有在此处" + action + "的权限！"), true);
        }
    }

    public static boolean canBreak(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return true;
        ClaimManager mgr = HerebyNamedServer.getClaimManager(player.getServer());
        ChunkCell cell = new ChunkCell(pos.getX() >> 4, pos.getZ() >> 4);
        boolean allowed = ClaimPermissionEvaluator.checkPermission(player, cell, ClaimFlag.BREAK, mgr);
        if (!allowed) notifyDenied(player, "破坏方块");
        return allowed;
    }

    public static boolean canPlace(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return true;
        ClaimManager mgr = HerebyNamedServer.getClaimManager(player.getServer());
        ChunkCell cell = new ChunkCell(pos.getX() >> 4, pos.getZ() >> 4);
        boolean allowed = ClaimPermissionEvaluator.checkPermission(player, cell, ClaimFlag.PLACE, mgr);
        if (!allowed) notifyDenied(player, "放置方块");
        return allowed;
    }

    public static boolean canInteractBlock(ServerPlayer player, BlockPos pos, BlockState state) {
        if (player == null || pos == null) return true;
        ClaimManager mgr = HerebyNamedServer.getClaimManager(player.getServer());
        ChunkCell cell = new ChunkCell(pos.getX() >> 4, pos.getZ() >> 4);

        if (state != null && (state.hasBlockEntity() || state.getMenuProvider(player.level(), pos) != null)) {
            boolean allowed = ClaimPermissionEvaluator.checkPermission(player, cell, ClaimFlag.CONTAINER, mgr);
            if (!allowed) notifyDenied(player, "使用容器");
            return allowed;
        }

        if (state != null) {
            Block block = state.getBlock();
            if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                    || block instanceof ButtonBlock || block instanceof LeverBlock) {
                boolean allowed = ClaimPermissionEvaluator.checkPermission(player, cell, ClaimFlag.INTERACT, mgr);
                if (!allowed) notifyDenied(player, "操作机关");
                return allowed;
            }
        }

        return true;
    }

    public static boolean canOpenContainer(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return true;
        ClaimManager mgr = HerebyNamedServer.getClaimManager(player.getServer());
        ChunkCell cell = new ChunkCell(pos.getX() >> 4, pos.getZ() >> 4);
        boolean allowed = ClaimPermissionEvaluator.checkPermission(player, cell, ClaimFlag.CONTAINER, mgr);
        if (!allowed) notifyDenied(player, "使用容器");
        return allowed;
    }

    public static boolean canAttackEntity(ServerPlayer attacker, Entity target) {
        if (attacker == null || target == null) return true;
        ClaimManager mgr = HerebyNamedServer.getClaimManager(attacker.getServer());
        ChunkCell cell = new ChunkCell(target.getBlockX() >> 4, target.getBlockZ() >> 4);

        if (target instanceof Player) {
            boolean allowed = ClaimPermissionEvaluator.checkPermission(attacker, cell, ClaimFlag.PVP, mgr);
            if (!allowed) notifyDenied(attacker, "攻击玩家 (PvP 已关闭)");
            return allowed;
        }

        if (target instanceof Animal || target instanceof Villager) {
            boolean allowed = ClaimPermissionEvaluator.checkPermission(attacker, cell, ClaimFlag.ANIMAL_PROTECT, mgr);
            if (!allowed) notifyDenied(attacker, "伤害动物/村民");
            return allowed;
        }

        return true;
    }

    public static boolean canExplode(Level level, BlockPos pos) {
        if (level == null || pos == null) return true;
        ClaimManager mgr = HerebyNamedServer.getActiveClaimManager();
        ChunkCell cell = new ChunkCell(pos.getX() >> 4, pos.getZ() >> 4);
        return !ClaimPermissionEvaluator.isEnvironmentProtected(level, cell, ClaimFlag.EXPLOSION_PROOF, mgr);
    }

    public static boolean canFireSpread(Level level, BlockPos pos) {
        if (level == null || pos == null) return true;
        ClaimManager mgr = HerebyNamedServer.getActiveClaimManager();
        ChunkCell cell = new ChunkCell(pos.getX() >> 4, pos.getZ() >> 4);
        return !ClaimPermissionEvaluator.isEnvironmentProtected(level, cell, ClaimFlag.FIRE_SPREAD, mgr);
    }

    private ClaimProtectionEngine() {}
}
