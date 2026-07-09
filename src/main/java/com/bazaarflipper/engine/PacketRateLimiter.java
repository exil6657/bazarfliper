package com.bazaarflipper.engine;

import com.bazaarflipper.util.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Adaptive rate limiting per action type, lag mode detection via rolling avg of last 10 server responses.
 */
public class PacketRateLimiter {

    public enum ActionType {
        GUI_CLICK,
        CHAT_COMMAND,
        INVENTORY_INTERACTION,
        MOVEMENT
    }

    private final Map<ActionType, ConcurrentLinkedDeque<Long>> responseTimes = new ConcurrentHashMap<>();
    private final Map<ActionType, Long> lastActionTime = new ConcurrentHashMap<>();
    private volatile double currentMultiplier = 1.0; // 1.0 normal, >1.0 slower (lag mode)

    public PacketRateLimiter() {
        for (ActionType type : ActionType.values()) {
            responseTimes.put(type, new ConcurrentLinkedDeque<>());
            lastActionTime.put(type, 0L);
        }
    }

    public boolean canPerformAction(ActionType type) {
        long now = System.currentTimeMillis();
        Long last = lastActionTime.get(type);
        if (last == null) return true;
        long minInterval = (long) (100 * currentMultiplier); // at least 100ms * multiplier
        return now - last >= minInterval;
    }

    public void recordActionSent(ActionType type) {
        lastActionTime.put(type, System.currentTimeMillis());
    }

    public void recordServerResponse(ActionType type, long responseTimeMs) {
        var deque = responseTimes.get(type);
        deque.addLast(responseTimeMs);
        while (deque.size() > 10) deque.pollFirst();

        // Evaluate lag mode if avg >500ms -> 50% slower
        long sum = 0;
        for (Long rt : deque) sum += rt;
        if (!deque.isEmpty()) {
            double avg = (double) sum / deque.size();
            if (avg > 500) {
                if (currentMultiplier < 1.5) {
                    Logger.warn("Lag detected avg " + avg + "ms, entering lag mode 50% slower");
                    currentMultiplier = 1.5;
                }
            } else if (avg < 300) {
                // Gradually recover
                currentMultiplier = Math.max(1.0, currentMultiplier - 0.05);
            }
        }
    }

    public double getCurrentMultiplier() {
        return currentMultiplier;
    }

    public void reset() {
        currentMultiplier = 1.0;
        responseTimes.values().forEach(ConcurrentLinkedDeque::clear);
    }
}
