package com.bazaarflipper.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Track per flip: placement timestamp, last check, fill percentage, relist count, order type, partial fill tracking, stale timer state
 */
public class OrderManager {

    public enum StaleStatus { FRESH, WARN, STALE }

    public static class ActiveOrder {
        public String productId;
        public long placementTimestamp;
        public long lastCheckTimestamp;
        public double fillPercentage; // 0.0-1.0
        public int relistCount;
        public String orderType; // BUY, SELL, AH
        public boolean partialFilled;
        public int filledQty;
        public int totalQty;
        public long staleTimerStart;
        public StaleStatus staleStatus = StaleStatus.FRESH;
    }

    private final Map<String, ActiveOrder> orders = new ConcurrentHashMap<>();

    public void markOrderPlaced(String productId, String orderType, int totalQty) {
        ActiveOrder order = new ActiveOrder();
        order.productId = productId;
        order.orderType = orderType;
        order.totalQty = totalQty;
        order.filledQty = 0;
        order.fillPercentage = 0;
        order.placementTimestamp = System.currentTimeMillis();
        order.lastCheckTimestamp = order.placementTimestamp;
        order.staleTimerStart = order.placementTimestamp;
        orders.put(productId, order);
    }

    public void markOrderFilled(String productId) {
        ActiveOrder order = orders.get(productId);
        if (order != null) {
            order.fillPercentage = 1.0;
            order.filledQty = order.totalQty;
            order.lastCheckTimestamp = System.currentTimeMillis();
        }
    }

    public void markOrderPartiallyFilled(String productId, int filledQty) {
        ActiveOrder order = orders.get(productId);
        if (order != null) {
            order.filledQty = filledQty;
            order.fillPercentage = (double) filledQty / Math.max(1, order.totalQty);
            order.partialFilled = order.fillPercentage >0 && order.fillPercentage <1;
            order.lastCheckTimestamp = System.currentTimeMillis();
        }
    }

    public void markOrderCancelled(String productId) {
        orders.remove(productId);
    }

    public StaleStatus checkStaleStatus(String productId, long patienceMs, double fillRate, double currentSpreadProfit) {
        ActiveOrder order = orders.get(productId);
        if (order == null) return StaleStatus.FRESH;

        long age = System.currentTimeMillis() - order.placementTimestamp;
        double fill = order.fillPercentage;

        // Dynamic three-factor logic is handled in FlipEngine but we provide status
        if (age > patienceMs) {
            order.staleStatus = StaleStatus.STALE;
        } else if (age > patienceMs * 0.75) {
            order.staleStatus = StaleStatus.WARN;
        } else {
            order.staleStatus = StaleStatus.FRESH;
        }
        return order.staleStatus;
    }

    public long getOrderAge(String productId) {
        ActiveOrder order = orders.get(productId);
        if (order == null) return 0;
        return System.currentTimeMillis() - order.placementTimestamp;
    }

    public Map<String, ActiveOrder> getAllActiveOrders() { return orders; }

    public double getOrderFillRate(String productId) {
        ActiveOrder order = orders.get(productId);
        if (order == null) return 0;
        long age = getOrderAge(productId);
        if (age==0) return 0;
        return order.fillPercentage / (age / 3600000.0); // per hour
    }
}
