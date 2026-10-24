package dev.herebynamed.client;

import dev.herebynamed.HerebyNamed;
import dev.herebynamed.domain.*;
import dev.herebynamed.net.*;
import dev.herebynamed.auto.AutoWork;
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
    private final PendingRequest pending = new PendingRequest();
    private long pendingSince;
    private boolean automaticSaving;
    private long session;
    public long generation;

    public boolean shared() { return client.getNetworkHandler() != null && ClientTransport.canSend(); }
    public void reset() {
        shared.clear(); local.clear(); failed.clear(); requested.clear(); store = null;
        editing = acquiring = null; pending.cancel(); pendingSince = 0; generation++; assembler = new Wire.Assembler();
        automaticSaving=false;session++;
    }
    public void receive(Wire.Fragment fragment) {
        try {
            Optional<byte[]> bytes = assembler.accept(fragment);
            if (bytes.isEmpty()) return;
            long epoch=session;
            AutoWork.submit(() -> Snapshot.decode(bytes.get())).whenComplete((snapshot,error) -> client.execute(() -> {
                if(epoch!=session) return;
                if(error!=null) { HerebyNamed.LOG.error("Cannot decode region snapshot",error);return; }
                accept(snapshot);
            }));
        } catch (Exception ex) { HerebyNamed.LOG.error("Cannot decode region snapshot", ex); message("区域同步失败，请重新打开地图"); }
    }
    private void accept(Snapshot snapshot) {
            shared.put(snapshot.dimension(), snapshot);
            Optional<PendingRequest.Request> completed = pending.complete(snapshot.responseTo(), snapshot.dimension());
            if (completed.isPresent()) pendingSince = 0;
            generation++;
            if (!snapshot.message().isBlank()) message(snapshot.message());
            if (completed.isPresent() && completed.get().acquisition()) {
                acquiring = null;
                if (mine(snapshot.holder()) && snapshot.authorized()) editing = snapshot.dimension();
            }
            if (snapshot.dimension().equals(editing) && (!mine(snapshot.holder()) || !snapshot.authorized())) editing = null;
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
        if(automaticSaving) return true;
        if (pendingSince != 0 && System.nanoTime() - pendingSince > 10_000_000_000L) {
            pending.cancel(); pendingSince = 0;
            String canceled = acquiring; acquiring = null;
            // Release follows ACQUIRE on the ordered connection, including when its ACK arrives late.
            if (canceled != null) send(new RegionEdit(canceled, 0, RegionEdit.Action.RELEASE, 0, "", "", 0, Set.of()));
            if (editing != null) request(editing);
            message("同步超时，请重试");
        }
        return pendingSince != 0;
    }
    public boolean automaticAllowed() {
        if(editing!=null || acquiring!=null || busy() || failed.contains("minecraft:overworld")) return false;
        if(!shared()) return true;
        Snapshot snapshot=shared.get("minecraft:overworld");
        if(snapshot==null) { request("minecraft:overworld");return false; }
        return snapshot.authorized() && snapshot.holder()==null;
    }
    public Path surfaceCachePath() { return store().directory().resolve("automatic-surface.bin.gz"); }
    public boolean automatic(RegionEdit proposal) {
        if(!automaticAllowed()) return false;
        String dim="minecraft:overworld";
        if(!proposal.dimension().equals(dim)) return false;
        Snapshot snapshot=shared.get(dim);
        RegionEdit edit=new RegionEdit(dim,snapshot==null?0:snapshot.revision(),proposal.action(),1,proposal.region(),proposal.name(),proposal.color(),proposal.cells());
        if(shared()) { pendingSince=System.nanoTime();send(edit);return true; }
        RegionBook current=book(dim).snapshot();RegionStore destination=store();long epoch=session;
        automaticSaving=true;
        AutoWork.submit(() -> {
            edit.apply(current);destination.save(dim,current);return current;
        }).whenComplete((next,error) -> client.execute(() -> {
            if(epoch!=session) return;
            automaticSaving=false;
            if(error!=null) { HerebyNamed.LOG.error("Automatic local save failed",error);failed.add(dim);return; }
            local.put(dim,next);generation++;
        }));
        return true;
    }
    public void enter(String dimension) {
        if (busy()) return;
        if (!shared()) { book(dimension); if (!failed.contains(dimension)) { editing = dimension; generation++; } return; }
        acquiring = dimension; pendingSince = System.nanoTime();
        send(new RegionEdit(dimension, 0, RegionEdit.Action.ACQUIRE, 0, "", "", 0, Set.of()));
    }
    public void exit() {
        String dim = editing != null ? editing : acquiring;
        editing = acquiring = null; pending.cancel(); pendingSince = 0;
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
        if (shared()) { pendingSince = System.nanoTime(); send(edit); return; }
        RegionBook book = book(dimension); RegionBook.State before = book.state();
        try { edit.apply(book); store().save(dimension, book); generation++; }
        catch (Exception ex) { book.replace(before); HerebyNamed.LOG.error("Local region edit failed", ex); message(Objects.toString(ex.getMessage(), "保存失败")); }
    }
    private void send(RegionEdit edit) {
        boolean tracked = edit.action() != RegionEdit.Action.READ && edit.action() != RegionEdit.Action.RELEASE;
        long id = tracked ? pending.begin(edit.dimension(), edit.action() == RegionEdit.Action.ACQUIRE) : pending.nextId();
        edit = new RegionEdit(edit.dimension(), edit.revision(), id, edit.action(), edit.layer(), edit.region(), edit.name(), edit.color(), edit.cells());
        if(edit.action()==RegionEdit.Action.AUTO_APPLY || edit.action()==RegionEdit.Action.AUTO_ALLOW_AGAIN) {
            RegionEdit automatic=edit;long epoch=session;
            AutoWork.submit(automatic::encode).whenComplete((bytes,error) -> client.execute(() -> {
                if(epoch!=session) return;
                if(error!=null) { pending.cancel();pendingSince=0;HerebyNamed.LOG.warn("Cannot encode automatic request",error);return; }
                try { Wire.send(bytes,ClientTransport::send); }
                catch(RuntimeException ex) { pending.cancel();pendingSince=0;HerebyNamed.LOG.warn("Cannot send automatic request",ex); }
            }));
            return;
        }
        try { Wire.send(edit.encode(), ClientTransport::send); }
        catch (IOException | RuntimeException ex) {
            if (tracked) { pending.cancel(); pendingSince = 0; acquiring = null; }
            message("无法发送区域请求"); HerebyNamed.LOG.error("Region send failed", ex);
        }
    }
    public void message(String text) { if (client.player != null) client.player.sendMessage(Text.literal("此地名曰：" + text), false); }
}
