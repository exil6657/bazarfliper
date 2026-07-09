package com.bazaarflipper.mayor;

import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MayorPriceModifier {
    private final Map<String, Map<String, Double>> mayorCategoryModifiers = new ConcurrentHashMap<>(); // mayor -> category -> modifier
    private final TaxCalculator taxCalculator;

    public MayorPriceModifier(TaxCalculator taxCalculator) {
        this.taxCalculator = taxCalculator;
        loadBundled();
    }

    @SuppressWarnings("unchecked")
    private void loadBundled() {
        try (InputStream is = MayorPriceModifier.class.getResourceAsStream("/assets/bazaarflipper/data/mayor_effects.json")) {
            if (is == null) {
                Logger.warn("mayor_effects.json not found");
                return;
            }
            Gson gson = new GsonBuilder().create();
            Map<String, Object> root = gson.fromJson(new InputStreamReader(is), Map.class);
            if (root.containsKey("mayors")) {
                Map<String, Object> mayors = (Map<String, Object>) root.get("mayors");
                for (String mayorName : mayors.keySet()) {
                    Map<String, Object> mayorObj = (Map<String, Object>) mayors.get(mayorName);
                    if (mayorObj.containsKey("perks")) {
                        java.util.List<Object> perks = (java.util.List<Object>) mayorObj.get("perks");
                        Map<String, Double> catMap = new ConcurrentHashMap<>();
                        for (Object perkObj : perks) {
                            Map<String, Object> perk = (Map<String, Object>) perkObj;
                            java.util.List<String> cats = (java.util.List<String>) perk.getOrDefault("categories", java.util.List.of());
                            double mod = 1.0;
                            if (perk.containsKey("modifier")) {
                                Number n = (Number) perk.get("modifier");
                                mod = n.doubleValue();
                            }
                            for (String cat : cats) {
                                catMap.put(cat, mod);
                            }
                        }
                        mayorCategoryModifiers.put(mayorName, catMap);
                    }
                }
            }
            Logger.info("Loaded mayor effects for " + mayorCategoryModifiers.size() + " mayors");
        } catch (Exception e) {
            Logger.error("Failed to load mayor_effects", e);
        }
    }

    public double getScoreModifier(String productId, MayorData mayor) {
        if (mayor == null) return 1.0;
        String category = getCategoryForProduct(productId);
        Map<String, Double> catMap = mayorCategoryModifiers.get(mayor.getName());
        if (catMap == null) return 1.0;
        double base = catMap.getOrDefault(category, 1.0);

        // Special Derpy handling: AH flip scores reduced by Derpy tax impact
        if (taxCalculator.isDerpyActive(mayor)) {
            // Apply penalty for AH items - assume productId high value? For now apply 0.5x penalty generically for AH
            // More precise logic in MayorFlipAdvisor
            if ("auction".equals(category) || "all".equals(category)) {
                return base * 0.5;
            }
        }
        return base;
    }

    public String getPriceImpactNote(String productId, MayorData mayor) {
        if (mayor == null) return "";
        String category = getCategoryForProduct(productId);
        Map<String, Double> catMap = mayorCategoryModifiers.get(mayor.getName());
        if (catMap == null) return "";
        Double mod = catMap.get(category);
        if (mod == null) return "";
        if (mod > 1.0) return String.format("%s Mayor bonus: %.0f%% boost for %s", mayor.getName(), (mod-1)*100, category);
        if (mod < 1.0) return String.format("%s Mayor penalty: %.0f%% reduction for %s", mayor.getName(), (1-mod)*100, category);
        return "";
    }

    public String getCategoryForProduct(String productId) {
        // Simplistic mapping
        String pid = productId.toLowerCase();
        if (pid.contains("coal") || pid.contains("iron") || pid.contains("gold") || pid.contains("diamond") || pid.contains("emerald") || pid.contains("ore")) return "mining";
        if (pid.contains("wheat") || pid.contains("carrot") || pid.contains("potato") || pid.contains("pumpkin") || pid.contains("melon") || pid.contains("cane") || pid.contains("cactus")) return "farming";
        if (pid.contains("log") || pid.contains("wood")) return "foraging";
        if (pid.contains("fish") || pid.contains("bait") || pid.contains("salmon")) return "fishing";
        if (pid.contains("bone") || pid.contains("string") || pid.contains("spider") || pid.contains("gunpowder")) return "combat";
        if (pid.contains("compactor") || pid.contains("lava_bucket")) return "auction";
        return "misc";
    }
}
