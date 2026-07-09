package com.bazaarflipper.data;

import java.util.*;

public class CraftingRecipes {

    public static class Recipe {
        public String outputProductId;
        public Map<String, Integer> ingredients = new HashMap<>(); // id -> qty
        public int outputQuantity = 1;
        public String collectionRequirement; // e.g. "COAL:7" or null
        public String skillRequirement; // e.g. "MINING:15" or null
        public boolean isBazaarTradeable = true;
        public boolean isAHTradeable = true;
        public String ahCategory = "MISC";

        public Recipe(String output, Map<String, Integer> ing, int outQty) {
            this.outputProductId = output;
            this.ingredients = ing;
            this.outputQuantity = outQty;
        }
    }

    private final Map<String, Recipe> recipes = new HashMap<>();

    public CraftingRecipes() {
        loadDefaults();
    }

    private void loadDefaults() {
        // Enchanted items: 160x base
        // Example: ENCHANTED_COAL = 160 COAL
        addEnchanted("ENCHANTED_COAL", "COAL");
        addEnchanted("ENCHANTED_COAL_BLOCK", "ENCHANTED_COAL");
        addEnchanted("ENCHANTED_IRON", "IRON_INGOT");
        addEnchanted("ENCHANTED_GOLD", "GOLD_INGOT");
        addEnchanted("ENCHANTED_DIAMOND", "DIAMOND");
        addEnchanted("ENCHANTED_EMERALD", "EMERALD");
        addEnchanted("ENCHANTED_REDSTONE", "REDSTONE");
        addEnchanted("ENCHANTED_LAPIS_LAZULI", "LAPIS_LAZULI");
        addEnchanted("ENCHANTED_GLOWSTONE", "GLOWSTONE_DUST");
        addEnchanted("ENCHANTED_STRING", "STRING");
        addEnchanted("ENCHANTED_FEATHER", "FEATHER");
        addEnchanted("ENCHANTED_LEATHER", "LEATHER");
        addEnchanted("ENCHANTED_PORK", "PORK");
        addEnchanted("ENCHANTED_BONE", "BONE");
        addEnchanted("ENCHANTED_SUGAR", "SUGAR_CANE");
        addEnchanted("ENCHANTED_CACTUS", "CACTUS");
        addEnchanted("ENCHANTED_CACTUS_GREEN", "ENCHANTED_CACTUS");
        addEnchanted("ENCHANTED_WHEAT", "WHEAT");
        addEnchanted("ENCHANTED_SEEDS", "SEEDS");
        addEnchanted("ENCHANTED_POTATO", "POTATO_ITEM");
        addEnchanted("ENCHANTED_CARROT", "CARROT_ITEM");
        addEnchanted("ENCHANTED_MELON", "MELON");
        addEnchanted("ENCHANTED_PUMPKIN", "PUMPKIN");
        addEnchanted("ENCHANTED_COBBLESTONE", "COBBLESTONE");
        addEnchanted("ENCHANTED_SAND", "SAND");
        addEnchanted("ENCHANTED_SUGAR_CANE", "SUGAR_CANE");
        addEnchanted("ENCHANTED_BLAZE_ROD", "BLAZE_ROD");
        addEnchanted("ENCHANTED_GHAST_TEAR", "GHAST_TEAR");
        addEnchanted("ENCHANTED_SLIME_BALL", "SLIME_BALL");
        addEnchanted("ENCHANTED_MAGMA_CREAM", "MAGMA_CREAM");

        // Double-enchanted example: ENCHANTED_COAL_BLOCK = 160 ENCHANTED_COAL (already added if iterative)
        // Actually need distinction: some double-enchanted need 160 enchanted
        // Our addEnchanted covers single level; for double we already double-use

        // Special multi-ingredient recipes - example AH-only craftable
        // Example: SUPER_COMPACTOR_3000 requires enchanted items
        Map<String, Integer> sc3000 = new HashMap<>();
        sc3000.put("ENCHANTED_COBBLESTONE", 7);
        sc3000.put("ENCHANTED_REDSTONE", 1);
        Recipe r = new Recipe("SUPER_COMPACTOR_3000", sc3000, 1);
        r.isBazaarTradeable = false;
        r.isAHTradeable = true;
        r.ahCategory = "MISC";
        r.collectionRequirement = "COBBLESTONE:9";
        recipes.put(r.outputProductId, r);

        // Enchanted Lava Bucket
        Map<String, Integer> lava = new HashMap<>();
        lava.put("ENCHANTED_COAL", 2);
        lava.put("ENCHANTED_IRON", 3);
        Recipe lavaR = new Recipe("ENCHANTED_LAVA_BUCKET", lava, 1);
        lavaR.isBazaarTradeable = false;
        lavaR.isAHTradeable = true;
        recipes.put(lavaR.outputProductId, lavaR);
    }

    private void addEnchanted(String output, String base) {
        Map<String, Integer> ing = new HashMap<>();
        ing.put(base, 160);
        Recipe recipe = new Recipe(output, ing, 1);
        recipes.put(output, recipe);
    }

    public Recipe getRecipe(String productId) {
        return recipes.get(productId);
    }

    public Collection<Recipe> getAllRecipes() {
        return recipes.values();
    }

    public List<Recipe> getBazaarCraftable() {
        List<Recipe> list = new ArrayList<>();
        for (Recipe r : recipes.values()) {
            if (r.isBazaarTradeable) list.add(r);
        }
        return list;
    }

    public List<Recipe> getAHCraftable() {
        List<Recipe> list = new ArrayList<>();
        for (Recipe r : recipes.values()) {
            if (r.isAHTradeable) list.add(r);
        }
        return list;
    }
}
