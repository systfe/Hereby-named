package dev.herebynamed.auto;

import dev.herebynamed.domain.ChunkCell;
import java.util.*;

/** Pure analysis of recorded surface data; call from the single background worker. */
public final class TerrainAnalysis {
    private static final int MAX_RIVER_BRIDGE_CHUNKS=8;
    public record Candidate(TerrainKind kind, Set<ChunkCell> cells, float temperature, int span) {
        public Candidate { cells = Set.copyOf(cells); }
    }
    public record Result(boolean unlocked, List<Candidate> candidates) { }
    public Result analyze(Map<ChunkCell, TerrainSample> input, AutoConfig config) {
        if (!unlocked(input,config.unlockSpan)) return new Result(false,List.of());
        Map<ChunkCell,TerrainSample> samples=classified(input,config);
        Set<ChunkCell> remaining = new HashSet<>(samples.keySet());
        List<Candidate> candidates = new ArrayList<>();
        analyzeWater(samples,remaining,candidates,config);
        List<ChunkCell> seeds=remaining.stream().sorted(Comparator.<ChunkCell>comparingInt(c -> -Math.abs(samples.get(c).climate()))
                .thenComparingInt(ChunkCell::x).thenComparingInt(ChunkCell::z)).toList();
        for(ChunkCell start:seeds) {
            if(!remaining.remove(start)) continue;
            TerrainSample seed=samples.get(start);
            int climate=seed.climate();
            Set<ChunkCell> cells=new HashSet<>(); cells.add(start);
            ArrayDeque<ChunkCell> pending=new ArrayDeque<>(); pending.add(start);
            float temperature=0;
            int minX=start.x(),maxX=minX,minZ=start.z(),maxZ=minZ;
            while(!pending.isEmpty()) {
                ChunkCell c=pending.removeFirst(); TerrainSample sample=samples.get(c);
                temperature+=sample.temperature();
                minX=Math.min(minX,c.x()); maxX=Math.max(maxX,c.x()); minZ=Math.min(minZ,c.z()); maxZ=Math.max(maxZ,c.z());
                for(ChunkCell n:neighbors(c)) {
                    TerrainSample next=samples.get(n);
                    if(!seed.kind().water() && next!=null && next.kind()==TerrainKind.RIVER) {
                        int dx=n.x()-c.x(),dz=n.z()-c.z();
                        List<ChunkCell> river= new ArrayList<>();
                        for(int step=0;step<MAX_RIVER_BRIDGE_CHUNKS && next!=null && next.kind()==TerrainKind.RIVER;step++) {
                            river.add(n);n=new ChunkCell(n.x()+dx,n.z()+dz);next=samples.get(n);
                        }
                        if(next!=null && next.kind()==seed.kind() && climate*next.climate()!=-1 && remaining.contains(n)) {
                            for(ChunkCell bridge:river) if(remaining.remove(bridge)) {
                                TerrainSample water=samples.get(bridge);cells.add(bridge);pending.add(bridge);
                                temperature+=water.temperature();
                                minX=Math.min(minX,bridge.x());maxX=Math.max(maxX,bridge.x());minZ=Math.min(minZ,bridge.z());maxZ=Math.max(maxZ,bridge.z());
                            }
                            remaining.remove(n);cells.add(n);pending.add(n);
                            if(climate==0) climate=next.climate();
                            continue;
                        }
                    }
                    if(next!=null && next.kind()==seed.kind() && climate*next.climate()!=-1 && remaining.contains(n)) {
                        if(climate==0) climate=next.climate();
                        remaining.remove(n); cells.add(n); pending.add(n);
                    }
                }
            }
            addConnectedCandidates(candidates,seed.kind(),cells,samples,config);
        }
        candidates.sort(Comparator.comparing(c -> c.cells().stream().min(Comparator.comparingInt(ChunkCell::x).thenComparingInt(ChunkCell::z)).orElseThrow(),
                Comparator.comparingInt(ChunkCell::x).thenComparingInt(ChunkCell::z)));
        return new Result(true,fillEnclosedRecordedHoles(candidates,samples));
    }
    private static Map<ChunkCell,TerrainSample> classified(Map<ChunkCell,TerrainSample> samples,AutoConfig config) {
        Map<ChunkCell,TerrainSample> result=new HashMap<>(samples.size());
        for(var entry:samples.entrySet()) {
            TerrainSample sample=entry.getValue();TerrainKind explicit=TerrainClassifier.explicitRule(sample.biome(),config);
            TerrainKind kind=explicit;
            if(kind==null) {
                kind=sample.kind();
                if(sample.temperature()<=0.15f && kind!=TerrainKind.MOUNTAIN) {
                    String path=sample.biome().substring(sample.biome().indexOf(':')+1);
                    boolean oldColdLake=kind==TerrainKind.FROZEN_OCEAN && path.contains("lake");
                    if(kind!=TerrainKind.FROZEN_OCEAN || oldColdLake) kind=TerrainKind.NORTHLAND;
                }
            }
            result.put(entry.getKey(),kind==sample.kind()?sample:new TerrainSample(sample.biome(),kind,sample.temperature(),sample.height()));
        }
        return result;
    }
    private static void analyzeWater(Map<ChunkCell,TerrainSample> samples,Set<ChunkCell> remaining,List<Candidate> out,AutoConfig config) {
        Set<ChunkCell> water=new HashSet<>();
        for(var e:samples.entrySet()) if(e.getValue().kind().water() && e.getValue().kind()!=TerrainKind.RIVER) water.add(e.getKey());
        remaining.removeAll(water);
        for(ChunkCell start:new ArrayList<>(water)) {
            if(!water.contains(start)) continue;
            Set<ChunkCell> body=flood(water,start);
            Candidate whole=candidate(TerrainKind.OCEAN,body,samples);
            boolean closed=enclosed(body,samples);
            if(closed && config.qualifies(TerrainKind.LAKE,whole.span())) {
                out.add(candidate(TerrainKind.LAKE,body,samples)); continue;
            }
            Set<ChunkCell> ice=new HashSet<>();
            for(ChunkCell c:body) if(samples.get(c).kind()==TerrainKind.FROZEN_OCEAN) ice.add(c);
            Set<ChunkCell> oceanBody=new HashSet<>(body);
            for(ChunkCell frozenStart:new ArrayList<>(ice)) {
                if(!ice.contains(frozenStart)) continue;
                Set<ChunkCell> part=flood(ice,frozenStart);
                if(part.size()*3L>=body.size() && config.qualifies(TerrainKind.FROZEN_OCEAN,candidate(TerrainKind.FROZEN_OCEAN,part,samples).span())) {
                    addIfQualified(out,TerrainKind.FROZEN_OCEAN,part,samples,config);
                    oceanBody.removeAll(part);
                }
            }
            for(ChunkCell oceanStart:new ArrayList<>(oceanBody)) if(oceanBody.contains(oceanStart))
                addIfQualified(out,TerrainKind.OCEAN,flood(oceanBody,oceanStart),samples,config);
        }
    }
    private static Set<ChunkCell> flood(Set<ChunkCell> remaining,ChunkCell start) {
        Set<ChunkCell> cells=new HashSet<>(); ArrayDeque<ChunkCell> pending=new ArrayDeque<>();
        remaining.remove(start); pending.add(start); cells.add(start);
        while(!pending.isEmpty()) for(ChunkCell n:neighbors(pending.removeFirst()))
            if(remaining.remove(n)) { cells.add(n); pending.add(n); }
        return cells;
    }
    private static Candidate candidate(TerrainKind kind,Set<ChunkCell> cells,Map<ChunkCell,TerrainSample> samples) {
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=minX,maxZ=maxX; double temperature=0;
        for(ChunkCell c:cells) { minX=Math.min(minX,c.x());maxX=Math.max(maxX,c.x());minZ=Math.min(minZ,c.z());maxZ=Math.max(maxZ,c.z());temperature+=samples.get(c).temperature(); }
        return new Candidate(kind,cells,(float)(temperature/cells.size()),(int)Math.min(Integer.MAX_VALUE,16L*Math.max((long)maxX-minX+1,(long)maxZ-minZ+1)));
    }
    private static void addIfQualified(List<Candidate> out,TerrainKind kind,Set<ChunkCell> cells,Map<ChunkCell,TerrainSample> samples,AutoConfig config) {
        Candidate c=candidate(kind,cells,samples);
        if(config.qualifies(kind,c.span())) out.add(c);
    }
    private static void addConnectedCandidates(List<Candidate> out,TerrainKind kind,Set<ChunkCell> cells,
                                               Map<ChunkCell,TerrainSample> samples,AutoConfig config) {
        Set<ChunkCell> unseen=new HashSet<>(cells);
        while(!unseen.isEmpty()) {
            ChunkCell start=unseen.iterator().next();unseen.remove(start);
            Set<ChunkCell> component=new HashSet<>();ArrayDeque<ChunkCell> pending=new ArrayDeque<>();
            component.add(start);pending.add(start);
            while(!pending.isEmpty()) for(ChunkCell neighbor:neighbors(pending.removeFirst()))
                if(unseen.remove(neighbor)) { component.add(neighbor);pending.addLast(neighbor); }
            for(Set<ChunkCell> sub:splitNecks(component)) addIfQualified(out,kind,sub,samples,config);
        }
    }
    private static List<Set<ChunkCell>> splitNecks(Set<ChunkCell> cells) {
        if(cells.size()<16) return List.of(cells);
        Set<ChunkCell> cores=new HashSet<>();
        for(ChunkCell c:cells) {
            boolean core=true;
            for(ChunkCell n:neighbors(c)) if(!cells.contains(n)) { core=false;break; }
            if(core) cores.add(c);
        }
        if(cores.isEmpty()) return List.of(cells);
        List<Set<ChunkCell>> coreComponents=new ArrayList<>();
        Set<ChunkCell> unseenCore=new HashSet<>(cores);
        while(!unseenCore.isEmpty()) {
            ChunkCell start=unseenCore.iterator().next();unseenCore.remove(start);
            Set<ChunkCell> comp=new HashSet<>();ArrayDeque<ChunkCell> queue=new ArrayDeque<>();
            comp.add(start);queue.add(start);
            while(!queue.isEmpty()) {
                ChunkCell cur=queue.removeFirst();
                for(ChunkCell n:neighbors(cur)) if(unseenCore.remove(n)) { comp.add(n);queue.add(n); }
            }
            if(comp.size()>=4) coreComponents.add(comp);
        }
        if(coreComponents.size()<=1) return List.of(cells);
        Map<ChunkCell,Integer> owner=new HashMap<>();ArrayDeque<ChunkCell> queue=new ArrayDeque<>();
        for(int i=0;i<coreComponents.size();i++) for(ChunkCell c:coreComponents.get(i)) { owner.put(c,i);queue.add(c); }
        while(!queue.isEmpty()) {
            ChunkCell cur=queue.removeFirst();int id=owner.get(cur);
            for(ChunkCell n:neighbors(cur)) if(cells.contains(n) && !owner.containsKey(n)) { owner.put(n,id);queue.add(n); }
        }
        Map<Integer,Set<ChunkCell>> partitioned=new HashMap<>();
        for(var entry:owner.entrySet()) partitioned.computeIfAbsent(entry.getValue(),k -> new HashSet<>()).add(entry.getKey());
        List<Set<ChunkCell>> result=new ArrayList<>();
        for(Set<ChunkCell> part:partitioned.values()) {
            Set<ChunkCell> unseenPart=new HashSet<>(part);
            while(!unseenPart.isEmpty()) {
                ChunkCell start=unseenPart.iterator().next();unseenPart.remove(start);
                Set<ChunkCell> connected=new HashSet<>();ArrayDeque<ChunkCell> q=new ArrayDeque<>();
                connected.add(start);q.add(start);
                while(!q.isEmpty()) for(ChunkCell n:neighbors(q.removeFirst())) if(unseenPart.remove(n)) { connected.add(n);q.add(n); }
                result.add(connected);
            }
        }
        return result.isEmpty()?List.of(cells):result;
    }
    private static List<Candidate> fillEnclosedRecordedHoles(List<Candidate> candidates,Map<ChunkCell,TerrainSample> samples) {
        Set<ChunkCell> claimed=new HashSet<>();for(Candidate candidate:candidates) claimed.addAll(candidate.cells());
        List<Candidate> result=new ArrayList<>(candidates.size());
        for(Candidate candidate:candidates) {
            int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=minX,maxZ=maxX;
            for(ChunkCell cell:candidate.cells()) { minX=Math.min(minX,cell.x());maxX=Math.max(maxX,cell.x());minZ=Math.min(minZ,cell.z());maxZ=Math.max(maxZ,cell.z()); }
            long width=(long)maxX-minX+1,height=(long)maxZ-minZ+1;
            if(width*height>1_000_000L) { result.add(candidate);continue; }
            Set<ChunkCell> visited=new HashSet<>(),holes=new HashSet<>();ArrayDeque<ChunkCell> pending=new ArrayDeque<>();
            for(int z=minZ;z<=maxZ;z++) for(int x=minX;x<=maxX;x++) {
                ChunkCell cell=new ChunkCell(x,z);
                if(candidate.cells().contains(cell) || claimed.contains(cell) || visited.contains(cell)) continue;
                Set<ChunkCell> component=new HashSet<>();pending.add(cell);visited.add(cell);boolean touchesEdge=false,unknown=false;
                while(!pending.isEmpty()) {
                    ChunkCell current=pending.removeFirst();
                    component.add(current);
                    if(current.x()==minX || current.x()==maxX || current.z()==minZ || current.z()==maxZ) touchesEdge=true;
                    if(!samples.containsKey(current)) unknown=true;
                    for(ChunkCell neighbor:neighbors(current)) {
                        if(neighbor.x()<minX || neighbor.x()>maxX || neighbor.z()<minZ || neighbor.z()>maxZ
                                || candidate.cells().contains(neighbor) || claimed.contains(neighbor) || !visited.add(neighbor)) continue;
                        pending.addLast(neighbor);
                    }
                }
                if(!touchesEdge && !unknown) holes.addAll(component);
            }
            if(holes.isEmpty()) { result.add(candidate);continue; }
            Set<ChunkCell> filled=new HashSet<>(candidate.cells());filled.addAll(holes);
            double temperature=0;for(ChunkCell cell:filled) temperature+=samples.get(cell).temperature();
            result.add(new Candidate(candidate.kind(),filled,(float)(temperature/filled.size()),candidate.span()));
        }
        return List.copyOf(result);
    }
    private static boolean enclosed(Set<ChunkCell> cells,Map<ChunkCell,TerrainSample> samples) {
        for(ChunkCell c:cells) for(ChunkCell n:neighbors(c)) if(!cells.contains(n)) {
            TerrainSample s=samples.get(n);
            if(s==null || s.kind().water() || s.kind()==TerrainKind.OTHER) return false;
        }
        return true;
    }
    private static boolean unlocked(Map<ChunkCell,TerrainSample> samples,int threshold) {
        Set<ChunkCell> remaining = new HashSet<>(samples.keySet());
        for(ChunkCell start:samples.keySet()) {
            if(!remaining.remove(start)) continue;
            ArrayDeque<ChunkCell> pending = new ArrayDeque<>(); pending.add(start);
            int minX=start.x(), maxX=minX, minZ=start.z(), maxZ=minZ;
            while (!pending.isEmpty()) {
                ChunkCell c = pending.removeFirst();
                minX=Math.min(minX,c.x()); maxX=Math.max(maxX,c.x());
                minZ=Math.min(minZ,c.z()); maxZ=Math.max(maxZ,c.z());
                for (ChunkCell n : neighbors(c)) if (remaining.remove(n)) pending.add(n);
            }
            if (16L * Math.max((long)maxX-minX+1, (long)maxZ-minZ+1) >= threshold) return true;
        }
        return false;
    }
    static List<ChunkCell> neighbors(ChunkCell c) {
        return List.of(new ChunkCell(c.x()-1,c.z()),new ChunkCell(c.x()+1,c.z()),
                new ChunkCell(c.x(),c.z()-1),new ChunkCell(c.x(),c.z()+1));
    }
}
