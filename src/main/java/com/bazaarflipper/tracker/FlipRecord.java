package com.bazaarflipper.tracker;

public class FlipRecord {
    public String productId;
    public String strategyType; // ORDER, CRAFT, NPC, AH_CRAFT
    public double buyPrice;
    public double sellPrice;
    public int quantity;
    public double profit;
    public double marginPercent;
    public double taxPaid;
    public String taxType; // BAZAAR, AH
    public double taxRate;
    public long durationMs;
    public long timestamp;
    public String status; // COMPLETED, FAILED

    public FlipRecord() {
        this.timestamp = System.currentTimeMillis();
    }

    public FlipRecord(String productId, String strategy, double buy, double sell, int qty, double profit, double taxPaid, String taxType, double taxRate) {
        this.productId = productId;
        this.strategyType = strategy;
        this.buyPrice = buy;
        this.sellPrice = sell;
        this.quantity = qty;
        this.profit = profit;
        this.taxPaid = taxPaid;
        this.taxType = taxType;
        this.taxRate = taxRate;
        this.timestamp = System.currentTimeMillis();
        this.marginPercent = buy != 0 ? (profit / (buy*qty)) * 100 : 0;
    }
}
