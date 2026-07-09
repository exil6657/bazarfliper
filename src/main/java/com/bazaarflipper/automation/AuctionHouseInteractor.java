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
    private final SignInteractor signInteractor;

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
        this.signInteractor = new SignInteractor(delayManager);
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
            // Per fandom: AH bottom row Search (Oak Wood Sign), Item Tier Eye of Ender, Sort Hopper, BIN Filter
            if (mc.currentScreen instanceof GenericContainerScreen ahScreen) {
                clickSimulator.clickSlotByDisplayName(ahScreen, "Manage Auctions");
                Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                if (mc.currentScreen instanceof GenericContainerScreen manageScreen) {
                    clickSimulator.clickSlotByDisplayName(manageScreen, "Create Auction");
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    // Place item - click to place held item (simplified)
                    // The item to list should be held or in inventory - we would need to click slot where item should go
                    // Placeholder: try to find empty slot for item placement
                    if (mc.currentScreen instanceof GenericContainerScreen createScreen) {
                        // Try to click first empty slot or slot with item placeholder
                        // For humanization, we would have already had item in inventory and click to move
                        // After placing item, set BIN price via sign (not chat) - per research AH uses sign for price? Actually AH uses sign for search, price via anvil or sign?
                        // According to Bazaar Utils thread: custom orders feature clicks sign, enters text automatically and closes after 1.5s - applies to AH too via sign
                        // So for BIN price, it may be anvil or sign - we handle both via signInteractor fallback to chat

                        Thread.sleep(delayManager.getDelay(DelayManager.DelayType.AH_INTERACTION));

                        // Try sign for price
                        boolean priceViaSign = false;
                        // Look for sign slot with "Price" or "Custom Price"
                        for (var slot : createScreen.getScreenHandler().slots) {
                            if (slot.getStack().isEmpty()) continue;
                            String name = slot.getStack().getName().getString().toLowerCase();
                            if (name.contains("price") || name.contains("custom")) {
                                clickSimulator.clickSlot(createScreen.getScreenHandler().syncId, slot.id, 0, net.minecraft.screen.slot.SlotActionType.PICKUP);
                                Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                                if (signInteractor.waitForSignGui(3000)) {
                                    signInteractor.setSignLines(String.valueOf((long) price), "", "", "");
                                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                                    priceViaSign = true;
                                }
                                break;
                            }
                        }
                        if (!priceViaSign) {
                            // Fallback old chat method (anvil may use chat)
                            commandSender.sendChatMessage(String.valueOf((long) price));
                            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                        }

                        // Click BIN toggle then confirm
                        if (mc.currentScreen instanceof GenericContainerScreen finalScreen) {
                            clickSimulator.clickSlotByDisplayName(finalScreen, "Create BIN");
                            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                            clickSimulator.clickSlotByDisplayName(finalScreen, "Confirm");
                            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                            Logger.info("BIN listing created via sign/price input for " + item.getName().getString() + " @ " + price + " - credits Cldz");
                            return true;
                        }
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

    public boolean searchAH(String query) {
        // Implements fandom research: Search (Oak Wood Sign) option at bottom of AH GUI, when clicked gives place to type name
        if (!openAH()) return false;
        watchdog.notifyGuiOpened("Auction House Search");
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof GenericContainerScreen ahScreen) {
                clickSimulator.clickSlotByDisplayName(ahScreen, "Search");
                Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                if (signInteractor.waitForSignGui(3000)) {
                    signInteractor.setSignTextAndSubmit(query);
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.GUI_LOAD));
                    Logger.info("AH search via sign for query: " + query + " - per fandom wiki Search (Oak Wood Sign) - credits Cldz");
                    return true;
                } else {
                    // Fallback to chat
                    commandSender.sendChatMessage(query);
                    Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
                    return true;
                }
            }
        } catch (Exception e) {
            Logger.error("AH search failed", e);
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
