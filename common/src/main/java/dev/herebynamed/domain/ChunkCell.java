package dev.herebynamed.domain;

public record ChunkCell(int x, int z) {
    public static final int MAX_COORDINATE = 1_875_000;
    public ChunkCell {
        if (Math.abs((long) x) > MAX_COORDINATE || Math.abs((long) z) > MAX_COORDINATE)
            throw new IllegalArgumentException("区块坐标超出世界范围");
    }
    // Dense rectangles cluster badly with the record default (31*x+z), especially
    // in immutable sets. Mix both coordinates before using them as spatial keys.
    @Override public int hashCode() {
        long value=((long)x<<32) ^ (z&0xffffffffL);
        value=(value^(value>>>33))*0xff51afd7ed558ccdL;
        value=(value^(value>>>33))*0xc4ceb9fe1a85ec53L;
        value^=value>>>33;
        return (int)(value^(value>>>32));
    }
}
