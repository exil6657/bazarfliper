package com.bazaarflipper.pathfinding;

import com.bazaarflipper.util.ChatUtils;
import com.bazaarflipper.util.Logger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.*;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;

public class LocationValidator {

    public enum WorldState {
        SKYBLOCK_HUB,
        SKYBLOCK_PRIVATE_ISLAND,
        SKYBLOCK_OTHER_ISLAND,
        HYPIXEL_LOBBY,
        LIMBO,
        UNKNOWN,
        DISCONNECTED
    }

    public enum ActionType {
        BAZAAR, AUCTION, BANK, CRAFT, NPC_SELL, ANY
    }

    private volatile WorldState cachedState = WorldState.UNKNOWN;
    private long lastRefresh = 0;
    private int tickCounter = 0;

    public WorldState getCurrentWorldState() {
        // If recently refreshed, return cached
        if (System.currentTimeMillis() - lastRefresh < 1000) {
            return cachedState;
        }
        return refreshWorldState();
    }

    public WorldState refreshWorldState() {
        tickCounter++;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null || mc.player == null) {
            cachedState = WorldState.DISCONNECTED;
            lastRefresh = System.currentTimeMillis();
            return cachedState;
        }

        String serverAddress = "";
        try {
            if (mc.getCurrentServerEntry() != null) {
                serverAddress = mc.getCurrentServerEntry().address;
            }
        } catch (Exception ignored) {}

        boolean onHypixel = serverAddress.contains("hypixel.net") || isHypixelFromScoreboardOrTab();

        if (!onHypixel) {
            // Could still be hypixel if address not set but scoreboard has hypixel? We'll check tab list for hypixel
            // For now, if not hypixel, UNKNOWN
        }

        // Check scoreboard
        String scoreboardTitle = ChatUtils.getScoreboardTitle();
        String sidebarStripped = getSidebarStripped();

        // LIMBO detection: empty title or contains limbo indicators, no Skyblock sidebar
        if (isLimbo(scoreboardTitle, sidebarStripped)) {
            cachedState = WorldState.LIMBO;
            lastRefresh = System.currentTimeMillis();
            return cachedState;
        }

        // Skyblock indicators
        if (isSkyblockSidebar(sidebarStripped)) {
            if (sidebarStripped.contains("Hub") || sidebarStripped.contains("⏣ Hub") || sidebarStripped.toLowerCase().contains("village")) {
                cachedState = WorldState.SKYBLOCK_HUB;
            } else if (sidebarStripped.contains("Your Island") || sidebarStripped.contains("Private Island") || sidebarStripped.contains("⏣ Your Island")) {
                cachedState = WorldState.SKYBLOCK_PRIVATE_ISLAND;
            } else {
                cachedState = WorldState.SKYBLOCK_OTHER_ISLAND;
            }
            lastRefresh = System.currentTimeMillis();
            return cachedState;
        }

        // Hypixel lobby: connected to hypixel, no skyblock sidebar
        if (onHypixel) {
            cachedState = WorldState.HYPIXEL_LOBBY;
            lastRefresh = System.currentTimeMillis();
            return cachedState;
        }

        cachedState = WorldState.UNKNOWN;
        lastRefresh = System.currentTimeMillis();
        return cachedState;
    }

    private boolean isLimbo(String title, String sidebar) {
        if (title == null || title.isBlank()) return true;
        String low = title.toLowerCase();
        if (low.contains("limbo")) return true;
        // Scoreboard empty + no skyblock elements + hypixel sends distinctive chat message handled elsewhere
        // For now, if sidebar lacks typical skyblock and title empty
        if (sidebar == null || sidebar.isBlank()) {
            // Could be limbo or lobby, but limbo has specific empty? We'll treat as UNKNOWN unless confirmed via chat
            // Spec says: Scoreboard title empty or contains limbo indicators. No Skyblock sidebar elements.
            // So empty title = limbo
            return title.isEmpty();
        }
        return false;
    }

    private boolean isSkyblockSidebar(String sidebar) {
        if (sidebar == null) return false;
        // Look for typical Skyblock sidebar elements
        return sidebar.contains("Purse") || sidebar.contains("Bits") || sidebar.contains("⏣") || sidebar.toLowerCase().contains("skyblock");
    }

    private boolean isHypixelFromScoreboardOrTab() {
        // Try to detect hypixel from tab list or scoreboard if server address not available
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null) return false;
            Collection<PlayerListEntry> entries = mc.getNetworkHandler().getPlayerList();
            for (PlayerListEntry e : entries) {
                if (e.getDisplayName() != null) {
                    String n = ChatUtils.stripColorCodes(e.getDisplayName().getString()).toLowerCase();
                    if (n.contains("hypixel")) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String getSidebarStripped() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return "";
            Scoreboard scoreboard = mc.world.getScoreboard();
            ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (objective == null) return "";
            StringBuilder sb = new StringBuilder();
            for (ScoreboardScore score : scoreboard.getAllPlayerScores(objective)) {
                String name = score.getPlayerName() != null ? ChatUtils.stripColorCodes(score.getPlayerName().getString()) : "";
                sb.append(name).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isInCorrectStateForAction(ActionType action) {
        WorldState state = getCurrentWorldState();
        return switch (action) {
            case BAZAAR, AUCTION, BANK, NPC_SELL, CRAFT -> state == WorldState.SKYBLOCK_HUB || state == WorldState.SKYBLOCK_PRIVATE_ISLAND || state == WorldState.SKYBLOCK_OTHER_ISLAND;
            case ANY -> state != WorldState.DISCONNECTED && state != WorldState.UNKNOWN && state != WorldState.LIMBO;
        };
    }

    public boolean waitForWorldState(WorldState target, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            WorldState current = refreshWorldState();
            if (current == target) return true;
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    public boolean isOnHypixel() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getCurrentServerEntry() != null) {
                return mc.getCurrentServerEntry().address.contains("hypixel.net");
            }
            return isHypixelFromScoreboardOrTab();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isOnSkyblock() {
        WorldState s = getCurrentWorldState();
        return s == WorldState.SKYBLOCK_HUB || s == WorldState.SKYBLOCK_PRIVATE_ISLAND || s == WorldState.SKYBLOCK_OTHER_ISLAND;
    }

    public boolean isInHub() {
        return getCurrentWorldState() == WorldState.SKYBLOCK_HUB;
    }

    public boolean isNearWaypoint(String waypointName, Vec3d pos) {
        // Would use WaypointRegistry, but for validator we need registry instance - placeholder returns false
        return false;
    }

    public boolean canInteractWithBazaar() {
        return isOnSkyblock();
    }

    public boolean canInteractWithAH() {
        return isOnSkyblock();
    }

    public boolean canInteractWithBank() {
        return isInHub() || isOnSkyblock();
    }

    public boolean canCraft() {
        return isOnSkyblock();
    }

    public void onTick() {
        tickCounter++;
        if (tickCounter % 5 == 0) {
            refreshWorldState();
        }
    }
}
