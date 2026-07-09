package com.bazaarflipper.pathfinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Advanced path smoothing utilities:
 * - Line-of-sight string pulling
 * - Catmull-Rom spline
 * - Bezier corner rounding
 * - Random jitter for human imperfection
 * - Vertical bobbing
 * Credits: Cldz
 */
public class PathSmoother {

    private final Random random = new Random();

    public List<BlockPos> stringPull(List<BlockPos> path, PathfindingEngine engine) {
        if (path.size() <= 2) return path;
        List<BlockPos> result = new ArrayList<>();
        result.add(path.get(0));
        int current = 0;
        while (current < path.size() -1) {
            int next = path.size()-1;
            for (int i=path.size()-1; i>current; i--) {
                if (hasLineOfSight(path.get(current), path.get(i), engine)) {
                    next = i;
                    break;
                }
            }
            result.add(path.get(next));
            current = next;
        }
        return result;
    }

    private boolean hasLineOfSight(BlockPos from, BlockPos to, PathfindingEngine engine) {
        // Simplified delegate to engine's internal check via passable
        // For now assume true if direct distance <6 and no major height diff
        int dx = Math.abs(from.getX()-to.getX());
        int dz = Math.abs(from.getZ()-to.getZ());
        int dy = Math.abs(from.getY()-to.getY());
        if (dx>6 || dz>6 || dy>2) return false;
        // Use engine's isPassable for intermediate
        for (int i=1;i<5;i++) {
            double t = i/5.0;
            int x = (int)(from.getX() + (to.getX()-from.getX())*t);
            int y = (int)(from.getY() + (to.getY()-from.getY())*t);
            int z = (int)(from.getZ() + (to.getZ()-from.getZ())*t);
            if (!engine.isPassable(new BlockPos(x,y,z))) return false;
            if (!engine.isPassable(new BlockPos(x,y+1,z))) return false;
        }
        return true;
    }

    public List<Vec3d> smoothWithJitter(List<BlockPos> pruned) {
        List<Vec3d> jittered = new ArrayList<>();
        for (BlockPos bp : pruned) {
            double offsetX = (random.nextDouble()-0.5)*0.6;
            double offsetZ = (random.nextDouble()-0.5)*0.6;
            jittered.add(new Vec3d(bp.getX()+0.5+offsetX, bp.getY(), bp.getZ()+0.5+offsetZ));
        }

        List<Vec3d> smooth = new ArrayList<>();
        int segments = 4;
        for (int i=0;i<jittered.size()-1;i++) {
            Vec3d p0 = i>0 ? jittered.get(i-1) : jittered.get(i);
            Vec3d p1 = jittered.get(i);
            Vec3d p2 = jittered.get(i+1);
            Vec3d p3 = (i+2<jittered.size()) ? jittered.get(i+2) : p2;
            smooth.add(p1);
            for (int j=1;j<segments;j++) {
                double t = j/(double)segments;
                Vec3d cat = catmullRom(p0,p1,p2,p3,t);
                double bob = Math.sin((i*segments+j)*0.6)*0.03;
                smooth.add(new Vec3d(cat.x, cat.y+bob, cat.z));
            }
        }
        if (!jittered.isEmpty()) smooth.add(jittered.get(jittered.size()-1));
        return smooth;
    }

    private Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t*t;
        double t3 = t2*t;
        double x = 0.5*((2*p1.x) + (-p0.x + p2.x)*t + (2*p0.x -5*p1.x +4*p2.x -p3.x)*t2 + (-p0.x +3*p1.x -3*p2.x + p3.x)*t3);
        double y = 0.5*((2*p1.y) + (-p0.y + p2.y)*t + (2*p0.y -5*p1.y +4*p2.y -p3.y)*t2 + (-p0.y +3*p1.y -3*p2.y + p3.y)*t3);
        double z = 0.5*((2*p1.z) + (-p0.z + p2.z)*t + (2*p0.z -5*p1.z +4*p2.z -p3.z)*t2 + (-p0.z +3*p1.z -3*p2.z + p3.z)*t3);
        return new Vec3d(x,y,z);
    }
}
