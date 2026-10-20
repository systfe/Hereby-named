package dev.herebynamed.client;

import dev.herebynamed.HerebyNamed;
import dev.herebynamed.domain.*;
import dev.herebynamed.net.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public final class ClientRegions {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Map<String, Snapshot> shared = new HashMap<>();
    private final Map<String, RegionBook> local = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final Map<String, Long> requested = new HashMap<>();
    private RegionStore store;
    private Wire.Assembler assembler = new Wire.Assembler();
    private String editing;
    private String acquiring;
    private String pendingDimension;
    private long pendingSince;
    public long generation;

    public boolean shared() { return client.getNetworkHandler() != null && ClientPlayNetworking.canSend(Wire.CHANNEL); }
    public void reset() {
        shared.clear(); local.clear(); failed.clear(); requested.clear(); store = null;
        editing = acquiring = pendingDimension = null; pendingSince = 0; generation++; assembler = new Wire.Assembler();
    }
    public void receive(Wire.Fragment fragment) {
        try {
            Optional<byte[]> bytes = assembler.accept(fragment);
            if (bytes.isEmpty()) return;
            Snapshot snapshot = Snapshot.decode(bytes.get());
            shared.put(snapshot.dimension(), snapshot);
            if (snapshot.dimension().equals(pendingDimension)) { pendingSince = 0; pendingDimension = null; }
            generation++;
            if (!snapshot.message().isBlank()) message(snapshot.message());
            if (snapshot.dimension().equals(acquiring)) {
                acquiring = null;
                if (mine(snapshot.holder())) editing = snapshot.dimension();
            }
            if (snapshot.dimension().equals(editing) && (!mine(snapshot.holder()) || !snapshot.authorized())) editing = null;
        } catch (Exception ex) { HerebyNamed.LOG.error("Cannot decode region snapshot", ex); message("区域同步失败，请重新打开地图"); }
    }
    private boolean mine(UUID holder) { return client.player != null && client.player.getUuid().equals(holder); }
    public RegionBook book(String dimension) {
        if (shared()) {
            Snapshot snapshot = shared.get(dimension);
            if (snapshot != null) return snapshot.book();
            request(dimension); return new RegionBook();
        }
        RegionBook book = local.get(dimension);
        if (book != null) return book;
        try { book = store().load(dimension); }
        catch (IOException ex) { failed.add(dimension); book = new RegionBook(); HerebyNamed.LOG.error("Cannot load local regions", ex); message("本地区域读取失败，已保留原文件并禁止编辑"); }
        local.put(dimension, book); return book;
    }
    private RegionStore store() {
        if (store == null) {
            String identity = client.getServer() != null ? "single:" + client.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath()
                    : "server:" + (client.getCurrentServerEntry() == null ? "unknown" : client.getCurrentServerEntry().address.toLowerCase(Locale.ROOT));
            String key = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
            Path root = client.runDirectory.toPath().resolve("hereby-named").resolve("local").resolve(key);
            store = new RegionStore(root);
        }
        return store;
    }
    public void request(String dimension) {
        long now = System.nanoTime();
        if (!shared() || now - requested.getOrDefault(dimension, 0L) < 1_000_000_000L) return;
        requested.put(dimension, now); send(new RegionEdit(dimension, 0, RegionEdit.Action.READ, 0, "", "", 0, Set.of()));
    }
    public boolean editing(String dimension) { return dimension.equals(editing); }
    public boolean busy() {
        if (pendingSince != 0 && System.nanoTime() - pendingSince > 10_000_000_000L) {
            pendingSince = 0; acquiring = pendingDimension = null;
            if (editing != null) request(editing);
            message("同步超时，请重试");
        }
        return pendingSince != 0;
    }
    public void enter(String dimension) {
        if (busy()) return;
        if (!shared()) { book(dimension); if (!failed.contains(dimension)) { editing = dimension; generation++; } return; }
        acquiring = pendingDimension = dimension; pendingSince = System.nanoTime();
        send(new RegionEdit(dimension, 0, RegionEdit.Action.ACQUIRE, 0, "", "", 0, Set.of()));
    }
    public void exit() {
        String dim = editing != null ? editing : acquiring;
        editing = acquiring = pendingDimension = null; pendingSince = 0;
        if (dim != null) {
            if (shared()) send(new RegionEdit(dim, 0, RegionEdit.Action.RELEASE, 0, "", "", 0, Set.of()));
            else { RegionBook book = local.get(dim); if (book != null) book.clearHistory(); }
        }
        generation++;
    }
    public void edit(String dimension, RegionEdit.Action action, int layer, String region, String name, int color, Set<ChunkCell> cells) {
        if (!editing(dimension) || busy()) return;
        Snapshot snapshot = shared.get(dimension);
        RegionEdit edit = new RegionEdit(dimension, snapshot == null ? 0 : snapshot.revision(), action, layer, region, name, color, cells);
        if (shared()) { pendingDimension = dimension; pendingSince = System.nanoTime(); send(edit); return; }
        RegionBook book = book(dimension); RegionBook.State before = book.state();
        try { edit.apply(book); store().save(dimension, book); generation++; }
        catch (Exception ex) { book.replace(before); HerebyNamed.LOG.error("Local region edit failed", ex); message(Objects.toString(ex.getMessage(), "保存失败")); }
    }
    private void send(RegionEdit edit) {
        try { Wire.send(edit.encode(), packet -> ClientPlayNetworking.send(Wire.CHANNEL, packet)); }
        catch (IOException | RuntimeException ex) { pendingSince = 0; acquiring = null; message("无法发送区域请求"); HerebyNamed.LOG.error("Region send failed", ex); }
    }
    public void message(String text) { if (client.player != null) client.player.sendMessage(Text.literal("此地名曰：" + text), false); }
}
