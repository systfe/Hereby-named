package dev.herebynamed.auto;

import java.util.*;

/** Names are a conservative fallback for untagged mod biomes, never an altitude assumption. */
public final class TerrainClassifier {
    private TerrainClassifier() { }
    public static TerrainKind classify(String id,Set<String> tags,float temperature,int relief,AutoConfig config) {
        for(var rule:config.biomeRules) {
            String pattern=rule.biome();
            if(pattern.endsWith("*")?id.startsWith(pattern.substring(0,pattern.length()-1)):id.equals(pattern)) return rule.kind();
        }
        String path=id.substring(id.indexOf(':')+1);
        if(has(tags,"is_river") || contains(path,"river","stream")) return TerrainKind.RIVER;
        if(has(tags,"is_ocean") || contains(path,"ocean","sea_","_sea"))
            return temperature<=0.15f || contains(path,"frozen","icy","cold_ocean")?TerrainKind.FROZEN_OCEAN:TerrainKind.OCEAN;
        if(contains(path,"lake")) return temperature<=0.15f?TerrainKind.FROZEN_OCEAN:TerrainKind.LAKE;
        if(has(tags,"is_mountain","is_peak","is_mountain/peak","is_mountain/slope")
                || contains(path,"mountain","peaks","peak","alpine","highlands","crag","ridge")) return TerrainKind.MOUNTAIN;
        if(has(tags,"is_floral","is_flower_forest") || contains(path,"flower","blossom","lavender","bloom","sakura","floral","orchid","poppy","tulip","dandelion","clover")) return TerrainKind.FLOWER;
        if(has(tags,"is_desert","is_badlands") || contains(path,"desert","badlands","dunes","outback","xeric","arid")) return TerrainKind.DESERT;
        if(has(tags,"is_forest","is_taiga","is_jungle") || contains(path,"forest","woodland","woods","grove","rainforest","taiga","jungle","thicket","redwood","old_growth","bamboo")) return TerrainKind.FOREST;
        if(has(tags,"is_plains","is_savanna") || contains(path,"plains","prairie","grassland","steppe","savanna","tundra","wasteland","shrubland","scrubland","meadow","pasture","field")) {
            // Adjacent recorded heights distinguish steep modded uplands from flat fields.
            return relief>=32?TerrainKind.MOUNTAIN:TerrainKind.PLAIN;
        }
        if(relief>=48 && !contains(path,"swamp","marsh","cave","beach","shore")) return TerrainKind.MOUNTAIN;
        return TerrainKind.OTHER;
    }
    private static boolean has(Set<String> tags,String... paths) {
        for(String p:paths) if(tags.contains("minecraft:"+p) || tags.contains("c:"+p) || tags.contains("forge:"+p)) return true;
        return false;
    }
    private static boolean contains(String value,String... words) { for(String word:words) if(value.contains(word)) return true;return false; }
}
