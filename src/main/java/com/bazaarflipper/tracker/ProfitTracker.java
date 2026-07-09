package com.bazaarflipper.tracker;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ProfitTracker {
    private static final String HISTORY_FILE = "config/bazaarflipper_history.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ConcurrentLinkedDeque<FlipRecord> history = new ConcurrentLinkedDeque<>();
    private volatile double sessionProfit = 0;
    private volatile int sessionFlips = 0;
    private volatile long sessionStartTime = 0;
    private volatile String mostProfitableItem = "None";
    private volatile double mostProfitableProfit = 0;

    public ProfitTracker() {
        loadHistory();
    }

    private void loadHistory() {
        File f = new File(HISTORY_FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                FlipRecord[] records = GSON.fromJson(r, FlipRecord[].class);
                if (records != null) {
                    for (FlipRecord rec : records) history.add(rec);
                }
                Logger.info("Loaded " + history.size() + " historical flips");
            } catch (Exception e) {
                Logger.error("Failed to load history", e);
            }
        }
    }

    private void saveHistory() {
        try {
            File f = new File(HISTORY_FILE);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(history, w);
            }
        } catch (Exception e) {
            Logger.error("Failed to save history", e);
        }
    }

    public void startSession() {
        sessionStartTime = System.currentTimeMillis();
        sessionProfit = 0;
        sessionFlips = 0;
        mostProfitableItem = "None";
        mostProfitableProfit = 0;
        Logger.info("ProfitTracker session started");
    }

    public void recordFlip(FlipRecord record) {
        history.addLast(record);
        sessionProfit += record.profit;
        sessionFlips++;
        if (record.profit > mostProfitableProfit) {
            mostProfitableProfit = record.profit;
            mostProfitableItem = record.productId;
        }
        saveHistory();
        Logger.info("Flip recorded: " + record.productId + " profit " + record.profit + " tax " + record.taxPaid + " (" + record.taxType + " " + (record.taxRate*100) + "%)");
    }

    public double getSessionProfit() { return sessionProfit; }
    public int getSessionFlips() { return sessionFlips; }
    public long getSessionStartTime() { return sessionStartTime; }
    public String getMostProfitableItem() { return mostProfitableItem; }
    public double getMostProfitableProfit() { return mostProfitableProfit; }

    public List<FlipRecord> getHistory() { return new ArrayList<>(history); }

    public SessionStats getSessionStats() {
        SessionStats stats = new SessionStats();
        stats.sessionStartTime = sessionStartTime;
        stats.totalProfit = sessionProfit;
        stats.totalFlipsCompleted = sessionFlips;
        stats.topItem = mostProfitableItem;
        stats.topItemProfit = mostProfitableProfit;
        stats.update(sessionProfit, sessionFlips);
        return stats;
    }

    public double getTotalProfit() {
        return history.stream().mapToDouble(r -> r.profit).sum();
    }
}
