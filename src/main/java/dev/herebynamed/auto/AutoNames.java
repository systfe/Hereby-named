package dev.herebynamed.auto;

import dev.herebynamed.domain.*;
import java.util.*;

public final class AutoNames {
    private AutoNames() { }
    public static Optional<String> choose(TerrainAnalysis.Candidate candidate,RegionBook book,AutoConfig config) {
        var existing=book.regions().stream().filter(r -> r.layer()==1 && r.automatic()!=null
                && !Collections.disjoint(r.cells(),candidate.cells()))
                .sorted(Comparator.comparingLong(r -> r.automatic().order())).toList();
        if(!existing.isEmpty()) {
            var oldest=existing.get(0);
            if(oldest.automatic().classification().equals(key(candidate))) return Optional.of(oldest.name());
            String adjusted=stem(oldest.name())+suffix(candidate);
            if(book.regions().stream().noneMatch(r -> !r.id().equals(oldest.id()) && r.name().equals(adjusted))) return Optional.of(adjusted);
        }
        Set<String> used=new HashSet<>(); for(var r:book.regions()) used.add(r.name());
        List<String> options=new ArrayList<>();
        for(var n:config.names) if(n.fits(candidate.kind(),candidate.temperature())) options.add(n.text());
        List<String> roots=config.roots.stream().filter(n -> n.fits(candidate.kind(),candidate.temperature())).map(AutoConfig.Name::text).toList();
        int seed=candidate.cells().stream().mapToInt(ChunkCell::hashCode).sum();
        if(!options.isEmpty()) Collections.rotate(options,Math.floorMod(seed,options.size()));
        for(String option:options) if(valid(option) && !used.contains(option)) return Optional.of(option);
        List<String> generated=new ArrayList<>();
        for(String root:roots) generated.add(root+suffix(candidate));
        if(!generated.isEmpty()) Collections.rotate(generated,Math.floorMod(seed,generated.size()));
        for(String option:generated) if(valid(option) && !used.contains(option)) return Optional.of(option);
        options.addAll(generated);
        // Pair whitelist roots only. Bound the search even for very large user lists.
        for(int i=0;i<Math.min(roots.size(),128);i++) for(int j=0;j<Math.min(roots.size(),128);j++) if(i!=j) {
            String option=roots.get(i)+roots.get(j)+suffix(candidate);
            if(valid(option) && !used.contains(option)) return Optional.of(option);
        }
        for(String option:options) {
            var same=book.regions().stream().filter(r -> r.name().equals(option) && !r.cells().isEmpty()).findFirst();
            if(same.isEmpty()) continue;
            double[] a=center(candidate.cells()),b=center(same.get().cells()); double dx=a[0]-b[0],dz=a[1]-b[1];
            String prefix=Math.abs(dx)+Math.abs(dz)<1?"":Math.abs(dx)>Math.abs(dz)?(dx>0?"东":"西"):(dz>0?"南":"北");
            if(prefix.isEmpty()) {
                double ratio=(double)candidate.cells().size()/same.get().cells().size();
                prefix=ratio>=1.5?"大":ratio<=2.0/3?"小":"";
            }
            if(!prefix.isEmpty() && valid(prefix+option) && !used.contains(prefix+option)) return Optional.of(prefix+option);
        }
        return Optional.empty();
    }
    public static String key(TerrainAnalysis.Candidate c) {
        return c.kind().name()+(c.kind().water()?"":c.temperature()<=0.15f?":COLD":c.temperature()>=1.2f?":HOT":":TEMPERATE");
    }
    private static boolean valid(String s) { return s.length()<=80 && s.codePoints().noneMatch(c -> Character.isDigit(c) || Character.isISOControl(c)); }
    private static double[] center(Set<ChunkCell> cells) {
        double x=0,z=0;for(ChunkCell c:cells) { x+=c.x();z+=c.z(); }return new double[]{x/cells.size(),z/cells.size()};
    }
    private static String stem(String name) {
        for(String suffix:List.of("山脉","山岭","森林","草甸","草原","荒原","沙漠","荒漠","冻洋","内海","大洋","群山","山","岭","峰","洋","海","湖","林"))
            if(name.endsWith(suffix) && name.length()>suffix.length()) return name.substring(0,name.length()-suffix.length());
        return name;
    }
    private static String suffix(TerrainAnalysis.Candidate c) {
        return switch(c.kind()) {
            case MOUNTAIN -> {
                int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=minX,maxZ=maxX;
                for(ChunkCell p:c.cells()) { minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z()); }
                yield Math.max(maxX-minX+1,maxZ-minZ+1)>1.8*Math.min(maxX-minX+1,maxZ-minZ+1)?"山脉":"山";
            }
            case OCEAN -> "洋"; case FROZEN_OCEAN -> "冻洋"; case PLAIN -> "原野";
            case FOREST -> "森林";case LAKE -> "湖";case FLOWER -> "花甸";case DESERT -> "沙漠";
            default -> "";
        };
    }
}
