package com.bazaarflipper.pathfinding;

import com.bazaarflipper.config.NPCConfig;
import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per waypoint: X,Y,Z, island identifier, name, arrival radius, category tag
 * Default waypoints researched from Hypixel Skyblock wiki - best-known coords.
 */
public class WaypointRegistry {
    private static final String CUSTOM_FILE = "config/bazaarflipper_waypoints.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Waypoint {
        public double x, y, z;
        public String island; // e.g. "hub"
        public String name;
        public double arrivalRadius = 2.0;
        public String category; // bazaar, bank, auction, spawn, lobby, npc_sell, crafting

        public Waypoint() {}

        public Waypoint(String name, double x, double y, double z, String island, double radius, String category) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.island = island;
            this.arrivalRadius = radius;
            this.category = category;
        }
    }

    private final Map<String, Waypoint> waypoints = new ConcurrentHashMap<>();

    public WaypointRegistry(NPCConfig npcConfig) {
        loadDefaults(npcConfig);
        loadCustom();
    }

    private void loadDefaults(NPCConfig npcConfig) {
        // Default waypoints from Hypixel wiki - need verification, coordinates best-known
        // Bazaar NPC in Hub
        waypoints.put("bazaar_npc", new Waypoint("bazaar_npc", -34.5, 70, -100.5, "hub", 3.0, "bazaar"));
        // Bank NPC
        waypoints.put("bank_npc", new Waypoint("bank_npc", -25.5, 71, -41.5, "hub", 3.0, "bank"));
        // Auction House NPC
        waypoints.put("auction_house_npc", new Waypoint("auction_house_npc", -30.5, 72, -78.5, "hub", 3.0, "auction"));
        // Hub spawn
        waypoints.put("hub_spawn", new Waypoint("hub_spawn", -2.5, 70, -69.5, "hub", 5.0, "spawn"));
        // Hypixel lobby Skyblock NPC
        waypoints.put("hypixel_lobby_skyblock_npc", new Waypoint("hypixel_lobby_skyblock_npc", 0.5, 70, 70.5, "hypixel_lobby", 3.0, "lobby"));
        // Crafting table locations (hub)
        waypoints.put("crafting_table_hub", new Waypoint("crafting_table_hub", -10.5, 70, -70.5, "hub", 2.0, "crafting"));

        // NPC sell waypoints from NPCConfig
        if (npcConfig.npcWaypoint1 != null) {
            NPCConfig.WaypointData wd = npcConfig.npcWaypoint1;
            waypoints.put(wd.name, new Waypoint(wd.name, wd.x, wd.y, wd.z, "hub", 2.0, "npc_sell"));
        }
        if (npcConfig.npcWaypoint2 != null) {
            NPCConfig.WaypointData wd = npcConfig.npcWaypoint2;
            waypoints.put(wd.name, new Waypoint(wd.name, wd.x, wd.y, wd.z, "hub", 2.0, "npc_sell"));
        }
        if (npcConfig.npcWaypoint3 != null) {
            NPCConfig.WaypointData wd = npcConfig.npcWaypoint3;
            waypoints.put(wd.name, new Waypoint(wd.name, wd.x, wd.y, wd.z, "hub", 2.0, "npc_sell"));
        }

        Logger.info("Loaded " + waypoints.size() + " default waypoints (verify coords from wiki)");
    }

    private void loadCustom() {
        File f = new File(CUSTOM_FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                Map<String, Waypoint> map = GSON.fromJson(r, new com.google.gson.reflect.TypeToken<Map<String, Waypoint>>(){}.getType());
                if (map != null) waypoints.putAll(map);
            } catch (Exception e) {
                Logger.error("Failed to load custom waypoints", e);
            }
        }
    }

    public void saveCustomWaypoints() {
        try {
            File f = new File(CUSTOM_FILE);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(waypoints, w);
            }
        } catch (Exception e) {
            Logger.error("Failed to save custom waypoints", e);
        }
    }

    public Waypoint getWaypoint(String name) {
        return waypoints.get(name);
    }

    public Waypoint getNearestWaypoint(String category, net.minecraft.util.math.Vec3d pos) {
        Waypoint nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (Waypoint wp : waypoints.values()) {
            if (category != null && !category.equals(wp.category)) continue;
            double dx = wp.x - pos.x;
            double dy = wp.y - pos.y;
            double dz = wp.z - pos.z;
            double dist = dx*dx + dy*dy + dz*dz;
            if (dist < bestDist) {
                bestDist = dist;
                nearest = wp;
            }
        }
        return nearest;
    }

    public boolean isPlayerAtWaypoint(String name, net.minecraft.util.math.Vec3d pos) {
        Waypoint wp = getWaypoint(name);
        if (wp == null) return false;
        double dx = wp.x - pos.x;
        double dy = wp.y - pos.y;
        double dz = wp.z - pos.z;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        return dist <= wp.arrivalRadius;
    }

    public void registerWaypoint(Waypoint wp) {
        waypoints.put(wp.name, wp);
        saveCustomWaypoints();
    }
}
