package dev.herebynamed.client;

import dev.herebynamed.HerebyNamed;
import dev.herebynamed.auto.*;
import dev.herebynamed.domain.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import xaero.map.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Client-thread sampling only; graph walks, naming, compression and cache I/O run on AutoWork. */
public final class AutomaticRegions {
    private record BiomeInfo(float temperature,Set<String> tags) { }
    private record Observation(ChunkCell cell,TerrainSample sample) { }
    private record Output(int samples,boolean unlocked,int unnamed,List<RegionEdit> proposals) { }
    private static final String DIMENSION="minecraft:overworld";
    private final MinecraftClient client=MinecraftClient.getInstance();
    private final ClientRegions regions=HerebyNamedClient.REGIONS;
    private final Path configDirectory=FabricLoader.getInstance().getConfigDir().resolve("hereby-named");
    private final Map<String,BiomeInfo> biomeInfo=new HashMap<>();
    private final ArrayDeque<RegionEdit> proposals=new ArrayDeque<>();
    private final ArrayDeque<ChunkCell> nearby=new ArrayDeque<>();
    private final List<Observation> observations=new ArrayList<>();
    private CompletableFuture<AutoConfig> loadingConfig=AutoWork.submit(() -> AutoConfig.load(configDirectory));
    private AutoConfig config;
    private WorkerState worker;
    private CompletableFuture<Output> work;
    private MapProcessor processor;
    private MapKnowledge knowledge;
    private SurfaceDiscovery discovery;
    private ChunkCell scanning,lastPlayer;
    private long scanWait,scanAgain,nextWork,nextCommit,lastTick,slowUntil,nearbyAgain;
    private int epoch;
    private String mapIdentity="",status="正在读取自动区域设置";
    private int sampleCount,unnamed;
    private boolean unlocked,forceAnalysis;

    public String status() { return status; }
    public int sampleCount() { return sampleCount; }
    public int unnamedCount() { return unnamed; }
    public boolean enabled() { return config!=null && config.enabled; }
    public Path configDirectory() { return configDirectory; }
    public boolean ready() { return config!=null && loadingConfig==null; }
    public void reset() {
        if(worker!=null) worker.active=false;
        epoch++;worker=null;work=null;processor=null;knowledge=null;discovery=null;scanning=lastPlayer=null;
        mapIdentity="";observations.clear();proposals.clear();nearby.clear();biomeInfo.clear();
        sampleCount=unnamed=0;unlocked=false;nextWork=nextCommit=scanAgain=0;
    }
    public void reload() {
        reset();config=null;loadingConfig=AutoWork.submit(() -> AutoConfig.load(configDirectory));status="正在重新读取设置";
    }
    public void toggle() {
        if(!ready()) return;
        config.enabled=!config.enabled;
        AutoConfig current=config;
        AutoWork.submit(() -> { current.save(configDirectory);return true; }).whenComplete((saved,error) -> {
            if(error!=null) HerebyNamed.LOG.error("Cannot save automatic settings",error);
        });
        if(!config.enabled) { proposals.clear();status="自动创建已关闭；已有区域保留"; }
        else { forceAnalysis=true;nextWork=0; }
    }
    public void reanalyze() {
        proposals.clear();forceAnalysis=true;nextWork=0;scanAgain=0;discovery=null;scanning=null;
        status="已安排重新分析；手动区域和删除保护保留";
    }
    public void allowAgain() {
        if(regions.automatic(new RegionEdit(DIMENSION,0,RegionEdit.Action.AUTO_ALLOW_AGAIN,1,"","",0,Set.of()))) reanalyze();
    }
    public void tick() {
        long now=System.nanoTime();
        if(lastTick!=0 && now-lastTick>100_000_000L) slowUntil=now+5_000_000_000L;
        lastTick=now;
        if(loadingConfig!=null && loadingConfig.isDone()) {
            try { config=loadingConfig.join();status="等待主世界地图"; }
            catch(RuntimeException ex) { HerebyNamed.LOG.error("Automatic configuration unavailable",ex);status="配置读取失败；修正文件后重新加载"; }
            loadingConfig=null;
        }
        if(config==null || !config.enabled || client.world==null || client.player==null) return;
        if(!client.world.getRegistryKey().getValue().toString().equals(DIMENSION)) { status="仅处理主世界地表";return; }
        if(!regions.automaticAllowed()) { status="等待区域编辑结束或共享编辑授权";return; }
        WorldMapSession session=WorldMapSession.getCurrentSession();
        MapProcessor next=session==null?null:session.getMapProcessor();
        if(next==null || next.getMapWorld()==null || next.getMapWorld().getCurrentDimension()==null
                || !next.getMapWorld().getCurrentDimension().getDimId().getValue().toString().equals(DIMENSION)) return;
        String identity=next.getCurrentWorldId()+"/"+next.getCurrentMWId();
        if(processor!=next || !mapIdentity.equals(identity)) {
            reset();processor=next;knowledge=new MapKnowledge(next);mapIdentity=identity;
            Path base=regions.surfaceCachePath();
            Path path=base.resolveSibling("automatic-surface-"+UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))+".gz");
            worker=new WorkerState(path);WorkerState state=worker;AutoConfig cfg=config;
            work=AutoWork.submit(() -> { workerLoad(state,cfg);return new Output(state.samples.size(),false,0,List.of()); });
        }
        if(work!=null) {
            if(!work.isDone()) { status="后台整理地表资料";return; }
            try {
                Output output=work.join();sampleCount=output.samples();unlocked=output.unlocked();unnamed=output.unnamed();
                if(proposals.isEmpty()) proposals.addAll(output.proposals());
            } catch(RuntimeException ex) { status="后台分析暂停，将稍后重试";HerebyNamed.LOG.warn("Automatic analysis failed",ex);nextWork=now+30_000_000_000L; }
            work=null;
        }
        if(!proposals.isEmpty() && now>=nextCommit && now>=slowUntil) {
            if(regions.automatic(proposals.peekFirst())) { proposals.removeFirst();nextCommit=now+3_000_000_000L; }
            return;
        }
        if(now<slowUntil && now%4!=0) { status="检测到卡顿，已降低处理速度";return; }
        ChunkCell player=new ChunkCell(client.player.getBlockX()>>4,client.player.getBlockZ()>>4);
        if(!player.equals(lastPlayer) || now>=nearbyAgain) {
            nearby.clear();for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) nearby.add(new ChunkCell(player.x()+dx,player.z()+dz));
            lastPlayer=player;nearbyAgain=now+10_000_000_000L;
        }
        long deadline=System.nanoTime()+1_000_000L;
        for(int count=0;count<32 && System.nanoTime()<deadline;count++) {
            if(observations.size()>=256) break;
            ChunkCell cell;
            if(!nearby.isEmpty()) cell=nearby.removeFirst();
            else {
                if(discovery==null) { if(now<scanAgain) break;discovery=new SurfaceDiscovery(processor); }
                if(scanning==null) { scanning=discovery.next();scanWait=now; }
                if(scanning==null) { discovery=null;scanAgain=now+60_000_000_000L;break; }
                cell=scanning;
            }
            MapKnowledge.Sample sample=knowledge.sample(cell,true);
            if(sample.loading()) {
                if(cell.equals(scanning) && now-scanWait>5_000_000_000L) scanning=null;
                break;
            }
            if(cell.equals(scanning)) scanning=null;
            if(sample.biome()!=null) {
                BiomeInfo info=biomeInfo.computeIfAbsent(sample.biome(),this::biome);
                if(info==null) continue;
                TerrainKind kind=TerrainClassifier.classify(sample.biome(),info.tags(),info.temperature(),0,config);
                if(sample.water() && !kind.water()) kind=info.temperature()<=0.15f?TerrainKind.FROZEN_OCEAN:TerrainKind.LAKE;
                observations.add(new Observation(cell,new TerrainSample(sample.biome(),kind,info.temperature(),sample.height())));
                if(observations.size()>=256) break;
            }
        }
        if(now>=nextWork) {
            List<Observation> batch=List.copyOf(observations);observations.clear();
            WorkerState state=worker;AutoConfig cfg=config;RegionBook snapshot=regions.book(DIMENSION).snapshot();
            boolean force=forceAnalysis;forceAnalysis=false;
            work=AutoWork.submit(() -> process(state,batch,snapshot,cfg,force));nextWork=now+2_000_000_000L;
        }
        if(sampleCount>=config.maximumSamples) status="地表缓存已达上限，可在配置中调整";
        else status=unlocked?"已启用 · 分批分析已探索地表":"等待连续地图跨度达到 "+config.unlockSpan+" 格";
    }
    private static void workerLoad(WorkerState state,AutoConfig cfg) {
        try { state.samples.putAll(SurfaceCache.load(state.path,cfg.maximumSamples));state.dirty=true;state.analysisDirty=true; }
        catch(Exception ex) { HerebyNamed.LOG.warn("Discarding unreadable disposable surface cache",ex); }
    }
    private BiomeInfo biome(String id) {
        var registry=client.world.getRegistryManager().get(RegistryKeys.BIOME);
        var key=Identifier.tryParse(id);if(key==null) return null;
        var entry=registry.getEntry(RegistryKey.of(RegistryKeys.BIOME,key));
        return entry.map(value -> new BiomeInfo(value.value().getTemperature(),value.streamTags().map(t -> t.id().toString()).collect(java.util.stream.Collectors.toUnmodifiableSet()))).orElse(null);
    }
    private static Output process(WorkerState state,List<Observation> batch,RegionBook book,AutoConfig config,boolean force) throws Exception {
        for(Observation o:batch) if(state.samples.size()<config.maximumSamples || state.samples.containsKey(o.cell())) {
            TerrainSample old=state.samples.put(o.cell(),o.sample());if(!o.sample().equals(old)) { state.dirty=true;state.analysisDirty=true; }
        }
        if(!state.active) return new Output(state.samples.size(),false,0,List.of());
        long now=System.nanoTime();
        boolean analyzed=false;
        if((state.analysisDirty || force) && (force || now-state.lastAnalysis>=config.analysisIntervalSeconds*1_000_000_000L)) {
            state.analysis=new TerrainAnalysis().analyze(withRelief(state.samples),config);state.lastAnalysis=now;state.analysisDirty=false;analyzed=true;
        }
        if(state.dirty && now-state.lastSave>30_000_000_000L) { SurfaceCache.save(state.path,state.samples);state.lastSave=now;state.dirty=false; }
        List<RegionEdit> edits=new ArrayList<>();int unnamed=0;
        if(!analyzed && state.lastBook==book.state()) return new Output(state.samples.size(),state.analysis!=null && state.analysis.unlocked(),state.unnamed,List.of());
        state.lastBook=book.state();
        if(state.analysis!=null && state.analysis.unlocked()) for(var candidate:state.analysis.candidates()) {
            if(!state.active) break;
            Optional<String> name=AutoNames.choose(candidate,book,config);
            if(name.isEmpty()) { unnamed++;continue; }
            int color=book.regions().stream().filter(r -> r.automatic()!=null && r.name().equals(name.get()) && r.automatic().classification().equals(AutoNames.key(candidate)))
                    .map(RegionBook.Region::color).findFirst().orElseGet(() -> candidate.kind().variedColor(name.get().hashCode()));
            if(book.automate(AutoNames.key(candidate),name.get(),color,candidate.cells())) {
                edits.add(new RegionEdit(DIMENSION,0,RegionEdit.Action.AUTO_APPLY,1,AutoNames.key(candidate),name.get(),color,candidate.cells()));
                if(edits.size()>=16) break;
            }
        }
        state.unnamed=unnamed;
        return new Output(state.samples.size(),state.analysis!=null && state.analysis.unlocked(),unnamed,List.copyOf(edits));
    }
    private static Map<ChunkCell,TerrainSample> withRelief(Map<ChunkCell,TerrainSample> samples) {
        Map<ChunkCell,TerrainSample> result=new HashMap<>(samples);
        for(var e:samples.entrySet()) {
            TerrainSample s=e.getValue();
            if(s.kind().water() || s.kind()==TerrainKind.DESERT || s.kind()==TerrainKind.MOUNTAIN || s.height()==TerrainSample.UNKNOWN_HEIGHT) continue;
            int relief=0;
            for(int[] d:new int[][]{{-2,0},{2,0},{0,-2},{0,2}}) {
                TerrainSample n=samples.get(new ChunkCell(e.getKey().x()+d[0],e.getKey().z()+d[1]));
                if(n!=null && !n.kind().water() && n.height()!=TerrainSample.UNKNOWN_HEIGHT) relief=Math.max(relief,Math.abs(s.height()-n.height()));
            }
            if(relief>=48 || (s.kind()==TerrainKind.PLAIN && relief>=32)) result.put(e.getKey(),new TerrainSample(s.biome(),TerrainKind.MOUNTAIN,s.temperature(),s.height()));
        }
        return result;
    }
    private static final class WorkerState {
        final Path path;final Map<ChunkCell,TerrainSample> samples=new HashMap<>();
        volatile boolean active=true;
        boolean dirty,analysisDirty;long lastAnalysis,lastSave;TerrainAnalysis.Result analysis;RegionBook.State lastBook;int unnamed;
        WorkerState(Path path) { this.path=path; }
    }
}
