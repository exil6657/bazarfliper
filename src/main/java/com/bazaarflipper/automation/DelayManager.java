package com.bazaarflipper.automation;

import com.bazaarflipper.engine.PacketRateLimiter;
import com.bazaarflipper.util.MathUtils;

import java.util.Random;

public class DelayManager {

    public enum DelayType {
        ACTION,
        CLICK,
        GUI_LOAD,
        POST_NAVIGATION,
        BANK_INTERACTION,
        CRAFT_INTERACTION,
        AH_INTERACTION,
        PATHFINDING_PAUSE,
        RECONNECT_WAIT
    }

    private final PacketRateLimiter packetRateLimiter;
    private final Random random = new Random();

    public DelayManager(PacketRateLimiter rateLimiter) {
        this.packetRateLimiter = rateLimiter;
    }

    public long getDelay(DelayType type) {
        double multiplier = packetRateLimiter != null ? packetRateLimiter.getCurrentMultiplier() : 1.0;
        long base = getBaseDelay(type);
        long delayed = (long) (base * multiplier);

        // 5% chance extra 2-8 sec distraction
        if (random.nextDouble() < 0.05) {
            delayed += MathUtils.randomInt(2000, 8000);
        }
        // 1% chance extra 15-45 sec long distraction
        if (random.nextDouble() < 0.01) {
            delayed += MathUtils.randomInt(15000, 45000);
        }
        return delayed;
    }

    private long getBaseDelay(DelayType type) {
        return switch (type) {
            case ACTION -> MathUtils.randomInt(300, 800);
            case CLICK -> MathUtils.randomInt(80, 200);
            case GUI_LOAD -> MathUtils.randomInt(300, 700);
            case POST_NAVIGATION -> MathUtils.randomInt(500, 1500);
            case BANK_INTERACTION -> MathUtils.randomInt(400, 900);
            case CRAFT_INTERACTION -> MathUtils.randomInt(350, 750);
            case AH_INTERACTION -> MathUtils.randomInt(400, 900);
            case PATHFINDING_PAUSE -> MathUtils.randomInt(200, 800);
            case RECONNECT_WAIT -> MathUtils.randomInt(10000, 60000);
        };
    }

    public void sleep(DelayType type) {
        try {
            Thread.sleep(getDelay(type));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void sleepRandom(long min, long max) {
        try {
            Thread.sleep(MathUtils.randomInt((int)min, (int)max));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
