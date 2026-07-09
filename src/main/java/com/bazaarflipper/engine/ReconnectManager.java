package com.bazaarflipper.engine;

import com.bazaarflipper.automation.ChatCommandSender;
import com.bazaarflipper.discord.DiscordEventHandler;
import com.bazaarflipper.pathfinding.LocationValidator;
import com.bazaarflipper.pathfinding.WorldStateRecovery;
import com.bazaarflipper.util.Logger;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.Minecraft;

public class ReconnectManager {

    private final SessionStateManager sessionStateManager;
    private final DiscordEventHandler discordHandler;
    private final com.bazaarflipper.config.ModConfig config;
    private final WorldStateRecovery worldStateRecovery;
    private final LocationValidator locationValidator;

    private volatile boolean isReconnecting = false;
    private volatile int attemptCount = 0;
    private final long[] reconnectGaps = {10_000, 30_000, 60_000}; // 10s/30s/60s gaps

    public ReconnectManager(SessionStateManager sessionStateManager, DiscordEventHandler discordHandler,
                            com.bazaarflipper.config.ModConfig config, WorldStateRecovery recovery,
                            LocationValidator validator) {
        this.sessionStateManager = sessionStateManager;
        this.discordHandler = discordHandler;
        this.config = config;
        this.levelStateRecovery = recovery;
        this.locationValidator = validator;
    }

    public void onDisconnect() {
        if (worldStateRecovery.isLimboCooldownActive()) {
            Logger.warn("Limbo cooldown active, not auto-reconnecting");
            return;
        }
        sessionStateManager.saveState();
        discordHandler.onError("Disconnected from Hypixel", "DISCONNECTED");
        beginReconnectLoop();
    }

    public void beginReconnectLoop() {
        if (isReconnecting) return;
        if (worldStateRecovery.isLimboCooldownActive()) {
            Logger.warn("Skipping reconnect due to Limbo cooldown");
            return;
        }
        isReconnecting = true;
        attemptCount = 0;
        new Thread(this::reconnectLoop, "ReconnectManager").start();
    }

    private void reconnectLoop() {
        try {
            int maxAttempts = config.reconnectMaxAttempts;
            while (attemptCount < maxAttempts) {
                attemptCount++;
                Logger.info("Reconnect attempt " + attemptCount + "/" + maxAttempts);
                discordHandler.onReconnectAttempt(attemptCount);

                long gap;
                if (attemptCount == 1) gap = reconnectGaps[0];
                else if (attemptCount == 2) gap = reconnectGaps[1];
                else gap = reconnectGaps[2];
                try { Thread.sleep(gap); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }

                // Try to reconnect: need to use Minecraft's reconnect? Simplified: we cannot fully implement without mixins; we will attempt to connect via server entry
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.getCurrentServer() != null) {
                        // In real mod, we would use mc's connect logic; placeholder
                        // For now we just check if network handler restored
                        if (mc.getConnection() != null) {
                            // Assume success
                            boolean worldReady = locationValidator.waitForWorldState(LocationValidator.WorldState.SKYBLOCK_HUB, 30_000);
                            if (worldReady) {
                                sessionStateManager.tryResume();
                                discordHandler.onReconnectSuccess(attemptCount);
                                Logger.info("Reconnect successful after " + attemptCount + " attempts");
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Logger.error("Reconnect attempt " + attemptCount + " failed", e);
                }
            }

            if (attemptCount >= config.reconnectMaxAttempts) {
                Logger.error("Max reconnect attempts reached, stopping");
                discordHandler.onError("Max reconnect attempts reached (" + attemptCount + ")", "RECONNECTING");
            }
        } finally {
            isReconnecting = false;
        }
    }

    public boolean isReconnecting() { return isReconnecting; }
    public int getAttemptCount() { return attemptCount; }
}
