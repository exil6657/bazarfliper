package com.bazaarflipper.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Ring buffer max 720 points per product.
 */
public class PriceHistory {
    public static class DataPoint {
        public double buyPrice;
        public double sellPrice;
        public double buyVolume;
        public double sellVolume;
        public long timestamp;

        public DataPoint(double buy, double sell, double buyVol, double sellVol, long ts) {
            this.buyPrice = buy;
            this.sellPrice = sell;
            this.buyVolume = buyVol;
            this.sellVolume = sellVol;
            this.timestamp = ts;
        }
    }

    private final Map<String, ConcurrentLinkedDeque<DataPoint>> history = new ConcurrentHashMap<>();
    private static final int MAX_POINTS = 720;

    public void addDataPoint(String productId, DataPoint dp) {
        ConcurrentLinkedDeque<DataPoint> deque = history.computeIfAbsent(productId, k -> new ConcurrentLinkedDeque<>());
        deque.addLast(dp);
        while (deque.size() > MAX_POINTS) deque.pollFirst();
    }

    public void addDataPoint(String productId, double buyPrice, double sellPrice, double buyVol, double sellVol) {
        addDataPoint(productId, new DataPoint(buyPrice, sellPrice, buyVol, sellVol, System.currentTimeMillis()));
    }

    public List<DataPoint> getRecentHistory(String productId, int n) {
        ConcurrentLinkedDeque<DataPoint> deque = history.get(productId);
        if (deque == null) return List.of();
        List<DataPoint> list = new ArrayList<>(deque);
        int from = Math.max(0, list.size() - n);
        return list.subList(from, list.size());
    }

    public double getAverageBuyPrice(String productId, int n) {
        List<DataPoint> recent = getRecentHistory(productId, n);
        if (recent.isEmpty()) return 0;
        return recent.stream().mapToDouble(dp -> dp.buyPrice).average().orElse(0);
    }

    public double getAverageSellPrice(String productId, int n) {
        List<DataPoint> recent = getRecentHistory(productId, n);
        if (recent.isEmpty()) return 0;
        return recent.stream().mapToDouble(dp -> dp.sellPrice).average().orElse(0);
    }

    public double getPriceVariance(String productId, int n) {
        List<DataPoint> recent = getRecentHistory(productId, n);
        if (recent.size() < 2) return 0;
        double avg = recent.stream().mapToDouble(dp -> dp.sellPrice).average().orElse(0);
        double var = recent.stream().mapToDouble(dp -> (dp.sellPrice - avg) * (dp.sellPrice - avg)).average().orElse(0);
        return Math.sqrt(var);
    }

    public boolean isStable(String productId, int n, double maxVariancePercent) {
        double avg = getAverageSellPrice(productId, n);
        if (avg == 0) return false;
        double stddev = getPriceVariance(productId, n);
        double variancePercent = (stddev / avg) * 100.0;
        return variancePercent <= maxVariancePercent;
    }
}
