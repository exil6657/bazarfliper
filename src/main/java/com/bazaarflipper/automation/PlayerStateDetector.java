package com.bazaarflipper.automation;

import com.bazaarflipper.config.PlayerCapabilityConfig;
import com.bazaarflipper.util.ChatUtils;
import com.bazaarflipper.util.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;

public class PlayerStateDetector {

    private final PlayerCapabilityConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PlayerStateDetector(PlayerCapabilityConfig config) {
        this.config = config;
    }

    public PlayerCapabilityConfig.HypixelRank detectRank() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return PlayerCapabilityConfig.HypixelRank.NONE;
        try {
            // Parse from tab list: format [MVP+], [VIP] etc
            Collection<PlayerInfo> entries = mc.getConnection().getOnlinePlayers();
            for (PlayerInfo entry : entries) {
                if (entry.getProfile() != null && entry.getProfile().getId().equals(mc.player.getUUID())) {
                    String display = entry.getTabListDisplayName() != null ? ChatUtils.stripColorCodes(entry.getTabListDisplayName().getString()) : "";
                    // Also check tab list name via stripped color
                    if (display.contains("MVP++")) return PlayerCapabilityConfig.HypixelRank.MVP_PLUS_PLUS;
                    if (display.contains("MVP+")) return PlayerCapabilityConfig.HypixelRank.MVP_PLUS;
                    if (display.contains("MVP")) return PlayerCapabilityConfig.HypixelRank.MVP;
                    if (display.contains("VIP+")) return PlayerCapabilityConfig.HypixelRank.VIP_PLUS;
                    if (display.contains("VIP")) return PlayerCapabilityConfig.HypixelRank.VIP;
                    // Fallback: check player list entry name? The tab list for self often shows rank
                }
            }
            // Alternative: parse from player's display name prefix
            String name = mc.player.getName().getString();
            String stripped = ChatUtils.stripColorCodes(name);
            // If includes rank, detect
            if (stripped.contains("[MVP++]")) return PlayerCapabilityConfig.HypixelRank.MVP_PLUS_PLUS;
            if (stripped.contains("[MVP+]")) return PlayerCapabilityConfig.HypixelRank.MVP_PLUS;
            if (stripped.contains("[MVP]")) return PlayerCapabilityConfig.HypixelRank.MVP;
            if (stripped.contains("[VIP+]")) return PlayerCapabilityConfig.HypixelRank.VIP_PLUS;
            if (stripped.contains("[VIP]")) return PlayerCapabilityConfig.HypixelRank.VIP;
        } catch (Exception e) {
            Logger.error("Rank detection failed", e);
        }
        return PlayerCapabilityConfig.HypixelRank.NONE;
    }

    public long detectCookieStatus() {
        // Parse from scoreboard or buff display
        // Buff display: scoreboard or tab? Cookie active shows remaining time?
        // For simplicity, check scoreboard for "Cookie buff"
        try {
            if (ChatUtils.getSidebarLines().isEmpty()) return 0;
            // Check all lines for cookie indicator? Hypixel shows "Bits" or "Cookie" buff elsewhere
            // Placeholder: assume cookie active if scoreboard has "Bits: "?
            // Actually cookie status better detected via tab list buffs or player buffs
            // For now return 0 if no detection

            // Potion effects? Not
            // We'll attempt to parse from tab list footer? Simplified
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public double detectTaxRate() {
        // Always 1.25% base for bazaar. Cookie does NOT affect bazaar tax. AH tax variable - separate.
        return 0.0125;
    }

    public void detectUnlockedCollections() {
        // Requires Hypixel API player endpoint with API key
        if (config == null) return;
        // Without key fallback to no-requirement recipes only handled elsewhere
        if (config.hypixelRank == null) return;
        // Placeholder: if we have API key, fetch player data
        // Use Hypixel API: https://api.hypixel.net/v2/skyblock/profiles?key=...
        // For now do nothing
    }

    public void detectSkillLevels() {
        // From Hypixel API player data
        // Placeholder
    }

    public boolean canUseCommand(String command) {
        return config.canUseCommand(command);
    }

    public void refreshAll() {
        PlayerCapabilityConfig.HypixelRank rank = detectRank();
        config.hypixelRank = rank;
        long expiry = detectCookieStatus();
        if (expiry > 0) {
            config.activeCookieExpiry = expiry;
        }
        config.recalcDerived();
        detectUnlockedCollections();
        detectSkillLevels();
        config.save();
        Logger.info("Player capabilities refreshed: rank=" + rank + " quickCraft=" + config.hasQuickCraft + " cookieActive=" + config.hasCookieActive);
    }
}
