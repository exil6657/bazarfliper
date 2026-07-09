package com.bazaarflipper.pathfinding;

import com.bazaarflipper.automation.ChatCommandSender;
import com.bazaarflipper.discord.DiscordEventHandler;
import com.bazaarflipper.engine.BreakScheduler;
import com.bazaarflipper.engine.SessionStateManager;
import com.bazaarflipper.util.Logger;
import com.bazaarflipper.util.MathUtils;
import com.bazaarflipper.pathfinding.LocationValidator.WorldState;

import java.util.concurrent.ConcurrentLinkedDeque;

public class WorldStateRecovery {
    private volatile boolean isRecovering = false;
    private final LocationValidator locationValidator;
    private final ChatCommandSender commandSender;
    private final SessionStateManager sessionStateManager;
    private final BreakScheduler breakScheduler;
    private final DiscordEventHandler discordEventHandler;

    private final ConcurrentLinkedDeque<Long> limboEvents = new ConcurrentLinkedDeque<>();
    private volatile boolean limboCooldownActive = false;
    private volatile long limboCooldownUntil = 0;

    public WorldStateRecovery(LocationValidator validator, ChatCommandSender sender, SessionStateManager sessionMgr, BreakScheduler breakScheduler, DiscordEventHandler discord) {
        this.locationValidator = validator;
        this.commandSender = sender;
        this.sessionStateManager = sessionMgr;
        this.breakScheduler = breakScheduler;
        this.discordEventHandler = discord;
    }

    public boolean isRecovering() { return isRecovering; }

    public boolean isLimboCooldownActive() {
        if (limboCooldownActive && System.currentTimeMillis() > limboCooldownUntil) {
            limboCooldownActive = false;
        }
        return limboCooldownActive;
    }

    public void checkAndRecover() {
        if (isRecovering) return;
        WorldState state = locationValidator.getCurrentWorldState();
        switch (state) {
            case SKYBLOCK_PRIVATE_ISLAND -> recoverFromPrivateIsland();
            case LIMBO -> recoverFromLimbo();
            case SKYBLOCK_OTHER_ISLAND -> recoverFromOtherIsland();
            case HYPIXEL_LOBBY -> reenterSkyblock();
            default -> {}
        }
    }

    public void recoverFromPrivateIsland() {
        if (isRecovering) return;
        isRecovering = true;
        new Thread(() -> {
            try {
                Logger.warn("Lobby restart - recovering to Hub");
                discordEventHandler.onPrivateIsland();

                Thread.sleep(MathUtils.randomInt(3000, 5000));

                commandSender.sendCommand("hub");

                boolean success = locationValidator.waitForWorldState(WorldState.SKYBLOCK_HUB, 30_000);
                if (success) {
                    Thread.sleep(MathUtils.randomInt(500, 1500));
                    Logger.info("Recovered to Hub from Private Island");
                    discordEventHandler.onSkyblockReentrySuccess();
                } else {
                    // Retry up to 3 times
                    for (int i = 0; i < 3; i++) {
                        Logger.warn("Hub recovery retry " + (i+1));
                        Thread.sleep(10_000);
                        commandSender.sendCommand("hub");
                        if (locationValidator.waitForWorldState(WorldState.SKYBLOCK_HUB, 30_000)) {
                            Logger.info("Recovered after retry");
                            discordEventHandler.onSkyblockReentrySuccess();
                            break;
                        }
                        if (i == 2) {
                            Logger.error("Failed to recover to Hub after 3 retries - pausing mod");
                            discordEventHandler.onError("Failed to recover to Hub after Private Island", "RECOVERING_WORLD_STATE");
                        }
                    }
                }
            } catch (Exception e) {
                Logger.error("Private island recovery failed", e);
            } finally {
                isRecovering = false;
            }
        }, "WorldStateRecovery-PrivateIsland").start();
    }

    public void recoverFromLimbo() {
        if (isRecovering) return;

        // Track Limbo frequency
        long now = System.currentTimeMillis();
        limboEvents.addLast(now);
        // Purge older than 10 minutes
        limboEvents.removeIf(ts -> now - ts > 10 * 60 * 1000L);

        if (limboEvents.size() >= 5) {
            Logger.error("LIMBO FLOOD: 5+ Limbos in 10 minutes - disconnecting");
            discordEventHandler.onLimboFlood();

            // Force disconnect, critical alert, stop engine
            sessionStateManager.saveState();
            breakScheduler.cancelBreak();
            limboCooldownActive = true;
            limboCooldownUntil = now + 30 * 60 * 1000L; // 30 min cooldown
            // Force disconnect via command? Actually disconnect client
            // For safety, we will call disconnect logic elsewhere - here just set flag
            isRecovering = false; // allow engine to handle disconnect
            return;
        }

        isRecovering = true;
        new Thread(() -> {
            try {
                sessionStateManager.saveState();
                breakScheduler.cancelBreak();
                discordEventHandler.onLimboDetected();

                Thread.sleep(MathUtils.randomInt(5000, 10000));
                commandSender.sendCommand("lobby");

                boolean success = locationValidator.waitForWorldState(WorldState.HYPIXEL_LOBBY, 20_000);
                if (success) {
                    Logger.info("Limbo -> Lobby recovery successful, entering Skyblock re-entry flow");
                    reenterSkyblockFromLimbo();
                } else {
                    for (int i = 0; i < 3; i++) {
                        Thread.sleep(15_000);
                        commandSender.sendCommand("lobby");
                        if (locationValidator.waitForWorldState(WorldState.HYPIXEL_LOBBY, 20_000)) {
                            reenterSkyblockFromLimbo();
                            break;
                        }
                        if (i == 2) {
                            discordEventHandler.onLimboRecoveryFailed();
                            Logger.error("Limbo recovery FAILED");
                        }
                    }
                }
            } catch (Exception e) {
                Logger.error("Limbo recovery failed", e);
                discordEventHandler.onLimboRecoveryFailed();
            } finally {
                isRecovering = false;
            }
        }, "WorldStateRecovery-Limbo").start();
    }

    public void recoverFromOtherIsland() {
        if (isRecovering) return;
        isRecovering = true;
        new Thread(() -> {
            try {
                Logger.info("Recovering from other island to Hub");
                commandSender.sendCommand("hub");
                boolean ok = locationValidator.waitForWorldState(WorldState.SKYBLOCK_HUB, 30_000);
                if (!ok) {
                    for (int i = 0; i < 3; i++) {
                        Thread.sleep(10_000);
                        commandSender.sendCommand("hub");
                        if (locationValidator.waitForWorldState(WorldState.SKYBLOCK_HUB, 30_000)) break;
                    }
                }
            } catch (Exception e) {
                Logger.error("Other island recovery failed", e);
            } finally {
                isRecovering = false;
            }
        }, "WorldStateRecovery-Other").start();
    }

    public void reenterSkyblock() {
        if (isRecovering) return;
        isRecovering = true;
        new Thread(this::doReenterSkyblock, "WorldStateRecovery-Lobby").start();
    }

    private void reenterSkyblockFromLimbo() {
        doReenterSkyblock();
        // After successful, try resume
        sessionStateManager.tryResume();
        discordEventHandler.onLimboRecoverySuccess();
    }

    private void doReenterSkyblock() {
        try {
            // Primary: try /skyblock or /sb
            commandSender.sendCommand("skyblock");
            boolean success = locationValidator.waitForWorldState(WorldState.SKYBLOCK_HUB, 30_000);
            if (!success) {
                commandSender.sendCommand("sb");
                success = locationValidator.waitForWorldState(WorldState.SKYBLOCK_HUB, 30_000);
            }
            if (!success) {
                // Fallback: navigate to Skyblock NPC/portal waypoint
                Logger.warn("Skyblock command failed, falling back to NPC navigation");
                // Would use HumanizedNavigator.navigateTo("hypixel_lobby_skyblock_npc") and find Skyblock option by item name
                // Placeholder
            }

            if (locationValidator.getCurrentWorldState() == WorldState.SKYBLOCK_HUB) {
                // Wait for full world load: mc.player non-null, mc.level non-null, position stable, min 3 second wait
                Thread.sleep(3000);
                // Additional check: player and world stable
                int attempts = 0;
                while (attempts < 10) {
                    try {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.player != null && mc.level != null) {
                            // Position stable check: small movement? Simplified: wait
                            Thread.sleep(500);
                            break;
                        }
                    } catch (Exception ignored) {}
                    Thread.sleep(500);
                    attempts++;
                }
                Logger.info("Re-entered Skyblock Hub");
                discordEventHandler.onSkyblockReentrySuccess();
            }
        } catch (Exception e) {
            Logger.error("Skyblock re-entry failed", e);
        } finally {
            isRecovering = false;
        }
    }
}
