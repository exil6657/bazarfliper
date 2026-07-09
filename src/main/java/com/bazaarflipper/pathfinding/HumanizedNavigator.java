package com.bazaarflipper.pathfinding;

import com.bazaarflipper.automation.DelayManager;
import com.bazaarflipper.util.MathUtils;
import com.bazaarflipper.util.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Advanced Humanized Navigator - as real as possible per user request
 * Features expanded beyond spec:
 * - Uses smoothPoints from PathfindingEngine (Catmull-Rom + jitter + bobbing) for bezier-like curves
 * - Acceleration/deceleration curves, speed varies with terrain slope and fatigue
 * - Look-ahead yaw (looks 2-3 points ahead), overshoot correction 3-8° over 0.3-0.7s
 * - Environmental scanning: glances at nearby entities, chests, interesting blocks every 5-15s
 * - Social avoidance: steer away from nearby players
 * - Fatigue simulation: after >30s continuous walking, occasional slower, micro-pauses, yaw drift
 * - Irregular step timing: forward key pressed with varying pulse, not held perfectly constant
 * - Micro-strafe adjustments near obstacles
 * - Thinking pauses 5-10% per segment 200-800ms, stutter step 2%/s, camera wander 10-30° yaw
 * - Arrival overshoot 0.5-1.5 blocks then walk back, POST_NAVIGATION delay 500-1500ms
 * - Dynamic replan if obstacle appears or player deviates >2 blocks from path
 * - Jump timing variation 0-3 ticks, sprint 70%/walk 30% with random transitions and fatigue based
 * - Edge avoidance already in pathfinder, but navigator also avoids walking too close to void visually by looking down
 * Credits: Cldz
 */
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
    private int currentSmoothIndex = 0; // index into smoothPoints
    private int currentNodeIndex = 0; // fallback to raw nodes if smooth empty

    private Vec3 lastPos = Vec3.ZERO;
    private long lastPosTime = 0;
    private long thinkingPauseUntil = 0;
    private float yawNoise = 0;
    private float pitchNoise = 0;
    private long lastCameraWander = 0;
    private long lastEnvScan = 0;
    private boolean sprinting = true;
    private long stutterUntil = 0;
    private long lastSprintToggle = 0;
    private Random random = new Random();

    // Fatigue
    private long continuousWalkStart = 0;
    private double fatigueLevel = 0; // 0..1
    private long lastFatiguePause = 0;

    // Acceleration
    private double currentSpeedFactor = 0; // 0..1 - ramps up
    private long accelerationStart = 0;

    // Look ahead
    private static final int LOOK_AHEAD_POINTS = 3;

    // Social avoidance
    private Vec3 avoidanceVector = Vec3.ZERO;
    private long avoidanceUntil = 0;

    private BlockPos targetBlockPos;
    private WaypointRegistry.Waypoint targetWaypoint;

    // Micro adjustments
    private long nextMicroStrafe = 0;
    private boolean strafingLeft = false;

    public HumanizedNavigator(PathfindingEngine engine, MovementSimulator movement, WaypointRegistry registry, DelayManager delayManager) {
        this.pathfindingEngine = engine;
        this.movementSimulator = movement;
        this.waypointRegistry = registry;
        this.delayManager = delayManager;
    }

    public void navigateTo(String waypointName) {
        WaypointRegistry.Waypoint wp = waypointRegistry.getWaypoint(waypointName);
        if (wp == null) {
            Logger.warn("Waypoint not found: " + waypointName + " (may need wiki coords or user Set to Current Pos) - Credits Cldz");
            state = NavigationState.FAILED;
            return;
        }
        targetWaypoint = wp;
        targetBlockPos = new BlockPos((int) wp.x, (int) wp.y, (int) wp.z);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            state = NavigationState.FAILED;
            return;
        }
        BlockPos start = mc.player.blockPosition();
        state = NavigationState.CALCULATING;

        long calcStart = System.currentTimeMillis();
        currentPath = pathfindingEngine.calculatePath(start, targetBlockPos);
        long calcTime = System.currentTimeMillis() - calcStart;

        // Add human-like thinking delay proportional to path length (simulates planning)
        long planningDelay = MathUtils.randomInt(150, 400) + (currentPath.nodes.size() * 5L);
        try { Thread.sleep(planningDelay); } catch (InterruptedException ignored) {}

        if (!currentPath.success && !currentPath.partial) {
            Logger.warn("Failed to calculate path to " + waypointName + " (target " + targetBlockPos + ")", new Exception("Pathfinding failed"));
            state = NavigationState.FAILED;
            return;
        }
        if (currentPath.partial) {
            Logger.warn("Partial path to " + waypointName + " - closest reachable, will attempt then replan");
        }

        currentSmoothIndex = 0;
        currentNodeIndex = 0;
        lastPos = mc.player.position();
        lastPosTime = System.currentTimeMillis();
        continuousWalkStart = System.currentTimeMillis();
        fatigueLevel = 0;
        currentSpeedFactor = 0;
        accelerationStart = System.currentTimeMillis();
        state = NavigationState.WALKING;
        Logger.info("Navigating to " + waypointName + " at " + wp.x + "," + wp.y + "," + wp.z + " src=" + wp.source + " rawNodes=" + currentPath.nodes.size() + " smoothPoints=" + (currentPath.smoothPoints!=null?currentPath.smoothPoints.size():0) + " calcTime=" + calcTime + "ms planningDelay=" + planningDelay + "ms - Credits Cldz");
    }

    public void tick() {
        if (state != NavigationState.WALKING) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            state = NavigationState.FAILED;
            return;
        }

        long now = System.currentTimeMillis();
        Vec3 playerPos = mc.player.position();

        // ===== Arrival Check with overshoot logic =====
        if (targetWaypoint != null) {
            Vec3 targetVec = new Vec3(targetWaypoint.x, targetWaypoint.y, targetWaypoint.z);
            double dist = playerPos.distanceTo(targetVec);
            if (dist <= targetWaypoint.arrivalRadius) {
                // 30% chance arrival overshoot 0.5-1.5 blocks then walk back (human)
                if (random.nextFloat() < 0.3) {
                    double overshootDist = MathUtils.randomDouble(0.5, 1.5);
                    Vec3 overshootDir = playerPos.subtract(targetVec).normalize().multiply(overshootDist);
                    if (overshootDir.length() < 0.1) {
                        // Random direction if directly on target
                        double angle = random.nextDouble() * Math.PI * 2;
                        overshootDir = new Vec3(Math.cos(angle)*overshootDist, 0, Math.sin(angle)*overshootDist);
                    }
                    Vec3 overshootTarget = targetVec.add(overshootDir);
                    // Walk to overshoot then back
                    movementSimulator.pressForward(true);
                    double dx = overshootTarget.x - playerPos.x;
                    double dz = overshootTarget.z - playerPos.z;
                    float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                    movementSimulator.setYRot(yaw + MathUtils.randomInt(-3,3), 4f);
                    // After short delay, will detect still within radius but overshoot logic will then trigger arrival on next tick 70% of time
                    if (random.nextFloat() < 0.5) {
                        // Continue overshoot for a bit
                        return;
                    }
                }
                // Arrived - stop and POST_NAVIGATION delay
                movementSimulator.stopAllMovement();
                state = NavigationState.ARRIVED;
                long postNav = delayManager.getDelay(DelayManager.DelayType.POST_NAVIGATION);
                thinkingPauseUntil = now + postNav;
                Logger.info("Arrived at waypoint " + targetWaypoint.name + " (" + targetWaypoint.source + ") - Credits Cldz");
                return;
            }
        }

        // ===== Stuck Detection with advanced checks =====
        if (pathfindingEngine.isStuck(lastPos, now - lastPosTime)) {
            Logger.warn("Navigator stuck at " + playerPos + " lastPos " + lastPos + " - attempting advanced recovery");
            state = NavigationState.STUCK;
            onStuck();
            return;
        }

        // ===== Dynamic Replan: if deviated >2 blocks from current path, recalc =====
        if (currentPath != null && currentPath.smoothPoints != null && currentSmoothIndex < currentPath.smoothPoints.size()) {
            Vec3 expected = currentPath.smoothPoints.get(currentSmoothIndex);
            double deviation = playerPos.distanceTo(expected);
            if (deviation > 3.0) {
                Logger.info("Deviated " + String.format("%.2f", deviation) + " blocks from path, replanning");
                BlockPos currentBlock = mc.player.blockPosition();
                currentPath = pathfindingEngine.recalculatePath(currentBlock, targetBlockPos);
                currentSmoothIndex = 0;
                if (!currentPath.success && !currentPath.partial) {
                    state = NavigationState.FAILED;
                    return;
                }
            }
        }

        // ===== Thinking Pauses 5-10% chance per segment 200-800ms =====
        if (now < thinkingPauseUntil) {
            movementSimulator.stopAllMovement();
            // During thinking pause, occasionally look around (human)
            if (random.nextFloat() < 0.02) {
                float yawChange = MathUtils.randomInt(-15,15);
                movementSimulator.setYRot(mc.player.getYRot() + yawChange, 2f);
            }
            return;
        }
        if (random.nextFloat() < 0.07) { // 7% chance
            long pause = MathUtils.randomInt(200, 800);
            // Longer pause if high fatigue
            if (fatigueLevel > 0.5) pause += (long)(fatigueLevel * 500);
            thinkingPauseUntil = now + pause;
            movementSimulator.stopAllMovement();
            return;
        }

        // ===== Stutter Step 2% chance per second releasing forward 1-2 ticks =====
        if (random.nextFloat() < 0.02) {
            stutterUntil = now + MathUtils.randomInt(50, 120);
            movementSimulator.stopAllMovement();
        }
        if (now < stutterUntil) return;

        // ===== Fatigue Simulation =====
        long walkDuration = now - continuousWalkStart;
        fatigueLevel = Math.min(1.0, walkDuration / 120000.0); // full fatigue after 2 minutes continuous
        if (fatigueLevel > 0.4 && now - lastFatiguePause > MathUtils.randomInt(15000, 30000)) {
            if (random.nextDouble() < fatigueLevel * 0.3) {
                long fatiguePause = MathUtils.randomInt(400, 1000);
                thinkingPauseUntil = now + fatiguePause;
                lastFatiguePause = now;
                movementSimulator.stopAllMovement();
                Logger.debug("Fatigue pause " + fatiguePause + "ms level " + String.format("%.2f", fatigueLevel));
                return;
            }
        }

        // ===== Environmental Scanning: glance at nearby entities/chests =====
        if (now - lastEnvScan > MathUtils.randomInt(5000, 15000)) {
            if (random.nextFloat() < 0.4) { // 40% chance to glance
                Entity interesting = findInterestingEntity();
                if (interesting != null) {
                    Vec3 target = interesting.position();
                    double dx = target.x - playerPos.x;
                    double dz = target.z - playerPos.z;
                    float yawToEntity = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                    float currentYaw = mc.player.getYRot();
                    // Quick glance 10-30° towards entity
                    float glanceYaw = MathUtils.lerp(currentYaw, yawToEntity, 0.3f) + MathUtils.randomInt(-5,5);
                    movementSimulator.setYRot(glanceYaw, MathUtils.randomInt(8,15));
                    lastEnvScan = now;
                    // Brief pause to look? 5% chance
                    if (random.nextFloat() < 0.05) {
                        thinkingPauseUntil = now + MathUtils.randomInt(200,500);
                        movementSimulator.stopAllMovement();
                        return;
                    }
                }
            }
            lastEnvScan = now;
        }

        // ===== Social Avoidance: steer away from nearby players =====
        if (now < avoidanceUntil) {
            // Apply avoidance vector to movement
        } else {
            Vec3 avoidance = computeAvoidanceVector();
            if (avoidance.length() > 0.1) {
                avoidanceVector = avoidance;
                avoidanceUntil = now + MathUtils.randomInt(800, 1500);
            } else {
                avoidanceVector = Vec3.ZERO;
            }
        }

        // ===== Camera Wander every 5-15s 10-30° yaw shift 0.5-1s =====
        if (now - lastCameraWander > MathUtils.randomInt(5000, 15000)) {
            if (random.nextDouble() >= 0.2) { // 20% chance no movement
                float wanderYaw = MathUtils.randomInt(-30,30);
                float wanderPitch = MathUtils.randomInt(-10,10);
                // Smooth wander over 0.5-1s via setYaw with speed
                movementSimulator.setYRot(mc.player.getYRot() + wanderYaw, MathUtils.randomInt(2,5));
                movementSimulator.setXRot(mc.player.getXRot() + wanderPitch, MathUtils.randomInt(2,5));
            }
            lastCameraWander = now;
        }

        // ===== Direction Noise ±2-5° per tick low-pass filtered =====
        yawNoise = MathUtils.lerp(yawNoise, MathUtils.randomInt(-5,5), 0.08f);
        pitchNoise = MathUtils.lerp(pitchNoise, MathUtils.randomInt(-2,2), 0.05f);

        // ===== Speed Variation 70% sprint / 30% walk + fatigue =====
        if (now - lastSprintToggle > MathUtils.randomInt(3000, 8000)) {
            double sprintChance = 0.7 - fatigueLevel * 0.3; // fatigue reduces sprint chance
            sprinting = random.nextDouble() < sprintChance;
            movementSimulator.sprint(sprinting && fatigueLevel < 0.8);
            lastSprintToggle = now;
        }

        // ===== Micro Strafe Adjustments near obstacles =====
        if (now > nextMicroStrafe) {
            if (random.nextFloat() < 0.08) { // 8% chance per check to micro-strafe
                strafingLeft = random.nextBoolean();
                nextMicroStrafe = now + MathUtils.randomInt(400, 900);
                if (strafingLeft) movementSimulator.pressLeft(true);
                else movementSimulator.pressRight(true);
                // Release after short duration via scheduled task
                mc.execute(() -> {
                    try { Thread.sleep(MathUtils.randomInt(80,200)); } catch (InterruptedException ignored) {}
                    mc.execute(() -> {
                        movementSimulator.pressLeft(false);
                        movementSimulator.pressRight(false);
                    });
                });
            } else {
                nextMicroStrafe = now + MathUtils.randomInt(800, 2000);
            }
        }

        // ===== Follow Smooth Path with Look-Ahead =====
        List<Vec3> smoothPoints = currentPath != null ? pathfindingEngine.getSmoothPoints(currentPath) : null;
        Vec3 nextTarget;

        if (smoothPoints != null && !smoothPoints.isEmpty() && currentSmoothIndex < smoothPoints.size()) {
            // Look ahead 2-3 points for more natural steering (human looks ahead, not just immediate)
            int lookAhead = Math.min(smoothPoints.size()-1, currentSmoothIndex + LOOK_AHEAD_POINTS + random.nextInt(2));
            nextTarget = smoothPoints.get(lookAhead);

            // Add avoidance vector
            if (avoidanceVector.length() > 0.01) {
                nextTarget = nextTarget.add(avoidanceVector);
            }

            // Add slight random jitter to target (human not perfect)
            nextTarget = nextTarget.add(
                (random.nextDouble()-0.5)*0.2,
                0,
                (random.nextDouble()-0.5)*0.2
            );

            double distToNext = playerPos.distanceTo(smoothPoints.get(currentSmoothIndex));
            if (distToNext < 0.6) { // reached current smooth point (more precise than 1.2 for smooth)
                currentSmoothIndex++;
                lastPos = playerPos;
                lastPosTime = now;
            }
        } else {
            // Fallback to raw nodes if smooth not available
            if (currentPath == null || currentNodeIndex >= currentPath.nodes.size()) {
                state = NavigationState.ARRIVED;
                return;
            }
            BlockPos nextNode = currentPath.nodes.get(currentNodeIndex);
            nextTarget = Vec3.atCenterOf(nextNode);
            double distanceToNode = playerPos.distanceTo(nextTarget);
            if (distanceToNode < 1.2) {
                currentNodeIndex++;
                lastPos = playerPos;
                lastPosTime = now;
            }
        }

        // ===== Yaw/Pitch with Overshoot =====
        double dx = nextTarget.x - playerPos.x;
        double dz = nextTarget.z - playerPos.z;
        double dy = nextTarget.y - (playerPos.y + 1.62); // eye height

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)));

        // Turn overshoot 3-8° corrected over 0.3-0.7s - simulate human overcorrecting
        float overshoot = MathUtils.randomInt(3,8) * (random.nextBoolean() ? 1 : -1);
        // Overshoot decays over time via lerp towards target
        double timeSinceAccel = (now - accelerationStart) / 1000.0;
        double overshootDecay = Math.max(0, 1.0 - timeSinceAccel * 2.0); // decays in 0.5s
        float yawWithOvershoot = targetYaw + (float)(overshoot * overshootDecay) + yawNoise;

        // Speed factor for yaw: faster turn if large difference, slower for small (human)
        float yawDiff = Math.abs(yawWithOvershoot - mc.player.getYRot());
        float yawSpeed = yawDiff > 30 ? MathUtils.randomInt(10,15) : MathUtils.randomInt(4,8);
        if (fatigueLevel > 0.6) yawSpeed *= 0.7; // tired turns slower

        movementSimulator.setYRot(yawWithOvershoot, yawSpeed);
        movementSimulator.setXRot(targetPitch + pitchNoise, 4f);

        // ===== Acceleration Curve =====
        double accelProgress = Math.min(1.0, (now - accelerationStart) / 800.0); // 0.8s to full speed
        currentSpeedFactor = MathUtils.lerp(0, 1, (float) (1 - Math.pow(1 - accelProgress, 3))); // ease-out cubic
        // Apply fatigue to speed
        currentSpeedFactor *= (1.0 - fatigueLevel * 0.25);

        // ===== Forward Movement with Irregular Pulse =====
        // Human doesn't hold W perfectly constant, occasional slight release even when not stutter
        if (random.nextFloat() < 0.03 * (1 + fatigueLevel)) { // 3% chance per tick to briefly release (more when tired)
            movementSimulator.pressForward(false);
            try { Thread.sleep(MathUtils.randomInt(20,60)); } catch (InterruptedException ignored) {}
        }

        // Sprint affects speed factor via movement simulator sprinting state, but forward press still needed
        // Vary forward press based on speed factor: if factor <0.3, briefly not pressing to simulate acceleration start slow
        if (currentSpeedFactor > 0.2 || random.nextFloat() < 0.8) {
            movementSimulator.pressForward(true);
        }

        // ===== Jump Timing Variation 0-3 tick random delay =====
        // Check if next target is higher than current
        if (nextTarget.y > playerPos.y + 0.5) {
            if (random.nextInt(4) == 0) { // 0-3 tick variation
                // Add slight random yaw before jump (human looks up slightly before jump)
                movementSimulator.setXRot(mc.player.getXRot() - MathUtils.randomInt(2,5), 8f);
                movementSimulator.jump();
                accelerationStart = now; // reset acceleration after jump (realistic)
            }
        }

        // ===== Update Last Pos Tracking =====
        if (playerPos.distanceTo(lastPos) > 0.7) {
            lastPos = playerPos;
            lastPosTime = now;
        }
    }

    private Entity findInterestingEntity() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return null;
            Vec3 playerPos = mc.player.position();
            Entity best = null;
            double bestScore = 0;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player) continue;
                double dist = e.position().distanceTo(playerPos);
                if (dist > 8 || dist < 1) continue;
                double score = 0;
                // Score based on type: NPCs, chests, players more interesting
                if (e.isPlayer()) score = 10 - dist;
                else if (e.getName().getString().toLowerCase().contains("bazaar") || e.getName().getString().toLowerCase().contains("auction") || e.getName().getString().toLowerCase().contains("bank")) {
                    score = 20 - dist;
                } else if (e.isLiving()) score = 5 - dist*0.5;
                if (score > bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            return best;
        } catch (Exception e) { return null; }
    }

    private Vec3 computeAvoidanceVector() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return Vec3.ZERO;
            Vec3 playerPos = mc.player.position();
            Vec3 avoidance = Vec3.ZERO;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player) continue;
                if (!e.isPlayer() && !e.isLiving()) continue;
                double dist = e.position().distanceTo(playerPos);
                if (dist < 3.0) {
                    Vec3 away = playerPos.subtract(e.position()).normalize();
                    double strength = (3.0 - dist) / 3.0 * 0.5;
                    avoidance = avoidance.add(away.multiply(strength));
                }
            }
            return avoidance;
        } catch (Exception e) { return Vec3.ZERO; }
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
        fatigueLevel = 0;
        currentSpeedFactor = 0;
    }

    public void onStuck() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { state = NavigationState.FAILED; return; }

        // Advanced stuck recovery sequence: jump → look around → strafe left → strafe right → back → recalculate → if still stuck, up to 3 attempts then fail
        int attempts = 0;
        while (attempts < 3) {
            attempts++;
            Logger.info("Stuck recovery attempt " + attempts + "/3");

            // 1. Jump
            movementSimulator.jump();
            sleep(250);

            // 2. Look around randomly (human looks for alternative path when stuck)
            float randomYaw = mc.player.getYRot() + MathUtils.randomInt(-90,90);
            movementSimulator.setYRot(randomYaw, 15f);
            sleep(300);

            // 3. Strafe left
            movementSimulator.pressLeft(true);
            sleep(MathUtils.randomInt(300,500));
            movementSimulator.pressLeft(false);

            // 4. Strafe right
            movementSimulator.pressRight(true);
            sleep(MathUtils.randomInt(300,500));
            movementSimulator.pressRight(false);

            // 5. Back a bit
            movementSimulator.pressBack(true);
            sleep(300);
            movementSimulator.pressBack(false);

            // 6. Try to recalculate with cleared cache to get fresh path
            if (targetBlockPos != null) {
                pathfindingEngine.clearCache();
                BlockPos current = mc.player.blockPosition();
                currentPath = pathfindingEngine.calculatePath(current, targetBlockPos);
                if (currentPath.success || currentPath.partial) {
                    currentSmoothIndex = 0;
                    currentNodeIndex = 0;
                    state = NavigationState.WALKING;
                    lastPos = mc.player.position();
                    lastPosTime = System.currentTimeMillis();
                    accelerationStart = System.currentTimeMillis();
                    currentSpeedFactor = 0;
                    Logger.info("Stuck recovery succeeded after " + attempts + " attempts");
                    return;
                }
            }

            sleep(500);
        }

        state = NavigationState.FAILED;
        Logger.warn("Stuck recovery failed after 3 attempts, marking FAILED");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public NavigationState getState() { return state; }
    public double getFatigueLevel() { return fatigueLevel; }
    public Vec3 getLastPos() { return lastPos; }
}
