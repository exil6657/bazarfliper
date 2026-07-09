package com.bazaarflipper.config;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * NPC Configuration persisted to config/bazaarflipper_npc.json
 * Ensures config saved even when restart game - save() called on every change + on game close
 * User can set coordinates themselves via NPC Config tab "Set to Current Pos" button
 * Coordinates sourced from official Hypixel SkyBlock Wiki (hypixelskyblock.minecraft.wiki & wiki.hypixel.net)
 * Credits: Cldz
 */
public class NPCConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "config/bazaarflipper_npc.json";

    public static class WaypointData {
        public String name;
        public double x, y, z;
        public String npcDisplayName;
        public boolean enabled = true;
        public String source = ""; // wiki source

        public WaypointData() {}

        public WaypointData(String name, double x, double y, double z, String npcName, String source) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.npcDisplayName = npcName;
            this.enabled = true;
            this.source = source;
        }

        public WaypointData(String name, double x, double y, double z, String npcName) {
            this(name, x, y, z, npcName, "Official Wiki");
        }
    }

    public int selectedNPCSlot = 1; // 1,2,3
    public WaypointData npcWaypoint1;
    public WaypointData npcWaypoint2;
    public WaypointData npcWaypoint3;
    public boolean autoSelectNearestNPC = true;

    public NPCConfig() {
        // Default Hub NPCs - researched from official Hypixel Skyblock wiki with redesign notes
        // All coordinates include wiki source and are user-overridable via Set to Current Pos feature
        // Official sources (May 2026 research):
        // - Builder: Builder's House -8.5,71,-61.5 per https://hypixelskyblock.minecraft.wiki/w/NPC/List/Hub (new after redesign), old -48,70,-34 per 2020 guide
        // - Farm Merchant: Farm 63.5,72,-113.5 per history moved Arthur Jan 30 2026, old 16,70,-70 per 2020 guide and 16.5,70,-73.5 placeholder
        // - Lumber Merchant: Village -49.5,70,-67.5 per wiki.hypixel.net/Lumber_Merchant, Foraging Camp -125,73,-42.5 alternate
        // User can set coords themselves via NPC Config tab - feature added per user request
        npcWaypoint1 = new WaypointData("npc_sell_builder", -8.5, 71, -61.5, "Builder", "hypixelskyblock.minecraft.wiki/w/NPC/List/Hub Builder's House -8.5,71,-61.5 (new), old -48,70,-34");
        npcWaypoint2 = new WaypointData("npc_sell_farm", 63.5, 72, -113.5, "Farm Merchant", "Official wiki Farm Merchant 63.5,72,-113.5 post-redesign Jan30 2026, old 16,70,-70");
        npcWaypoint3 = new WaypointData("npc_sell_lumber", -49.5, 70, -67.5, "Lumber Merchant", "wiki.hypixel.net/Lumber_Merchant Village -49.5,70,-67.5, Foraging -125,73,-42.5");
    }

    public static NPCConfig load() {
        File f = new File(FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                NPCConfig c = GSON.fromJson(r, NPCConfig.class);
                if (c != null) {
                    Logger.info("NPCConfig loaded from " + FILE + " - custom coordinates preserved across restarts, credits Cldz");
                    return c;
                }
            } catch (Exception e) {
                Logger.error("Failed to load NPC config, using defaults with wiki coords", e);
            }
        }
        NPCConfig cfg = new NPCConfig();
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
            Logger.info("NPCConfig saved to " + FILE + " - persists across game restarts, credits Cldz");
        } catch (Exception e) {
            Logger.error("Failed to save NPC config", e);
        }
    }

    public WaypointData getSelectedWaypoint() {
        return switch (selectedNPCSlot) {
            case 2 -> npcWaypoint2;
            case 3 -> npcWaypoint3;
            default -> npcWaypoint1;
        };
    }

    // Explicit save all for game close
    public void saveAll() {
        save();
    }
}
