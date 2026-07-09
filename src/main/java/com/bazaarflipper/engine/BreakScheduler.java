package com.bazaarflipper.engine;

import com.bazaarflipper.config.ModConfig;
import com.bazaarflipper.pathfinding.MovementSimulator;
import com.bazaarflipper.util.Logger;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.Minecraft;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simulate human-like breaks to avoid bot detection.
 * Two-tier periodic breaks + order wait breaks.
 */
public class BreakScheduler {

    public enum BreakType {
        ORDER_WAIT,
        SHORT_PERIODIC,
        LONG_SESSION
    }

    public static class BreakRecord {
        public BreakType type;
        public long startTimestamp;
        public long endTimestamp;
        public long durationMs;
        public String reason;

        public BreakRecord(BreakType type, long start, long end, String reason) {
            this.type = type;
            this.startTimestamp = start;
            this.endTimestamp = end;
            this.durationMs = end - start;
            this.reason = reason;
        }
    }

    private final ModConfig config;
    private final MovementSimulator movementSimulator;

    private volatile long sessionStartTime = 0;
    private volatile long totalActiveTime = 0;
    private volatile long totalBreakTime = 0;
    private volatile long currentWindowStart = 0;
    private volatile long breakTimeInCurrentWindow = 0;
    private volatile long timeSinceLastLongBreak = 0;

    private volatile boolean isOnBreak = false;
    private volatile BreakType currentBreakType = null;
    private volatile long breakStartTime = 0;
    private volatile long breakEndTime = 0;
    private final ConcurrentLinkedDeque<BreakRecord> breakHistory = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedQueue<CommandQueue.FlipAction> postBreakActionQueue = new ConcurrentLinkedQueue<>();

    private final Random random = new Random();
    private long lastIdleCameraMove = 0;
    private long lastShuffle = 0;
    private long lastActiveTick = 0;

    public BreakScheduler(ModConfig config, MovementSimulator movementSimulator) {
        this.config = config;
        this.movementSimulator = movementSimulator;
    }

    public void startSession() {
        sessionStartTime = System.currentTimeMillis();
        currentWindowStart = sessionStartTime;
        breakTimeInCurrentWindow = 0;
        totalActiveTime = 0;
        totalBreakTime = 0;
        timeSinceLastLongBreak = 0;
        isOnBreak = false;
        lastActiveTick = System.currentTimeMillis();
        Logger.info("BreakScheduler session started");
    }

    public void endSession() {
        if (isOnBreak) endBreak();
        Logger.info("BreakScheduler session ended, total break time " + totalBreakTime + "ms");
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (isOnBreak) {
            if (now >= breakEndTime) {
                endBreak();
            } else {
                runIdleBehavior();
            }
            return;
        } else {
            if (lastActiveTick != 0) {
                long delta = now - lastActiveTick;
                totalActiveTime += delta;
                timeSinceLastLongBreak += delta;
                if (now - currentWindowStart > config.shortBreakWindowMinutes * 60L * 1000L) {
                    currentWindowStart = now;
                    breakTimeInCurrentWindow = 0;
                }
            }
            lastActiveTick = now;

            long longBreakIntervalMs = config.longBreakIntervalHours * 3600L * 1000L;
            if (timeSinceLastLongBreak >= longBreakIntervalMs) {
                startBreak(BreakType.LONG_SESSION);
                return;
            }

            long windowMs = config.shortBreakWindowMinutes * 60L * 1000L;
            long quotaMs = config.shortBreakWindowMinBreakMinutes * 60L * 1000L;
            long windowElapsed = now - currentWindowStart;
            long timeLeftInWindow = windowMs - windowElapsed;
            long deficit = quotaMs - breakTimeInCurrentWindow;

            if (timeLeftInWindow < 5 * 60 * 1000L && deficit > 0) {
                Logger.info("Forcing short break to cover deficit " + deficit + "ms window closing in " + timeLeftInWindow + "ms");
                startBreak(BreakType.SHORT_PERIODIC);
                return;
            }

            double progress = windowMs > 0 ? (double) windowElapsed / windowMs : 0;
            double weight = 1.0 - Math.abs(progress - 0.5) * 2;
            weight = Math.max(0, weight);
            double baseProb = 0.001;
            double prob = baseProb * weight * (deficit > 0 ? 2.0 : 0.5);
            if (random.nextDouble() < prob) {
                if (deficit > 0 && progress > 0.2) {
                    startBreak(BreakType.SHORT_PERIODIC);
                }
            }
        }
    }

    public void startBreak(BreakType type) {
        if (!config.breaksEnabled && type != BreakType.ORDER_WAIT) {
            return;
        }
        if (isOnBreak) return;

        long duration = switch (type) {
            case ORDER_WAIT -> MathUtils.randomInt((int) config.orderWaitMinSeconds, (int) config.orderWaitMaxSeconds) * 1000L;
            case SHORT_PERIODIC -> MathUtils.randomInt((int) config.shortBreakMinDuration, (int) config.shortBreakMaxDuration) * 1000L;
            case LONG_SESSION -> MathUtils.randomInt((int) config.longBreakMinDuration, (int) config.longBreakMaxDuration) * 1000L;
        };

        isOnBreak = true;
        currentBreakType = type;
        breakStartTime = System.currentTimeMillis();
        breakEndTime = breakStartTime + duration;
        Logger.info("Break started: " + type + " duration " + duration + "ms");
        lastIdleCameraMove = System.currentTimeMillis();
        lastShuffle = System.currentTimeMillis();
    }

    public void startOrderWaitBreak() {
        startBreak(BreakType.ORDER_WAIT);
    }

    public void endBreak() {
        if (!isOnBreak) return;
        long now = System.currentTimeMillis();
        long duration = now - breakStartTime;
        if (duration < 0) duration = breakEndTime - breakStartTime;

        BreakRecord record = new BreakRecord(currentBreakType, breakStartTime, now, currentBreakType.name());
        breakHistory.addLast(record);
        while (breakHistory.size() > 100) breakHistory.pollFirst();

        totalBreakTime += duration;
        if (currentBreakType == BreakType.SHORT_PERIODIC || currentBreakType == BreakType.LONG_SESSION) {
            breakTimeInCurrentWindow += duration;
        }
        if (currentBreakType == BreakType.LONG_SESSION) {
            timeSinceLastLongBreak = 0;
        }

        isOnBreak = false;
        BreakType endedType = currentBreakType;
        currentBreakType = null;

        Logger.info("Break ended: " + endedType + " duration " + duration + "ms");
    }

    public void cancelBreak() {
        if (isOnBreak) {
            Logger.info("Break cancelled: " + currentBreakType);
            long now = System.currentTimeMillis();
            long elapsed = now - breakStartTime;
            totalBreakTime += elapsed;
            if (currentBreakType == BreakType.SHORT_PERIODIC || currentBreakType == BreakType.LONG_SESSION) {
                breakTimeInCurrentWindow += elapsed;
            }
            isOnBreak = false;
            currentBreakType = null;
        }
    }

    private void runIdleBehavior() {
        if (!isOnBreak) return;
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();

        if (config.breakIdleCameraMovement) {
            if (now - lastIdleCameraMove > MathUtils.randomInt(15000, 45000)) {
                if (MathUtils.randomInt(0, 100) >= 20) {
                    float yawChange = MathUtils.randomInt(5, 25) * (random.nextBoolean() ? 1 : -1);
                    float pitchChange = MathUtils.randomInt(-10, 10);
                    mc.execute(() -> {
                        if (mc.player != null) {
                            movementSimulator.setYRot(mc.player.getYRot() + yawChange, MathUtils.randomInt(2, 5));
                            movementSimulator.setXRot(mc.player.getXRot() + pitchChange, MathUtils.randomInt(2, 5));
                        }
                    });
                }
                lastIdleCameraMove = now;
            }
        }

        if (config.breakIdleShuffleStep) {
            if (now - lastShuffle > 60_000) {
                if (random.nextDouble() < 0.10) {
                    mc.execute(() -> {
                        int steps = MathUtils.randomInt(1, 3);
                        int dir = MathUtils.randomInt(0, 3);
                        movementSimulator.pressForward(dir == 0);
                        try { Thread.sleep(200L * steps); } catch (InterruptedException ignored) {}
                        movementSimulator.stopAllMovement();
                    });
                }
                lastShuffle = now;
            }
        }
    }

    public boolean isBreakDue() {
        if (!config.breaksEnabled) return false;
        if (isOnBreak) return false;
        long now = System.currentTimeMillis();
        long longInterval = config.longBreakIntervalHours * 3600L * 1000L;
        if (timeSinceLastLongBreak >= longInterval) return true;

        long windowMs = config.shortBreakWindowMinutes * 60L * 1000L;
        long quotaMs = config.shortBreakWindowMinBreakMinutes * 60L * 1000L;
        long windowElapsed = now - currentWindowStart;
        long timeLeft = windowMs - windowElapsed;
        long deficit = quotaMs - breakTimeInCurrentWindow;
        return timeLeft < 5 * 60 * 1000L && deficit > 0;
    }

    public boolean isOnBreak() { return isOnBreak; }
    public long getBreakTimeInCurrentWindow() { return breakTimeInCurrentWindow; }
    public long getDeficitInCurrentWindow() {
        long quotaMs = config.shortBreakWindowMinBreakMinutes * 60L * 1000L;
        return Math.max(0, quotaMs - breakTimeInCurrentWindow);
    }
    public long getTimeUntilNextForcedBreak() {
        long windowMs = config.shortBreakWindowMinutes * 60L * 1000L;
        long now = System.currentTimeMillis();
        long elapsed = now - currentWindowStart;
        long remaining = windowMs - elapsed;
        long deficit = getDeficitInCurrentWindow();
        if (deficit <= 0) return remaining;
        if (remaining < 5 * 60 * 1000L) return 0;
        return remaining - 5 * 60 * 1000L;
    }
    public long getTimeUntilLongBreak() {
        long interval = config.longBreakIntervalHours * 3600L * 1000L;
        return Math.max(0, interval - timeSinceLastLongBreak);
    }
    public BreakType getCurrentBreakType() { return currentBreakType; }
    public long getRemainingBreakTime() {
        if (!isOnBreak) return 0;
        return Math.max(0, breakEndTime - System.currentTimeMillis());
    }
    public long getTotalBreakTime() { return totalBreakTime; }
    public ConcurrentLinkedDeque<BreakRecord> getBreakHistory() { return breakHistory; }
    public void queuePostBreakAction(CommandQueue.FlipAction action) { postBreakActionQueue.offer(action); }
    public ConcurrentLinkedQueue<CommandQueue.FlipAction> getPostBreakActionQueue() { return postBreakActionQueue; }
    public long getTotalActiveTime() { return totalActiveTime; }
    public long getTimeSinceLastLongBreak() { return timeSinceLastLongBreak; }
    public long getCurrentWindowStart() { return currentWindowStart; }
    public void restoreState(long totalActive, long timeSinceLong, long breakTimeWindow, long windowStart) {
        this.totalActiveTime = totalActive;
        this.timeSinceLastLongBreak = timeSinceLong;
        this.breakTimeInCurrentWindow = breakTimeWindow;
        this.currentWindowStart = windowStart;
    }
}
