package dev.herebynamed.domain;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class RegionStore {
    private final Path directory;
    public RegionStore(Path directory) { this.directory = directory; }
    private Path path(String dimension) {
        if (dimension.length() > 256) throw new IllegalArgumentException("维度名称过长");
        String name = Base64.getUrlEncoder().withoutPadding().encodeToString(dimension.getBytes(StandardCharsets.UTF_8));
        return directory.resolve(name + ".dat");
    }
    public RegionBook load(String dimension) throws IOException {
        Path path = path(dimension);
        if (!Files.exists(path)) return new RegionBook();
        if (Files.size(path) > 8_000_000) throw new IOException("区域存档过大：" + path);
        return BookCodec.decode(Files.readAllBytes(path));
    }
    public void save(String dimension, RegionBook book) throws IOException {
        Files.createDirectories(directory);
        Path path = path(dimension), temporary = path.resolveSibling(path.getFileName()+".tmp");
        byte[] data = BookCodec.encode(book);
        Files.write(temporary, data);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
        if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName()+".bak"), StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
