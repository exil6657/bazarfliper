package com.bazaarflipper.mayor;

import com.bazaarflipper.api.MayorAPIClient;
import com.bazaarflipper.util.ChatUtils;
import com.bazaarflipper.util.Logger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.*;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detection priority: API -> scoreboard -> tab list -> calendar fallback
 * Also provides static convenience isDerpyActive
 */
public class MayorTracker {
    private static volatile String cachedCurrentMayorName = "Unknown";
    private static volatile MayorData cachedCurrentMayor = null;

    private volatile MayorData currentMayor;
    private final MayorAPIClient apiClient;
    private final MayorCalendar calendar;

    public MayorTracker(MayorAPIClient apiClient, MayorCalendar calendar) {
        this.apiClient = apiClient;
        this.calendar = calendar;
        this.currentMayor = new MayorData("Unknown");
        cachedCurrentMayor = currentMayor;
        cachedCurrentMayorName = "Unknown";

        apiClient.addListener((newMayor, oldMayor) -> {
            currentMayor = newMayor;
            cachedCurrentMayor = newMayor;
            cachedCurrentMayorName = newMayor.getName();
            Logger.info("Mayor updated via API: " + newMayor.getName());

            // Check Derpy changes
            boolean wasDerpy = oldMayor != null && oldMayor.isDerpy();
            boolean nowDerpy = newMayor.isDerpy();
            if (wasDerpy != nowDerpy) {
                if (nowDerpy) {
                    Logger.warn("Derpy is now active - AH claiming taxes increased");
                } else {
                    Logger.info("Derpy term ended - AH taxes back to normal");
                }
            }
        });
    }

    public MayorData getCurrentMayor() {
        if (currentMayor != null) return currentMayor;
        return cachedCurrentMayor != null ? cachedCurrentMayor : new MayorData("Unknown");
    }

    public java.util.List<MayorData.Perk> getCurrentPerks() {
        return getCurrentMayor().getPerks();
    }

    public double getMayorBonus(String itemCategory) {
        // Load from mayor_effects.json? Simplified
        MayorData mayor = getCurrentMayor();
        if (mayor == null) return 1.0;
        // Derpy gives 2x minion etc but not general bonus
        return 1.0;
    }

    public void refreshFromAPI() {
        apiClient.fetchElectionData();
    }

    public boolean isElectionActive() {
        return calendar.isElectionPeriod();
    }

    public String getLeadingCandidate() {
        // Would parse from API votes, placeholder
        return "Unknown";
    }

    public MayorData predictNextMayor() {
        return new MayorData(getLeadingCandidate());
    }

    public void detectFromScoreboard() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            Scoreboard scoreboard = mc.world.getScoreboard();
            ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (objective == null) return;

            for (ScoreboardScore score : scoreboard.getAllPlayerScores(objective)) {
                String line = ChatUtils.stripColorCodes(score.getPlayerName() != null ? score.getPlayerName().getString() : "");
                if (line.contains("Mayor")) {
                    // Parse mayor name from line like "Mayor: Derpy"
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        String maybeMayor = parts[1].trim();
                        if (!maybeMayor.isEmpty() && !"Unknown".equals(maybeMayor)) {
                            cachedCurrentMayorName = maybeMayor;
                            if (currentMayor == null || !currentMayor.getName().equals(maybeMayor)) {
                                currentMayor = new MayorData(maybeMayor);
                                cachedCurrentMayor = currentMayor;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.debug("Scoreboard mayor detection failed: " + e.getMessage());
        }
    }

    public void detectFromTabList() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null) return;
            Collection<PlayerListEntry> entries = mc.getNetworkHandler().getPlayerList();
            for (PlayerListEntry entry : entries) {
                String display = ChatUtils.stripColorCodes(entry.getDisplayName() != null ? entry.getDisplayName().getString() : "");
                if (display.contains("Mayor")) {
                    // Similar parse
                }
            }
        } catch (Exception e) {
            Logger.debug("Tab list mayor detection failed: " + e.getMessage());
        }
    }

    // Static convenience for TaxCalculator
    public static boolean isDerpyActiveStatic() {
        return "Derpy".equalsIgnoreCase(cachedCurrentMayorName);
    }

    public void setCurrentMayor(MayorData mayor) {
        this.currentMayor = mayor;
        cachedCurrentMayor = mayor;
        cachedCurrentMayorName = mayor.getName();
    }
}
