package com.bazaarflipper.mayor;

import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.util.Logger;

import java.util.ArrayList;
import java.util.List;

public class MayorFlipAdvisor {
    private final MayorPriceModifier priceModifier;
    private final TaxCalculator taxCalculator;

    public MayorFlipAdvisor(MayorPriceModifier modifier, TaxCalculator taxCalc) {
        this.priceModifier = modifier;
        this.taxCalculator = taxCalc;
    }

    public String getRecommendedStrategyMode(MayorData mayor) {
        if (mayor == null) return "ALL";
        if (mayor.isDerpy()) {
            // During Derpy, avoid AH craft flips
            return "ORDER_CRAFT_NPC"; // exclude AH_CRAFT
        }
        return "ALL";
    }

    public List<String> getItemCategoryBoostList(MayorData mayor) {
        List<String> boosted = new ArrayList<>();
        if (mayor == null) return boosted;
        String name = mayor.getName();
        switch (name.toLowerCase()) {
            case "cole" -> { boosted.add("mining"); boosted.add("ore"); }
            case "finnegan" -> boosted.add("farming");
            case "marina", "foxy" -> boosted.add("fishing");
            case "paul" -> boosted.add("dungeon");
            case "diana" -> boosted.add("mythology");
        }
        return boosted;
    }

    public List<String> getItemCategoryPenaltyList(MayorData mayor) {
        List<String> penalty = new ArrayList<>();
        if (mayor == null) return penalty;
        if (mayor.isDerpy()) {
            // All high-value AH craft flip items (priced above 10M) penalty due to increased AH claiming tax
            penalty.add("auction_high_value"); // custom marker
            penalty.add("auction"); // all auction
            Logger.info("Derpy active - penalty list includes high-value AH craft flip items");
        }
        return penalty;
    }

    public boolean shouldSwitchStrategy(MayorData current, String currentMode) {
        if (current == null) return false;
        if (current.isDerpy() && currentMode.contains("AH_CRAFT")) {
            // Should recommend moving away from AH craft flips if Derpy makes them unprofitable
            // Check if tax makes them unprofitable: we can't know without calculating, but heuristic: if Derpy active, recommend switch
            return true;
        }
        return false;
    }

    public List<String> getPreElectionOpportunities(BazaarData data, String predictedMayor) {
        List<String> opps = new ArrayList<>();
        if (predictedMayor == null) return opps;
        // Example: if Diana predicted, pre-position griffin items
        if (predictedMayor.equalsIgnoreCase("Diana")) {
            opps.add("GRIFFIN_FEATHER - Diana coming, mythology items increase");
        }
        if (predictedMayor.equalsIgnoreCase("Cole")) {
            opps.add("ENCHANTED_COAL, ENCHANTED_IRON - Cole mining boost");
        }
        if (predictedMayor.equalsIgnoreCase("Derpy")) {
            opps.add("WARNING: Derpy predicted - avoid listing high-value AH items above 10M");
        }
        return opps;
    }
}
