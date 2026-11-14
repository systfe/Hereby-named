package dev.herebynamed.claim;

import dev.herebynamed.domain.ChunkCell;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkLoaderManager {
    public static final TicketType<ChunkPos> HEREBY_LOADER =
            TicketType.create("hereby_named_loader", Comparator.comparingLong(ChunkPos::toLong));

    private static final Map<UUID, Map<String, Set<ChunkCell>>> ACTIVE_LOADERS = new ConcurrentHashMap<>();

    public static boolean setForceLoaded(ServerLevel level, UUID player, ChunkCell cell, boolean forceLoaded) {
        String dim = level.dimension().location().toString();
        ChunkPos pos = new ChunkPos(cell.x(), cell.z());
        Map<String, Set<ChunkCell>> playerDims = ACTIVE_LOADERS.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        Set<ChunkCell> cells = playerDims.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet());

        if (forceLoaded) {
            cells.add(cell);
            level.getChunkSource().addRegionTicket(HEREBY_LOADER, pos, 2, pos);
            return true;
        } else {
            cells.remove(cell);
            level.getChunkSource().removeRegionTicket(HEREBY_LOADER, pos, 2, pos);
            return false;
        }
    }

    public static boolean isForceLoaded(UUID player, String dim, ChunkCell cell) {
        Map<String, Set<ChunkCell>> playerDims = ACTIVE_LOADERS.get(player);
        if (playerDims == null) return false;
        Set<ChunkCell> cells = playerDims.get(dim);
        return cells != null && cells.contains(cell);
    }

    public static int getPlayerLoadedCount(UUID player) {
        Map<String, Set<ChunkCell>> playerDims = ACTIVE_LOADERS.get(player);
        if (playerDims == null) return 0;
        int count = 0;
        for (Set<ChunkCell> set : playerDims.values()) {
            count += set.size();
        }
        return count;
    }

    public static int getGlobalLoadedCount() {
        int count = 0;
        for (Map<String, Set<ChunkCell>> playerDims : ACTIVE_LOADERS.values()) {
            for (Set<ChunkCell> set : playerDims.values()) {
                count += set.size();
            }
        }
        return count;
    }

    public static void onPlayerDisconnect(net.minecraft.server.MinecraftServer server, UUID player) {
        Map<String, Set<ChunkCell>> playerDims = ACTIVE_LOADERS.get(player);
        if (playerDims == null || server == null) return;
        for (Map.Entry<String, Set<ChunkCell>> entry : playerDims.entrySet()) {
            ResourceLocation loc = ResourceLocation.tryParse(entry.getKey());
            if (loc == null) continue;
            ServerLevel dimLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, loc));
            if (dimLevel != null) {
                for (ChunkCell cell : entry.getValue()) {
                    ChunkPos pos = new ChunkPos(cell.x(), cell.z());
                    dimLevel.getChunkSource().removeRegionTicket(HEREBY_LOADER, pos, 2, pos);
                }
            }
        }
    }

    public static void onPlayerReconnect(net.minecraft.server.MinecraftServer server, UUID player) {
        Map<String, Set<ChunkCell>> playerDims = ACTIVE_LOADERS.get(player);
        if (playerDims == null || server == null) return;
        for (Map.Entry<String, Set<ChunkCell>> entry : playerDims.entrySet()) {
            ResourceLocation loc = ResourceLocation.tryParse(entry.getKey());
            if (loc == null) continue;
            ServerLevel dimLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, loc));
            if (dimLevel != null) {
                for (ChunkCell cell : entry.getValue()) {
                    ChunkPos pos = new ChunkPos(cell.x(), cell.z());
                    dimLevel.getChunkSource().addRegionTicket(HEREBY_LOADER, pos, 2, pos);
                }
            }
        }
    }

    public static void clearAll() {
        ACTIVE_LOADERS.clear();
    }

    private ChunkLoaderManager() {}
}
