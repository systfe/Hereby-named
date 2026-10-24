package dev.herebynamed.domain;

import java.util.*;

/** A dimension's named regions. All changes pass through this transactional boundary. */
public final class RegionBook {
    public record Automatic(String classification, long order, List<String> formerNames) {
        public Automatic { formerNames=List.copyOf(formerNames); }
    }
    public record Region(String id, int layer, String name, int color, Set<ChunkCell> cells, Automatic automatic) {
        public Region(String id,int layer,String name,int color,Set<ChunkCell> cells) { this(id,layer,name,color,cells,null); }
        public Region { cells = Set.copyOf(cells); }
    }
    public record State(int highestLayer, Set<Integer> layers, List<Region> regions, Set<ChunkCell> suppressed) {
        public State(int highestLayer,Set<Integer> layers,List<Region> regions) { this(highestLayer,layers,regions,Set.of()); }
        public State { layers = Set.copyOf(layers); regions = List.copyOf(regions); suppressed=Set.copyOf(suppressed); }
    }
    private State state = new State(2, Set.of(1, 2), List.of());
    private final Deque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    public State state() { return state; }
    public RegionBook snapshot() { RegionBook copy=new RegionBook();copy.state=state;return copy; }
    public List<Region> regions() { return state.regions(); }
    public int addLayer() {
        if (state.layers().size() >= 256 || state.highestLayer() == Integer.MAX_VALUE)
            throw new IllegalArgumentException("图层数量已达上限");
        int layer = state.highestLayer() + 1;
        Set<Integer> layers = new HashSet<>(state.layers()); layers.add(layer);
        commit(new State(layer, layers, regions(),state.suppressed())); return layer;
    }
    public void deleteLayer(int layer) {
        if (!state.layers().contains(layer)) throw new IllegalArgumentException("图层不存在");
        Set<Integer> layers = new HashSet<>(state.layers()); layers.remove(layer);
        Set<ChunkCell> suppressed=new HashSet<>(state.suppressed());
        for(Region r:regions()) if(r.layer()==layer && r.automatic()!=null) suppressed.addAll(r.cells());
        commit(new State(state.highestLayer(), layers, regions().stream().filter(r -> r.layer() != layer).toList(),suppressed));
    }
    public void deleteRegion(String id) {
        Set<ChunkCell> suppressed=new HashSet<>(state.suppressed());
        region(id).filter(r -> r.automatic()!=null).ifPresent(r -> suppressed.addAll(r.cells()));
        commit(new State(state.highestLayer(), state.layers(), regions().stream().filter(r -> !r.id().equals(id)).toList(),suppressed));
    }
    public void renameRegion(String id, String name, int color) {
        region(id).orElseThrow(() -> new IllegalArgumentException("区域不存在"));
        String valid = validName(name);
        commit(new State(state.highestLayer(), state.layers(), regions().stream()
                .map(r -> r.id().equals(id) ? new Region(id, r.layer(), valid, color & 0xffffff, r.cells()) : r).toList(),state.suppressed()));
    }
    public List<String> namesAtBlock(int blockX, int blockZ) {
        int chunkX=blockX >> 4, chunkZ=blockZ >> 4;
        // Map screens can render with sentinel mouse coordinates while UI focus changes.
        if(Math.abs((long)chunkX)>ChunkCell.MAX_COORDINATE || Math.abs((long)chunkZ)>ChunkCell.MAX_COORDINATE) return List.of();
        return namesAt(new ChunkCell(chunkX,chunkZ));
    }
    public List<String> namesAt(ChunkCell cell) {
        List<Region> found = regions().stream().filter(r -> r.cells().contains(cell))
                .sorted(Comparator.comparingInt(Region::layer)).toList();
        if (found.isEmpty()) return List.of();
        return found.size() == 1 ? List.of(found.get(0).name())
                : List.of(found.get(0).name(), found.get(found.size()-1).name());
    }
    public Optional<Region> visibleAt(ChunkCell cell, Integer layer) {
        return regions().stream().filter(r -> (layer == null || r.layer() == layer) && r.cells().contains(cell))
                .max(Comparator.comparingInt(Region::layer));
    }
    public void replace(State loaded) {
        Objects.requireNonNull(loaded);
        if (loaded.highestLayer() < 2 || loaded.layers().size() > 256 || loaded.regions().size() > 1024)
            throw new IllegalArgumentException("区域数据无效");
        Set<String> ids = new HashSet<>();
        Map<Integer, Set<ChunkCell>> occupied = new HashMap<>();
        long total = 0;
        for (int layer : loaded.layers())
            if (layer < 1 || layer > loaded.highestLayer()) throw new IllegalArgumentException("图层编号无效");
        for (Region r : loaded.regions()) {
            if (r.id() == null || !ids.add(r.id()) || !loaded.layers().contains(r.layer())) throw new IllegalArgumentException("区域归属无效");
            validName(r.name());
            Set<ChunkCell> seen = occupied.computeIfAbsent(r.layer(), key -> new HashSet<>());
            for (ChunkCell c : r.cells()) if (!seen.add(c)) throw new IllegalArgumentException("同层区域重叠");
            total += r.cells().size();
        }
        if (total > 500_000) throw new IllegalArgumentException("单维度最多 500000 个区域区块");
        if(loaded.suppressed().size()>500_000) throw new IllegalArgumentException("自动保护区块已达上限");
        state = loaded; clearHistory();
    }
    public Optional<Region> region(String id) { return regions().stream().filter(r -> r.id().equals(id)).findFirst(); }
    public Optional<Region> owner(int layer, ChunkCell cell) {
        return regions().stream().filter(r -> r.layer() == layer && r.cells().contains(cell)).findFirst();
    }
    public String createRegion(int layer, String name, int color) {
        if (!state.layers().contains(layer)) throw new IllegalArgumentException("图层不存在");
        if (regions().size() >= 1024) throw new IllegalArgumentException("区域数量已达上限 1024");
        String id = UUID.randomUUID().toString();
        List<Region> regions = new ArrayList<>(regions());
        regions.add(new Region(id, layer, validName(name), color & 0xffffff, Set.of()));
        commit(new State(state.highestLayer(), state.layers(), regions,state.suppressed()));
        return id;
    }
    public void paint(String id, Set<ChunkCell> cells, boolean add) {
        Region target = region(id).orElseThrow(() -> new IllegalArgumentException("区域不存在"));
        if (cells.size() > 65_536) throw new IllegalArgumentException("单次最多编辑 65536 个区块，请分批操作");
        List<Region> updated = new ArrayList<>();
        for (Region r : regions()) {
            if (r.layer() != target.layer() || (!add && !r.id().equals(id))) { updated.add(r); continue; }
            Set<ChunkCell> content = new HashSet<>(r.cells());
            if (add && r.id().equals(id)) content.addAll(cells); else content.removeAll(cells);
            boolean touched=r.id().equals(id) || !Collections.disjoint(r.cells(),cells);
            updated.add(new Region(r.id(), r.layer(), r.name(), r.color(), content,touched?null:r.automatic()));
        }
        commit(new State(state.highestLayer(), state.layers(), updated,state.suppressed()));
    }
    public record PaintPlan(Set<ChunkCell> conflicts,Set<ChunkCell> uncontested) {
        public PaintPlan { conflicts=Set.copyOf(conflicts); uncontested=Set.copyOf(uncontested); }
    }
    public PaintPlan planPaint(String id,Set<ChunkCell> cells) {
        Region target=region(id).orElseThrow(() -> new IllegalArgumentException("区域不存在"));
        if(cells.size()>65_536) throw new IllegalArgumentException("单次最多编辑 65536 个区块，请分批操作");
        Set<ChunkCell> conflicts=new HashSet<>();
        for(Region other:regions()) if(other.layer()==target.layer() && !other.id().equals(id)) {
            if(other.cells().size()<cells.size()) { for(ChunkCell c:other.cells()) if(cells.contains(c)) conflicts.add(c); }
            else { for(ChunkCell c:cells) if(other.cells().contains(c)) conflicts.add(c); }
        }
        Set<ChunkCell> uncontested=new HashSet<>(cells); uncontested.removeAll(conflicts);
        return new PaintPlan(conflicts,uncontested);
    }
    public void paintPreserving(String id,Set<ChunkCell> cells) { paint(id,planPaint(id,cells).uncontested(),true); }
    /** Atomic background update. It never claims a manually managed chunk. */
    public boolean automate(String classification,String name,int color,Set<ChunkCell> proposed) {
        if(!state.layers().contains(1)) return false;
        if(classification==null || classification.length()>40) throw new IllegalArgumentException("自动分类无效");
        Set<ChunkCell> cells=new HashSet<>(proposed);
        cells.removeAll(state.suppressed());
        for(Region r:regions()) if(r.layer()==1 && r.automatic()==null) cells.removeAll(r.cells());
        if(cells.isEmpty()) return false;
        List<Region> overlapping=regions().stream().filter(r -> r.layer()==1 && r.automatic()!=null
                && (r.automatic().classification().equals(classification) || cells.containsAll(r.cells())) && !Collections.disjoint(r.cells(),cells))
                .sorted(Comparator.comparingLong(r -> r.automatic().order())).toList();
        Region first=overlapping.isEmpty()?null:overlapping.get(0);
        Set<String> merged=new HashSet<>(); List<String> former=new ArrayList<>();
        for(Region r:overlapping) { merged.add(r.id());cells.addAll(r.cells());former.addAll(r.automatic().formerNames()); if(r!=first) former.add(r.name()); }
        String chosen=first==null || !first.automatic().classification().equals(classification)?validName(name):first.name();
        if(first==null && regions().stream().anyMatch(r -> r.name().equals(chosen))) return false;
        long order=first==null?regions().stream().filter(r -> r.automatic()!=null).mapToLong(r -> r.automatic().order()).max().orElse(0)+1:first.automatic().order();
        Automatic info=new Automatic(classification,order,former.stream().distinct().limit(128).toList());
        Region next=new Region(first==null?UUID.randomUUID().toString():first.id(),1,chosen,color&0xffffff,cells,info);
        if(overlapping.size()==1 && next.equals(first)) return false;
        List<Region> result=new ArrayList<>();
        for(Region r:regions()) {
            if(merged.contains(r.id())) continue;
            if(r.layer()==1 && r.automatic()!=null && !Collections.disjoint(r.cells(),cells)) {
                Set<ChunkCell> rest=new HashSet<>(r.cells());rest.removeAll(cells);
                if(!rest.isEmpty()) result.add(new Region(r.id(),r.layer(),r.name(),r.color(),rest,r.automatic()));
            } else result.add(r);
        }
        result.add(next);
        if(result.size()>1024) throw new IllegalArgumentException("区域数量已达上限 1024");
        State updated=new State(state.highestLayer(),state.layers(),result,state.suppressed());
        if(updated.equals(state)) return false;
        replace(updated); return true;
    }
    public void allowAutomaticAgain() {
        commit(new State(state.highestLayer(),state.layers(),regions(),Set.of()));
    }
    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(state); state = keepHighest(undo.pop()); return true;
    }
    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(state); state = keepHighest(redo.pop()); return true;
    }
    public void clearHistory() { undo.clear(); redo.clear(); }
    private State keepHighest(State next) {
        return new State(Math.max(state.highestLayer(), next.highestLayer()), next.layers(), next.regions(),next.suppressed());
    }
    private void commit(State next) {
        if (state.equals(next)) return;
        if(next.suppressed().size()>500_000) throw new IllegalArgumentException("自动保护区块已达上限");
        if (next.regions().stream().mapToLong(r -> r.cells().size()).sum() > 500_000)
            throw new IllegalArgumentException("单维度最多 500000 个区域区块");
        undo.push(state);
        while (undo.size() > 32 || (undo.size() > 1 && undo.stream()
                .flatMap(s -> s.regions().stream()).mapToLong(r -> r.cells().size()).sum() > 2_000_000)) undo.removeLast();
        redo.clear(); state = next;
    }
    private static String validName(String name) {
        if (name == null || name.isBlank() || name.length() > 80 || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("区域名需为 1～80 个字符且不含控制字符");
        return name.strip();
    }
}
