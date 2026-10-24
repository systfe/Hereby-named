package dev.herebynamed.auto;

import dev.herebynamed.domain.ChunkCell;
import java.util.*;

/** Pure analysis of recorded surface data; call from the single background worker. */
public final class TerrainAnalysis {
    public record Candidate(TerrainKind kind, Set<ChunkCell> cells, float temperature, int span) {
        public Candidate { cells = Set.copyOf(cells); }
    }
    public record Result(boolean unlocked, List<Candidate> candidates) { }
    public Result analyze(Map<ChunkCell, TerrainSample> samples, AutoConfig config) {
        if (!unlocked(samples,config.unlockSpan)) return new Result(false,List.of());
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
            Map<String,Integer> biomes=new HashMap<>(); float temperature=0;
            int minX=start.x(),maxX=minX,minZ=start.z(),maxZ=minZ;
            while(!pending.isEmpty()) {
                ChunkCell c=pending.removeFirst(); TerrainSample sample=samples.get(c);
                biomes.merge(sample.biome(),1,Integer::sum); temperature+=sample.temperature();
                minX=Math.min(minX,c.x()); maxX=Math.max(maxX,c.x()); minZ=Math.min(minZ,c.z()); maxZ=Math.max(maxZ,c.z());
                for(ChunkCell n:neighbors(c)) {
                    TerrainSample next=samples.get(n);
                    if(!seed.kind().water() && next!=null && next.kind()==TerrainKind.RIVER) {
                        int dx=n.x()-c.x(),dz=n.z()-c.z();
                        for(int step=0;step<2 && next!=null && next.kind()==TerrainKind.RIVER;step++) {
                            n=new ChunkCell(n.x()+dx,n.z()+dz); next=samples.get(n);
                        }
                    }
                    if(next!=null && next.kind()==seed.kind() && climate*next.climate()!=-1 && remaining.contains(n)) {
                        if(climate==0) climate=next.climate();
                        remaining.remove(n); cells.add(n); pending.add(n);
                    }
                }
            }
            int span=(int)Math.min(Integer.MAX_VALUE,16L*Math.max((long)maxX-minX+1,(long)maxZ-minZ+1));
            long count=biomes.values().stream().filter(n -> n>=config.minimumBiomeChunks).count();
            TerrainKind kind=seed.kind();
            if(config.qualifies(kind,span,count))
                candidates.add(new Candidate(kind,cells,temperature/cells.size(),span));
        }
        candidates.sort(Comparator.comparing(c -> c.cells().stream().min(Comparator.comparingInt(ChunkCell::x).thenComparingInt(ChunkCell::z)).orElseThrow(),
                Comparator.comparingInt(ChunkCell::x).thenComparingInt(ChunkCell::z)));
        return new Result(true,List.copyOf(candidates));
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
            long wholeBiomes=biomeCount(body,samples,config);
            if(closed && config.qualifies(TerrainKind.LAKE,whole.span(),wholeBiomes)) {
                out.add(candidate(TerrainKind.LAKE,body,samples)); continue;
            }
            Set<ChunkCell> ice=new HashSet<>(),warm=new HashSet<>(body);
            for(ChunkCell c:body) if(samples.get(c).kind()==TerrainKind.FROZEN_OCEAN) ice.add(c);
            warm.removeAll(ice); int warmArea=warm.size();
            for(ChunkCell frozenStart:new ArrayList<>(ice)) {
                if(!ice.contains(frozenStart)) continue;
                Set<ChunkCell> part=flood(ice,frozenStart);
                if(warmArea>0 && part.size()*3L<warmArea && config.qualifies(TerrainKind.OCEAN,whole.span(),wholeBiomes)) warm.addAll(part);
                else addIfQualified(out,TerrainKind.FROZEN_OCEAN,part,samples,config);
            }
            for(ChunkCell warmStart:new ArrayList<>(warm)) if(warm.contains(warmStart))
                addIfQualified(out,TerrainKind.OCEAN,flood(warm,warmStart),samples,config);
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
        if(config.qualifies(kind,c.span(),biomeCount(cells,samples,config))) out.add(c);
    }
    private static long biomeCount(Set<ChunkCell> cells,Map<ChunkCell,TerrainSample> samples,AutoConfig config) {
        Map<String,Integer> biomes=new HashMap<>();
        for(ChunkCell cell:cells) biomes.merge(samples.get(cell).biome(),1,Integer::sum);
        return biomes.values().stream().filter(n -> n>=config.minimumBiomeChunks).count();
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
