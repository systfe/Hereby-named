package dev.herebynamed.auto;

import dev.herebynamed.domain.*;
import java.util.*;
import java.util.random.RandomGenerator;

public final class AutoNames {
    private static final List<String> NORTHLAND_SUFFIXES=List.of("冻土","北地","地","原","冰原");
    private AutoNames() { }

    public static Optional<String> choose(TerrainAnalysis.Candidate candidate,RegionBook book,AutoConfig config) {
        return choose(candidate,book,config,RandomGenerator.getDefault());
    }
    public static Optional<String> choose(TerrainAnalysis.Candidate candidate,RegionBook book,AutoConfig config,RandomGenerator random) {
        var existing=book.regions().stream().filter(r -> r.layer()==1 && r.automatic()!=null
                && TerrainKind.matches(r.automatic().classification(),candidate.kind())
                && !Collections.disjoint(r.cells(),candidate.cells()))
                .sorted(Comparator.comparingLong(r -> r.automatic().order())).toList();
        if(!existing.isEmpty()) {
            var oldest=existing.get(0);
            int overlap=0;
            for(ChunkCell c:candidate.cells()) if(oldest.cells().contains(c)) overlap++;
            boolean substantial=overlap>=16 || overlap*4>=candidate.cells().size() || overlap*4>=oldest.cells().size();
            if(substantial) {
                if(candidate.kind()==TerrainKind.MOUNTAIN && oldest.automatic().classification().startsWith("MOUNTAIN")) {
                    String group=mountainGroup(candidate,config);
                    String currentGroup=group.isEmpty()?"山":group;
                    String currentStem=stem(oldest.name());
                    boolean bareDefault=config.names.stream().anyMatch(n -> n.fits(candidate.kind(),candidate.temperature())
                            && (n.text().equals(oldest.name()) || n.text().equals(currentStem)));
                    if(bareDefault) {
                        String decorated=currentStem+currentGroup;
                        if(book.regions().stream().noneMatch(r -> !r.id().equals(oldest.id()) && r.name().equals(decorated)))
                            return Optional.of(decorated);
                    }
                    boolean exhausted=existing.stream().anyMatch(r -> r.automatic().mountainRenamed());
                    if(exhausted || group.isEmpty() || candidate.cells().size()<=oldest.cells().size()
                            || !oldest.name().matches(".+[山峰]")) return Optional.of(oldest.name());
                    String adjusted=stem(oldest.name())+group;
                    if(book.regions().stream().noneMatch(r -> !r.id().equals(oldest.id()) && r.name().equals(adjusted))) return Optional.of(adjusted);
                    for(var root:config.roots) if(root.fits(candidate.kind(),candidate.temperature())) {
                        String alternate=root.text()+group;
                        if(valid(alternate) && book.regions().stream().noneMatch(r -> r.name().equals(alternate))) return Optional.of(alternate);
                    }
                    return Optional.of(oldest.name());
                }
                List<AutoConfig.Name> defaults=config.names.stream()
                        .filter(n -> n.fits(candidate.kind(),candidate.temperature()))
                        .filter(n -> n.text().equals(oldest.name())).toList();
                if(!defaults.isEmpty()) return Optional.of(decorate(oldest.name(),suffix(candidate,config,random)));
                if(book.regions().stream().noneMatch(r -> !r.id().equals(oldest.id()) && r.name().equals(oldest.name()))) {
                    return Optional.of(oldest.name());
                }
            }
        }

        Set<String> used=new HashSet<>();for(var region:book.regions()) used.add(region.name());
        List<AutoConfig.Name> defaults=config.names.stream().filter(n -> n.fits(candidate.kind(),candidate.temperature())).toList();
        List<AutoConfig.Name> shuffledDefaults=new ArrayList<>(defaults);
        Collections.shuffle(shuffledDefaults,new Random(random.nextLong()));
        String ending=suffix(candidate,config,random);
        for(var name:shuffledDefaults) {
            String decorated=decorate(name.text(),ending);
            if(valid(decorated) && !used.contains(decorated)) return Optional.of(decorated);
        }

        List<String> roots=config.roots.stream().filter(n -> n.fits(candidate.kind(),candidate.temperature()))
                .map(AutoConfig.Name::text).distinct().toList();
        List<Integer> lengths=new ArrayList<>(List.of(1,2,3));Collections.shuffle(lengths,new Random(random.nextLong()));
        for(int length:lengths) {
            Optional<String> unique=findUnique(roots,length,ending,used,random);
            if(unique.isPresent()) return unique;
        }

        for(var name:shuffledDefaults) {
            String decorated=decorate(name.text(),ending);
            if(valid(decorated)) return Optional.of(decorated);
        }
        if(!roots.isEmpty()) {
            List<Integer> repeatedLengths=new ArrayList<>();
            for(int length=1;length<=Math.min(3,roots.size());length++) repeatedLengths.add(length);
            Collections.shuffle(repeatedLengths,new Random(random.nextLong()));
            for(int length:repeatedLengths) {
                Optional<String> repeated=findAny(roots,length,ending,random);
                if(repeated.isPresent()) return repeated;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findUnique(List<String> roots,int length,String ending,Set<String> used,RandomGenerator random) {
        List<String> shuffled=new ArrayList<>(roots);Collections.shuffle(shuffled,new Random(random.nextLong()));
        return compose(shuffled,new ArrayList<>(),length,ending,used,false);
    }
    private static Optional<String> findAny(List<String> roots,int length,String ending,RandomGenerator random) {
        List<String> shuffled=new ArrayList<>(roots);Collections.shuffle(shuffled,new Random(random.nextLong()));
        return compose(shuffled,new ArrayList<>(),length,ending,Set.of(),true);
    }
    private static Optional<String> compose(List<String> roots,List<String> selected,int remaining,String ending,
                                            Set<String> used,boolean allowUsed) {
        if(remaining==0) {
            String candidate=String.join("",selected)+ending;
            return valid(candidate) && (allowUsed || !used.contains(candidate))?Optional.of(candidate):Optional.empty();
        }
        for(String root:roots) if(!selected.contains(root)) {
            selected.add(root);
            Optional<String> result=compose(roots,selected,remaining-1,ending,used,allowUsed);
            selected.remove(selected.size()-1);
            if(result.isPresent()) return result;
        }
        return Optional.empty();
    }

    public static String key(TerrainAnalysis.Candidate c) {
        return c.kind().name()+(c.kind().water()?"":c.temperature()<=0.15f?":COLD":c.temperature()>=1.2f?":HOT":":TEMPERATE");
    }
    private static boolean valid(String s) { return s.length()<=80 && s.codePoints().noneMatch(c -> Character.isDigit(c) || Character.isISOControl(c)); }
    private static String decorate(String name,String ending) { return ending.isEmpty()?name:stem(name)+ending; }
    private static String stem(String name) {
        for(String suffix:List.of("山脉","山岭","之森","之林","森林","草甸","草原","荒原","平原","之原","冰原","北地","冻土","高原","沙漠","荒漠","冻洋","内海","大洋","群山","山","岭","峰","峦","洋","海","湖","林","森","地","原"))
            if(name.endsWith(suffix) && name.length()>suffix.length()) return name.substring(0,name.length()-suffix.length());
        return name;
    }
    private static String suffix(TerrainAnalysis.Candidate c,AutoConfig config,RandomGenerator random) {
        return switch(c.kind()) {
            case MOUNTAIN -> { String group=mountainGroup(c,config);yield group.isEmpty()?"山":group; }
            case OCEAN -> "洋";case FROZEN_OCEAN -> "冻洋";
            case PLAIN -> List.of("平原","之原").get(Math.floorMod(c.cells().stream().mapToInt(ChunkCell::hashCode).sum(),2));
            case NORTHLAND -> NORTHLAND_SUFFIXES.get(random.nextInt(NORTHLAND_SUFFIXES.size()));
            case FOREST -> List.of("森林","林").get(Math.floorMod(c.cells().stream().mapToInt(ChunkCell::hashCode).sum(),2));
            case LAKE -> "湖";case FLOWER -> "花甸";case DESERT -> "沙漠";
            default -> "";
        };
    }
    private static String mountainGroup(TerrainAnalysis.Candidate c,AutoConfig config) {
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=minX,maxZ=maxX;
        for(ChunkCell p:c.cells()) { minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z()); }
        long width=(long)maxX-minX+1,height=(long)maxZ-minZ+1;
        if(Math.max(width,height)*100>config.mountainRidgeRatioPercent*Math.min(width,height)) return c.span()>=config.mountainRangeSpan?"山脉":"岭";
        return c.span()>=config.mountainRangeSpan || c.cells().size()>=config.mountainMassifChunks?"峦":"";
    }
}
