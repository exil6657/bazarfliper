package com.bazaarflipper.pathfinding;

import com.bazaarflipper.automation.DelayManager;
import com.bazaarflipper.util.MathUtils;
import com.bazaarflipper.util.Logger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

public class HumanizedNavigator {

    public enum NavigationState {
        IDLE, CALCULATING, WALKING, STUCK, ARRIVED, FAILED
    }

    private final PathfindingEngine pathfindingEngine;
    private final MovementSimulator movementSimulator;
    private final WaypointRegistry waypointRegistry;
    private final DelayManager delayManager;

    private volatile NavigationState state = NavigationState.IDLE;
    private PathfindingEngine.Path currentPath;
    private int currentNodeIndex = 0;
    private Vec3d lastPos = Vec3d.ZERO;
    private long lastPosTime = 0;
    private long thinkingPauseUntil = 0;
    private float yawNoise = 0;
    private long lastCameraWander = 0;
    private boolean sprinting = true;
    private long stutterUntil = 0;
    private Random random = new Random();

    private BlockPos targetBlockPos;
    private WaypointRegistry.Waypoint targetWaypoint;

    public HumanizedNavigator(PathfindingEngine engine, MovementSimulator movement, WaypointRegistry registry, DelayManager delayManager) {
        this.pathfindingEngine = engine;
        this.movementSimulator = movement;
        this.waypointRegistry = registry;
        this.delayManager = delayManager;
    }

    public void navigateTo(String waypointName) {
        WaypointRegistry.Waypoint wp = waypointRegistry.getWaypoint(waypointName);
        if (wp == null) {
            Logger.warn("Waypoint not found: " + waypointName);
            state = NavigationState.FAILED;
            return;
        }
        targetWaypoint = wp;
        targetBlockPos = new BlockPos((int) wp.x, (int) wp.y, (int) wp.z);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            state = NavigationState.FAILED;
            return;
        }
        BlockPos start = mc.player.getBlockPos();
        state = NavigationState.CALCULATING;
        currentPath = pathfindingEngine.calculatePath(start, targetBlockPos);
        if (!currentPath.success) {
            Logger.warn("Failed to calculate path to " + waypointName);
            state = NavigationState.FAILED;
            return;
        }
        currentNodeIndex = 0;
        lastPos = mc.player.getPos();
        lastPosTime = System.currentTimeMillis();
        state = NavigationState.WALKING;
        Logger.info("Navigating to " + waypointName + " with " + currentPath.nodes.size() + " nodes");
    }

    public void tick() {
        if (state != NavigationState.WALKING) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            state = NavigationState.FAILED;
            return;
        }

        long now = System.currentTimeMillis();

        // Check arrival
        if (targetWaypoint != null) {
            Vec3d playerPos = mc.player.getPos();
            double dist = playerPos.distanceTo(new Vec3d(targetWaypoint.x, targetWaypoint.y, targetWaypoint.z));
            if (dist <= targetWaypoint.arrivalRadius) {
                // Arrival overshoot behavior: 0.5-1.5 blocks overshoot then walk back
                if (random.nextFloat() < 0.3) {
                    // Overshoot slightly
                    movementSimulator.pressForward(true);
                    // Then after short delay, stop and correct
                } else {
                    // Arrived
                    movementSimulator.stopAllMovement();
                    state = NavigationState.ARRIVED;
                    // Post-navigation delay 500-1500ms handled by caller via DelayManager
                    long postNav = delayManager.getDelay(DelayManager.DelayType.POST_NAVIGATION);
                    thinkingPauseUntil = now + postNav;
                    Logger.info("Arrived at waypoint " + targetWaypoint.name);
                    return;
                }
            }
        }

        // Stuck detection
        if (pathfindingEngine.isStuck(lastPos, now - lastPosTime)) {
            Logger.warn("Navigator stuck, attempting recovery");
            state = NavigationState.STUCK;
            onStuck();
            return;
        }

        // Thinking pauses 5-10% chance per segment
        if (now < thinkingPauseUntil) {
            movementSimulator.stopAllMovement();
            return;
        }
        if (random.nextFloat() < 0.07) { // 7% chance
            long pause = MathUtils.randomInt(200, 800);
            thinkingPauseUntil = now + pause;
            movementSimulator.stopAllMovement();
            return;
        }

        // Stutter step 2% chance per second
        if (random.nextFloat() < 0.02) {
            stutterUntil = now + MathUtils.randomInt(50, 100); // 1-2 ticks ~50-100ms
            movementSimulator.stopAllMovement();
        }
        if (now < stutterUntil) return;

        // Camera wander every 5-15 seconds 10-30 degree yaw shift 0.5-1 second
        if (now - lastCameraWander > MathUtils.randomInt(5000, 15000)) {
            float wanderYaw = MathUtils.randomInt(-30, 30);
            movementSimulator.setYaw(mc.player.getYaw() + wanderYaw, 5F);
            lastCameraWander = now;
        }

        // Direction noise ±2-5 degrees per tick low-pass filtered
        yawNoise = MathUtils.lerp(yawNoise, MathUtils.randomInt(-5, 5), 0.1f);
        // Apply noise later

        // Speed variation 70% sprint, 30% walk random transitions
        if (random.nextFloat() < 0.01) { // 1% chance per tick to toggle
            sprinting = random.nextFloat() < 0.7;
            movementSimulator.sprint(sprinting);
        }

        // Follow path
        if (currentPath == null || currentNodeIndex >= currentPath.nodes.size()) {
            state = NavigationState.ARRIVED;
            return;
        }

        BlockPos nextNode = currentPath.nodes.get(currentNodeIndex);
        Vec3d targetVec = Vec3d.ofCenter(nextNode);
        Vec3d playerPos = mc.player.getPos();
        double distanceToNode = playerPos.distanceTo(targetVec);

        // Look at target with turn overshoot 3-8 degrees corrected over 0.3-0.7 seconds
        double dx = targetVec.x - playerPos.x;
        double dz = targetVec.z - playerPos.z;
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        float overshoot = MathUtils.randomInt(3, 8) * (random.nextBoolean() ? 1 : -1);
        float yawWithOvershoot = targetYaw + overshoot + yawNoise;
        movementSimulator.setYaw(yawWithOvershoot, 5F);

        // Move forward
        movementSimulator.pressForward(true);

        // Jump timing variation 0-3 tick random delay
        BlockPos playerBlock = mc.player.getBlockPos();
        if (nextNode.getY() > playerBlock.getY()) {
            if (random.nextInt(4) == 0) { // 0-3 tick variation
                movementSimulator.jump();
            }
        }

        if (distanceToNode < 1.2) {
            currentNodeIndex++;
            lastPos = playerPos;
            lastPosTime = now;
        }

        // Update last pos tracking
        if (playerPos.distanceTo(lastPos) > 1.0) {
            lastPos = playerPos;
            lastPosTime = now;
        }
    }

    public boolean isNavigating() {
        return state == NavigationState.WALKING || state == NavigationState.CALCULATING;
    }

    public boolean hasArrived() {
        return state == NavigationState.ARRIVED;
    }

    public void cancelNavigation() {
        movementSimulator.stopAllMovement();
        state = NavigationState.IDLE;
        currentPath = null;
    }

    public void onStuck() {
        // stuck recovery: jump → strafe left → strafe right → recalculate
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { state = NavigationState.FAILED; return; }

        // Try jump
        movementSimulator.jump();
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        // Strafe left
        movementSimulator.pressLeft(true);
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        movementSimulator.pressLeft(false);

        // Strafe right
        movementSimulator.pressRight(true);
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        movementSimulator.pressRight(false);

        // Recalculate
        if (targetBlockPos != null) {
            BlockPos current = mc.player.getBlockPos();
            currentPath = pathfindingEngine.recalculatePath(current, targetBlockPos);
            if (currentPath.success) {
                currentNodeIndex = 0;
                state = NavigationState.WALKING;
                lastPos = mc.player.getPos();
                lastPosTime = System.currentTimeMillis();
            } else {
                state = NavigationState.FAILED;
            }
        } else {
            state = NavigationState.FAILED;
        }
    }

    public NavigationState getState() { return state; }
}
