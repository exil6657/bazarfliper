package com.bazaarflipper.automation;

import com.bazaarflipper.engine.GuiWatchdog;
import com.bazaarflipper.mayor.MayorTracker;
import com.bazaarflipper.pathfinding.HumanizedNavigator;
import com.bazaarflipper.pathfinding.LocationValidator;
import com.bazaarflipper.util.Logger;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

public class BazaarInteractor {

    private final LocationValidator locationValidator;
    private final ChatCommandSender commandSender;
    private final ClickSimulator clickSimulator;
    private final DelayManager delayManager;
    private final GuiWatchdog watchdog;
    private final HumanizedNavigator navigator;
    private final InventoryScanner inventoryScanner;
    private final com.bazaarflipper.config.PlayerCapabilityConfig playerCapabilityConfig;
    private final SignInteractor signInteractor;

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
        this.signInteractor = new SignInteractor(delayManager);
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        // Find nearest entity with name containing
        double bestDist = Double.MAX_VALUE;
        net.minecraft.world.entity.Entity bestEntity = null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity.getName().getString().toLowerCase().contains(npcNameContains.toLowerCase())) {
                double dist = entity.position().distanceTo(mc.player.position());
                if (dist < bestDist && dist < 5) {
                    bestDist = dist;
                    bestEntity = entity;
                }
            }
        }
        if (bestEntity != null) {
            // Simulate right-click handled by interactionManager
            // Simplified: use mc.gameMode.interactEntity
            try {
                mc.gameMode.interact(mc.player, bestEntity, new net.minecraft.world.phys.EntityHitResult(bestEntity), net.minecraft.world.InteractionHand.MAIN_HAND);
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

            // Watchdog active
            watchdog.notifyGuiOpened("Bazaar");

            try {
                Minecraft mc = Minecraft.getInstance();

                // Try quick find via /bz <item> command if cookie active - per wiki: /bz or /bazaar to quickly find what you are looking for
                // This bypasses sign search GUI and directly opens product GUI
                if (playerCapabilityConfig.hasCookieActive) {
                    // Use /bz <display name> - productId with spaces
                    String displayName = productId.replace('_', ' ');
                    commandSender.sendCommand("bz " + displayName);
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                } else {
                    // No cookie - need to search via sign in bazaar GUI
                    if (mc.screen instanceof ContainerScreen bazaarScreen) {
                        // Search via sign: bottom row Search (Oak Wood Sign) per Auction House fandom and Bazaar guide
                        // Click Search sign
                        clickSimulator.clickSlotByDisplayName(bazaarScreen, "Search");
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));

                        // Wait for sign GUI and input product name
                        if (signInteractor.waitForSignGui(3000)) {
                            // Human-like typing with small delays per DelayManager
                            signInteractor.setSignTextAndSubmit(displayNameOrProductId(productId));
                            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                        } else {
                            // Fallback to chat message per old spec (if sign fails, try chat)
                            commandSender.sendChat(productId.replace('_', ' '));
                            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                        }
                    }
                }

                // Now product should be visible - click product by name/lore never hardcoded indices
                if (mc.screen instanceof ContainerScreen productScreen) {
                    // Find product slot by display name from ItemDatabase or productId
                    clickSimulator.clickSlotByDisplayName(productScreen, productId.replace('_', ' '));
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                }

                // After product click, GUI shows Buy/Sell options - click Buy Order (filled map)
                if (mc.screen instanceof ContainerScreen buySellScreen) {
                    clickSimulator.clickSlotByDisplayName(buySellScreen, "Buy Order");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                }

                // Now Buy Order GUI: set quantity first - 3 presets 64,160,1024 + sign for custom amount up to 71,680 per wiki
                if (mc.screen instanceof ContainerScreen quantityScreen) {
                    // Look for sign with "Custom Amount" per guide: "Clicking the sign allows purchase up to 71,680"
                    // Try to find sign slot
                    boolean customAmountClicked = false;
                    for (var slot : quantityScreen.getMenu().slots) {
                        if (slot.getItem().isEmpty()) continue;
                        String name = slot.getItem().getHoverName().getString().toLowerCase();
                        if (name.contains("custom") && name.contains("amount")) {
                            clickSimulator.clickSlot(quantityScreen.getMenu().containerId, slot.index, 0, null);
                            customAmountClicked = true;
                            break;
                        }
                    }
                    if (!customAmountClicked) {
                        // Fallback: try sign with "Custom"
                        clickSimulator.clickSlotByDisplayName(quantityScreen, "Custom");
                    }
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));

                    // Wait for sign GUI for quantity input
                    if (signInteractor.waitForSignGui(3000)) {
                        // First line is quantity - per research, bazaar uses sign with 4 lines, first line input
                        signInteractor.setSignLines(String.valueOf(quantity), "", "", "");
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    } else {
                        // Fallback old chat method (for backwards compatibility)
                        commandSender.sendChat(String.valueOf(quantity));
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                    }
                }

                // Now unit price GUI: 3 presets same as highest buy order, +0.1, 5% diff + sign for custom price per wiki
                if (mc.screen instanceof ContainerScreen priceScreen) {
                    boolean customPriceClicked = false;
                    for (var slot : priceScreen.getMenu().slots) {
                        if (slot.getItem().isEmpty()) continue;
                        String name = slot.getItem().getHoverName().getString().toLowerCase();
                        if (name.contains("custom") && name.contains("price")) {
                            clickSimulator.clickSlot(priceScreen.getMenu().containerId, slot.index, 0, null);
                            customPriceClicked = true;
                            break;
                        }
                    }
                    if (!customPriceClicked) {
                        clickSimulator.clickSlotByDisplayName(priceScreen, "Custom");
                    }
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));

                    if (signInteractor.waitForSignGui(3000)) {
                        // Custom price sign - first line price
                        signInteractor.setSignLines(String.valueOf((long) pricePerUnit), "", "", "");
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    } else {
                        commandSender.sendChat(String.valueOf((long) pricePerUnit));
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                    }
                }

                watchdog.notifyGuiProgressed();
                // Confirm GUI - click Confirm
                if (mc.screen instanceof ContainerScreen confirmScreen) {
                    clickSimulator.clickSlotByDisplayName(confirmScreen, "Confirm");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    watchdog.notifyGuiClosed();
                    Logger.info("Buy order placed for " + productId + " qty " + quantity + " @ " + pricePerUnit + " via sign input (per wiki) - credits Cldz");
                    return true;
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

    private String displayNameOrProductId(String productId) {
        // Convert productId like ENCHANTED_COAL to display name Enchanted Coal for sign search
        return productId.replace('_', ' ');
    }

    public boolean placeSellOffer(String productId, double pricePerUnit, int quantity) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!openBazaar()) continue;
            watchdog.notifyGuiOpened("Bazaar");
            try {
                Minecraft mc = Minecraft.getInstance();

                // Quick find via /bz command if cookie active
                if (playerCapabilityConfig.hasCookieActive) {
                    commandSender.sendCommand("bz " + productId.replace('_', ' '));
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                } else {
                    if (mc.screen instanceof ContainerScreen bazaarScreen) {
                        clickSimulator.clickSlotByDisplayName(bazaarScreen, "Search");
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                        if (signInteractor.waitForSignGui(3000)) {
                            signInteractor.setSignTextAndSubmit(productId.replace('_', ' '));
                            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                        } else {
                            commandSender.sendChat(productId.replace('_', ' '));
                            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                        }
                    }
                }

                if (mc.screen instanceof ContainerScreen productScreen) {
                    clickSimulator.clickSlotByDisplayName(productScreen, productId.replace('_', ' '));
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                }

                if (mc.screen instanceof ContainerScreen buySellScreen) {
                    clickSimulator.clickSlotByDisplayName(buySellScreen, "Sell Offer");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                }

                // Quantity via sign
                if (mc.screen instanceof ContainerScreen qtyScreen) {
                    boolean customClicked = false;
                    for (var slot : qtyScreen.getMenu().slots) {
                        if (slot.getItem().isEmpty()) continue;
                        String name = slot.getItem().getHoverName().getString().toLowerCase();
                        if (name.contains("custom") && name.contains("amount")) {
                            clickSimulator.clickSlot(qtyScreen.getMenu().containerId, slot.index, 0, null);
                            customClicked = true;
                            break;
                        }
                    }
                    if (!customClicked) clickSimulator.clickSlotByDisplayName(qtyScreen, "Custom");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    if (signInteractor.waitForSignGui(3000)) {
                        signInteractor.setSignLines(String.valueOf(quantity), "", "", "");
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    } else {
                        commandSender.sendChat(String.valueOf(quantity));
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                    }
                }

                // Price via sign
                if (mc.screen instanceof ContainerScreen priceScreen) {
                    boolean customClicked = false;
                    for (var slot : priceScreen.getMenu().slots) {
                        if (slot.getItem().isEmpty()) continue;
                        String name = slot.getItem().getHoverName().getString().toLowerCase();
                        if (name.contains("custom") && name.contains("price")) {
                            clickSimulator.clickSlot(priceScreen.getMenu().containerId, slot.index, 0, null);
                            customClicked = true;
                            break;
                        }
                    }
                    if (!customClicked) clickSimulator.clickSlotByDisplayName(priceScreen, "Custom");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    if (signInteractor.waitForSignGui(3000)) {
                        signInteractor.setSignLines(String.valueOf((long) pricePerUnit), "", "", "");
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    } else {
                        commandSender.sendChat(String.valueOf((long) pricePerUnit));
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                    }
                }

                watchdog.notifyGuiProgressed();
                if (mc.screen instanceof ContainerScreen confirmScreen) {
                    clickSimulator.clickSlotByDisplayName(confirmScreen, "Confirm");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    watchdog.notifyGuiClosed();
                    Logger.info("Sell offer placed for " + productId + " via sign input - credits Cldz");
                    return true;
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
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ContainerScreen ordersScreen) {
                clickSimulator.clickSlotByDisplayName(ordersScreen, productId.replace('_',' '));
                Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                if (mc.screen instanceof ContainerScreen orderDetail) {
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
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ContainerScreen ordersScreen) {
                clickSimulator.clickSlotByDisplayName(ordersScreen, productId.replace('_',' '));
                Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                if (mc.screen instanceof ContainerScreen orderDetail) {
                    clickSimulator.clickSlotByDisplayName(orderDetail, "Cancel");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                    if (mc.screen instanceof ContainerScreen confirm) {
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
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                String title = mc.screen.getTitle().getString();
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
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null && mc.player != null) {
                var entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (entry != null) return 100; // placeholder, real would get latency
            }
        } catch (Exception ignored) {}
        return 100;
    }
}
