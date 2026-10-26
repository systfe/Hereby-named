package dev.herebynamed.auto;

import java.util.*;

/** Names are a conservative fallback for untagged mod biomes, never an altitude assumption. */
public final class TerrainClassifier {
    private TerrainClassifier() { }
    static TerrainKind explicitRule(String id,AutoConfig config) {
        if(config.biomeOverrides!=null && config.biomeOverrides.containsKey(id)) {
            String kindName=config.biomeOverrides.get(id);
            try { return TerrainKind.valueOf(kindName); } catch(Exception ignored) { }
        }
        for(var rule:config.biomeRules) {
            String pattern=rule.biome();
            if(pattern.endsWith("*")?id.startsWith(pattern.substring(0,pattern.length()-1)):id.equals(pattern)) return rule.kind();
        }
        return null;
    }
    public static TerrainKind classify(String id,Set<String> tags,float temperature,int relief,AutoConfig config) {
        TerrainKind explicit=explicitRule(id,config);if(explicit!=null) return explicit;
        String path=id.substring(id.indexOf(':')+1);
        if(has(tags,"is_nether","is_end","caves","is_cave")
                || contains(path,"cave","caves","grotto","deep_dark","chasm","void","abyss","undergrowth","inferno","infernal","spider_nest","visceral_heap")
                || id.startsWith("galosphere:") || id.startsWith("yungscavebiomes:") || id.startsWith("heaven:")
                || contains(path,"end_corruption","end_reef","end_wilds","blackstone_basin","placeholder"))
            return TerrainKind.OTHER;
        if(has(tags,"is_ocean") || contains(path,"ocean","sea_","_sea","reef","deeps")) {
            boolean frozen = contains(path,"frozen","icy") || (temperature<=0.15f && !contains(path,"cold_ocean"));
            return frozen ? TerrainKind.FROZEN_OCEAN : TerrainKind.OCEAN;
        }
        if(has(tags,"is_mountain","is_peak","is_mountain/peak","is_mountain/slope")
                || contains(path,"mountain","mountains","peaks","peak","alpine","highlands","highland","crag","ridge","cliff","cliffs","canyon","canyons","spire","spires","slope","slopes","hill","hills","volcano","caldera","crater","stacks","barrera","bluff","skylands")) return TerrainKind.MOUNTAIN;
        boolean plains=has(tags,"is_plains","is_savanna","is_swamp","swamps")
                || contains(path,"plains","prairie","grassland","steppe","savanna","tundra","wasteland","shrubland","scrubland","meadow","pasture","field","patch","greens","swamp","swamplands","marsh","bog","fen","bayou","wetland","wetlands","floodplain","floodlands","valley","vale","basin","lowlands","delta","gour","brushland","bushland","chaparral","moor","wilds","beach","shore","shores","island","islands","isle","isles","yellowstone");
        if(plains && relief>=32) return TerrainKind.MOUNTAIN;
        if(temperature<=0.15f && relief>=48 && !contains(path,"swamp","marsh","cave","beach","shore")) return TerrainKind.MOUNTAIN;
        if(temperature<=0.15f) return TerrainKind.NORTHLAND;
        if(has(tags,"is_river") || contains(path,"river","stream")) return TerrainKind.RIVER;
        if(contains(path,"lake","lagoon","hot_springs")) return TerrainKind.LAKE;
        if(has(tags,"is_floral","is_flower_forest") || contains(path,"flower","blossom","lavender","bloom","sakura","floral","orchid","poppy","tulip","dandelion","clover")) return TerrainKind.FLOWER;
        if(has(tags,"is_desert","is_badlands") || contains(path,"desert","badlands","dunes","outback","xeric","arid","mesa","sands","oasis","dryland","drylands")) return TerrainKind.DESERT;
        if(has(tags,"is_forest","is_taiga","is_jungle")
                || contains(path,"forest","woodland","woods","wooded","grove","rainforest","taiga","jungle","thicket","redwood","old_growth","bamboo","glade","clearing","orchard","covert","boreal","tangle","garden","shield","tropics","erdtree","canopy","timber")) return TerrainKind.FOREST;
        if(plains) {
            // Adjacent recorded heights distinguish steep modded uplands from flat fields.
            return TerrainKind.PLAIN;
        }
        if(relief>=48 && !contains(path,"swamp","marsh","cave","beach","shore")) return TerrainKind.MOUNTAIN;
        return TerrainKind.OTHER;
    }
    public static TerrainKind defaultClassify(String id) {
        return classify(id,Set.of(),0.7f,0,new AutoConfig());
    }
    private static boolean has(Set<String> tags,String... paths) {
        for(String p:paths) if(tags.contains("minecraft:"+p) || tags.contains("c:"+p) || tags.contains("forge:"+p)) return true;
        return false;
    }
    private static boolean contains(String value,String... words) { for(String word:words) if(value.contains(word)) return true;return false; }
}
