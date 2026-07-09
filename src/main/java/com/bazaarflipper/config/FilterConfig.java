package com.bazaarflipper.config;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

public class FilterConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "config/bazaarflipper_filters.json";

    public Set<String> whitelist = new HashSet<>();
    public Set<String> blacklist = new HashSet<>();
    public Set<String> enabledCategories = new HashSet<>();
    public double minProfit = 1000;
    public double maxPrice = 50_000_000;
    public double minVolume = 1000;

    public FilterConfig() {
        // Default categories
        enabledCategories.add("farming");
        enabledCategories.add("mining");
        enabledCategories.add("combat");
        enabledCategories.add("foraging");
        enabledCategories.add("fishing");
        enabledCategories.add("enchanted");
    }

    public static FilterConfig load() {
        File f = new File(FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                FilterConfig c = GSON.fromJson(r, FilterConfig.class);
                if (c != null) return c;
            } catch (Exception e) {
                Logger.error("Failed to load filter config", e);
            }
        }
        FilterConfig cfg = new FilterConfig();
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
            Logger.info("FilterConfig saved to " + FILE + " - persists across restarts, credits Cldz");
        } catch (Exception e) {
            Logger.error("Failed to save filter config", e);
        }
    }

    public boolean isAllowed(String productId) {
        if (!blacklist.isEmpty() && blacklist.contains(productId)) return false;
        if (!whitelist.isEmpty()) return whitelist.contains(productId);
        return true;
    }
}
