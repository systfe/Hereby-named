package dev.herebynamed.domain;

import java.util.*;

/** A dimension's named regions. All changes pass through this transactional boundary. */
public final class RegionBook {
    public record Automatic(String classification, long order, List<String> formerNames, boolean mountainRenamed) {
        public Automatic(String classification,long order,List<String> formerNames) { this(classification,order,formerNames,false); }
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
        deleteRegion(id,true);
    }
    public void deleteRegion(String id,boolean suppress) {
        Set<ChunkCell> suppressed=new HashSet<>(state.suppressed());
        if(suppress) region(id).filter(r -> r.automatic()!=null).ifPresent(r -> suppressed.addAll(r.cells()));
        commit(new State(state.highestLayer(), state.layers(), regions().stream().filter(r -> !r.id().equals(id)).toList(),suppressed));
    }
    public boolean pruneUnderqualifiedAutomatic(dev.herebynamed.auto.AutoConfig config) {
        Set<ChunkCell> manualCells=new HashSet<>();
        for(Region r:regions()) if(r.layer()==1 && r.automatic()==null) manualCells.addAll(r.cells());
        List<Region> remaining=new ArrayList<>();
        boolean changed=false;
        for(Region r:regions()) {
            if(r.layer()==1 && r.automatic()!=null) {
                Set<ChunkCell> available=new HashSet<>(r.cells());
                available.removeAll(manualCells);
                if(available.isEmpty()) { changed=true;continue; }
                String classification=r.automatic().classification();
                String kindName=classification.contains(":")?classification.substring(0,classification.indexOf(':')):classification;
                dev.herebynamed.auto.TerrainKind kind;
                try { kind=dev.herebynamed.auto.TerrainKind.valueOf(kindName); } catch(Exception ex) { kind=null; }
                if(kind!=null) {
                    int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=minX,maxZ=maxX;
                    for(ChunkCell c:available) { minX=Math.min(minX,c.x());maxX=Math.max(maxX,c.x());minZ=Math.min(minZ,c.z());maxZ=Math.max(maxZ,c.z()); }
                    int span=(int)Math.min(Integer.MAX_VALUE,16L*Math.max((long)maxX-minX+1,(long)maxZ-minZ+1));
                    if(!config.qualifies(kind,span)) { changed=true;continue; }
                }
                if(available.size()!=r.cells().size()) {
                    changed=true;
                    remaining.add(new Region(r.id(),r.layer(),r.name(),r.color(),available,r.automatic()));
                    continue;
                }
            }
            remaining.add(r);
        }
        if(changed) {
            commit(new State(state.highestLayer(),state.layers(),remaining,state.suppressed()));
            return true;
        }
        return false;
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
            if (content.isEmpty() && r.automatic() != null) continue;
            boolean targetTouched = r.id().equals(id);
            updated.add(new Region(r.id(), r.layer(), r.name(), r.color(), content, targetTouched ? null : r.automatic()));
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
        List<Set<ChunkCell>> proposedComponents=components(cells);
        if(proposedComponents.size()>1) {
            boolean changed=false;
            for(Set<ChunkCell> component:proposedComponents) changed|=automate(classification,name,color,component);
            return changed;
        }
        List<Region> overlapping=regions().stream().filter(r -> r.layer()==1 && r.automatic()!=null
                && !Collections.disjoint(r.cells(),cells))
                .sorted(Comparator.comparingLong(r -> r.automatic().order())).toList();
        List<Region> sameKind=overlapping.stream()
                .filter(r -> dev.herebynamed.auto.TerrainKind.matches(r.automatic().classification(),classification))
                .toList();
        Region first=sameKind.isEmpty()?null:sameKind.get(0);
        Set<String> merged=new HashSet<>(); List<String> former=new ArrayList<>();
        for(Region r:overlapping) merged.add(r.id());
        for(Region r:sameKind) { former.addAll(r.automatic().formerNames()); if(r!=first) former.add(r.name()); }
        boolean mountain=classification.startsWith("MOUNTAIN") && first!=null && first.automatic().classification().startsWith("MOUNTAIN");
        boolean renamed=sameKind.stream().anyMatch(r -> r.automatic().mountainRenamed());
        boolean renameMountain=mountain && !renamed && cells.size()>first.cells().size() && !first.name().equals(name);
        String chosen=mountain?(renameMountain?validName(name):first.name())
                :first==null || !first.automatic().classification().equals(classification)
                || (sameKind.size()==1 && isSuffixUpgrade(first.name(),name))?validName(name):first.name();
        if((first==null || !chosen.equals(first.name())) && regions().stream().anyMatch(r -> !merged.contains(r.id()) && r.name().equals(chosen))) return false;
        if(renameMountain) former.add(first.name());
        long order=first==null?regions().stream().filter(r -> r.automatic()!=null).mapToLong(r -> r.automatic().order()).max().orElse(0)+1:first.automatic().order();
        Automatic info=new Automatic(classification,order,former.stream().distinct().limit(128).toList(),renamed || renameMountain);
        Region next=new Region(first==null?UUID.randomUUID().toString():first.id(),1,chosen,color&0xffffff,cells,info);
        if(sameKind.size()==1 && next.equals(first)) return false;
        List<Region> result=new ArrayList<>();
        for(Region r:regions()) {
            if(merged.contains(r.id())) {
                Set<ChunkCell> rest=new HashSet<>(r.cells());rest.removeAll(cells);
                List<Set<ChunkCell>> comps=components(rest);
                if(!comps.isEmpty()) {
                    Set<ChunkCell> largest=comps.stream().max(Comparator.comparingInt(Set::size)).orElseThrow();
                    result.add(new Region(UUID.randomUUID().toString(),r.layer(),r.name(),r.color(),largest,r.automatic()));
                }
            } else result.add(r);
        }
        result.add(next);
        if(result.size()>1024) throw new IllegalArgumentException("区域数量已达上限 1024");
        State updated=new State(state.highestLayer(),state.layers(),result,state.suppressed());
        if(updated.equals(state)) return false;
        replace(updated); return true;
    }
    private static boolean isSuffixUpgrade(String oldName,String newName) {
        return !oldName.equals(newName) && stem(oldName).equals(stem(newName));
    }
    private static String stem(String name) {
        for(String suffix:List.of("山脉","山岭","之森","之林","森林","草甸","草原","荒原","平原","之原","冰原","北地","冻土","高原","沙漠","荒漠","冻洋","内海","大洋","群山","花甸","山","岭","峰","峦","洋","海","湖","林","森","地","原"))
            if(name.endsWith(suffix) && name.length()>suffix.length()) return name.substring(0,name.length()-suffix.length());
        return name;
    }
    public static List<Set<ChunkCell>> components(Set<ChunkCell> cells) {
        Set<ChunkCell> unseen=new HashSet<>(cells);List<Set<ChunkCell>> result=new ArrayList<>();
        while(!unseen.isEmpty()) {
            ChunkCell first=unseen.iterator().next();unseen.remove(first);
            Set<ChunkCell> component=new HashSet<>();ArrayDeque<ChunkCell> pending=new ArrayDeque<>();
            component.add(first);pending.add(first);
            while(!pending.isEmpty()) {
                ChunkCell current=pending.removeFirst();
                for(ChunkCell next:List.of(new ChunkCell(current.x()-1,current.z()),new ChunkCell(current.x()+1,current.z()),
                        new ChunkCell(current.x(),current.z()-1),new ChunkCell(current.x(),current.z()+1)))
                    if(unseen.remove(next)) { component.add(next);pending.addLast(next); }
            }
            result.add(component);
        }
        return result;
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
