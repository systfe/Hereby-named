package dev.herebynamed.domain;

import java.util.*;

/** Cached, world-space drawing geometry for the visible ownership of named regions. */
public record RegionMesh(List<Fill> fills,List<Edge> edges,List<Label> labels) {
    public RegionMesh { fills=List.copyOf(fills); edges=List.copyOf(edges); labels=List.copyOf(labels); }
    public record Fill(int x1,int z1,int x2,int z2,int color) {}
    public record Edge(int x1,int z1,int x2,int z2,int color) {}
    public record Label(String name,double x,double z,double span) {
        public float textScale(int textWidth,double pixelsPerChunk,int viewportWidth) {
            return (float)Math.max(0,Math.min(3,Math.min(span*pixelsPerChunk,viewportWidth*0.8)/Math.max(1,textWidth)));
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
        long sumX,sumZ;
        int count,minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE;
        double anchorX,anchorZ,distance=Double.POSITIVE_INFINITY;
        Area(String name) { this.name=name; }
        void include(int x,int z) { sumX+=x; sumZ+=z; count++; minX=Math.min(minX,x); maxX=Math.max(maxX,x); }
        void anchor(int x,int z) {
            double dx=x-(double)sumX/count, dz=z-(double)sumZ/count, next=dx*dx+dz*dz;
            if(next<distance || (next==distance && (x<anchorX-0.5 || (x==anchorX-0.5 && z<anchorZ-0.5)))) {
                distance=next; anchorX=x+0.5; anchorZ=z+0.5;
            }
        }
        Label label() { return new Label(name,anchorX,anchorZ,Math.min(Math.sqrt(count)*0.9,(maxX-minX+1)*0.85)); }
    }
}
