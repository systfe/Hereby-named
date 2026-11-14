package dev.herebynamed.client;

import com.google.gson.*;
import dev.herebynamed.HerebyNamed;
import dev.herebynamed.platform.PlatformHelper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Timing and motion of the region title: the letterbox bars and the entrance settle. */
final class TitleVisuals {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private Config config = new Config();
    void load() {
        Path file = PlatformHelper.get().configDir().resolve("hereby-named").resolve("title-visuals.json");
        try {
            Files.createDirectories(file.getParent());
            Config defaults = new Config();
            if (!Files.exists(file)) { Files.writeString(file, JSON.toJson(defaults), StandardCharsets.UTF_8); config = defaults; return; }
            JsonObject input = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject filled = JSON.toJsonTree(defaults).getAsJsonObject();
            boolean missing = false;
            for (String key : filled.keySet()) if (!input.has(key)) { input.add(key, filled.get(key)); missing = true; }
            Config loaded = JSON.fromJson(input, Config.class);
            if (loaded == null) throw new IllegalArgumentException("配置文件为空");
            loaded.validate();
            if (missing) Files.writeString(file, JSON.toJson(loaded), StandardCharsets.UTF_8);
            config = loaded;
        } catch (Exception ex) {
            HerebyNamed.LOG.warn("Cannot read title visuals configuration; using defaults", ex);
            config = new Config(); config.validate();
        }
    }
    Config config() { return config; }
    /** Decelerating curves: most of the travel happens early, then the motion keeps creeping in. */
    static float ease(float t, String curve) {
        t = Math.max(0f, Math.min(1f, t));
        if ("cubic".equals(curve)) return 1f - cube(1f - t);
        if ("quartic".equals(curve)) { float u = 1f - t; return 1f - u * u * u * u; }
        return brake(t);
    }
    /** 75% of the travel in the first 40% of the time; the last quarter takes the remaining 60%. */
    private static float brake(float t) {
        if (t < 0.4f) { float u = t / 0.4f; return 0.75f * (1f - cube(1f - u)); }
        float u = (t - 0.4f) / 0.6f, v = 1f - u;
        return 0.75f + 0.25f * (1f - v * v);
    }
    private static float cube(float v) { return v * v * v; }
    /** Written to config/hereby-named/title-visuals.json on first launch. */
    static final class Config {
        float letterboxPercent = 8f;
        int letterboxMs = 1300;
        String curve = "brake";
        String entranceMode = "settle";
        int entranceMs = 1300;
        float entranceScale = 1.14f;
        float entranceRise = 8f;
        int lineStaggerMs = 100;
        int repeatCooldownMs = 15_000;
        int switchCooldownMs = 5_000;
        void validate() {
            letterboxPercent = clamp(letterboxPercent, 0f, 25f);
            letterboxMs = clamp(letterboxMs, 200, 5_000);
            entranceMs = clamp(entranceMs, 0, 5_000);
            entranceScale = clamp(entranceScale, 1f, 1.5f);
            entranceRise = clamp(entranceRise, -40f, 40f);
            lineStaggerMs = clamp(lineStaggerMs, 0, 1_000);
            repeatCooldownMs = clamp(repeatCooldownMs, 0, 600_000);
            switchCooldownMs = clamp(switchCooldownMs, 0, 600_000);
            if (!"cubic".equals(curve) && !"quartic".equals(curve)) curve = "brake";
            if (!"squeeze".equals(entranceMode) && !"rise".equals(entranceMode) && !"none".equals(entranceMode)) entranceMode = "settle";
        }
        private static float clamp(float value, float minimum, float maximum) { return Math.max(minimum, Math.min(maximum, value)); }
        private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    }
}
