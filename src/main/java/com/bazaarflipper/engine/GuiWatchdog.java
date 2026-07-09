package com.bazaarflipper.engine;

import com.bazaarflipper.discord.DiscordEventHandler;
import com.bazaarflipper.util.Logger;

public class GuiWatchdog {
    private volatile long guiOpenTime = 0;
    private volatile String currentGuiTitle = null;
    private volatile boolean guiOpen = false;
    private volatile int retryCount = 0;
    private volatile long currentPingMs = 100;

    private final DiscordEventHandler discordHandler;

    public GuiWatchdog(DiscordEventHandler discord) {
        this.discordHandler = discord;
    }

    public void notifyGuiOpened(String title) {
        guiOpen = true;
        currentGuiTitle = title;
        guiOpenTime = System.currentTimeMillis();
        retryCount = 0;
        Logger.info("GUI opened: " + title);
    }

    public void notifyGuiClosed() {
        guiOpen = false;
        currentGuiTitle = null;
        retryCount = 0;
    }

    public void notifyGuiProgressed() {
        // Reset timer as progress made
        guiOpenTime = System.currentTimeMillis();
    }

    public void tick() {
        if (!guiOpen) return;
        long now = System.currentTimeMillis();
        long elapsed = now - guiOpenTime;
        long timeout = 5000 + (currentPingMs * 3);
        timeout = Math.max(3000, Math.min(timeout, 15000));

        if (elapsed > timeout) {
            Logger.warn("GUI Watchdog timeout for " + currentGuiTitle + " elapsed " + elapsed + " timeout " + timeout + " retry " + retryCount);
            retryCount++;
            if (retryCount < 3) {
                // Close GUI and re-attempt
                discordHandler.onGuiWatchdog(currentGuiTitle, retryCount, false);
                // Close GUI
                try {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.screen != null) mc.setScreen(null);
                    });
                } catch (Exception e) {
                    Logger.error("Failed to close GUI on watchdog", e);
                }
                // Wait 1 second
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                guiOpenTime = now; // reset for next attempt
            } else {
                // Mark failed, save state, discord critical, pause engine
                Logger.error("GUI Watchdog failed after 3 retries for " + currentGuiTitle);
                discordHandler.onGuiWatchdog(currentGuiTitle, retryCount, true);
                notifyGuiClosed();
                // Would call engine pause - handled via callback elsewhere
            }
        }
    }

    public void setCurrentPingMs(long ping) { this.currentPingMs = ping; }

    public long getCurrentPingMs() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.getConnection() != null && mc.player != null) {
                var entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (entry != null) {
                    // entry.getLatency() in mojmap is latency
                    // return entry.getLatency();
                    return 100;
                }
            }
        } catch (Exception ignored) {}
        return currentPingMs;
    }

    public boolean isGuiOpen() { return guiOpen; }
    public String getCurrentGuiTitle() { return currentGuiTitle; }
}
