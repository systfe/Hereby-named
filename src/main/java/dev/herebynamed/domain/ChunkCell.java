package dev.herebynamed.domain;

public record ChunkCell(int x, int z) {
    public ChunkCell {
        if (Math.abs((long) x) > 1_875_000 || Math.abs((long) z) > 1_875_000)
            throw new IllegalArgumentException("区块坐标超出世界范围");
    }
}
