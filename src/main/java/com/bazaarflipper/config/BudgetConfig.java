package com.bazaarflipper.config;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class BudgetConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "config/bazaarflipper_budget.json";

    public double totalBudgetCap = 100_000_000; // 100M default
    public double reservedBalance = 10_000_000; // keep 10M
    public double maxInvestmentPerItem = 20_000_000;
    public int maxConcurrentItems = 10; // 1-28
    public boolean autoAdjustToBalance = true;

    public static BudgetConfig load() {
        File f = new File(FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                BudgetConfig c = GSON.fromJson(r, BudgetConfig.class);
                if (c != null) {
                    c.validate();
                    return c;
                }
            } catch (Exception e) {
                Logger.error("Failed to load budget config", e);
            }
        }
        BudgetConfig cfg = new BudgetConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            File f = new File(FILE);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            Logger.error("Failed to save budget config", e);
        }
    }

    public void validate() {
        if (totalBudgetCap <= 0) totalBudgetCap = 10_000_000;
        if (reservedBalance < 0) reservedBalance = 0;
        if (reservedBalance > totalBudgetCap * 0.9) reservedBalance = totalBudgetCap * 0.9;
        if (maxInvestmentPerItem <= 0) maxInvestmentPerItem = totalBudgetCap;
        if (maxInvestmentPerItem > totalBudgetCap) maxInvestmentPerItem = totalBudgetCap;
        if (maxConcurrentItems < 1) maxConcurrentItems = 1;
        if (maxConcurrentItems > 28) maxConcurrentItems = 28;
    }
}
