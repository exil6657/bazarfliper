package com.bazaarflipper.automation;

import com.bazaarflipper.engine.GuiWatchdog;
import com.bazaarflipper.pathfinding.HumanizedNavigator;
import com.bazaarflipper.pathfinding.LocationValidator;
import com.bazaarflipper.util.Logger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;

public class AuctionHouseInteractor {
    private final LocationValidator locationValidator;
    private final ChatCommandSender commandSender;
    private final ClickSimulator clickSimulator;
    private final DelayManager delayManager;
    private final GuiWatchdog watchdog;
    private final HumanizedNavigator navigator;
    private final com.bazaarflipper.config.PlayerCapabilityConfig playerCap;

    public AuctionHouseInteractor(LocationValidator validator, ChatCommandSender sender, ClickSimulator clickSim,
                                  DelayManager delayManager, GuiWatchdog watchdog, HumanizedNavigator navigator,
                                  com.bazaarflipper.config.PlayerCapabilityConfig playerCap) {
        this.locationValidator = validator;
        this.commandSender = sender;
        this.clickSimulator = clickSim;
        this.delayManager = delayManager;
        this.watchdog = watchdog;
        this.navigator = navigator;
        this.playerCap = playerCap;
    }

    public boolean openAH() {
        if (!locationValidator.canInteractWithAH()) {
            Logger.warn("Cannot interact with AH - wrong world state");
            return false;
        }
        if (playerCap.hasCookieActive) {
            commandSender.sendCommand("ah");
            return waitForGui("Auction House");
        } else {
            navigator.navigateTo("auction_house_npc");
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 60_000) {
                if (navigator.hasArrived()) break;
                try { Thread.sleep(100); } catch (InterruptedException e) { return false; }
            }
            if (!navigator.hasArrived()) return false;
            try { Thread.sleep(delayManager.getDelay(DelayManager.DelayType.POST_NAVIGATION)); } catch (InterruptedException ignored) {}
            return interactWithNpc("Auction");
        }
    }

    private boolean interactWithNpc(String nameContains) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return false;
        for (var entity : mc.world.getEntities()) {
            if (entity.getName().getString().toLowerCase().contains(nameContains.toLowerCase())) {
                double dist = entity.getPos().distanceTo(mc.player.getPos());
                if (dist < 5) {
                    mc.interactionManager.interactEntity(mc.player, entity, net.minecraft.util.Hand.MAIN_HAND);
                    return waitForGui("Auction House");
                }
            }
        }
        return false;
    }

    public boolean createBINListing(ItemStack item, double price) {
        if (!openAH()) return false;
        watchdog.notifyGuiOpened("Auction House");
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            // Steps: Manage Auctions -> Create Auction -> put item -> BIN -> set price -> confirm
            // Simplified
            if (mc.currentScreen instanceof GenericContainerScreen ahScreen) {
                clickSimulator.clickSlotByDisplayName(ahScreen, "Manage Auctions");
                Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                if (mc.currentScreen instanceof GenericContainerScreen manageScreen) {
                    clickSimulator.clickSlotByDisplayName(manageScreen, "Create Auction");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    // Now need to place item - usually click to place held item? Complex
                    // Placeholder flow
                    clickSimulator.clickSlotByDisplayName((GenericContainerScreen) mc.currentScreen, "Auction House Browser"); // maybe not
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.AH_INTERACTION));

                    // Set BIN price via chat? Some servers use anvil
                    commandSender.sendChatMessage(String.valueOf((long)price));
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));

                    // Click BIN toggle then confirm
                    if (mc.currentScreen instanceof GenericContainerScreen finalScreen) {
                        clickSimulator.clickSlotByDisplayName(finalScreen, "Create BIN");
                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Create BIN listing failed", e);
        } finally {
            watchdog.notifyGuiClosed();
        }
        return false;
    }

    public boolean checkMyListings() {
        if (!openAH()) return false;
        watchdog.notifyGuiOpened("Auction House");
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof GenericContainerScreen ahScreen) {
                clickSimulator.clickSlotByDisplayName(ahScreen, "Manage Auctions");
                Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                // Would parse listings
                return true;
            }
        } catch (Exception e) {
            Logger.error("Check listings failed", e);
        } finally {
            watchdog.notifyGuiClosed();
        }
        return false;
    }

    public boolean cancelListing(String itemId) {
        // Similar flow
        return checkMyListings();
    }

    public boolean collectExpiredListing(String itemId) {
        return checkMyListings();
    }

    public boolean collectSoldListing(String itemId) {
        return checkMyListings();
    }

    private boolean waitForGui(String expectedContains) {
        long start = System.currentTimeMillis();
        long timeout = 5000 + 100 * 3;
        timeout = Math.min(Math.max(timeout, 3000), 15000);
        while (System.currentTimeMillis() - start < timeout) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen != null) {
                String title = mc.currentScreen.getTitle().getString();
                if (title.toLowerCase().contains(expectedContains.toLowerCase())) return true;
            }
            try { Thread.sleep(50); } catch (InterruptedException e) { return false; }
        }
        return false;
    }
}
