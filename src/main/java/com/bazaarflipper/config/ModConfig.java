package com.bazaarflipper.config;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "config/bazaarflipper.json";

    // API Settings
    public String hypixelApiKey = "";
    public long apiRefreshIntervalMs = 10_000; // 10 seconds default bazaar
    public long ahRefreshIntervalMs = 5 * 60 * 1000; // 5 minutes
    public long mayorRefreshIntervalMs = 10 * 60 * 1000; // 10 minutes

    // Flip Settings
    public String flipMode = "ALL"; // ORDER, CRAFT, NPC, AH_CRAFT, ALL
    public double minProfitMarginPercent = 5.0;
    public double maxProfitMarginPercent = 100.0;
    public double minDailyVolume = 100_000;
    public double maxBuyPricePerUnit = 10_000_000;
    public double minProfitPerUnit = 1000;
    public double undercutAmount = 0.1;
    public long undercutCheckIntervalMs = 30_000;
    public int maxRelistCount = 5;

    // Delay Settings
    public long minActionDelayMs = 300;
    public long maxActionDelayMs = 800;
    public boolean naturalMouseMovement = true;

    // Navigation Settings
    public boolean pathfindingEnabled = true;
    public boolean lobbyRestartRecoveryEnabled = true;
    public boolean limboRecoveryEnabled = true;
    public long sessionResumeTimeoutHours = 4;

    // Break Settings (Part 6.4)
    public boolean breaksEnabled = true;
    public long shortBreakMinDuration = 60; // seconds
    public long shortBreakMaxDuration = 240;
    public long longBreakMinDuration = 120;
    public long longBreakMaxDuration = 600;
    public int shortBreakWindowMinutes = 30; // rolling window
    public int shortBreakWindowMinBreakMinutes = 3; // min break within window
    public int longBreakIntervalHours = 2;
    public long orderWaitMinSeconds = 45;
    public long orderWaitMaxSeconds = 180;
    public boolean breakIdleCameraMovement = true;
    public boolean breakIdleShuffleStep = true;

    // Tax Settings (Part 5.3) - Advanced collapsible
    public double bazaarTaxRate = 0.0125; // 1.25% fixed, cookie does NOT affect
    public double ahTaxLowRate = 0.01; // <10M
    public double ahTaxMidRate = 0.02; // 10M-100M
    public double ahTaxHighRate = 0.025; // >100M
    public long ahLowMidThreshold = 10_000_000L;
    public long ahMidHighThreshold = 100_000_000L;
    // Derpy multiplier researched from wiki: quadruples taxes (4x). Source: Hypixel forum thread 5739552 "Derpy's 4x taxes are ridiculous", NamuWiki, Coflnet guide.
    // TODO: Verify exact Derpy multiplier from current Hypixel SkyBlock wiki if changed after Better Mayors update. Default 4x conservative based on multiple sources.
    public double derpyAHTaxMultiplier = 4.0;
    public long derpyTaxAppliesAbove = 1_000_000L;

    // Discord Settings
    public String discordMode = "DISABLED"; // DISABLED, WEBHOOK, BOT
    public String webhookUrl = "";
    public String botToken = "";
    public String commandChannelId = "";
    public boolean notifyOnEveryFlip = false;
    public double notifyFlipProfitThreshold = 100_000;
    public boolean hourlySummaryEnabled = true;
    public long hourlySummaryIntervalMinutes = 60;
    public boolean notifyLongBreaks = true;
    public boolean notifyDerpyChanges = true;

    // HUD
    public boolean hudEnabled = true;
    public boolean hudCollapsed = false;
    public int hudX = 10;
    public int hudY = 10;

    // Session resume
    public int reconnectMaxAttempts = 10;

    public static ModConfig load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
                if (cfg != null) return cfg;
            } catch (Exception e) {
                Logger.error("Failed to load config, using defaults", e);
            }
        }
        ModConfig cfg = new ModConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            File file = new File(CONFIG_FILE);
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Logger.error("Failed to save config", e);
        }
    }

    public void validate() {
        if (shortBreakMinDuration < 10) shortBreakMinDuration = 10;
        if (shortBreakMaxDuration > 1200) shortBreakMaxDuration = 1200;
        if (longBreakMinDuration < 30) longBreakMinDuration = 30;
        if (longBreakMaxDuration > 1800) longBreakMaxDuration = 1800;
        if (shortBreakWindowMinutes < 15) shortBreakWindowMinutes = 15;
        if (shortBreakWindowMinutes > 60) shortBreakWindowMinutes = 60;
        if (shortBreakWindowMinBreakMinutes < 1) shortBreakWindowMinBreakMinutes = 1;
        if (longBreakIntervalHours < 1) longBreakIntervalHours = 1;
        if (longBreakIntervalHours > 4) longBreakIntervalHours = 4;
        if (bazaarTaxRate < 0 || bazaarTaxRate > 0.5) bazaarTaxRate = 0.0125;
        if (ahTaxLowRate < 0 || ahTaxLowRate > 0.5) ahTaxLowRate = 0.01;
        if (ahTaxMidRate < 0 || ahTaxMidRate > 0.5) ahTaxMidRate = 0.02;
        if (ahTaxHighRate < 0 || ahTaxHighRate > 0.5) ahTaxHighRate = 0.025;
    }
}
