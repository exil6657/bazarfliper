package com.bazaarflipper.data;

import com.bazaarflipper.config.PlayerCapabilityConfig;

import java.util.*;
import java.util.stream.Collectors;

public class UnlockedContentRegistry {
    private final CraftingRecipes craftingRecipes;
    private PlayerCapabilityConfig playerConfig;

    public UnlockedContentRegistry(CraftingRecipes recipes) {
        this.craftingRecipes = recipes;
    }

    public void refreshFromPlayerData(PlayerCapabilityConfig config) {
        this.playerConfig = config;
    }

    public boolean isRecipeUnlocked(String recipeId) {
        if (playerConfig == null) {
            // No API key fallback: zero-requirement recipes only
            CraftingRecipes.Recipe r = craftingRecipes.getRecipe(recipeId);
            if (r == null) return false;
            return r.collectionRequirement == null && r.skillRequirement == null;
        }
        if (playerConfig.unlockedRecipes.contains(recipeId)) return true;
        return meetsCollectionRequirement(recipeId) && meetsSkillRequirement(recipeId);
    }

    public boolean meetsCollectionRequirement(String recipeId) {
        CraftingRecipes.Recipe r = craftingRecipes.getRecipe(recipeId);
        if (r == null) return false;
        if (r.collectionRequirement == null) return true;
        if (playerConfig == null) return false;
        // Format "COLLECTION:TIER" e.g. "COAL:7"
        String[] parts = r.collectionRequirement.split(":");
        if (parts.length != 2) return true;
        String coll = parts[0];
        // Simplistic: check if collection set contains coll
        // Real impl would check tier level via API
        return playerConfig.unlockedCollections.contains(coll) || playerConfig.unlockedCollections.contains(recipeId);
    }

    public boolean meetsSkillRequirement(String recipeId) {
        CraftingRecipes.Recipe r = craftingRecipes.getRecipe(recipeId);
        if (r == null) return false;
        if (r.skillRequirement == null) return true;
        if (playerConfig == null) return false;
        String[] parts = r.skillRequirement.split(":");
        if (parts.length != 2) return true;
        String skill = parts[0];
        int reqLevel;
        try { reqLevel = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return true; }
        return playerConfig.skillLevels.getOrDefault(skill, 0) >= reqLevel;
    }

    public List<CraftingRecipes.Recipe> getUnlockedCraftFlipRecipes() {
        return craftingRecipes.getAllRecipes().stream()
                .filter(r -> isRecipeUnlocked(r.outputProductId))
                .collect(Collectors.toList());
    }

    public String getRequirementsForRecipe(String recipeId) {
        CraftingRecipes.Recipe r = craftingRecipes.getRecipe(recipeId);
        if (r == null) return "Unknown recipe";
        List<String> reqs = new ArrayList<>();
        if (r.collectionRequirement != null) reqs.add("Collection: " + r.collectionRequirement);
        if (r.skillRequirement != null) reqs.add("Skill: " + r.skillRequirement);
        return reqs.isEmpty() ? "No requirements" : String.join(", ", reqs);
    }
}
