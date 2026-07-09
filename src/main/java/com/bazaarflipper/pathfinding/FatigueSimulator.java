package com.bazaarflipper.pathfinding;

import com.bazaarflipper.util.MathUtils;

import java.util.Random;

/**
 * Simulates human fatigue over long navigation sessions
 * Affects speed, yaw accuracy, thinking pauses, sprint preference
 * Credits: Cldz
 */
public class FatigueSimulator {

    private long walkStart = 0;
    private double fatigue = 0; // 0..1
    private final Random random = new Random();
    private long lastMicroPause = 0;

    public void startWalking() {
        if (walkStart == 0) walkStart = System.currentTimeMillis();
    }

    public void stopWalking() {
        // Fatigue recovers when stopped, but slowly
        long now = System.currentTimeMillis();
        if (walkStart != 0) {
            long walkDuration = now - walkStart;
            // If stopped for >10s, recover some fatigue
            // For simplicity, reset walkStart and reduce fatigue
            fatigue = Math.max(0, fatigue - 0.05);
            walkStart = 0;
        }
    }

    public void tick(boolean isWalking) {
        long now = System.currentTimeMillis();
        if (isWalking) {
            if (walkStart == 0) walkStart = now;
            long duration = now - walkStart;
            fatigue = Math.min(1.0, duration / 180000.0); // full fatigue after 3 minutes continuous
        } else {
            // Recovery
            fatigue = Math.max(0, fatigue - 0.001); // slowly recover when not walking
        }
    }

    public double getFatigue() { return fatigue; }

    public double getSpeedMultiplier() {
        // Speed reduces up to 25% when fully fatigued
        return 1.0 - fatigue * 0.25;
    }

    public double getSprintChanceModifier() {
        // Sprint less when fatigued
        return 1.0 - fatigue * 0.4;
    }

    public long getThinkingPauseExtra() {
        return (long)(fatigue * 400); // extra 0-400ms thinking when tired
    }

    public boolean shouldMicroPause() {
        long now = System.currentTimeMillis();
        if (now - lastMicroPause < MathUtils.randomInt(10000, 25000)) return false;
        if (random.nextDouble() < fatigue * 0.25) {
            lastMicroPause = now;
            return true;
        }
        return false;
    }

    public double getYawAccuracyPenalty() {
        // When tired, yaw less accurate (more noise)
        return fatigue * 2.0;
    }
}
