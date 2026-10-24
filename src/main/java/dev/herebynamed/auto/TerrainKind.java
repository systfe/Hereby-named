package dev.herebynamed.auto;

public enum TerrainKind {
    MOUNTAIN(0xa8a7b0), OCEAN(0x438ac7), FROZEN_OCEAN(0x99dce6), PLAIN(0xc1bd70),
    FOREST(0x549b63), LAKE(0x63bfc7), FLOWER(0xdba3c4), DESERT(0xd8b679),
    RIVER(0x6aaac4), OTHER(0x888888);
    public final int color;
    TerrainKind(int color) { this.color = color; }
    public int variedColor(int seed) {
        int offset=Math.floorMod(seed,19)-9;
        return Math.max(0,Math.min(255,(color>>16&255)+offset))<<16
                | Math.max(0,Math.min(255,(color>>8&255)+offset))<<8 | Math.max(0,Math.min(255,(color&255)+offset));
    }
    public boolean water() { return this == OCEAN || this == FROZEN_OCEAN || this == RIVER || this == LAKE; }
}
