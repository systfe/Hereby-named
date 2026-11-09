package dev.herebynamed.domain;

import java.util.*;

/** Immutable bounding-volume index; large crossing shapes occur only once in the tree. */
final class GeometryIndex<T extends RegionMesh.Shape> {
    private final List<T> shapes;
    private final Node root;
    private final class Node {
        final int start,end,minX,minZ,maxX,maxZ;
        final Node left,right;
        Node(int start,int end) {
            this.start=start; this.end=end;
            int x1=Integer.MAX_VALUE,z1=Integer.MAX_VALUE,x2=Integer.MIN_VALUE,z2=Integer.MIN_VALUE;
            for(int i=start;i<end;i++) {
                T shape=shapes.get(i);
                x1=Math.min(x1,shape.x1()); z1=Math.min(z1,shape.z1());
                x2=Math.max(x2,shape.x2()); z2=Math.max(z2,shape.z2());
            }
            minX=x1; minZ=z1; maxX=x2; maxZ=z2;
            if(end-start<=32) { left=right=null; return; }
            boolean splitX=maxX-minX>=maxZ-minZ;
            shapes.subList(start,end).sort(Comparator.comparingLong(s -> splitX?(long)s.x1()+s.x2():(long)s.z1()+s.z2()));
            int middle=(start+end)>>>1;
            left=new Node(start,middle); right=new Node(middle,end);
        }
        void query(double x1,double z1,double x2,double z2,List<T> found) {
            if(maxX<x1 || maxZ<z1 || minX>x2 || minZ>z2) return;
            if(x1<=minX && z1<=minZ && x2>=maxX && z2>=maxZ) {
                found.addAll(shapes.subList(start,end)); return;
            }
            if(left!=null) { left.query(x1,z1,x2,z2,found); right.query(x1,z1,x2,z2,found); return; }
            for(int i=start;i<end;i++) {
                T shape=shapes.get(i);
                if(shape.x2()>=x1 && shape.z2()>=z1 && shape.x1()<=x2 && shape.z1()<=z2) found.add(shape);
            }
        }
    }
    GeometryIndex(List<T> shapes) { this.shapes=new ArrayList<>(shapes); root=shapes.isEmpty()?null:new Node(0,shapes.size()); }
    List<T> query(double x1,double z1,double x2,double z2) {
        List<T> found=new ArrayList<>(); if(root!=null) root.query(x1,z1,x2,z2,found); return found;
    }
}
