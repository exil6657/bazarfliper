package com.bazaarflipper.tracker;

import com.bazaarflipper.util.Logger;

import java.util.List;
import java.util.stream.Collectors;

public class HistoryManager {
    private final ProfitTracker profitTracker;

    public HistoryManager(ProfitTracker tracker) {
        this.profitTracker = tracker;
    }

    public List<FlipRecord> getRecentFlips(int count) {
        List<FlipRecord> all = profitTracker.getHistory();
        int from = Math.max(0, all.size() - count);
        return all.subList(from, all.size());
    }

    public String exportToClipboardFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("Item,Strategy,Bought,Sold,Profit,Margin%,Tax,Duration,Timestamp\n");
        for (FlipRecord rec : profitTracker.getHistory()) {
            sb.append(String.format("%s,%s,%.0f,%.0f,%.0f,%.2f%%,%.2f%% (%s),%d,%d\n",
                    rec.productId, rec.strategyType, rec.buyPrice, rec.sellPrice, rec.profit,
                    rec.marginPercent, rec.taxRate*100, rec.taxType, rec.durationMs, rec.timestamp));
        }
        return sb.toString();
    }

    public double getRunningTotal() {
        return profitTracker.getTotalProfit();
    }

    public void logStats() {
        Logger.info("Total flips: " + profitTracker.getHistory().size() + " total profit: " + profitTracker.getTotalProfit());
    }
}
