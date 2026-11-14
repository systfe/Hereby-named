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

    public static Set<ChunkCell> getPlayerCells(UUID player, String dim) {
        Map<String, Set<ChunkCell>> playerDims = ACTIVE_LOADERS.get(player);
        if (playerDims == null) return Collections.emptySet();
        Set<ChunkCell> cells = playerDims.get(dim);
        return cells != null ? Collections.unmodifiableSet(cells) : Collections.emptySet();
    }

    public static void save(java.nio.file.Path root) {
        try {
            java.nio.file.Files.createDirectories(root);
            java.nio.file.Path file = root.resolve("chunk_loaders.json");
            com.google.gson.JsonObject rootObj = new com.google.gson.JsonObject();
            for (Map.Entry<UUID, Map<String, Set<ChunkCell>>> pEntry : ACTIVE_LOADERS.entrySet()) {
                com.google.gson.JsonObject pObj = new com.google.gson.JsonObject();
                for (Map.Entry<String, Set<ChunkCell>> dEntry : pEntry.getValue().entrySet()) {
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    for (ChunkCell c : dEntry.getValue()) {
                        com.google.gson.JsonArray pair = new com.google.gson.JsonArray();
                        pair.add(c.x());
                        pair.add(c.z());
                        arr.add(pair);
                    }
                    pObj.add(dEntry.getKey(), arr);
                }
                rootObj.add(pEntry.getKey().toString(), pObj);
            }
            java.nio.file.Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            java.nio.file.Files.writeString(temp, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rootObj), java.nio.charset.StandardCharsets.UTF_8);
            try {
                java.nio.file.Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                java.nio.file.Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            dev.herebynamed.HerebyNamed.LOG.error("Failed to save chunk loaders", ex);
        }
    }

    public static void load(java.nio.file.Path root, net.minecraft.server.MinecraftServer server) {
        java.nio.file.Path file = root.resolve("chunk_loaders.json");
        if (!java.nio.file.Files.exists(file)) return;
        try {
            String json = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject rootObj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (String uuidStr : rootObj.keySet()) {
                UUID player = UUID.fromString(uuidStr);
                com.google.gson.JsonObject pObj = rootObj.getAsJsonObject(uuidStr);
                Map<String, Set<ChunkCell>> playerDims = ACTIVE_LOADERS.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
                for (String dim : pObj.keySet()) {
                    Set<ChunkCell> set = playerDims.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet());
                    com.google.gson.JsonArray arr = pObj.getAsJsonArray(dim);
                    for (com.google.gson.JsonElement el : arr) {
                        com.google.gson.JsonArray pair = el.getAsJsonArray();
                        set.add(new ChunkCell(pair.get(0).getAsInt(), pair.get(1).getAsInt()));
                    }
                }
            }
        } catch (Exception ex) {
            dev.herebynamed.HerebyNamed.LOG.error("Failed to load chunk loaders", ex);
        }
    }

    public static void activateAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (Map<String, Set<ChunkCell>> pDims : ACTIVE_LOADERS.values()) {
            for (Map.Entry<String, Set<ChunkCell>> entry : pDims.entrySet()) {
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
    }

    public static void clearAll() {
        ACTIVE_LOADERS.clear();
    }

    private ChunkLoaderManager() {}
}
