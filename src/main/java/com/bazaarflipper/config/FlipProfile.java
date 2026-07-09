package com.bazaarflipper.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a flip profile configuration.
 */
public class FlipProfile {
    public String name = "Default";
    public String description = "Default flip profile";
    public List<String> enabledStrategies = new ArrayList<>(); // ORDER, CRAFT, NPC, AH_CRAFT
    public boolean enabled = true;
    public double budgetAllocationPercent = 100.0;

    public FlipProfile() {
        enabledStrategies.add("ORDER");
        enabledStrategies.add("CRAFT");
        enabledStrategies.add("NPC");
        enabledStrategies.add("AH_CRAFT");
    }

    public FlipProfile(String name, List<String> strategies) {
        this.name = name;
        this.enabledStrategies = strategies;
    }
}
