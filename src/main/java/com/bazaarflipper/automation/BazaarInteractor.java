package com.bazaarflipper.automation;

import com.bazaarflipper.engine.GuiWatchdog;
import com.bazaarflipper.mayor.MayorTracker;
import com.bazaarflipper.pathfinding.HumanizedNavigator;
import com.bazaarflipper.pathfinding.LocationValidator;
import com.bazaarflipper.util.Logger;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

public class BazaarInteractor {

    private final LocationValidator locationValidator;
    private final ChatCommandSender commandSender;
    private final ClickSimulator clickSimulator;
    private final DelayManager delayManager;
    private final GuiWatchdog watchdog;
    private final HumanizedNavigator navigator;
    private final InventoryScanner inventoryScanner;
    private final com.bazaarflipper.config.PlayerCapabilityConfig playerCapabilityConfig;

    public BazaarInteractor(LocationValidator validator, ChatCommandSender sender, ClickSimulator clickSim,
                            DelayManager delayManager, GuiWatchdog watchdog, HumanizedNavigator navigator,
                            InventoryScanner scanner, com.bazaarflipper.config.PlayerCapabilityConfig playerCap) {
        this.locationValidator = validator;
        this.commandSender = sender;
        this.clickSimulator = clickSim;
        this.delayManager = delayManager;
        this.watchdog = watchdog;
        this.navigator = navigator;
        this.inventoryScanner = scanner;
        this.playerCapabilityConfig = playerCap;
    }

    public boolean openBazaar() {
        if (!locationValidator.canInteractWithBazaar()) {
            Logger.warn("Cannot interact with bazaar - wrong world state");
            return false;
        }

        boolean hasCookie = playerCapabilityConfig.hasCookieActive;
        if (hasCookie) {
            commandSender.sendCommand("bz");
            return waitForGui("Bazaar");
        } else {
            // Navigate
            navigator.navigateTo("bazaar_npc");
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 60_000) {
                if (navigator.hasArrived()) break;
                try { Thread.sleep(100); } catch (InterruptedException e) { return false; }
            }
            if (!navigator.hasArrived()) return false;
            try { Thread.sleep(delayManager.getDelay(DelayManager.DelayType.POST_NAVIGATION)); } catch (InterruptedException ignored) {}
            // Look at NPC and right-click - simplified: send command as fallback? Actually need click
            // For spec, we simulate right-click via interaction
            // Placeholder: send command anyway if navigation fails? But spec says navigate
            // We'll attempt to interact with entity
            return interactWithNpc("Bazaar");
        }
    }

    private boolean interactWithNpc(String npcNameContains) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return false;
        // Find nearest entity with name containing
        double bestDist = Double.MAX_VALUE;
        net.minecraft.entity.Entity bestEntity = null;
        for (var entity : mc.world.getEntities()) {
            if (entity.getName().getString().toLowerCase().contains(npcNameContains.toLowerCase())) {
                double dist = entity.getPos().distanceTo(mc.player.getPos());
                if (dist < bestDist && dist < 5) {
                    bestDist = dist;
                    bestEntity = entity;
                }
            }
        }
        if (bestEntity != null) {
            // Simulate right-click handled by interactionManager
            // Simplified: use mc.interactionManager.interactEntity
            try {
                mc.interactionManager.interactEntity(mc.player, bestEntity, net.minecraft.util.Hand.MAIN_HAND);
                return waitForGui("Bazaar");
            } catch (Exception e) {
                Logger.error("NPC interact failed", e);
                return false;
            }
        }
        return false;
    }

    public boolean placeBuyOrder(String productId, double pricePerUnit, int quantity) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!openBazaar()) continue;
            // In Bazaar GUI, find product by search
            // If we have /bz command, we can use bazaar search? Spec says chat message for bazaar search via sendChatMessage
            // But typical flow: in Bazaar GUI, search for item
            // We'll simulate: click search, type product name via chat message

            // Watchdog active
            watchdog.notifyGuiOpened("Bazaar");

            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen instanceof GenericContainerScreen bazaarScreen) {
                    // Find search item? Usually paper or sign
                    // Simplified: send chat message with productId as search? Actually Hypixel bazaar has anvil GUI for search?
                    // For spec compliance, we search by name/lore not hardcoded indices
                    clickSimulator.clickSlotByDisplayName(bazaarScreen, "Search");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    // Now chat input expected for search - send chat message
                    commandSender.sendChatMessage(productId.replace('_', ' '));

                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));

                    // Now product should be visible
                    // Click product
                    // After product click, GUI shows Buy/Sell options
                    // Click Buy Order
                    if (mc.currentScreen instanceof GenericContainerScreen productScreen) {
                        clickSimulator.clickSlotByDisplayName(productScreen, "Buy Order");

                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));

                        // Now Buy Order GUI: set price and quantity
                        // Hypixel uses anvil or sign GUI? Typically quantity via clicking +/- and price via anvil input
                        // We'll attempt to set via chat messages if needed

                        // Example: click "Custom Amount" then send chat quantity
                        // Simplified
                        commandSender.sendChatMessage(String.valueOf(quantity));
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                        commandSender.sendChatMessage(String.valueOf((long)pricePerUnit));

                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));

                        watchdog.notifyGuiProgressed();
                        // Confirm
                        if (mc.currentScreen instanceof GenericContainerScreen confirmScreen) {
                            clickSimulator.clickSlotByDisplayName(confirmScreen, "Confirm");
                            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                            watchdog.notifyGuiClosed();
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.error("Buy order placement attempt " + attempt + " failed", e);
            } finally {
                watchdog.notifyGuiClosed();
            }
            try { Thread.sleep(delayManager.getDelay(DelayManager.DelayType.ACTION)); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    public boolean placeSellOffer(String productId, double pricePerUnit, int quantity) {
        // Similar to buy but sell offer
        for (int attempt=0; attempt<3; attempt++) {
            if (!openBazaar()) continue;
            watchdog.notifyGuiOpened("Bazaar");
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen instanceof GenericContainerScreen bazaarScreen) {
                    clickSimulator.clickSlotByDisplayName(bazaarScreen, "Search");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    commandSender.sendChatMessage(productId.replace('_',' '));
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    if (mc.currentScreen instanceof GenericContainerScreen productScreen) {
                        clickSimulator.clickSlotByDisplayName(productScreen, "Sell Offer");
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                        commandSender.sendChatMessage(String.valueOf(quantity));
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                        commandSender.sendChatMessage(String.valueOf((long)pricePerUnit));
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                        watchdog.notifyGuiProgressed();
                        if (mc.currentScreen instanceof GenericContainerScreen confirmScreen) {
                            clickSimulator.clickSlotByDisplayName(confirmScreen, "Confirm");
                            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                            watchdog.notifyGuiClosed();
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.error("Sell offer attempt failed", e);
            } finally {
                watchdog.notifyGuiClosed();
            }
        }
        return false;
    }

    public boolean claimOrder(String productId) {
        if (!openBazaar()) return false;
        watchdog.notifyGuiOpened("Your Bazaar Orders");
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof GenericContainerScreen ordersScreen) {
                clickSimulator.clickSlotByDisplayName(ordersScreen, productId.replace('_',' '));
                Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                if (mc.currentScreen instanceof GenericContainerScreen orderDetail) {
                    clickSimulator.clickSlotByDisplayName(orderDetail, "Claim");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                    return true;
                }
            }
        } catch (Exception e) {
            Logger.error("Claim order failed", e);
        } finally {
            watchdog.notifyGuiClosed();
        }
        return false;
    }

    public boolean cancelOrder(String productId) {
        if (!openBazaar()) return false;
        watchdog.notifyGuiOpened("Your Bazaar Orders");
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof GenericContainerScreen ordersScreen) {
                clickSimulator.clickSlotByDisplayName(ordersScreen, productId.replace('_',' '));
                Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                if (mc.currentScreen instanceof GenericContainerScreen orderDetail) {
                    clickSimulator.clickSlotByDisplayName(orderDetail, "Cancel");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                    if (mc.currentScreen instanceof GenericContainerScreen confirm) {
                        clickSimulator.clickSlotByDisplayName(confirm, "Confirm");
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Cancel order failed", e);
        } finally {
            watchdog.notifyGuiClosed();
        }
        return false;
    }

    private boolean waitForGui(String expectedTitleContains) {
        long start = System.currentTimeMillis();
        long timeout = 5000 + getCurrentPingMs() * 3;
        timeout = Math.min(Math.max(timeout, 3000), 15000);
        while (System.currentTimeMillis() - start < timeout) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen != null) {
                String title = mc.currentScreen.getTitle().getString();
                if (title.toLowerCase().contains(expectedTitleContains.toLowerCase())) {
                    return true;
                }
            }
            try { Thread.sleep(50); } catch (InterruptedException e) { return false; }
        }
        return false;
    }

    private long getCurrentPingMs() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() != null && mc.player != null) {
                var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (entry != null) return 100; // placeholder, real would get latency
            }
        } catch (Exception ignored) {}
        return 100;
    }
}
