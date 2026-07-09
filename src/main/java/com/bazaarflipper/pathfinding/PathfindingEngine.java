package com.bazaarflipper.pathfinding;

import com.bazaarflipper.util.Logger;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced A* pathfinding with humanization:
 * - Octile heuristic for 8-directional movement
 * - Dangerous block avoidance (lava, fire, cactus, water, void)
 * - Height, diagonal, crowd, edge costs
 * - Partial path fallback (closest reachable if target blocked)
 * - Path caching LRU with expiry
 * - Line-of-sight string-pulling smoothing + Catmull-Rom spline for bezier-like curves
 * - Dynamic obstacle detection & replan trigger
 * - Randomized offset within block for human-like not-perfect-center walking
 * Credits: Cldz
 */
public class PathfindingEngine {

    private static final int SEARCH_RADIUS = 96;
    private static final int MAX_ITERATIONS = 8000;
    private static final int CACHE_MAX = 128;
    private static final long CACHE_EXPIRY_MS = 30_000L;

    public static class Path {
        public List<BlockPos> nodes; // raw A* nodes
        public List<Vec3> smoothPoints; // smoothed bezier-like points for human walking
        public BlockPos target;
        public boolean success;
        public boolean partial;
        public double totalCost;
        public long computedAt;

        public Path(List<BlockPos> nodes, List<Vec3> smooth, BlockPos target, boolean success, boolean partial, double cost) {
            this.nodes = nodes;
            this.smoothPoints = smooth;
            this.target = target;
            this.success = success;
            this.partial = partial;
            this.totalCost = cost;
            this.computedAt = System.currentTimeMillis();
        }
    }

    private static class Node implements Comparable<Node> {
        BlockPos pos;
        double g;
        double h;
        double f;
        Node parent;
        int depth;

        Node(BlockPos pos, double g, double h, Node parent, int depth) {
            this.pos = pos;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.parent = parent;
            this.depth = depth;
        }

        @Override public int compareTo(Node o) { return Double.compare(this.f, o.f); }
        @Override public boolean equals(Object o) { return o instanceof Node n && pos.equals(n.pos); }
        @Override public int hashCode() { return pos.hashCode(); }
    }

    private static class CachedPath {
        Path path;
        long timestamp;
        CachedPath(Path p) { this.path = p; this.timestamp = System.currentTimeMillis(); }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS; }
    }

    private final Map<String, CachedPath> pathCache = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // ===== Main API =====

    public Path calculatePath(BlockPos start, BlockPos end) {
        String cacheKey = start.toShortString() + "->" + end.toShortString();
        CachedPath cached = pathCache.get(cacheKey);
        if (cached != null && !cached.isExpired() && isCacheStillValid(cached.path)) {
            Logger.debug("Path cache hit for " + cacheKey);
            return cached.path;
        }

        Path result = aStar(start, end);

        // Cache even partial paths for short time
        if (pathCache.size() >= CACHE_MAX) {
            // Evict oldest
            pathCache.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().timestamp))
                .ifPresent(e -> pathCache.remove(e.getKey()));
        }
        pathCache.put(cacheKey, new CachedPath(result));

        return result;
    }

    private Path aStar(BlockPos start, BlockPos end) {
        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        Map<BlockPos, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start, 0, heuristicOctile(start, end), null, 0);
        open.add(startNode);
        gScore.put(start, 0.0);
        allNodes.put(start, startNode);

        Node bestNode = startNode; // for partial fallback
        double bestHeuristic = startNode.h;

        int iterations = 0;
        while (!open.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = open.poll();

            // Track best node for partial path fallback
            if (current.h < bestHeuristic) {
                bestHeuristic = current.h;
                bestNode = current;
            }

            if (current.pos.equals(end)) {
                List<BlockPos> raw = reconstructPath(current);
                List<Vec3> smooth = smoothPath(raw);
                return new Path(raw, smooth, end, true, false, current.g);
            }

            if (current.pos.getManhattanDistance(end) > SEARCH_RADIUS) continue;

            closed.add(current.pos);

            for (BlockPos neighbor : getNeighbors(current.pos)) {
                if (closed.contains(neighbor)) continue;

                PassableResult pass = isPassableAdvanced(neighbor);
                if (!pass.passable) continue;

                double moveCost = moveCost(current.pos, neighbor, pass);
                if (moveCost >= 1000) continue; // impassable high cost

                double tentativeG = current.g + moveCost;

                Double existingG = gScore.get(neighbor);
                if (existingG != null && tentativeG >= existingG) continue;

                gScore.put(neighbor, tentativeG);
                double h = heuristicOctile(neighbor, end);
                Node neighborNode = new Node(neighbor, tentativeG, h, current, current.depth+1);
                open.add(neighborNode);
                allNodes.put(neighbor, neighborNode);
            }
        }

        // Failed to reach target - return partial path to closest node
        if (bestNode != null && bestNode != startNode) {
            List<BlockPos> raw = reconstructPath(bestNode);
            List<Vec3> smooth = smoothPath(raw);
            Logger.warn("Pathfinding partial from " + start + " to " + end + " after " + iterations + " iterations, closest dist " + bestHeuristic);
            return new Path(raw, smooth, end, false, true, bestNode.g);
        }

        Logger.warn("Pathfinding failed from " + start + " to " + end + " after " + iterations + " iterations");
        return new Path(List.of(), List.of(), end, false, false, Double.MAX_VALUE);
    }

    private boolean isCacheStillValid(Path path) {
        if (path.nodes.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        // Quick check: first few nodes still passable and no block change
        for (int i=0;i<Math.min(3, path.nodes.size());i++) {
            if (!isPassableAdvanced(path.nodes.get(i)).passable) return false;
        }
        return true;
    }

    // ===== Heuristics =====

    private double heuristicOctile(BlockPos a, BlockPos b) {
        // Octile distance for 8-directional movement with diagonals cost sqrt2
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        int dmin = Math.min(dx, dz);
        int dmax = Math.max(dx, dz);
        double D = 1.0;
        double D2 = 1.41421356237; // sqrt2
        return D * (dmax - dmin) + D2 * dmin + dy * 1.2; // vertical penalty slightly higher
    }

    // ===== Movement Cost =====

    private static class PassableResult {
        boolean passable;
        double dangerCost;
        boolean needsJump;
        boolean isWater;
        boolean isLava;
        Block blockBelow;
        PassableResult(boolean passable, double danger, boolean jump, boolean water, boolean lava, Block below) {
            this.passable = passable;
            this.dangerCost = danger;
            this.needsJump = jump;
            this.isWater = water;
            this.isLava = lava;
            this.blockBelow = below;
        }
    }

    private double moveCost(BlockPos from, BlockPos to, PassableResult pass) {
        double cost = 1.0;

        // Diagonal cost
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        if (dx == 1 && dz == 1) cost = 1.414; // diagonal

        // Vertical cost
        if (to.getY() > from.getY()) {
            cost += 1.2 + (pass.needsJump ? 0.5 : 0); // jumping extra
        } else if (to.getY() < from.getY()) {
            cost += 0.8; // dropping slightly cheaper but still cost for fall risk
            int dy = from.getY() - to.getY();
            if (dy > 3) cost += dy * 0.5; // big drop penalty
        }

        // Danger cost
        cost += pass.dangerCost;

        // Crowd avoidance: nearby players/entities increase cost
        cost += crowdCost(to) * 0.5;

        // Edge void avoidance: if below is air for >3 blocks, heavy penalty (avoid walking near cliffs/void)
        cost += edgeVoidCost(to);

        // Soul sand, slime etc slow
        if (pass.blockBelow != null) {
            if (pass.blockBelow == Blocks.SOUL_SAND) cost += 0.5;
            if (pass.blockBelow == Blocks.SLIME_BLOCK) cost += 0.3;
        }

        // Water penalty (avoid unless necessary)
        if (pass.isWater) cost += 2.0;

        // Lava impassable already filtered but extra cost if near
        if (pass.isLava) cost += 1000;

        // Add tiny random noise to avoid perfectly deterministic same path every time (humanization at planning level)
        cost += random.nextDouble() * 0.05;

        return cost;
    }

    private double crowdCost(BlockPos pos) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return 0;
            Vec3 center = Vec3.atCenterOf(pos);
            double cost = 0;
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity == mc.player) continue;
                if (entity.isPlayer() || entity.isLiving()) {
                    double dist = entity.position().distanceTo(center);
                    if (dist < 2.0) cost += (2.0 - dist) * 2.0; // high cost if very close
                    else if (dist < 4.0) cost += (4.0 - dist) * 0.3;
                }
            }
            return cost;
        } catch (Exception e) { return 0; }
    }

    private double edgeVoidCost(BlockPos pos) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return 0;
            // Check below: if air for 4+ blocks down, it's a cliff/void edge -> penalize heavily unless target is down there
            for (int i=1;i<=4;i++) {
                BlockPos below = pos.below(i);
                var state = mc.level.getBlockState(below);
                if (!state.isAir() && state.isSolidRender(mc.level, below)) {
                    return 0; // has ground within 4 blocks - safe
                }
            }
            // No ground within 4 - dangerous edge (common in hub near void, or private island)
            return 5.0;
        } catch (Exception e) { return 0; }
    }

    // ===== Passability with danger detection =====

    private PassableResult isPassableAdvanced(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return new PassableResult(false, 1000, false, false, false, null);
        try {
            var state = mc.level.getBlockState(pos);
            var stateUp = mc.level.getBlockState(pos.above());
            var stateDown = mc.level.getBlockState(pos.below());
            var stateBelow2 = mc.level.getBlockState(pos.below(2));

            Block block = state.getBlock();
            Block blockUp = stateUp.getBlock();
            Block blockDown = stateDown.getBlock();

            // Check current and head are not solid
            boolean solidCurrent = state.isSolidRender(mc.level, pos);
            boolean solidUp = stateUp.isSolidRender(mc.level, pos.above());

            if (solidCurrent || solidUp) {
                // Except: allow if current is water/ladder/vine etc? For simplicity, treat solid as impassable
                // Check if it's climbable: ladder, vine, scaffolding
                if (!(block == Blocks.LADDER || block == Blocks.VINE || block == Blocks.SCAFFOLDING || block == Blocks.WATER)) {
                    return new PassableResult(false, 1000, false, false, false, blockDown);
                }
            }

            // Check below: need solid ground or water/ladder etc within 1-2 blocks for valid standing
            boolean hasGround = stateDown.isSolidRender(mc.level, pos.below()) || stateDown.getBlock() == Blocks.WATER || stateDown.getBlock() == Blocks.LADDER || stateBelow2.isSolidRender(mc.level, pos.below(2));

            // For hub paths, we want ground; but allow air below for up to 1 block drop (handled in cost)
            // If no ground at all and not water, still allow but with high cost handled elsewhere (void check) — passable for now

            // Danger checks
            double danger = 0;
            boolean isWater = false;
            boolean isLava = false;
            boolean needsJump = false;

            // Lava
            if (block == Blocks.LAVA || blockUp == Blocks.LAVA || blockDown == Blocks.LAVA) {
                return new PassableResult(false, 1000, false, false, true, blockDown);
            }
            // Fire
            if (block == Blocks.FIRE || blockUp == Blocks.FIRE) {
                danger += 10;
            }
            // Cactus
            if (block == Blocks.CACTUS || blockUp == Blocks.CACTUS) {
                danger += 8;
            }
            // Sweet berry bush
            if (block == Blocks.SWEET_BERRY_BUSH) danger += 1;
            // Water - passable but penalized
            if (block == Blocks.WATER) {
                isWater = true;
                danger += 1.5;
            }
            // Magma block damage
            if (blockDown == Blocks.MAGMA_BLOCK) danger += 3;

            // Check headroom for jump: if target is 1 up, need 2 air above current head? Simplified: if pos Y > from Y (handled in cost) ensure 3 air tall
            // We'll check up two blocks above pos for jump clearance
            var stateUp2 = mc.level.getBlockState(pos.above(2));
            if (stateUp2.isSolidRender(mc.level, pos.above(2))) {
                // Not enough headroom to jump up here
                // Mark as needing jump but not passable if head blocked
                // For now allow but add cost
                danger += 2;
                needsJump = true;
            }

            // Check if we need to jump (pos is 1 higher than typical ground) - we detect via caller from/to but also here if below is fence/wall etc
            if (blockDown == Blocks.FENCE || blockDown == Blocks.FENCE_GATE || blockDown == Blocks.WALL) {
                needsJump = true;
                danger += 1;
            }

            return new PassableResult(true, danger, needsJump, isWater, isLava, blockDown);

        } catch (Exception e) {
            return new PassableResult(false, 1000, false, false, false, null);
        }
    }

    // Keep old simple isPassable for compatibility
    public boolean isPassable(BlockPos pos) {
        return isPassableAdvanced(pos).passable;
    }

    // ===== Neighbors with 26-directional but filtered =====

    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>(26);
        for (int dx=-1; dx<=1; dx++) {
            for (int dy=-1; dy<=1; dy++) {
                for (int dz=-1; dz<=1; dz++) {
                    if (dx==0 && dy==0 && dz==0) continue;
                    // Limit vertical to -1..1 for realistic step (no teleport up 2)
                    if (Math.abs(dy) > 1) continue;
                    // Avoid pure vertical up without horizontal unless needed (jump in place) — allow but low priority
                    // For efficiency, skip direct up/down unless part of step up logic
                    BlockPos n = pos.add(dx, dy, dz);
                    neighbors.add(n);
                }
            }
        }
        // Shuffle neighbors slightly to add variation in pathfinding (human-like not always same expansion order)
        if (random.nextDouble() < 0.3) {
            Collections.shuffle(neighbors, random);
        }
        return neighbors;
    }

    // ===== Path Reconstruction & Smoothing =====

    private List<BlockPos> reconstructPath(Node node) {
        List<BlockPos> path = new ArrayList<>();
        Node cur = node;
        while (cur != null) {
            path.add(cur.pos);
            cur = cur.parent;
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Advanced smoothing:
     * 1. Line-of-sight string pulling (remove unnecessary waypoints if direct line is clear)
     * 2. Catmull-Rom spline to generate smooth bezier-like Vec3 points with random offset within block (human not walking perfect center)
     * 3. Add small vertical bobbing
     */
    public List<Vec3> smoothPath(List<BlockPos> raw) {
        if (raw == null || raw.size() < 2) return raw != null ? raw.stream().map(Vec3::ofCenter).toList() : List.of();

        // Step 1: String pulling - remove colinear and line-of-sight redundant points
        List<BlockPos> pruned = stringPull(raw);

        // Step 2: Catmull-Rom spline with random offset for human-like path not perfect center
        List<Vec3> smooth = new ArrayList<>();
        // Add some random offset within block (human doesn't walk exactly center, ±0.3 blocks)
        List<Vec3> jittered = new ArrayList<>();
        for (BlockPos bp : pruned) {
            double offsetX = (random.nextDouble() - 0.5) * 0.6; // ±0.3
            double offsetZ = (random.nextDouble() - 0.5) * 0.6;
            double offsetY = 0; // keep Y at ground + a little
            jittered.add(new Vec3(bp.getX() + 0.5 + offsetX, bp.getY() + offsetY, bp.getZ() + 0.5 + offsetZ));
        }

        // Catmull-Rom with alpha 0.5 (centripetal) to avoid cusps
        // Generate intermediate points between each segment
        int segmentsPerEdge = 4; // higher = smoother

        for (int i=0; i<jittered.size()-1; i++) {
            Vec3 p0 = i>0 ? jittered.get(i-1) : jittered.get(i);
            Vec3 p1 = jittered.get(i);
            Vec3 p2 = jittered.get(i+1);
            Vec3 p3 = (i+2 < jittered.size()) ? jittered.get(i+2) : p2;

            smooth.add(p1); // add original

            for (int j=1; j<segmentsPerEdge; j++) {
                double t = j / (double)segmentsPerEdge;
                Vec3 point = catmullRom(p0, p1, p2, p3, t);
                // Add subtle vertical bobbing simulation for walking (sine wave based on progress)
                double bob = Math.sin((i*segmentsPerEdge + j) * 0.6) * 0.03; // 3cm bob
                point = new Vec3(point.x, point.y + bob, point.z);
                smooth.add(point);
            }
        }
        if (!jittered.isEmpty()) smooth.add(jittered.get(jittered.size()-1));

        return smooth;
    }

    private List<BlockPos> stringPull(List<BlockPos> path) {
        if (path.size() <= 2) return path;
        List<BlockPos> result = new ArrayList<>();
        result.add(path.get(0));
        int currentIndex = 0;
        while (currentIndex < path.size() -1) {
            int nextIndex = path.size()-1;
            // Try to find farthest visible point from current
            for (int i=path.size()-1; i>currentIndex; i--) {
                if (hasLineOfSight(path.get(currentIndex), path.get(i))) {
                    nextIndex = i;
                    break;
                }
            }
            result.add(path.get(nextIndex));
            currentIndex = nextIndex;
        }
        return result;
    }

    private boolean hasLineOfSight(BlockPos from, BlockPos to) {
        // Raycast check: step along line and ensure no solid blocks intersect (except ground)
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        int steps = Math.max(Math.abs(from.getX()-to.getX()), Math.max(Math.abs(from.getY()-to.getY()), Math.abs(from.getZ()-to.getZ()))) * 2;
        steps = Math.max(steps, 1);
        for (int i=0;i<=steps;i++) {
            double t = i / (double)steps;
            double x = from.getX() + (to.getX()-from.getX())*t;
            double y = from.getY() + (to.getY()-from.getY())*t;
            double z = from.getZ() + (to.getZ()-from.getZ())*t;
            BlockPos check = new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
            // Skip check for from/to themselves
            if (check.equals(from) || check.equals(to)) continue;
            // If solid block at check or check up (head), no LOS
            try {
                var state = mc.level.getBlockState(check);
                var stateUp = mc.level.getBlockState(check.above());
                if (state.isSolidRender(mc.level, check) || stateUp.isSolidRender(mc.level, check.above())) {
                    // Allow if it's ground below (we only care about obstacles at body/head level)
                    // For LOS we consider only blocks at our height
                    return false;
                }
            } catch (Exception e) { return false; }
        }
        return true;
    }

    private Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t*t;
        double t3 = t2*t;
        // Catmull-Rom basis
        double x = 0.5 * ((2*p1.x) + (-p0.x + p2.x)*t + (2*p0.x -5*p1.x +4*p2.x -p3.x)*t2 + (-p0.x +3*p1.x -3*p2.x + p3.x)*t3);
        double y = 0.5 * ((2*p1.y) + (-p0.y + p2.y)*t + (2*p0.y -5*p1.y +4*p2.y -p3.y)*t2 + (-p0.y +3*p1.y -3*p2.y + p3.y)*t3);
        double z = 0.5 * ((2*p1.z) + (-p0.z + p2.z)*t + (2*p0.z -5*p1.z +4*p2.z -p3.z)*t2 + (-p0.z +3*p1.z -3*p2.z + p3.z)*t3);
        return new Vec3(x,y,z);
    }

    // ===== Utility APIs =====

    public Path recalculatePath(BlockPos currentPos, BlockPos target) {
        // Invalidate cache for this target to force fresh calculation (dynamic obstacles)
        pathCache.remove(currentPos.toShortString()+"->"+target.toShortString());
        return calculatePath(currentPos, target);
    }

    public boolean isStuck(Vec3 lastPos, long msElapsed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Vec3 curr = mc.player.position();
        double dist = curr.distanceTo(lastPos);
        // More advanced stuck detection: also check if velocity near zero for long time
        double vel = mc.player.getDeltaMovement().length();
        return (msElapsed > 4000 && dist < 0.6 && vel < 0.05) || (msElapsed > 8000 && dist < 1.2);
    }

    public void clearCache() { pathCache.clear(); }

    // For HumanizedNavigator to get smooth points
    public List<Vec3> getSmoothPoints(Path path) {
        return path.smoothPoints != null ? path.smoothPoints : path.nodes.stream().map(Vec3::ofCenter).toList();
    }
}
