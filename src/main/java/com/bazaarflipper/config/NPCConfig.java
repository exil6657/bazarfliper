package com.bazaarflipper.config;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class NPCConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "config/bazaarflipper_npc.json";

    public static class WaypointData {
        public String name;
        public double x, y, z;
        public String npcDisplayName;
        public boolean enabled = true;

        public WaypointData() {}

        public WaypointData(String name, double x, double y, double z, String npcName) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.npcDisplayName = npcName;
            this.enabled = true;
        }
    }

    public int selectedNPCSlot = 1; // 1,2,3
    public WaypointData npcWaypoint1;
    public WaypointData npcWaypoint2;
    public WaypointData npcWaypoint3;
    public boolean autoSelectNearestNPC = true;

    public NPCConfig() {
        // Default Hub NPCs - researched from Hypixel Skyblock wiki - coordinates are best-known defaults, user-overridable
        // Builder NPC near hub spawn, Farmer etc.
        // TODO: Verify coordinates in-game as Hypixel updates may drift.
        npcWaypoint1 = new WaypointData("npc_sell_builder", -4.5, 70, -86.5, "Builder");
        npcWaypoint2 = new WaypointData("npc_sell_farm", 16.5, 70, -73.5, "Farm Merchant");
        npcWaypoint3 = new WaypointData("npc_sell_lumber", -23.5, 70, -15.5, "Lumber Merchant");
    }

    public static NPCConfig load() {
        File f = new File(FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                NPCConfig c = GSON.fromJson(r, NPCConfig.class);
                if (c != null) return c;
            } catch (Exception e) {
                Logger.error("Failed to load NPC config", e);
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
}
