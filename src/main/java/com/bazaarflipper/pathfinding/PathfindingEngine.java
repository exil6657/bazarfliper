package com.bazaarflipper.pathfinding;

import com.bazaarflipper.util.Logger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PathfindingEngine {
    // Simple A* on local block grid

    private static final int SEARCH_RADIUS = 64;
    private static final int MAX_ITERATIONS = 5000;

    public static class Path {
        public List<BlockPos> nodes;
        public BlockPos target;
        public boolean success;

        public Path(List<BlockPos> nodes, BlockPos target, boolean success) {
            this.nodes = nodes;
            this.target = target;
            this.success = success;
        }
    }

    private static class Node implements Comparable<Node> {
        BlockPos pos;
        double g; // cost from start
        double h; // heuristic to end
        Node parent;

        Node(BlockPos pos, double g, double h, Node parent) {
            this.pos = pos;
            this.g = g;
            this.h = h;
            this.parent = parent;
        }

        double f() { return g + h; }

        @Override public int compareTo(Node o) { return Double.compare(this.f(), o.f()); }

        @Override public boolean equals(Object o) {
            if (o instanceof Node n) return pos.equals(n.pos);
            return false;
        }

        @Override public int hashCode() { return pos.hashCode(); }
    }

    public Path calculatePath(BlockPos start, BlockPos end) {
        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(start, 0, heuristic(start, end), null);
        open.add(startNode);
        gScore.put(start, 0.0);

        int iterations = 0;
        while (!open.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            Node current = open.poll();
            if (current.pos.equals(end)) {
                return new Path(reconstructPath(current), end, true);
            }
            closed.add(current.pos);

            for (BlockPos neighbor : getNeighbors(current.pos)) {
                if (closed.contains(neighbor)) continue;
                if (!isPassable(neighbor)) continue;
                double tentativeG = current.g + cost(current.pos, neighbor);
                Double existingG = gScore.get(neighbor);
                if (existingG == null || tentativeG < existingG) {
                    gScore.put(neighbor, tentativeG);
                    double h = heuristic(neighbor, end);
                    Node neighborNode = new Node(neighbor, tentativeG, h, current);
                    open.add(neighborNode);
                }
            }
        }
        Logger.warn("Pathfinding failed from " + start + " to " + end + " after " + iterations + " iterations");
        return new Path(List.of(), end, false);
    }

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

    private double heuristic(BlockPos a, BlockPos b) {
        return Math.sqrt(a.getSquaredDistance(b));
    }

    private double cost(BlockPos a, BlockPos b) {
        // Basic cost: 1 for horizontal, 1.5 for diagonal, +1 if up
        double base = 1.0;
        if (b.getY() > a.getY()) base += 1.0; // jumping cost
        return base;
    }

    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        // 4 cardinal + diagonals + up/down steps
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                neighbors.add(pos.add(dx, 0, dz));
                neighbors.add(pos.add(dx, 1, dz)); // step up
                neighbors.add(pos.add(dx, -1, dz)); // step down
            }
        }
        neighbors.add(pos.up());
        neighbors.add(pos.down());
        return neighbors;
    }

    public boolean isPassable(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        // Check if block is solid at pos and pos up (need 2 air for player)
        try {
            var state = mc.world.getBlockState(pos);
            var stateUp = mc.world.getBlockState(pos.up());
            var stateDown = mc.world.getBlockState(pos.down());
            boolean solidDown = stateDown.isSolidBlock(mc.world, pos.down()); // need ground?
            // For simplicity: passable if not solid and above not solid
            boolean airCurrent = !state.isSolidBlock(mc.world, pos);
            boolean airUp = !stateUp.isSolidBlock(mc.world, pos.up());
            return airCurrent && airUp;
        } catch (Exception e) {
            return false;
        }
    }

    public Path recalculatePath(BlockPos currentPos, BlockPos target) {
        return calculatePath(currentPos, target);
    }

    public boolean isStuck(Vec3d lastPos, long msElapsed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        Vec3d curr = mc.player.getPos();
        double dist = curr.distanceTo(lastPos);
        return msElapsed > 5000 && dist < 0.5;
    }
}
