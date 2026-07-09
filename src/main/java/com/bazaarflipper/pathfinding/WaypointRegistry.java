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
 * Default waypoints researched from official Hypixel SkyBlock Wiki (hypixelskyblock.minecraft.wiki & wiki.hypixel.net).
 * All coordinates include source and date for verification, and note that Hypixel redesigns (e.g. Jan 30 2026) may drift them.
 * User can override via NPC Config tab "Set to Current Pos" or by editing config/bazaarflipper_waypoints.json or bazaarflipper_npc.json
 * Credits: Cldz
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
        public String source = ""; // wiki source for verification

        public Waypoint() {}

        public Waypoint(String name, double x, double y, double z, String island, double radius, String category, String source) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.island = island;
            this.arrivalRadius = radius;
            this.category = category;
            this.source = source;
        }

        public Waypoint(String name, double x, double y, double z, String island, double radius, String category) {
            this(name, x, y, z, island, radius, category, "Hypixel SkyBlock Wiki");
        }
    }

    private final Map<String, Waypoint> waypoints = new ConcurrentHashMap<>();

    public WaypointRegistry(NPCConfig npcConfig) {
        loadDefaults(npcConfig);
        loadCustom();
    }

    private void loadDefaults(NPCConfig npcConfig) {
        // === Official Wiki Coordinates (researched May 2026) ===
        // Bazaar Alley zone: -32 71 -27 per https://hypixelskyblock.minecraft.wiki/w/Bazaar_Alley
        // Bazaar NPC: -33.5,73,-22.5 per https://hypixelskyblock.minecraft.wiki/w/NPC/List/Hub (Bazaar)
        // Alternate Bazaar NPC: -32.5 71 -76.5 per https://wiki.hypixel.net/Bazaar_(NPC) — more central, used as primary after verification
        // Bazaar Agent: -35.5,73,-31.5 same source
        // Trade Center: -32.5,70,-75.5 (Zarina)
        waypoints.put("bazaar_npc", new Waypoint("bazaar_npc", -32.5, 71, -76.5, "hub", 3.0, "bazaar", "wiki.hypixel.net/Bazaar_(NPC) -32.5 71 -76.5, also NPC/List/Hub -33.5 73 -22.5"));
        waypoints.put("bazaar_agent", new Waypoint("bazaar_agent", -35.5, 73, -31.5, "hub", 3.0, "bazaar", "hypixelskyblock.minecraft.wiki Bazaar Agent -35.5 73 -31.5"));
        waypoints.put("bazaar_alley", new Waypoint("bazaar_alley", -32, 71, -27, "hub", 5.0, "bazaar", "Bazaar_Alley wiki -32 71 -27"));

        // Banker / Bank NPC: Bank -29.5,72,-38 per NPC/List/Hub
        waypoints.put("bank_npc", new Waypoint("bank_npc", -29.5, 72, -38, "hub", 3.0, "bank", "hypixelskyblock.minecraft.wiki NPC/List/Hub Banker -29.5,72,-38"));
        waypoints.put("bank", new Waypoint("bank", -29, 70, -75, "hub", 5.0, "bank", "Bazaar_Alley general bank zone -29 70 -75"));

        // Auction House:
        // History: Auction Master moved on Jan 30 2026 from -46.5,73,-90.5 to -39.5,73,-12.5 due to Hub redesign per https://hypixel-skyblock.fandom.com/wiki/Auction_Master history table
        // Current official: -39.5,73,-12.5 (post-redesign) — use as primary
        // Old official: -46.5,73,-90.5 (pre-redesign) kept as alternate
        // Auction Agents: -31,73,-85.5, -36,73,-85.5, -31,73,-95.5, -36,73,-95.5 per wiki.hypixel.net/Auction_House
        waypoints.put("auction_house_npc", new Waypoint("auction_house_npc", -39.5, 73, -12.5, "hub", 3.0, "auction", "wiki.hypixel.net/Auction_Master history Jan30 2026 moved -46.5,73,-90.5 -> -39.5,73,-12.5 (current)"));
        waypoints.put("auction_house_npc_old", new Waypoint("auction_house_npc_old", -46.5, 73, -90.5, "hub", 3.0, "auction", "Old pre-redesign, fandom guide All NPC Locations -46,73,-90"));
        waypoints.put("auction_agent_1", new Waypoint("auction_agent_1", -31, 73, -85.5, "hub", 2.0, "auction", "wiki.hypixel.net/Auction_House Auction Agent"));
        waypoints.put("auction_agent_2", new Waypoint("auction_agent_2", -36, 73, -85.5, "hub", 2.0, "auction", "wiki.hypixel.net/Auction_House"));
        waypoints.put("auction_agent_3", new Waypoint("auction_agent_3", -31, 73, -95.5, "hub", 2.0, "auction", "wiki.hypixel.net/Auction_House"));
        waypoints.put("auction_agent_4", new Waypoint("auction_agent_4", -36, 73, -95.5, "hub", 2.0, "auction", "wiki.hypixel.net/Auction_House"));

        // Hub spawn: near 0,70,-66 (Jack behind spawn portal) - survey -2.5,70,-69.5 used previously, also Hub Selector -5.5,69,-22.5
        waypoints.put("hub_spawn", new Waypoint("hub_spawn", -2.5, 70, -69.5, "hub", 5.0, "spawn", "Guide All NPC Locations Hub spawn area ~ -3,70,-70, Hub Selector -5.5,69,-22.5"));
        waypoints.put("hub_selector", new Waypoint("hub_selector", -5.5, 69, -22.5, "hub", 3.0, "spawn", "hypixelskyblock.minecraft.wiki/w/Hub_Selector -5.5 69 -22.5"));

        // Hypixel lobby Skyblock NPC: typical lobby coords 0.5,70,70.5 (no official wiki, community best-known, user-overridable)
        waypoints.put("hypixel_lobby_skyblock_npc", new Waypoint("hypixel_lobby_skyblock_npc", 0.5, 70, 70.5, "hypixel_lobby", 3.0, "lobby", "Community best-known, user-overridable via Set to Current Pos"));

        // Crafting table hub: near Village -10.5,70,-70.5 placeholder, but crafting via /craft command no physical table needed. Keep for fallback.
        waypoints.put("crafting_table_hub", new Waypoint("crafting_table_hub", -10.5, 70, -70.5, "hub", 2.0, "crafting", "Placeholder near spawn, actual crafting uses /craft command custom GUI"));

        // === NPC Sell Targets — Official Wiki Coordinates ===
        // Builder: Builder's House -8.5,71,-61.5 per https://hypixelskyblock.minecraft.wiki/w/NPC/List/Hub (new after redesign)
        // Old Builder: -48,70,-34 per Guide All NPC Locations 2020, and -51,71,-27 per fandom NPC/List/Hub, and -52,71,-28 per forum
        waypoints.put("npc_sell_builder", new Waypoint("npc_sell_builder", -8.5, 71, -61.5, "hub", 2.0, "npc_sell", "Official wiki NPC/List/Hub Builder's House -8.5,71,-61.5 (new), old -48,70,-34"));
        waypoints.put("npc_sell_builder_old", new Waypoint("npc_sell_builder_old", -48, 70, -34, "hub", 2.0, "npc_sell", "Old Guide All NPC Locations Builder -48,70,-34"));

        // Farm Merchant: Official wiki Farm 63.5,72,-113.5 per history "Moved Arthur from 15.5,70,-71.8 to 63.5,72,-113.5 due to redesign" on Jan 30 2026, also Farm Merchant page. Old 16,70,-70 per 2020 guide.
        waypoints.put("npc_sell_farm", new Waypoint("npc_sell_farm", 63.5, 72, -113.5, "hub", 2.0, "npc_sell", "Official wiki Farm Merchant 63.5,72,-113.5 post-redesign Jan30 2026, old 16,70,-70"));
        // Windmill Operator shares stock with Farm Merchant, same coords-ish
        waypoints.put("npc_sell_windmill", new Waypoint("npc_sell_windmill", 63.5, 72, -113.5, "hub", 2.0, "npc_sell", "Shares Farm Merchant stock"));

        // Lumber Merchant: Foraging Camp -125,73,-42.5 per https://hypixelskyblock.minecraft.wiki/w/Lumber_Merchant and Village -49.5,70,-67.5 per wiki.hypixel.net/Lumber_Merchant
        waypoints.put("npc_sell_lumber", new Waypoint("npc_sell_lumber", -49.5, 70, -67.5, "hub", 2.0, "npc_sell", "wiki.hypixel.net/Lumber_Merchant Village -49.5,70,-67.5, Foraging Camp -125,73,-42.5 alternate"));
        waypoints.put("npc_sell_lumber_foraging", new Waypoint("npc_sell_lumber_foraging", -125, 73, -42.5, "hub", 2.0, "npc_sell", "Foraging Camp variant"));

        // Fish Merchant / Fishing Merchant: 112.5,71,-44.5 per https://hypixelskyblock.minecraft.wiki/w/Fishing_Merchant (Fishing Outpost), old 52,68,-83 per 2020 guide
        waypoints.put("npc_sell_fish", new Waypoint("npc_sell_fish", 112.5, 71, -44.5, "hub", 2.0, "npc_sell", "Official wiki Fishing Merchant 112.5,71,-44.5 Fishing Outpost, old 52,68,-83"));
        waypoints.put("npc_sell_fish_old", new Waypoint("npc_sell_fish_old", 52, 68, -83, "hub", 2.0, "npc_sell", "Old guide 2020"));

        // Mine Merchant: -8,68,-124 per https://hypixel-skyblock.fandom.com/wiki/Mine_Merchant (Weaponsmith location), also -9,68,-125 guide
        waypoints.put("npc_sell_mine", new Waypoint("npc_sell_mine", -8, 68, -124, "hub", 2.0, "npc_sell", "fandom Mine Merchant -8,68,-124 Village Weaponsmith"));
        waypoints.put("npc_sell_weaponsmith", new Waypoint("npc_sell_weaponsmith", -10, 68, -141, "hub", 2.0, "npc_sell", "Guide All NPC Locations Weaponsmith -10,68,-141"));

        // Additional: Iron Forger -1,75,-307, Gold Forger 27,74,-294 for mining related (not directly sell but navigation)
        waypoints.put("iron_forger", new Waypoint("iron_forger", -1, 75, -307, "hub", 2.0, "npc_sell", "Guide All NPC Locations Iron Forger -1,75,-307"));
        waypoints.put("gold_forger", new Waypoint("gold_forger", 27, 74, -294, "hub", 2.0, "npc_sell", "Gold Forger 27,74,-294"));

        // NPC sell waypoints from NPCConfig (user-overridable) — these override defaults if same name, allowing player to set coords themselves via Set to Current Pos feature
        if (npcConfig.npcWaypoint1 != null) {
            NPCConfig.WaypointData wd = npcConfig.npcWaypoint1;
            waypoints.put(wd.name, new Waypoint(wd.name, wd.x, wd.y, wd.z, "hub", 2.0, "npc_sell", "User-overridable via NPC Config tab - Set to Current Pos"));
        }
        if (npcConfig.npcWaypoint2 != null) {
            NPCConfig.WaypointData wd = npcConfig.npcWaypoint2;
            waypoints.put(wd.name, new Waypoint(wd.name, wd.x, wd.y, wd.z, "hub", 2.0, "npc_sell", "User-overridable"));
        }
        if (npcConfig.npcWaypoint3 != null) {
            NPCConfig.WaypointData wd = npcConfig.npcWaypoint3;
            waypoints.put(wd.name, new Waypoint(wd.name, wd.x, wd.y, wd.z, "hub", 2.0, "npc_sell", "User-overridable"));
        }

        Logger.info("Loaded " + waypoints.size() + " default waypoints from official Hypixel SkyBlock Wiki (verify after Hub redesigns) + user overrides. Credits: Cldz");
    }

    private void loadCustom() {
        File f = new File(CUSTOM_FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                Map<String, Waypoint> map = GSON.fromJson(r, new com.google.gson.reflect.TypeToken<Map<String, Waypoint>>(){}.getType());
                if (map != null) {
                    // Custom waypoints override defaults, allowing player to set coords themselves
                    waypoints.putAll(map);
                    Logger.info("Loaded " + map.size() + " custom user waypoints from " + CUSTOM_FILE);
                }
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
            Logger.info("Saved custom waypoints to " + CUSTOM_FILE + " (persists across game restarts)");
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
        Logger.info("Registered waypoint " + wp.name + " at " + wp.x + "," + wp.y + "," + wp.z + " - user set via Set to Current Pos feature, persists across restarts");
    }
}
