package dev.herebynamed.claim;

import com.google.gson.*;
import dev.herebynamed.claim.model.Claim;
import dev.herebynamed.claim.model.ClaimEconomyConfig;
import dev.herebynamed.claim.model.ClaimRole;
import dev.herebynamed.domain.ChunkCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ClaimStore {
    private static final Logger LOG = LoggerFactory.getLogger("hereby_named_claims");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path root;
    private final Path claimsFile;
    private final Path configFile;

    public ClaimStore(Path root) {
        this.root = root;
        this.claimsFile = root.resolve("claims.json");
        this.configFile = root.resolve("claims_config.json");
    }

    public void load(ClaimManager manager) {
        if (!Files.exists(claimsFile)) return;
        try {
            String json = Files.readString(claimsFile, StandardCharsets.UTF_8);
            JsonObject rootObj = JsonParser.parseString(json).getAsJsonObject();
            JsonObject dims = rootObj.has("dimensions") ? rootObj.getAsJsonObject("dimensions") : new JsonObject();
            for (String dim : dims.keySet()) {
                JsonArray arr = dims.getAsJsonArray(dim);
                for (JsonElement el : arr) {
                    try {
                        Claim c = parseClaim(el.getAsJsonObject());
                        if (c != null) {
                            manager.registerClaim(dim, c);
                        }
                    } catch (Exception ex) {
                        LOG.error("Failed to parse claim in dimension {}", dim, ex);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Cannot load claims from {}", claimsFile, ex);
        }
    }

    public void save(ClaimManager manager, Set<String> dimensions) {
        try {
            Files.createDirectories(root);
            JsonObject rootObj = new JsonObject();
            rootObj.addProperty("version", 1);
            JsonObject dims = new JsonObject();

            for (String dim : dimensions) {
                Collection<Claim> list = manager.allClaims(dim);
                JsonArray arr = new JsonArray();
                for (Claim c : list) {
                    arr.add(serializeClaim(c));
                }
                dims.add(dim, arr);
            }
            rootObj.add("dimensions", dims);

            Path temp = claimsFile.resolveSibling(claimsFile.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(rootObj), StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temp, claimsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, claimsFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            LOG.error("Cannot save claims to {}", claimsFile, ex);
        }
    }

    public ClaimEconomyConfig loadConfig() {
        if (!Files.exists(configFile)) {
            ClaimEconomyConfig def = ClaimEconomyConfig.DEFAULT;
            saveConfig(def);
            return def;
        }
        try {
            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            ClaimEconomyConfig config = GSON.fromJson(json, ClaimEconomyConfig.class);
            return config != null ? config : ClaimEconomyConfig.DEFAULT;
        } catch (Exception ex) {
            LOG.error("Cannot load claim config, using defaults", ex);
            return ClaimEconomyConfig.DEFAULT;
        }
    }

    public void saveConfig(ClaimEconomyConfig config) {
        try {
            Files.createDirectories(root);
            Path temp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(config), StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temp, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            LOG.error("Cannot save claims config", ex);
        }
    }

    private static Claim parseClaim(JsonObject obj) {
        UUID id = UUID.fromString(obj.get("id").getAsString());
        UUID owner = UUID.fromString(obj.get("owner").getAsString());
        String name = obj.get("name").getAsString();
        int color = obj.has("color") ? obj.get("color").getAsInt() : 0xFFFFFF;
        long createdTime = obj.has("createdTime") ? obj.get("createdTime").getAsLong() : System.currentTimeMillis();
        boolean forceLoaded = obj.has("forceLoaded") && obj.get("forceLoaded").getAsBoolean();
        int visitorFlags = obj.has("visitorFlags") ? obj.get("visitorFlags").getAsInt() : 0;
        int memberFlags = obj.has("memberFlags") ? obj.get("memberFlags").getAsInt() : 0;
        int environmentFlags = obj.has("environmentFlags") ? obj.get("environmentFlags").getAsInt() : 0;

        Set<ChunkCell> chunks = new HashSet<>();
        if (obj.has("chunks")) {
            for (JsonElement cEl : obj.getAsJsonArray("chunks")) {
                JsonObject cObj = cEl.getAsJsonObject();
                chunks.add(new ChunkCell(cObj.get("x").getAsInt(), cObj.get("z").getAsInt()));
            }
        }

        Map<UUID, ClaimRole> members = new HashMap<>();
        if (obj.has("members")) {
            JsonObject mObj = obj.getAsJsonObject("members");
            for (String key : mObj.keySet()) {
                members.put(UUID.fromString(key), ClaimRole.valueOf(mObj.get(key).getAsString()));
            }
        }

        return new Claim(id, owner, name, color, chunks, members, visitorFlags, memberFlags, environmentFlags, forceLoaded, createdTime);
    }

    private static JsonObject serializeClaim(Claim c) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", c.id().toString());
        obj.addProperty("owner", c.owner().toString());
        obj.addProperty("name", c.name());
        obj.addProperty("color", c.color());
        obj.addProperty("createdTime", c.createdTime());
        obj.addProperty("forceLoaded", c.forceLoaded());
        obj.addProperty("visitorFlags", c.visitorFlags());
        obj.addProperty("memberFlags", c.memberFlags());
        obj.addProperty("environmentFlags", c.environmentFlags());

        JsonArray chunksArr = new JsonArray();
        for (ChunkCell cell : c.chunks()) {
            JsonObject cellObj = new JsonObject();
            cellObj.addProperty("x", cell.x());
            cellObj.addProperty("z", cell.z());
            chunksArr.add(cellObj);
        }
        obj.add("chunks", chunksArr);

        JsonObject membersObj = new JsonObject();
        for (Map.Entry<UUID, ClaimRole> entry : c.members().entrySet()) {
            membersObj.addProperty(entry.getKey().toString(), entry.getValue().name());
        }
        obj.add("members", membersObj);

        return obj;
    }
}
