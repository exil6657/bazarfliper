package com.bazaarflipper.config;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlayerCapabilityConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "config/bazaarflipper_player.json";

    public enum HypixelRank {
        NONE, VIP, VIP_PLUS, MVP, MVP_PLUS, MVP_PLUS_PLUS
    }

    public HypixelRank hypixelRank = HypixelRank.NONE;
    public boolean hasQuickCraft = false; // true if VIP or above
    public long activeCookieExpiry = 0; // unix timestamp
    public boolean hasCookieActive = false; // derived
    public boolean canUseSlashBZ = false;
    public boolean canUseSlashAH = false;
    public Set<String> unlockedCollections = new HashSet<>();
    public Map<String, Integer> skillLevels = new HashMap<>();
    public Set<String> unlockedRecipes = new HashSet<>();

    public static PlayerCapabilityConfig load() {
        File f = new File(FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                PlayerCapabilityConfig c = GSON.fromJson(r, PlayerCapabilityConfig.class);
                if (c != null) {
                    c.recalcDerived();
                    return c;
                }
            } catch (Exception e) {
                Logger.error("Failed to load player capability config", e);
            }
        }
        PlayerCapabilityConfig cfg = new PlayerCapabilityConfig();
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
            Logger.error("Failed to save player capability config", e);
        }
    }

    public void recalcDerived() {
        hasQuickCraft = hypixelRank != HypixelRank.NONE;
        hasCookieActive = activeCookieExpiry > System.currentTimeMillis() / 1000L;
        canUseSlashBZ = hasCookieActive;
        canUseSlashAH = hasCookieActive;
    }

    public boolean canUseCommand(String command) {
        if (command.equalsIgnoreCase("bz") || command.equalsIgnoreCase("ah")) {
            return hasCookieActive;
        }
        if (command.equalsIgnoreCase("craft")) return true;
        return true;
    }
}
