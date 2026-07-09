package com.bazaarflipper.data;

import com.bazaarflipper.util.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Map product ID -> NPC sell price.
 * Document as requiring verification against current Hypixel game state.
 * No tax applies to NPC sells.
 * Prices may be outdated - never crash on wrong price.
 */
public class NPCPrices {
    private final Map<String, Double> npcPrices = new HashMap<>();

    public NPCPrices() {
        // Best-known defaults from wiki, user note that prices may be outdated.
        // Example placeholder values
        npcPrices.put("COAL", 2.0);
        npcPrices.put("COBBLESTONE", 2.0);
        npcPrices.put("IRON_INGOT", 3.0);
        npcPrices.put("GOLD_INGOT", 4.0);
        npcPrices.put("DIAMOND", 8.0);
        npcPrices.put("EMERALD", 6.0);
        npcPrices.put("REDSTONE", 2.0);
        npcPrices.put("STRING", 3.0);
        npcPrices.put("FEATHER", 3.0);
        npcPrices.put("LEATHER", 3.0);
        npcPrices.put("PORK", 3.0);
        npcPrices.put("BONE", 3.0);
        npcPrices.put("WHEAT", 1.0);
        npcPrices.put("CARROT_ITEM", 1.0);
        npcPrices.put("POTATO_ITEM", 1.0);
        npcPrices.put("PUMPKIN", 5.0);
        npcPrices.put("MELON", 2.0);
        npcPrices.put("CACTUS", 1.0);
        npcPrices.put("SUGAR_CANE", 2.0);
        // Enchanted versions generally higher - placeholder
        npcPrices.put("ENCHANTED_COAL", 320.0);
        npcPrices.put("ENCHANTED_IRON", 480.0);
        Logger.info("NPCPrices loaded with " + npcPrices.size() + " entries (requires verification against current Hypixel state)");
    }

    public double getPrice(String productId) {
        return npcPrices.getOrDefault(productId, 0.0);
    }

    public boolean hasPrice(String productId) {
        return npcPrices.containsKey(productId);
    }

    public Map<String, Double> getAll() {
        return npcPrices;
    }
}
