package com.bazaarflipper.pathfinding;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans environment for interesting points to glance at, obstacles, hazards, other players
 * Makes navigation look more human by having gaze wander to points of interest
 * Credits: Cldz
 */
public class EnvironmentalScanner {

    public static class InterestingPoint {
        public Vec3d pos;
        public String type; // npc, chest, player, decoration, hazard
        public double interestScore;
        public InterestingPoint(Vec3d pos, String type, double score) {
            this.pos = pos; this.type = type; this.interestScore = score;
        }
    }

    public List<InterestingPoint> scanNearbyPOI(double radius) {
        List<InterestingPoint> points = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return points;
        Vec3d playerPos = mc.player.getPos();

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            double dist = e.getPos().distanceTo(playerPos);
            if (dist > radius) continue;
            String name = e.getName().getString().toLowerCase();
            double score = 0;
            String type = "entity";
            if (name.contains("bazaar") || name.contains("auction") || name.contains("bank")) {
                score = 20 - dist;
                type = "npc";
            } else if (e.isPlayer()) {
                score = 10 - dist*0.5;
                type = "player";
            } else if (e.getType().toString().contains("armor_stand")) {
                score = 3;
                type = "decoration";
            } else if (e.isLiving()) {
                score = 2;
                type = "mob";
            }
            if (score > 0) points.add(new InterestingPoint(e.getPos().add(0, e.getHeight()/2, 0), type, score));
        }

        // Scan for chests, interesting blocks (could be extended to scan block entities)
        // For brevity, we only scan entities here; block scanning would need chunk iteration

        points.sort((a,b) -> Double.compare(b.interestScore, a.interestScore));
        return points;
    }

    public boolean isHazardNearby(BlockPos pos, double radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        // Check for lava, fire, cactus within radius
        for (int dx=-(int)radius; dx<=radius; dx++) {
            for (int dy=-(int)radius; dy<=radius; dy++) {
                for (int dz=-(int)radius; dz<=radius; dz++) {
                    BlockPos check = pos.add(dx,dy,dz);
                    try {
                        var state = mc.world.getBlockState(check);
                        String blockId = state.getBlock().toString().toLowerCase();
                        if (blockId.contains("lava") || blockId.contains("fire") || blockId.contains("cactus") || blockId.contains("magma")) {
                            return true;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return false;
    }

    public boolean isVoidBelow(BlockPos pos, int depth) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        for (int i=1;i<=depth;i++) {
            BlockPos below = pos.down(i);
            try {
                var state = mc.world.getBlockState(below);
                if (!state.isAir() && state.isSolidBlock(mc.world, below)) return false;
            } catch (Exception e) { return true; }
        }
        return true; // no ground found within depth -> void
    }
}
