package dev.herebynamed.client;

import com.google.gson.*;
import dev.herebynamed.HerebyNamed;
import dev.herebynamed.platform.PlatformHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** The arrival sound for a region title: independent layers with their own level, pitch and delay. */
final class TitleSound {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private Config config = new Config();
    private long[] due = new long[0];
    private float groupPitch = 1f;
    private long lastPlayed;
    void load() {
        Path file = PlatformHelper.get().configDir().resolve("hereby-named").resolve("title-sound.json");
        try {
            Files.createDirectories(file.getParent());
            Config defaults = new Config();
            if (!Files.exists(file)) { write(file, defaults); config = defaults; return; }
            JsonObject input = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject filled = JSON.toJsonTree(defaults).getAsJsonObject();
            boolean missing = false;
            for (String key : filled.keySet()) if (!input.has(key)) { input.add(key, filled.get(key)); missing = true; }
            Config loaded = JSON.fromJson(input, Config.class);
            if (loaded == null) throw new IllegalArgumentException("配置文件为空");
            loaded.validate();
            if (missing) write(file, loaded);
            config = loaded;
        } catch (Exception ex) {
            HerebyNamed.LOG.warn("Cannot read title sound configuration; using defaults", ex);
            config = new Config(); config.validate();
        }
    }
    private static void write(Path file, Config config) throws java.io.IOException {
        Files.writeString(file, JSON.toJson(config), StandardCharsets.UTF_8);
    }
    boolean builtInTitles() { return config.builtInTitles; }
    /** Schedules every layer of one arrival; the whole group shares one detune so the layers stay in tune. */
    void arm(long nowMillis) {
        if (!config.enabled) return;
        if (config.minimumIntervalMs > 0 && nowMillis - lastPlayed < config.minimumIntervalMs) return;
        lastPlayed = nowMillis;
        groupPitch = jitter();
        if (due.length != config.layers.size()) due = new long[config.layers.size()];
        for (int i = 0; i < due.length; i++)
            due[i] = nowMillis + config.delayMs + config.layers.get(i).delayMs;
    }
    void tick(long nowMillis) {
        for (int i = 0; i < due.length; i++) {
            if (due[i] == 0 || nowMillis < due[i]) continue;
            due[i] = 0;
            if (i >= config.layers.size()) continue;
            Layer layer = config.layers.get(i);
            play(layer.sound, layer.volume, layer.pitch * groupPitch);
        }
    }
    void reset() { Arrays.fill(due, 0); }
    /** Slight random detune keeps repeated crossings from sounding mechanical. */
    private float jitter() {
        return config.pitchJitter <= 0 ? 1f : 1f + (RandomSource.create().nextFloat() * 2f - 1f) * config.pitchJitter;
    }
    private void play(String id, float volume, float pitch) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) { HerebyNamed.LOG.warn("Ignoring invalid title sound id: {}", id); return; }
        try {
            var manager = Minecraft.getInstance().getSoundManager();
            if (manager == null) return;
            manager.play(new SimpleSoundInstance(parsed, category(), volume, pitch, RandomSource.create(), false, 0,
                    SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
        } catch (RuntimeException ex) { HerebyNamed.LOG.warn("Cannot play title sound {}", id, ex); }
    }
    private SoundSource category() {
        try { return SoundSource.valueOf(config.category.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return SoundSource.AMBIENT; }
    }
    /** Written to config/hereby-named/title-sound.json on first launch. */
    static final class Config {
        boolean enabled = true;
        String category = "ambient";
        int delayMs = 120, minimumIntervalMs = 0;
        float pitchJitter = 0.03f;
        boolean builtInTitles = true;
        List<Layer> layers = defaults();
        static List<Layer> defaults() {
            List<Layer> list = new ArrayList<>();
            list.add(new Layer("minecraft:item.book.page_turn", 0.50f, 0.95f, 0));
            list.add(new Layer("minecraft:entity.villager.work_cartographer", 3.20f, 0.95f, 300));
            list.add(new Layer("minecraft:entity.villager.work_cartographer", 2.40f, 1.10f, 560));
            list.add(new Layer("minecraft:item.book.put", 2.00f, 0.95f, 1060));
            return list;
        }
        void validate() {
            delayMs = clamp(delayMs, 0, 5_000);
            minimumIntervalMs = clamp(minimumIntervalMs, 0, 120_000);
            pitchJitter = clamp(pitchJitter, 0f, 0.5f);
            if (category == null || category.isBlank()) category = "ambient";
            if (layers == null) layers = defaults();
            layers.removeIf(Objects::isNull);
            for (Layer layer : layers) layer.validate();
            if (layers.isEmpty()) layers = defaults();
        }
    }
    /** One voice of the mix: the bell impact, the amethyst shimmer, or anything you add in the config. */
    static final class Layer {
        String sound = "";
        float volume = 1f, pitch = 1f;
        int delayMs;
        Layer() { }
        Layer(String sound, float volume, float pitch, int delayMs) {
            this.sound = sound; this.volume = volume; this.pitch = pitch; this.delayMs = delayMs;
        }
        void validate() {
            if (sound == null) sound = "";
            volume = clamp(volume, 0f, 8f);
            pitch = clamp(pitch, 0.5f, 2f);
            delayMs = clamp(delayMs, 0, 5_000);
        }
    }
    private static float clamp(float value, float minimum, float maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
