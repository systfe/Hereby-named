package dev.herebynamed.domain;

import java.io.*;
import java.util.*;

public record RegionEdit(String dimension, long revision, Action action, int layer,
                         String region, String name, int color, Set<ChunkCell> cells) {
    public enum Action { READ, ACQUIRE, RELEASE, CREATE, RENAME, DELETE_REGION, ADD_LAYER, DELETE_LAYER, ADD, REMOVE, UNDO, REDO }
    public RegionEdit {
        Objects.requireNonNull(dimension); Objects.requireNonNull(action);
        region = region == null ? "" : region; name = name == null ? "" : name;
        cells = cells == null ? Set.of() : Set.copyOf(cells);
        if (dimension.length() > 256 || region.length() > 80 || name.length() > 80 || cells.size() > 65_536)
            throw new IllegalArgumentException("编辑请求过大");
    }
    public void apply(RegionBook book) {
        switch (action) {
            case CREATE -> book.createRegion(layer, name, color);
            case RENAME -> book.renameRegion(region, name, color);
            case DELETE_REGION -> book.deleteRegion(region);
            case ADD_LAYER -> book.addLayer();
            case DELETE_LAYER -> book.deleteLayer(layer);
            case ADD -> book.paint(region, cells, true);
            case REMOVE -> book.paint(region, cells, false);
            case UNDO -> book.undo();
            case REDO -> book.redo();
            default -> throw new IllegalArgumentException("不是区域修改请求");
        }
    }
    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(dimension); out.writeLong(revision); out.writeInt(action.ordinal());
            out.writeInt(layer); out.writeUTF(region); out.writeUTF(name); out.writeInt(color); out.writeInt(cells.size());
            for (ChunkCell cell : cells) { out.writeInt(cell.x()); out.writeInt(cell.z()); }
        }
        return bytes.toByteArray();
    }
    public static RegionEdit decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String dim = in.readUTF(); long revision = in.readLong(); int action = in.readInt();
            if (action < 0 || action >= Action.values().length) throw new IOException("未知操作");
            int layer = in.readInt(); String region = in.readUTF(), name = in.readUTF(); int color = in.readInt();
            int size = in.readInt(); if (size < 0 || size > 65_536) throw new IOException("区块数量过大");
            Set<ChunkCell> cells = new HashSet<>();
            for (int i = 0; i < size; i++) cells.add(new ChunkCell(in.readInt(), in.readInt()));
            if (in.read() != -1) throw new IOException("请求尾部无效");
            return new RegionEdit(dim, revision, Action.values()[action], layer, region, name, color, cells);
        } catch (IllegalArgumentException ex) { throw new IOException("无效编辑请求", ex); }
    }
}
