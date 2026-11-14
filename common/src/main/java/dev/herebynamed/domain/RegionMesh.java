package dev.herebynamed.domain;

import java.util.*;

/** Cached, world-space drawing geometry for the visible ownership of named regions. */
public final class RegionMesh {
    private final List<Fill> fills;
    private final List<Edge> edges;
    private final List<Label> labels;
    private final GeometryIndex<Fill> fillIndex;
    private final GeometryIndex<Edge> edgeIndex;
    public RegionMesh(List<Fill> fills,List<Edge> edges,List<Label> labels) {
        this.fills=List.copyOf(fills); this.edges=List.copyOf(edges); this.labels=List.copyOf(labels);
        fillIndex=new GeometryIndex<>(this.fills); edgeIndex=new GeometryIndex<>(this.edges);
    }
    public List<Fill> fills() { return fills; }
    public List<Edge> edges() { return edges; }
    public List<Label> labels() { return labels; }
    public record View(List<Fill> fills,List<Edge> edges) {}
    public View inView(double minX,double minZ,double maxX,double maxZ) {
        return new View(fillIndex.query(minX,minZ,maxX,maxZ),edgeIndex.query(minX,minZ,maxX,maxZ));
    }
    interface Shape { int x1(); int z1(); int x2(); int z2(); }
    public record Fill(int x1,int z1,int x2,int z2,int color) implements Shape {}
    public record Edge(int x1,int z1,int x2,int z2,int color) implements Shape {}
    public record GlyphPlacement(char ch, double x, double z, float angleRad) {}
    public record Label(String name, double x, double z, double span, float angleRad, List<GlyphPlacement> glyphs) {
        public Label(String name, double x, double z, double span) {
            this(name, x, z, span, 0f, List.of());
        }
        public float textScale(int textWidth, double pixelsPerChunk, int viewportWidth) {
            return (float)Math.max(0, Math.min(3, Math.min(span * pixelsPerChunk, viewportWidth * 0.8) / Math.max(1, textWidth)));
        }
    }
    public static RegionMesh build(List<RegionBook.Region> regions,Integer layer) {
        Map<ChunkCell,RegionBook.Region> owners=new HashMap<>();
        regions.stream().filter(r -> layer==null || r.layer()==layer).sorted(Comparator.comparingInt(RegionBook.Region::layer))
                .forEach(r -> { for(ChunkCell cell:r.cells()) owners.put(cell,r); });
        TreeMap<Integer,List<Integer>> rows=new TreeMap<>();
        Map<String,Area> areas=new TreeMap<>();
        Map<EdgeKey,List<Integer>> boundary=new HashMap<>();
        for(var entry:owners.entrySet()) {
            int x=entry.getKey().x(), z=entry.getKey().z();
            RegionBook.Region r=entry.getValue();
            rows.computeIfAbsent(z,k -> new ArrayList<>()).add(x);
            areas.computeIfAbsent(r.id(),k -> new Area(r.name())).include(x,z);
            if(!same(owners,x,z-1,r)) edge(boundary,r,true,z,x);
            if(!same(owners,x,z+1,r)) edge(boundary,r,true,z+1,x);
            if(!same(owners,x-1,z,r)) edge(boundary,r,false,x,z);
            if(!same(owners,x+1,z,r)) edge(boundary,r,false,x+1,z);
        }
        List<Fill> fills=new ArrayList<>();
        Map<Run,Integer> previous=Map.of();
        int previousZ=Integer.MIN_VALUE;
        for(var row:rows.entrySet()) {
            int z=row.getKey(); row.getValue().sort(Integer::compareTo);
            Map<Run,Integer> current=new HashMap<>();
            if(z!=previousZ+1) previous=Map.of();
            List<Integer> xs=row.getValue();
            for(int i=0;i<xs.size();) {
                int x1=xs.get(i), x2=x1+1;
                RegionBook.Region r=owners.get(new ChunkCell(x1,z));
                while(++i<xs.size() && xs.get(i)==x2 && same(owners,x2,z,r)) x2++;
                Run run=new Run(x1,x2,r.id());
                Integer index=previous.get(run);
                if(index==null) { index=fills.size(); fills.add(new Fill(x1,z,x2,z+1,r.color())); }
                else { Fill before=fills.get(index); fills.set(index,new Fill(x1,before.z1(),x2,z+1,r.color())); }
                current.put(run,index);
            }
            previous=current; previousZ=z;
        }
        List<Edge> edges=new ArrayList<>();
        boundary.forEach((line,positions) -> {
            positions.sort(Integer::compareTo);
            for(int i=0;i<positions.size();) {
                int from=positions.get(i), to=from+1;
                while(++i<positions.size() && positions.get(i)==to) to++;
                edges.add(line.horizontal?new Edge(from,line.fixed,to,line.fixed,line.color)
                        :new Edge(line.fixed,from,line.fixed,to,line.color));
            }
        });
        // Pick an owned cell near the area centroid, including concave regions and holes.
        for(var entry:owners.entrySet()) areas.get(entry.getValue().id()).anchor(entry.getKey().x(),entry.getKey().z());
        List<Label> labels=areas.values().stream().map(Area::label).toList();
        return new RegionMesh(fills,edges,labels);
    }
    private static boolean same(Map<ChunkCell,RegionBook.Region> owners,int x,int z,RegionBook.Region region) {
        if(Math.abs((long)x)>1_875_000 || Math.abs((long)z)>1_875_000) return false;
        RegionBook.Region neighbor=owners.get(new ChunkCell(x,z));
        return neighbor!=null && neighbor.id().equals(region.id());
    }
    private static void edge(Map<EdgeKey,List<Integer>> edges,RegionBook.Region r,boolean horizontal,int fixed,int start) {
        edges.computeIfAbsent(new EdgeKey(r.id(),horizontal,fixed,r.color()),k -> new ArrayList<>()).add(start);
    }
    private record Run(int x1,int x2,String region) {}
    private record EdgeKey(String region,boolean horizontal,int fixed,int color) {}
    private static final class Area {
        final String name;
        long sumX, sumZ;
        int count, minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        double anchorX, anchorZ, distance = Double.POSITIVE_INFINITY;
        final List<ChunkCell> cells = new ArrayList<>();
        Area(String name) { this.name = name; }
        void include(int x, int z) {
            sumX += x; sumZ += z; count++;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            cells.add(new ChunkCell(x, z));
        }
        void anchor(int x, int z) {
            double dx = x - (double)sumX / count, dz = z - (double)sumZ / count, next = dx * dx + dz * dz;
            if (next < distance || (next == distance && (x < anchorX - 0.5 || (x == anchorX - 0.5 && z < anchorZ - 0.5)))) {
                distance = next; anchorX = x + 0.5; anchorZ = z + 0.5;
            }
        }
        Label label() {
            double defaultSpan = Math.min(Math.sqrt(count) * 0.9, (maxX - minX + 1) * 0.85);
            if (name.isEmpty() || count <= 1) {
                return new Label(name, anchorX, anchorZ, defaultSpan, 0f,
                        name.isEmpty() ? List.of() : List.of(new GlyphPlacement(name.charAt(0), anchorX, anchorZ, 0f)));
            }

            double meanX = (double) sumX / count + 0.5;
            double meanZ = (double) sumZ / count + 0.5;
            double varX = 0, varZ = 0, covXZ = 0;
            for (ChunkCell c : cells) {
                double dx = (c.x() + 0.5) - meanX;
                double dz = (c.z() + 0.5) - meanZ;
                varX += dx * dx;
                varZ += dz * dz;
                covXZ += dx * dz;
            }
            varX /= count; varZ /= count; covXZ /= count;

            double theta = 0.5 * Math.atan2(2.0 * covXZ, varX - varZ);
            while (theta > Math.PI / 2.0) theta -= Math.PI;
            while (theta < -Math.PI / 2.0) theta += Math.PI;
            if (Math.abs(theta) < 0.04) theta = 0.0;

            double cos = Math.cos(theta), sin = Math.sin(theta);
            double minU = Double.POSITIVE_INFINITY, maxU = Double.NEGATIVE_INFINITY;
            for (ChunkCell c : cells) {
                double dx = (c.x() + 0.5) - meanX;
                double dz = (c.z() + 0.5) - meanZ;
                double u = dx * cos + dz * sin;
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
            }
            double longSpan = Math.max(1.0, maxU - minU);
            double span = Math.max(defaultSpan, longSpan * 0.75);

            int n = name.length();
            if (n == 1) {
                return new Label(name, anchorX, anchorZ, span, (float)theta,
                        List.of(new GlyphPlacement(name.charAt(0), anchorX, anchorZ, (float)theta)));
            }

            double maxCharSpread = Character.UnicodeScript.of(name.charAt(0)) == Character.UnicodeScript.HAN ? 2.0 : 3.2;
            double targetLength = Math.max(1.0, Math.min(longSpan * 0.70, (n - 1) * maxCharSpread));
            double stepU = targetLength / (n - 1);

            double[] gx = new double[n];
            double[] gz = new double[n];
            for (int i = 0; i < n; i++) {
                double u = (i - (n - 1) / 2.0) * stepU;
                double sumV = 0;
                int countV = 0;
                for (ChunkCell c : cells) {
                    double dx = (c.x() + 0.5) - meanX;
                    double dz = (c.z() + 0.5) - meanZ;
                    double cu = dx * cos + dz * sin;
                    if (Math.abs(cu - u) <= Math.max(1.0, stepU * 0.75)) {
                        double cv = -dx * sin + dz * cos;
                        sumV += cv;
                        countV++;
                    }
                }
                double v = countV > 0 ? (sumV / countV) : 0.0;
                gx[i] = anchorX + (u * cos - v * sin);
                gz[i] = anchorZ + (u * sin + v * cos);
            }

            List<GlyphPlacement> glyphs = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                double glyphAngle;
                if (i == 0) {
                    glyphAngle = Math.atan2(gz[1] - gz[0], gx[1] - gx[0]);
                } else if (i == n - 1) {
                    glyphAngle = Math.atan2(gz[n - 1] - gz[n - 2], gx[n - 1] - gx[n - 2]);
                } else {
                    glyphAngle = Math.atan2(gz[i + 1] - gz[i - 1], gx[i + 1] - gx[i - 1]);
                }
                while (glyphAngle > Math.PI / 2.0) glyphAngle -= Math.PI;
                while (glyphAngle < -Math.PI / 2.0) glyphAngle += Math.PI;
                if (Math.abs(glyphAngle - theta) > 0.45) {
                    glyphAngle = 0.5 * glyphAngle + 0.5 * theta;
                }
                glyphs.add(new GlyphPlacement(name.charAt(i), gx[i], gz[i], (float)glyphAngle));
            }

            return new Label(name, anchorX, anchorZ, span, (float)theta, List.copyOf(glyphs));
        }
    }
}
