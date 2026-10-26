package dev.herebynamed.auto;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class AutoConfig {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public record Name(String text,List<String> kinds,List<String> climates) {
        public Name(String text,List<String> kinds,String climate) {
            this(text,kinds,"ANY".equals(climate)?List.of():List.of(climate));
        }
        public boolean fits(TerrainKind kind,float temperature) {
            if(!kinds.isEmpty() && !kinds.contains("ALL") && !kinds.contains(kind.name())) return false;
            if(climates.isEmpty()) return true;
            String climate=temperature<=0.15f?"COLD":temperature>=1.2f?"HOT":"TEMPERATE";
            return climates.contains(climate);
        }
    }
    public record BiomeRule(String biome,TerrainKind kind) { }
    /** maximumSpan is exclusive, with zero meaning unlimited. */
    public record Threshold(int minimumSpan,int maximumSpan) {
        public boolean accepts(int span) {
            return (maximumSpan==0 || span<maximumSpan) && minimumSpan>0 && span>=minimumSpan;
        }
        void validate() {
            if(minimumSpan<1 || minimumSpan>1_000_000 || maximumSpan<0 || maximumSpan>1_000_000
                    || (maximumSpan>0 && minimumSpan>=maximumSpan))
                throw new IllegalArgumentException("区域跨度门槛无效：上限必须大于最小跨度");
        }
    }
    public static final class Thresholds {
        public Threshold mountain=new Threshold(1000,0);
        public Threshold ocean=new Threshold(2000,0);
        public Threshold frozenOcean=new Threshold(1000,0);
        public Threshold plain=new Threshold(1000,0);
        public Threshold forest=new Threshold(1000,0);
        public Threshold flower=new Threshold(1000,0);
        public Threshold desert=new Threshold(1500,0);
        public Threshold lake=new Threshold(500,2000);
        public Threshold northland=new Threshold(1000,0);
        Threshold forKind(TerrainKind kind) {
            return switch(kind) {
                case MOUNTAIN -> mountain;case OCEAN -> ocean;case FROZEN_OCEAN -> frozenOcean;
                case PLAIN -> plain;case FOREST -> forest;case FLOWER -> flower;case DESERT -> desert;case LAKE -> lake;
                case NORTHLAND -> northland;
                default -> null;
            };
        }
    }
    public Thresholds thresholds=new Thresholds();
    public boolean qualifies(TerrainKind kind,int span) {
        Threshold threshold=thresholds.forKind(kind);
        return span>=minimumSpan && threshold!=null && threshold.accepts(span);
    }
    public boolean enabled = true;
    public boolean suppressDeletedRegions = true;
    public int unlockSpan = 2000;
    public int minimumSpan = 256;
    public int maximumSamples = 250_000;
    public int analysisIntervalSeconds = 20;
    public int mountainRangeSpan = 2000;
    public int mountainMassifChunks = 4096;
    public int mountainRidgeRatioPercent = 180;
    public List<BiomeRule> biomeRules=new ArrayList<>();
    public transient List<Name> names=new ArrayList<>(),roots=new ArrayList<>();
    public static AutoConfig load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file=directory.resolve("automatic.json"); AutoConfig config;
        try {
            if(Files.exists(file) && Files.size(file)>1_000_000) throw new IOException("自动区域配置过大");
            JsonObject input=Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():new JsonObject();
            normalizeLegacyThresholds(input);
            input.remove("minimumBiomeChunks");
            boolean expanded=fillMissing(input,JSON.toJsonTree(new AutoConfig()).getAsJsonObject());
            config=JSON.fromJson(input,AutoConfig.class);
            if(config==null) throw new IllegalArgumentException("配置为空");
            LoadedNames names=loadNames(directory,"names.json","/assets/hereby_named/auto/names.json");
            LoadedNames roots=loadNames(directory,"roots.json","/assets/hereby_named/auto/roots.json");
            config.names=names.values();config.roots=roots.values();
            config.validate();
            config.names=upgradeDefaultNames(directory,"names.json",names,"df7f1ee69c954aeca9261a24bc3da99116363837e45d7c8d1bd4770b9ea06cc1");
            config.roots=upgradeDefaultNames(directory,"roots.json",roots,"136ac016273bf16ae9ee790ab5245a2e07e8b8438fef079c0474a2fd9aacf03f");
            if(expanded) writeConfiguration(directory,input);
            return config;
        } catch(RuntimeException ex) { throw new IOException("自动区域配置无效，请检查 automatic.json、names.json 和 roots.json",ex); }
    }
    private static void normalizeLegacyThresholds(JsonObject input) {
        JsonObject thresholds=input.getAsJsonObject("thresholds");
        if(thresholds==null) return;
        Map<String,Integer> spans=Map.of("mountain",1000,"ocean",2000,"frozenOcean",1000,"plain",1000,
                "forest",1000,"flower",1000,"desert",1500,"lake",500,"northland",1000);
        for(var entry:spans.entrySet()) {
            if(!thresholds.has(entry.getKey()) || !thresholds.get(entry.getKey()).isJsonObject()) continue;
            JsonObject threshold=thresholds.getAsJsonObject(entry.getKey());
            threshold.remove("minimumBiomes");
            if(!threshold.has("minimumSpan") || threshold.get("minimumSpan").getAsInt()==0)
                threshold.addProperty("minimumSpan",entry.getValue());
        }
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
    private record LoadedNames(List<Name> values,String legacySignature,boolean migrated) { }
    private static LoadedNames loadNames(Path directory,String file,String resource) throws IOException {
        Path target=directory.resolve(file);
        if(!Files.exists(target)) try(InputStream in=AutoConfig.class.getResourceAsStream(resource)) {
            if(in==null) throw new IOException("缺少默认名称资源"); Files.copy(in,target);
        }
        if(Files.size(target)>1_000_000) throw new IOException("名称白名单过大");
        JsonArray input=JsonParser.parseString(Files.readString(target)).getAsJsonArray();
        StringBuilder legacy=new StringBuilder();boolean migrated=false,allLegacy=true;
        for(JsonElement element:input) {
            JsonObject object=element.getAsJsonObject();JsonElement oldClimate=object.remove("climate");
            if(!object.has("climates") && oldClimate!=null) {
                String value=oldClimate.getAsString();JsonArray climates=new JsonArray();
                if(!value.equals("ANY")) climates.add(value);
                object.add("climates",climates);migrated=true;
                legacy.append(object.get("text").getAsString()).append('|');
                JsonArray kinds=object.getAsJsonArray("kinds");
                for(int i=0;i<kinds.size();i++) { if(i>0) legacy.append(',');legacy.append(kinds.get(i).getAsString()); }
                legacy.append('|').append(value).append('\n');
            } else allLegacy=false;
        }
        return new LoadedNames(new ArrayList<>(Arrays.asList(JSON.fromJson(input,Name[].class))),
                allLegacy&&migrated?legacy.toString():null,migrated);
    }
    private static List<Name> upgradeDefaultNames(Path directory,String file,LoadedNames loaded,String oldHash) throws IOException {
        List<Name> result=loaded.values();boolean upgrade=false;
        try {
            if(loaded.legacySignature()!=null) {
                String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(loaded.legacySignature().getBytes(StandardCharsets.UTF_8)));
                upgrade=hash.equals(oldHash);
            }
            if(upgrade) result=defaultNames(file.equals("roots.json"));
            if(upgrade || loaded.migrated()) {
                Path temporary=directory.resolve(file+".tmp");Files.writeString(temporary,JSON.toJson(result),StandardCharsets.UTF_8);
                Files.move(temporary,directory.resolve(file),StandardCopyOption.REPLACE_EXISTING);
            }
        } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        return result;
    }
    public static List<Name> defaultNames(boolean roots) throws IOException {
        try(InputStream in=AutoConfig.class.getResourceAsStream("/assets/hereby_named/auto/"+(roots?"roots":"names")+".json")) {
            if(in==null) throw new IOException("缺少默认名称资源");
            return new ArrayList<>(Arrays.asList(JSON.fromJson(new String(in.readAllBytes(),StandardCharsets.UTF_8),Name[].class)));
        }
    }
    public AutoConfig copy() {
        AutoConfig copy=JSON.fromJson(JSON.toJsonTree(this),AutoConfig.class);
        copy.names=new ArrayList<>(names.stream().map(n -> new Name(n.text(),List.copyOf(n.kinds()),List.copyOf(n.climates()))).toList());
        copy.roots=new ArrayList<>(roots.stream().map(n -> new Name(n.text(),List.copyOf(n.kinds()),List.copyOf(n.climates()))).toList());
        return copy;
    }
    /** Validate and stage the entire settings draft before replacing any active file. */
    public void saveAll(Path directory) throws IOException {
        validate();Files.createDirectories(directory);
        Map<String,String> values=new LinkedHashMap<>();
        values.put("automatic.json",JSON.toJson(this));values.put("names.json",JSON.toJson(names));values.put("roots.json",JSON.toJson(roots));
        Map<Path,byte[]> previous=new LinkedHashMap<>();
        try {
            for(var entry:values.entrySet()) {
                byte[] bytes=entry.getValue().getBytes(StandardCharsets.UTF_8);
                if(bytes.length>1_000_000) throw new IOException("设置文件过大");
                Path file=directory.resolve(entry.getKey());previous.put(file,Files.exists(file)?Files.readAllBytes(file):null);
                Files.write(directory.resolve(entry.getKey()+".tmp"),bytes);
            }
            for(String file:values.keySet()) Files.move(directory.resolve(file+".tmp"),directory.resolve(file),StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException ex) {
            for(var entry:previous.entrySet()) try {
                if(entry.getValue()==null) Files.deleteIfExists(entry.getKey());else Files.write(entry.getKey(),entry.getValue());
            } catch(IOException rollback) { ex.addSuppressed(rollback); }
            throw ex;
        } finally { for(String file:values.keySet()) Files.deleteIfExists(directory.resolve(file+".tmp")); }
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
                || maximumSamples<4096 || maximumSamples>500_000
                || analysisIntervalSeconds<10 || analysisIntervalSeconds>600 || biomeRules==null || biomeRules.size()>4096)
            throw new IllegalArgumentException("自动区域参数超出范围");
        if(mountainRangeSpan<16 || mountainRangeSpan>1_000_000 || mountainMassifChunks<1 || mountainMassifChunks>500_000
                || mountainRidgeRatioPercent<110 || mountainRidgeRatioPercent>1000)
            throw new IllegalArgumentException("山地形态参数超出范围");
        for(BiomeRule r:biomeRules) if(r==null || r.kind()==null || r.biome()==null || !r.biome().matches("[a-z0-9_.-]+:[a-z0-9_./*-]+")) throw new IllegalArgumentException("群系覆盖规则无效");
        for(List<Name> list:List.of(names,roots)) {
            if(list==null || list.size()>4096) throw new IllegalArgumentException("名称数量超出范围");
            for(Name n:list) if(n==null || n.text()==null || n.text().isBlank() || n.text().length()>48
                    || n.text().codePoints().anyMatch(c -> Character.isISOControl(c) || Character.isDigit(c))
                    || n.kinds()==null || n.kinds().stream().anyMatch(k -> !k.equals("ALL") && Arrays.stream(TerrainKind.values()).noneMatch(t -> t.name().equals(k)))
                    || n.climates()==null || n.climates().stream().anyMatch(c -> !Set.of("COLD","TEMPERATE","HOT").contains(c))
                    || n.climates().stream().distinct().count()!=n.climates().size()) throw new IllegalArgumentException("名称条目无效");
        }
    }
}
