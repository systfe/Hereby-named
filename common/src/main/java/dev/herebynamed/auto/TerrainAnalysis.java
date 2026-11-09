package dev.herebynamed.auto;

import dev.herebynamed.domain.ChunkCell;
import java.util.*;

/** Pure analysis of recorded surface data using Biome Patch Graph; call from the single background worker. */
public final class TerrainAnalysis {
    private static final int MAX_RIVER_BRIDGE_CHUNKS = 8;

    public record Candidate(TerrainKind kind, Set<ChunkCell> cells, float temperature, int span) {
        public Candidate { cells = Set.copyOf(cells); }
    }
    public record Result(boolean unlocked, List<Candidate> candidates) { }

    public static final class BiomePatch {
        public final int id;
        public final String biome;
        public TerrainKind kind;
        public final Set<ChunkCell> cells = new HashSet<>();
        public double totalTemperature;
        public int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        public BiomePatch(int id, String biome, TerrainKind kind) {
            this.id = id;
            this.biome = biome;
            this.kind = kind;
        }

        public void addCell(ChunkCell c, float temperature) {
            cells.add(c);
            totalTemperature += temperature;
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }

        public float avgTemperature() {
            return cells.isEmpty() ? 0.7f : (float)(totalTemperature / cells.size());
        }

        public int climate() {
            float temp = avgTemperature();
            return temp <= 0.15f ? -1 : temp >= 1.2f ? 1 : 0;
        }

        public int span() {
            return (int)Math.min(Integer.MAX_VALUE, 16L * Math.max((long)maxX - minX + 1, (long)maxZ - minZ + 1));
        }

        public void absorb(BiomePatch other) {
            cells.addAll(other.cells);
            totalTemperature += other.totalTemperature;
            minX = Math.min(minX, other.minX);
            maxX = Math.max(maxX, other.maxX);
            minZ = Math.min(minZ, other.minZ);
            maxZ = Math.max(maxZ, other.maxZ);
        }
    }

    public Result analyze(Map<ChunkCell, TerrainSample> input, AutoConfig config) {
        if (!unlocked(input, config.unlockSpan)) return new Result(false, List.of());
        Map<ChunkCell, TerrainSample> samples = classified(input, config);

        Map<ChunkCell, BiomePatch> cellToPatch = new HashMap<>(samples.size());
        List<BiomePatch> patches = clusterPatches(samples, cellToPatch);
        absorbTinyPatches(patches, cellToPatch);

        List<Candidate> candidates = new ArrayList<>();
        analyzeWaterPatches(patches, cellToPatch, samples, candidates, config);

        analyzeLandPatches(patches, cellToPatch, samples, candidates, config);

        candidates.sort(Comparator.comparing(c -> c.cells().stream().min(Comparator.comparingInt(ChunkCell::x).thenComparingInt(ChunkCell::z)).orElseThrow(),
                Comparator.comparingInt(ChunkCell::x).thenComparingInt(ChunkCell::z)));
        return new Result(true, fillEnclosedRecordedHoles(candidates, samples));
    }

    private static List<BiomePatch> clusterPatches(Map<ChunkCell, TerrainSample> samples, Map<ChunkCell, BiomePatch> cellToPatch) {
        Set<ChunkCell> remaining = new HashSet<>(samples.keySet());
        List<BiomePatch> patches = new ArrayList<>();
        int patchId = 0;
        for (ChunkCell start : samples.keySet()) {
            if (!remaining.remove(start)) continue;
            TerrainSample seed = samples.get(start);
            String biome = seed.biome();
            BiomePatch patch = new BiomePatch(patchId++, biome, seed.kind());
            ArrayDeque<ChunkCell> queue = new ArrayDeque<>();
            queue.add(start);
            patch.addCell(start, seed.temperature());
            cellToPatch.put(start, patch);

            while (!queue.isEmpty()) {
                ChunkCell cur = queue.removeFirst();
                for (ChunkCell n : neighbors(cur)) {
                    if (remaining.contains(n)) {
                        TerrainSample next = samples.get(n);
                        if (next.biome().equals(biome) && next.kind() == seed.kind()) {
                            remaining.remove(n);
                            patch.addCell(n, next.temperature());
                            cellToPatch.put(n, patch);
                            queue.add(n);
                        }
                    }
                }
            }
            patches.add(patch);
        }
        return patches;
    }

    private static void absorbTinyPatches(List<BiomePatch> patches, Map<ChunkCell, BiomePatch> cellToPatch) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BiomePatch patch : new ArrayList<>(patches)) {
                if (patch.cells.isEmpty() || patch.cells.size() > 3) continue;
                BiomePatch host = null;
                boolean singleHost = true;
                for (ChunkCell c : patch.cells) {
                    for (ChunkCell n : neighbors(c)) {
                        if (patch.cells.contains(n)) continue;
                        BiomePatch neighborPatch = cellToPatch.get(n);
                        if (neighborPatch == null) {
                            singleHost = false;
                            break;
                        }
                        if (host == null) {
                            host = neighborPatch;
                        } else if (host != neighborPatch) {
                            singleHost = false;
                            break;
                        }
                    }
                    if (!singleHost) break;
                }
                if (singleHost && host != null && host != patch) {
                    for (ChunkCell c : patch.cells) {
                        cellToPatch.put(c, host);
                    }
                    host.absorb(patch);
                    patch.cells.clear();
                    patches.remove(patch);
                    changed = true;
                    break;
                }
            }
        }
    }

    private static void analyzeLandPatches(List<BiomePatch> patches, Map<ChunkCell, BiomePatch> cellToPatch,
                                          Map<ChunkCell, TerrainSample> samples, List<Candidate> out, AutoConfig config) {
        List<BiomePatch> landPatches = new ArrayList<>();
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        Map<Integer, BiomePatch> patchById = new HashMap<>();

        for (BiomePatch patch : patches) {
            if (patch.cells.isEmpty() || patch.kind.water() || patch.kind == TerrainKind.OTHER) continue;
            landPatches.add(patch);
            patchById.put(patch.id, patch);
            adj.put(patch.id, new HashSet<>());
        }

        // Direct edge adjacency between land patches of same kind and compatible climate
        for (BiomePatch p1 : landPatches) {
            for (ChunkCell c : p1.cells) {
                for (ChunkCell n : neighbors(c)) {
                    BiomePatch p2 = cellToPatch.get(n);
                    if (p2 != null && p2 != p1 && p2.kind == p1.kind && p1.climate() * p2.climate() != -1 && patchById.containsKey(p2.id)) {
                        adj.get(p1.id).add(p2.id);
                        adj.get(p2.id).add(p1.id);
                    }
                }
            }
        }

        // River bridge connectivity
        Map<Integer, Set<ChunkCell>> bridgedRiverCells = new HashMap<>();
        for (BiomePatch river : patches) {
            if (river.cells.isEmpty() || river.kind != TerrainKind.RIVER) continue;
            if (river.span() > 16 * MAX_RIVER_BRIDGE_CHUNKS && river.cells.size() > MAX_RIVER_BRIDGE_CHUNKS * 4) continue;
            Set<BiomePatch> touchingLand = new HashSet<>();
            for (ChunkCell c : river.cells) {
                for (ChunkCell n : neighbors(c)) {
                    BiomePatch p = cellToPatch.get(n);
                    if (p != null && patchById.containsKey(p.id)) touchingLand.add(p);
                }
            }
            Map<TerrainKind, List<BiomePatch>> byKind = new HashMap<>();
            for (BiomePatch p : touchingLand) byKind.computeIfAbsent(p.kind, k -> new ArrayList<>()).add(p);
            for (var entry : byKind.entrySet()) {
                List<BiomePatch> list = entry.getValue();
                if (list.size() >= 2) {
                    for (int i = 0; i < list.size(); i++) {
                        for (int j = i + 1; j < list.size(); j++) {
                            BiomePatch a = list.get(i), b = list.get(j);
                            if (a.climate() * b.climate() != -1) {
                                adj.get(a.id).add(b.id);
                                adj.get(b.id).add(a.id);
                                bridgedRiverCells.computeIfAbsent(a.id, k -> new HashSet<>()).addAll(river.cells);
                                bridgedRiverCells.computeIfAbsent(b.id, k -> new HashSet<>()).addAll(river.cells);
                            }
                        }
                    }
                }
            }
        }

        // Group connected components
        Set<Integer> visited = new HashSet<>();
        landPatches.sort(Comparator.<BiomePatch>comparingInt(p -> -Math.abs(p.climate())).thenComparingInt(p -> p.id));
        for (BiomePatch seed : landPatches) {
            if (visited.contains(seed.id)) continue;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(seed.id);
            visited.add(seed.id);
            Set<ChunkCell> componentCells = new HashSet<>();
            int climate = seed.climate();

            while (!queue.isEmpty()) {
                int curId = queue.removeFirst();
                BiomePatch curPatch = patchById.get(curId);
                componentCells.addAll(curPatch.cells);
                Set<ChunkCell> rivers = bridgedRiverCells.get(curId);
                if (rivers != null) componentCells.addAll(rivers);

                for (int nextId : adj.getOrDefault(curId, Set.of())) {
                    if (visited.add(nextId)) {
                        BiomePatch nextPatch = patchById.get(nextId);
                        if (climate == 0) climate = nextPatch.climate();
                        queue.add(nextId);
                    }
                }
            }

            addConnectedCandidates(out, seed.kind, componentCells, samples, config);
        }
    }

    private static void analyzeWaterPatches(List<BiomePatch> patches, Map<ChunkCell, BiomePatch> cellToPatch,
                                           Map<ChunkCell, TerrainSample> samples, List<Candidate> out, AutoConfig config) {
        Set<ChunkCell> waterCells = new HashSet<>();
        for (BiomePatch p : patches) {
            if (p.kind.water() && p.kind != TerrainKind.RIVER) waterCells.addAll(p.cells);
        }

        for (ChunkCell start : new ArrayList<>(waterCells)) {
            if (!waterCells.contains(start)) continue;
            Set<ChunkCell> body = flood(waterCells, start);
            Candidate whole = candidate(TerrainKind.OCEAN, body, samples);
            boolean closed = enclosed(body, samples);
            if (closed && config.qualifies(TerrainKind.LAKE, whole.span())) {
                out.add(candidate(TerrainKind.LAKE, body, samples));
                continue;
            }
            Set<ChunkCell> ice = new HashSet<>();
            for (ChunkCell c : body) if (samples.get(c).kind() == TerrainKind.FROZEN_OCEAN) ice.add(c);
            Set<ChunkCell> oceanBody = new HashSet<>(body);
            for (ChunkCell frozenStart : new ArrayList<>(ice)) {
                if (!ice.contains(frozenStart)) continue;
                Set<ChunkCell> part = flood(ice, frozenStart);
                if (part.size() * 3L >= body.size() && config.qualifies(TerrainKind.FROZEN_OCEAN, candidate(TerrainKind.FROZEN_OCEAN, part, samples).span())) {
                    addIfQualified(out, TerrainKind.FROZEN_OCEAN, part, samples, config);
                    oceanBody.removeAll(part);
                }
            }
            for (ChunkCell oceanStart : new ArrayList<>(oceanBody)) {
                if (oceanBody.contains(oceanStart)) {
                    addIfQualified(out, TerrainKind.OCEAN, flood(oceanBody, oceanStart), samples, config);
                }
            }
        }
    }

    private static Map<ChunkCell, TerrainSample> classified(Map<ChunkCell, TerrainSample> samples, AutoConfig config) {
        Map<ChunkCell, TerrainSample> result = new HashMap<>(samples.size());
        for (var entry : samples.entrySet()) {
            TerrainSample sample = entry.getValue();
            TerrainKind explicit = TerrainClassifier.explicitRule(sample.biome(), config);
            TerrainKind kind = explicit;
            if (kind == null) {
                kind = sample.kind();
                if (sample.temperature() <= 0.15f && kind != TerrainKind.MOUNTAIN) {
                    String path = sample.biome().substring(sample.biome().indexOf(':') + 1);
                    boolean oldColdLake = kind == TerrainKind.FROZEN_OCEAN && path.contains("lake");
                    if (kind != TerrainKind.FROZEN_OCEAN || oldColdLake) kind = TerrainKind.NORTHLAND;
                }
            }
            result.put(entry.getKey(), kind == sample.kind() ? sample : new TerrainSample(sample.biome(), kind, sample.temperature(), sample.height()));
        }
        return result;
    }

    private static Set<ChunkCell> flood(Set<ChunkCell> remaining, ChunkCell start) {
        Set<ChunkCell> cells = new HashSet<>();
        ArrayDeque<ChunkCell> pending = new ArrayDeque<>();
        remaining.remove(start);
        pending.add(start);
        cells.add(start);
        while (!pending.isEmpty()) {
            for (ChunkCell n : neighbors(pending.removeFirst())) {
                if (remaining.remove(n)) {
                    cells.add(n);
                    pending.add(n);
                }
            }
        }
        return cells;
    }

    private static Candidate candidate(TerrainKind kind, Set<ChunkCell> cells, Map<ChunkCell, TerrainSample> samples) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = minX, maxZ = maxX;
        double temperature = 0;
        for (ChunkCell c : cells) {
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
            temperature += samples.get(c).temperature();
        }
        return new Candidate(kind, cells, (float)(temperature / cells.size()), (int)Math.min(Integer.MAX_VALUE, 16L * Math.max((long)maxX - minX + 1, (long)maxZ - minZ + 1)));
    }

    private static void addIfQualified(List<Candidate> out, TerrainKind kind, Set<ChunkCell> cells, Map<ChunkCell, TerrainSample> samples, AutoConfig config) {
        Candidate c = candidate(kind, cells, samples);
        if (config.qualifies(kind, c.span())) out.add(c);
    }

    private static void addConnectedCandidates(List<Candidate> out, TerrainKind kind, Set<ChunkCell> cells,
                                               Map<ChunkCell, TerrainSample> samples, AutoConfig config) {
        Set<ChunkCell> unseen = new HashSet<>(cells);
        while (!unseen.isEmpty()) {
            ChunkCell start = unseen.iterator().next();
            unseen.remove(start);
            Set<ChunkCell> component = new HashSet<>();
            ArrayDeque<ChunkCell> pending = new ArrayDeque<>();
            component.add(start);
            pending.add(start);
            while (!pending.isEmpty()) {
                for (ChunkCell neighbor : neighbors(pending.removeFirst())) {
                    if (unseen.remove(neighbor)) {
                        component.add(neighbor);
                        pending.addLast(neighbor);
                    }
                }
            }
            for (Set<ChunkCell> sub : splitNecks(component)) addIfQualified(out, kind, sub, samples, config);
        }
    }

    private static List<Set<ChunkCell>> splitNecks(Set<ChunkCell> cells) {
        if (cells.size() < 16) return List.of(cells);
        Set<ChunkCell> cores = new HashSet<>();
        for (ChunkCell c : cells) {
            boolean core = true;
            for (ChunkCell n : neighbors(c)) if (!cells.contains(n)) { core = false; break; }
            if (core) cores.add(c);
        }
        if (cores.isEmpty()) return List.of(cells);
        List<Set<ChunkCell>> coreComponents = new ArrayList<>();
        Set<ChunkCell> unseenCore = new HashSet<>(cores);
        while (!unseenCore.isEmpty()) {
            ChunkCell start = unseenCore.iterator().next();
            unseenCore.remove(start);
            Set<ChunkCell> comp = new HashSet<>();
            ArrayDeque<ChunkCell> queue = new ArrayDeque<>();
            comp.add(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                ChunkCell cur = queue.removeFirst();
                for (ChunkCell n : neighbors(cur)) if (unseenCore.remove(n)) { comp.add(n); queue.add(n); }
            }
            if (comp.size() >= 4) coreComponents.add(comp);
        }
        if (coreComponents.size() <= 1) return List.of(cells);
        Map<ChunkCell, Integer> owner = new HashMap<>();
        ArrayDeque<ChunkCell> queue = new ArrayDeque<>();
        for (int i = 0; i < coreComponents.size(); i++) for (ChunkCell c : coreComponents.get(i)) { owner.put(c, i); queue.add(c); }
        while (!queue.isEmpty()) {
            ChunkCell cur = queue.removeFirst();
            int id = owner.get(cur);
            for (ChunkCell n : neighbors(cur)) if (cells.contains(n) && !owner.containsKey(n)) { owner.put(n, id); queue.add(n); }
        }
        Map<Integer, Set<ChunkCell>> partitioned = new HashMap<>();
        for (var entry : owner.entrySet()) partitioned.computeIfAbsent(entry.getValue(), k -> new HashSet<>()).add(entry.getKey());
        List<Set<ChunkCell>> result = new ArrayList<>();
        for (Set<ChunkCell> part : partitioned.values()) {
            Set<ChunkCell> unseenPart = new HashSet<>(part);
            while (!unseenPart.isEmpty()) {
                ChunkCell start = unseenPart.iterator().next();
                unseenPart.remove(start);
                Set<ChunkCell> connected = new HashSet<>();
                ArrayDeque<ChunkCell> q = new ArrayDeque<>();
                connected.add(start);
                q.add(start);
                while (!q.isEmpty()) for (ChunkCell n : neighbors(q.removeFirst())) if (unseenPart.remove(n)) { connected.add(n); q.add(n); }
                result.add(connected);
            }
        }
        return result.isEmpty() ? List.of(cells) : result;
    }

    private static List<Candidate> fillEnclosedRecordedHoles(List<Candidate> candidates, Map<ChunkCell, TerrainSample> samples) {
        Set<ChunkCell> claimed = new HashSet<>();
        for (Candidate candidate : candidates) claimed.addAll(candidate.cells());
        List<Candidate> result = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = minX, maxZ = maxX;
            for (ChunkCell cell : candidate.cells()) {
                minX = Math.min(minX, cell.x());
                maxX = Math.max(maxX, cell.x());
                minZ = Math.min(minZ, cell.z());
                maxZ = Math.max(maxZ, cell.z());
            }
            long width = (long)maxX - minX + 1, height = (long)maxZ - minZ + 1;
            if (width * height > 1_000_000L) { result.add(candidate); continue; }
            Set<ChunkCell> visited = new HashSet<>(), holes = new HashSet<>();
            ArrayDeque<ChunkCell> pending = new ArrayDeque<>();
            for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                ChunkCell cell = new ChunkCell(x, z);
                if (candidate.cells().contains(cell) || claimed.contains(cell) || visited.contains(cell)) continue;
                Set<ChunkCell> component = new HashSet<>();
                pending.add(cell);
                visited.add(cell);
                boolean touchesEdge = false, unknown = false;
                while (!pending.isEmpty()) {
                    ChunkCell current = pending.removeFirst();
                    component.add(current);
                    if (current.x() == minX || current.x() == maxX || current.z() == minZ || current.z() == maxZ) touchesEdge = true;
                    if (!samples.containsKey(current)) unknown = true;
                    for (ChunkCell neighbor : neighbors(current)) {
                        if (neighbor.x() < minX || neighbor.x() > maxX || neighbor.z() < minZ || neighbor.z() > maxZ
                                || candidate.cells().contains(neighbor) || claimed.contains(neighbor) || !visited.add(neighbor)) continue;
                        pending.addLast(neighbor);
                    }
                }
                if (!touchesEdge && !unknown) holes.addAll(component);
            }
            if (holes.isEmpty()) { result.add(candidate); continue; }
            Set<ChunkCell> filled = new HashSet<>(candidate.cells());
            filled.addAll(holes);
            double temperature = 0;
            for (ChunkCell cell : filled) temperature += samples.get(cell).temperature();
            result.add(new Candidate(candidate.kind(), filled, (float)(temperature / filled.size()), candidate.span()));
        }
        return List.copyOf(result);
    }

    private static boolean enclosed(Set<ChunkCell> cells, Map<ChunkCell, TerrainSample> samples) {
        for (ChunkCell c : cells) for (ChunkCell n : neighbors(c)) if (!cells.contains(n)) {
            TerrainSample s = samples.get(n);
            if (s == null || s.kind().water() || s.kind() == TerrainKind.OTHER) return false;
        }
        return true;
    }

    private static boolean unlocked(Map<ChunkCell, TerrainSample> samples, int threshold) {
        Set<ChunkCell> remaining = new HashSet<>(samples.keySet());
        for (ChunkCell start : samples.keySet()) {
            if (!remaining.remove(start)) continue;
            ArrayDeque<ChunkCell> pending = new ArrayDeque<>();
            pending.add(start);
            int minX = start.x(), maxX = minX, minZ = start.z(), maxZ = minZ;
            while (!pending.isEmpty()) {
                ChunkCell c = pending.removeFirst();
                minX = Math.min(minX, c.x());
                maxX = Math.max(maxX, c.x());
                minZ = Math.min(minZ, c.z());
                maxZ = Math.max(maxZ, c.z());
                for (ChunkCell n : neighbors(c)) if (remaining.remove(n)) pending.add(n);
            }
            if (16L * Math.max((long)maxX - minX + 1, (long)maxZ - minZ + 1) >= threshold) return true;
        }
        return false;
    }

    static List<ChunkCell> neighbors(ChunkCell c) {
        return List.of(new ChunkCell(c.x() - 1, c.z()), new ChunkCell(c.x() + 1, c.z()),
                new ChunkCell(c.x(), c.z() - 1), new ChunkCell(c.x(), c.z() + 1));
    }
}
