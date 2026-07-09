package com.bazaarflipper.tracker;

public class SessionStats {
    public long sessionStartTime;
    public long totalDurationMs;
    public double totalProfit;
    public int totalFlipsCompleted;
    public double coinsPerHour;
    public double roiPercent;
    public String topItem;
    public double topItemProfit;

    public SessionStats() {
        this.sessionStartTime = System.currentTimeMillis();
    }

    public void update(double profit, int flips) {
        this.totalProfit = profit;
        this.totalFlipsCompleted = flips;
        this.totalDurationMs = System.currentTimeMillis() - sessionStartTime;
        if (totalDurationMs > 0) {
            double hours = totalDurationMs / 3600000.0;
            this.coinsPerHour = hours > 0 ? totalProfit / hours : 0;
        }
    }
}
