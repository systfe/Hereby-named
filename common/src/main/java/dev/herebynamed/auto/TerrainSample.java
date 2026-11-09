package dev.herebynamed.auto;

/** One recorded surface chunk. An absent height is never interpreted as sea level. */
public record TerrainSample(String biome, TerrainKind kind, float temperature, int height) {
    public static final int UNKNOWN_HEIGHT = Integer.MIN_VALUE;
    public int climate() { return temperature <= 0.15f ? -1 : temperature >= 1.2f ? 1 : 0; }
}
