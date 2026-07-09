package com.bazaarflipper.engine;

import com.bazaarflipper.util.Logger;

import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Prioritised command queue
 * Priority order (highest to lowest):
 * 1. Claim filled orders
 * 2. Cancel undercut orders
 * 3. Relist cancelled orders
 * 4. Place new sell offers / AH listings
 * 5. Craft items
 * 6. Place new buy orders
 * 7. Navigate to location
 */
public class CommandQueue {

    public enum ActionType {
        CLAIM_FILLED,
        CANCEL_UNDERCUT,
        RELIST_CANCELLED,
        PLACE_SELL_OFFER,
        PLACE_AH_LISTING,
        CRAFT_ITEMS,
        PLACE_BUY_ORDER,
        NAVIGATE
    }

    public static class FlipAction {
        public ActionType type;
        public String productId;
        public double price;
        public int quantity;
        public int priority; // lower number = higher priority
        public long timestamp;

        public FlipAction(ActionType type, String productId, double price, int quantity) {
            this.type = type;
            this.productId = productId;
            this.price = price;
            this.quantity = quantity;
            this.timestamp = System.currentTimeMillis();
            this.priority = switch (type) {
                case CLAIM_FILLED -> 1;
                case CANCEL_UNDERCUT -> 2;
                case RELIST_CANCELLED -> 3;
                case PLACE_SELL_OFFER, PLACE_AH_LISTING -> 4;
                case CRAFT_ITEMS -> 5;
                case PLACE_BUY_ORDER -> 6;
                case NAVIGATE -> 7;
            };
        }
    }

    private final PriorityBlockingQueue<FlipAction> queue = new PriorityBlockingQueue<>(100,
            Comparator.comparingInt((FlipAction a) -> a.priority).thenComparingLong(a -> a.timestamp));

    private volatile boolean paused = false;

    public void enqueue(FlipAction action) {
        queue.offer(action);
        Logger.info("Enqueued action " + action.type + " for " + action.productId + " priority " + action.priority);
    }

    public void enqueue(ActionType type, String productId, double price, int qty) {
        enqueue(new FlipAction(type, productId, price, qty));
    }

    public FlipAction poll() {
        if (paused) return null;
        return queue.poll();
    }

    public FlipAction peek() {
        return queue.peek();
    }

    public boolean isEmpty() { return queue.isEmpty(); }

    public int size() { return queue.size(); }

    public void pause() { paused = true; }

    public void resume() { paused = false; }

    public void clear() { queue.clear(); }

    public void stop() { paused = true; queue.clear(); }
}
