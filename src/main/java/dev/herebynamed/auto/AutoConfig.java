package dev.herebynamed.auto;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class AutoConfig {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public record Name(String text,List<String> kinds,String climate) {
        public boolean fits(TerrainKind kind,float temperature) {
            return (kinds.contains(kind.name()) || kinds.contains("ALL")) && switch(climate) {
                case "COLD" -> temperature<=0.15f;
                case "HOT" -> temperature>=1.2f;
                case "TEMPERATE" -> temperature>0.15f && temperature<1.2f;
                default -> true;
            };
        }
    }
    public record BiomeRule(String biome,TerrainKind kind) { }
    /** Zero disables a lower-bound branch; maximumSpan is exclusive, with zero meaning unlimited. */
    public record Threshold(int minimumBiomes,int minimumSpan,int maximumSpan) {
        public boolean accepts(int span,long biomes) {
            return (maximumSpan==0 || span<maximumSpan)
                    && ((minimumBiomes>0 && biomes>=minimumBiomes) || (minimumSpan>0 && span>=minimumSpan));
        }
        void validate() {
            if(minimumBiomes<0 || minimumBiomes>4096 || minimumSpan<0 || minimumSpan>1_000_000
                    || maximumSpan<0 || maximumSpan>1_000_000 || (minimumBiomes==0 && minimumSpan==0)
                    || (maximumSpan>0 && minimumSpan>=maximumSpan))
                throw new IllegalArgumentException("区域门槛无效：数量和跨度至少启用一项，上限必须大于最小跨度");
        }
    }
    public static final class Thresholds {
        public Threshold mountain=new Threshold(0,1000,0);
        public Threshold ocean=new Threshold(0,2000,0);
        public Threshold frozenOcean=new Threshold(5,1000,0);
        public Threshold plain=new Threshold(5,1000,0);
        public Threshold forest=new Threshold(5,1000,0);
        public Threshold flower=new Threshold(5,1000,0);
        public Threshold desert=new Threshold(4,1500,0);
        public Threshold lake=new Threshold(0,500,2000);
        Threshold forKind(TerrainKind kind) {
            return switch(kind) {
                case MOUNTAIN -> mountain;case OCEAN -> ocean;case FROZEN_OCEAN -> frozenOcean;
                case PLAIN -> plain;case FOREST -> forest;case FLOWER -> flower;case DESERT -> desert;case LAKE -> lake;
                default -> null;
            };
        }
    }
    public Thresholds thresholds=new Thresholds();
    public boolean qualifies(TerrainKind kind,int span,long biomes) {
        Threshold threshold=thresholds.forKind(kind);
        return span>=minimumSpan && threshold!=null && threshold.accepts(span,biomes);
    }
    public boolean enabled = true;
    public int unlockSpan = 2000;
    public int minimumSpan = 256;
    public int minimumBiomeChunks = 4;
    public int maximumSamples = 250_000;
    public int analysisIntervalSeconds = 20;
    public List<BiomeRule> biomeRules=new ArrayList<>();
    public transient List<Name> names=new ArrayList<>(),roots=new ArrayList<>();
    public static AutoConfig load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file=directory.resolve("automatic.json"); AutoConfig config;
        try {
            if(Files.exists(file) && Files.size(file)>1_000_000) throw new IOException("自动区域配置过大");
            JsonObject input=Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():new JsonObject();
            boolean expanded=fillMissing(input,JSON.toJsonTree(new AutoConfig()).getAsJsonObject());
            config=JSON.fromJson(input,AutoConfig.class);
            if(config==null) throw new IllegalArgumentException("配置为空");
            config.names=loadNames(directory,"names.json","/assets/hereby_named/auto/names.json");
            config.roots=loadNames(directory,"roots.json","/assets/hereby_named/auto/roots.json");
            config.validate();
            if(expanded) writeConfiguration(directory,input);
            return config;
        } catch(RuntimeException ex) { throw new IOException("自动区域配置无效，请检查 automatic.json、names.json 和 roots.json",ex); }
    }
    private static boolean fillMissing(JsonObject input,JsonObject defaults) {
        boolean changed=false;
        for(var entry:defaults.entrySet()) {
            JsonElement existing=input.get(entry.getKey());
            if(existing==null) { input.add(entry.getKey(),entry.getValue().deepCopy());changed=true; }
            else if(existing.isJsonObject() && entry.getValue().isJsonObject())
                changed|=fillMissing(existing.getAsJsonObject(),entry.getValue().getAsJsonObject());
        }
        return changed;
    }
    private static List<Name> loadNames(Path directory,String file,String resource) throws IOException {
        Path target=directory.resolve(file);
        if(!Files.exists(target)) try(InputStream in=AutoConfig.class.getResourceAsStream(resource)) {
            if(in==null) throw new IOException("缺少默认名称资源"); Files.copy(in,target);
        }
        if(Files.size(target)>1_000_000) throw new IOException("名称白名单过大");
        return new ArrayList<>(Arrays.asList(JSON.fromJson(Files.readString(target),Name[].class)));
    }
    public void save(Path directory) throws IOException {
        validate();writeConfiguration(directory,JSON.toJsonTree(this).getAsJsonObject());
    }
    private static void writeConfiguration(Path directory,JsonObject json) throws IOException {
        Files.createDirectories(directory); Path target=directory.resolve("automatic.json"),temp=directory.resolve("automatic.json.tmp");
        Files.writeString(temp,JSON.toJson(json),StandardCharsets.UTF_8);
        Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
    }
    public void validate() {
        if(thresholds==null) throw new IllegalArgumentException("区域门槛不能为空");
        for(TerrainKind kind:TerrainKind.values()) {
            if(kind==TerrainKind.RIVER || kind==TerrainKind.OTHER) continue;
            Threshold threshold=thresholds.forKind(kind);
            if(threshold==null) throw new IllegalArgumentException("区域门槛不能为空："+kind);
            threshold.validate();
        }
        if(unlockSpan<256 || unlockSpan>1_000_000 || minimumSpan<16 || minimumSpan>100_000
                || minimumBiomeChunks<1 || minimumBiomeChunks>4096 || maximumSamples<4096 || maximumSamples>500_000
                || analysisIntervalSeconds<10 || analysisIntervalSeconds>600 || biomeRules==null || biomeRules.size()>4096)
            throw new IllegalArgumentException("自动区域参数超出范围");
        for(BiomeRule r:biomeRules) if(r==null || r.kind()==null || r.biome()==null || !r.biome().matches("[a-z0-9_.-]+:[a-z0-9_./*-]+")) throw new IllegalArgumentException("群系覆盖规则无效");
        for(List<Name> list:List.of(names,roots)) {
            if(list==null || list.size()>4096) throw new IllegalArgumentException("名称数量超出范围");
            for(Name n:list) if(n==null || n.text()==null || n.text().isBlank() || n.text().length()>48
                    || n.text().codePoints().anyMatch(c -> Character.isISOControl(c) || Character.isDigit(c))
                    || n.kinds()==null || n.kinds().isEmpty() || n.kinds().stream().anyMatch(k -> !k.equals("ALL") && Arrays.stream(TerrainKind.values()).noneMatch(t -> t.name().equals(k)))
                    || !Set.of("ANY","COLD","TEMPERATE","HOT").contains(n.climate())) throw new IllegalArgumentException("名称条目无效");
        }
    }
}
