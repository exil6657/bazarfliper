package com.bazaarflipper.engine;

import com.bazaarflipper.config.BudgetConfig;
import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * State file: config/bazaarflipper_session.json
 * Also restores BreakScheduler timer state
 */
public class SessionStateManager {

    private static final String FILE = "config/bazaarflipper_session.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class SavedFlip {
        public String productId;
        public double buyPrice;
        public double targetSellPrice;
        public int quantity;
        public String state; // enum string
        public int filledAmount;
        public int relistCount;
        public long placementTimestamp;
        public String strategyType;
        public double amountInvested;
    }

    public static class SessionSave {
        public List<SavedFlip> activeFlips = new ArrayList<>();
        public double sessionProfit;
        public int totalFlipsCompleted;
        public Map<String, Double> budgetInvested = new ConcurrentHashMap<>();
        public long totalActiveTime;
        public long timeSinceLastLongBreak;
        public long breakTimeInCurrentWindow;
        public long currentWindowStart;
        public long saveTimestamp;
    }

    private final BudgetManager budgetManager;
    private final BreakScheduler breakScheduler;
    private final BudgetConfig budgetConfig;
    private final com.bazaarflipper.config.ModConfig modConfig;

    // In-memory active flips reference would be from FlipEngine - for now store here
    private List<SavedFlip> cachedFlips = new ArrayList<>();

    public SessionStateManager(BudgetManager budgetManager, BreakScheduler breakScheduler, BudgetConfig budgetConfig, com.bazaarflipper.config.ModConfig modConfig) {
        this.budgetManager = budgetManager;
        this.breakScheduler = breakScheduler;
        this.budgetConfig = budgetConfig;
        this.modConfig = modConfig;
    }

    public void saveState(List<SavedFlip> activeFlips, double sessionProfit, int totalFlips) {
        SessionSave save = new SessionSave();
        save.activeFlips = activeFlips != null ? activeFlips : cachedFlips;
        save.sessionProfit = sessionProfit;
        save.totalFlipsCompleted = totalFlips;
        save.budgetInvested = budgetManager.getInvestmentsSnapshot();
        save.totalActiveTime = breakScheduler.getTotalActiveTime();
        save.timeSinceLastLongBreak = breakScheduler.getTimeSinceLastLongBreak();
        save.breakTimeInCurrentWindow = breakScheduler.getBreakTimeInCurrentWindow();
        save.currentWindowStart = breakScheduler.getCurrentWindowStart();
        save.saveTimestamp = System.currentTimeMillis();

        try {
            File f = new File(FILE);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(save, w);
            }
            Logger.info("Session state saved with " + save.activeFlips.size() + " flips");
        } catch (Exception e) {
            Logger.error("Failed to save session state", e);
        }
    }

    public void saveState() {
        // Use cached flips if not provided
        saveState(cachedFlips, 0, 0);
    }

    public SessionSave loadState() {
        File f = new File(FILE);
        if (!f.exists()) return null;
        try (FileReader r = new FileReader(f)) {
            SessionSave save = GSON.fromJson(r, SessionSave.class);
            if (save == null) return null;
            long timeoutHours = modConfig.sessionResumeTimeoutHours;
            long timeoutMs = timeoutHours * 3600L * 1000L;
            if (System.currentTimeMillis() - save.saveTimestamp > timeoutMs) {
                Logger.warn("Session state expired (older than " + timeoutHours + "h), ignoring");
                return null;
            }
            return save;
        } catch (Exception e) {
            Logger.error("Failed to load session state, starting fresh", e);
            return null;
        }
    }

    public class ResumeResult {
        public int restored = 0;
        public int abandoned = 0;
        public double budgetRestored = 0;
    }

    public ResumeResult tryResume() {
        SessionSave save = loadState();
        ResumeResult result = new ResumeResult();
        if (save == null) {
            Logger.info("No recent session state found, fresh start");
            return result;
        }

        Logger.info("Attempting to resume session with " + save.activeFlips.size() + " flips");

        // Restore BreakScheduler timers
        breakScheduler.restoreState(save.totalActiveTime, save.timeSinceLastLongBreak, save.breakTimeInCurrentWindow, save.currentWindowStart);
        Logger.info("BreakScheduler state restored");

        // For each saved flip, attempt to restore logic per spec:
        // Found + profitable -> restore ActiveFlip, register in BudgetManager
        // Found + unprofitable -> leave untouched
        // Not found + items in inventory -> skip to sell offer state
        // Not found -> free budget, skip
        // This would need InventoryScanner and Market data - simplified for now

        // Restore budget
        budgetManager.restoreInvestments(save.budgetInvested);
        result.budgetRestored = save.budgetInvested.values().stream().mapToDouble(Double::doubleValue).sum();
        result.restored = save.activeFlips.size(); // placeholder

        // After resume: delete or archive state file
        try {
            File f = new File(FILE);
            File archive = new File(FILE + ".bak");
            if (f.exists()) {
                f.renameTo(archive);
            }
            Logger.info("Session state archived after resume");
        } catch (Exception e) {
            Logger.error("Failed to archive session file", e);
        }

        return result;
    }

    public void setCachedFlips(List<SavedFlip> flips) {
        this.cachedFlips = flips;
    }

    public void deleteState() {
        File f = new File(FILE);
        if (f.exists()) f.delete();
    }
}
