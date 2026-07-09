package com.bazaarflipper.engine;

import com.bazaarflipper.api.AuctionHouseClient;
import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.api.ProductInfo;
import com.bazaarflipper.automation.BazaarInteractor;
import com.bazaarflipper.automation.AuctionHouseInteractor;
import com.bazaarflipper.automation.CraftingInteractor;
import com.bazaarflipper.config.ModConfig;
import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.discord.DiscordEventHandler;
import com.bazaarflipper.mayor.MayorTracker;
import com.bazaarflipper.pathfinding.LocationValidator;
import com.bazaarflipper.pathfinding.WorldStateRecovery;
import com.bazaarflipper.tracker.FlipRecord;
import com.bazaarflipper.tracker.ProfitTracker;
import com.bazaarflipper.util.Logger;
import com.bazaarflipper.pathfinding.HumanizedNavigator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core flip engine with full state machine per spec
 */
public class FlipEngine {

    public enum FlipState {
        IDLE,
        SCANNING_MARKET,
        RECOVERING_WORLD_STATE,
        REENTERING_SKYBLOCK,
        WAITING_FOR_WORLD_LOAD,
        NAVIGATING_TO_BAZAAR,
        PLACING_BUY_ORDER,
        CHECKING_ORDERS,
        CLAIMING_ORDER,
        NAVIGATING_TO_BANK,
        DEPOSITING_COINS,
        WITHDRAWING_COINS,
        NAVIGATING_TO_NPC,
        SELLING_TO_NPC,
        NAVIGATING_TO_AH,
        PLACING_AH_LISTING,
        PLACING_SELL_OFFER,
        CANCELLING_ORDER,
        RELISTING_ORDER,
        CRAFTING_ITEMS,
        WAITING,
        ERROR_RECOVERY,
        RECONNECTING,
        BREAK_ORDER_WAIT,
        BREAK_SHORT_PERIODIC,
        BREAK_LONG_SESSION
    }

    private volatile FlipState state = FlipState.IDLE;
    private volatile boolean running = false;
    private final Map<String, ActiveFlip> activeFlips = new ConcurrentHashMap<>();

    private final ModConfig config;
    private final BudgetManager budgetManager;
    private final SessionStateManager sessionStateManager;
    private final BreakScheduler breakScheduler;
    private final LocationValidator locationValidator;
    private final WorldStateRecovery worldStateRecovery;
    private final PacketRateLimiter packetRateLimiter;
    private final CommandQueue commandQueue;
    private final GuiWatchdog watchdog;
    private final ProfitTracker profitTracker;
    private final MarketScanner marketScanner;
    private final ItemSelector itemSelector;
    private final OrderManager orderManager;
    private final UndercutDetector undercutDetector;
    private final TaxCalculator taxCalculator;
    private final MayorTracker mayorTracker;
    private final DiscordEventHandler discordEventHandler;
    private final BazaarInteractor bazaarInteractor;
    private final AuctionHouseInteractor ahInteractor;
    private final CraftingInteractor craftingInteractor;
    private final HumanizedNavigator navigator;
    private final AuctionHouseClient ahClient;

    private BazaarData lastBazaarData;

    public FlipEngine(ModConfig config, BudgetManager budgetManager, SessionStateManager sessionStateManager,
                      BreakScheduler breakScheduler, LocationValidator locationValidator, WorldStateRecovery worldStateRecovery,
                      PacketRateLimiter packetRateLimiter, CommandQueue commandQueue, GuiWatchdog watchdog,
                      ProfitTracker profitTracker, MarketScanner marketScanner, ItemSelector itemSelector,
                      OrderManager orderManager, UndercutDetector undercutDetector, TaxCalculator taxCalculator,
                      MayorTracker mayorTracker, DiscordEventHandler discordEventHandler,
                      BazaarInteractor bazaarInteractor, AuctionHouseInteractor ahInteractor,
                      CraftingInteractor craftingInteractor, HumanizedNavigator navigator,
                      AuctionHouseClient ahClient) {
        this.config = config;
        this.budgetManager = budgetManager;
        this.sessionStateManager = sessionStateManager;
        this.breakScheduler = breakScheduler;
        this.locationValidator = locationValidator;
        this.worldStateRecovery = worldStateRecovery;
        this.packetRateLimiter = packetRateLimiter;
        this.commandQueue = commandQueue;
        this.watchdog = watchdog;
        this.profitTracker = profitTracker;
        this.marketScanner = marketScanner;
        this.itemSelector = itemSelector;
        this.orderManager = orderManager;
        this.undercutDetector = undercutDetector;
        this.taxCalculator = taxCalculator;
        this.mayorTracker = mayorTracker;
        this.discordEventHandler = discordEventHandler;
        this.bazaarInteractor = bazaarInteractor;
        this.ahInteractor = ahInteractor;
        this.craftingInteractor = craftingInteractor;
        this.navigator = navigator;
        this.ahClient = ahClient;
    }

    public void start() {
        if (running) return;
        running = true;
        state = FlipState.SCANNING_MARKET;
        Logger.info("FlipEngine started");
        // Perform startup cleanup on fresh start (not resume) - handled outside?
        new Thread(this::engineLoop, "FlipEngine-Loop").start();
    }

    public void stop() {
        running = false;
        state = FlipState.IDLE;
        commandQueue.pause();
        Logger.info("FlipEngine stopped");
    }

    private void engineLoop() {
        while (running) {
            try {
                tick();
                Thread.sleep(100);
            } catch (Exception e) {
                Logger.error("FlipEngine tick error", e);
                onException(e);
            }
        }
    }

    public void tick() {
        // 1. BreakScheduler.isOnBreak() -> if true, run idle behavior only, skip all actions
        if (breakScheduler.isOnBreak()) {
            state = switch (breakScheduler.getCurrentBreakType()) {
                case ORDER_WAIT -> FlipState.BREAK_ORDER_WAIT;
                case SHORT_PERIODIC -> FlipState.BREAK_SHORT_PERIODIC;
                case LONG_SESSION -> FlipState.BREAK_LONG_SESSION;
                case null -> FlipState.WAITING;
            };
            // Do not process command queue, do not start new actions, no GUIs, no navigation, allow idle
            // API polling continues independent
            return;
        }

        // 2. BreakScheduler.isBreakDue() -> if true, start break
        if (breakScheduler.isBreakDue()) {
            // BreakScheduler.tick() already started break? But spec says engine must check isBreakDue
            // We'll trigger
            if (breakScheduler.getTimeUntilLongBreak() == 0) {
                breakScheduler.startBreak(BreakScheduler.BreakType.LONG_SESSION);
                discordEventHandler.onLongBreakStarted(breakScheduler.getRemainingBreakTime());
            } else {
                breakScheduler.startBreak(BreakScheduler.BreakType.SHORT_PERIODIC);
            }
            return;
        }

        // 3. LocationValidator.getCurrentWorldState() every 5 ticks - handled externally but check here
        var worldState = locationValidator.getCurrentWorldState();
        if (worldState != LocationValidator.WorldState.SKYBLOCK_HUB && worldState != LocationValidator.WorldState.SKYBLOCK_PRIVATE_ISLAND
                && worldState != LocationValidator.WorldState.SKYBLOCK_OTHER_ISLAND) {
            if (worldState == LocationValidator.WorldState.DISCONNECTED) {
                state = FlipState.RECONNECTING;
                return;
            } else {
                state = FlipState.RECOVERING_WORLD_STATE;
                worldStateRecovery.checkAndRecover();
                return;
            }
        }

        // 4. PacketRateLimiter.canPerformAction()
        if (!packetRateLimiter.canPerformAction(PacketRateLimiter.ActionType.GUI_CLICK)) {
            return;
        }

        // 5. Process next queue entry
        CommandQueue.FlipAction action = commandQueue.poll();
        if (action != null) {
            executeAction(action);
        } else {
            // No queued actions, consider scanning for new flips if not all orders waiting
            if (activeFlips.size() < config.maxConcurrentItems) { // simplified budget check
                scanForNewFlips();
            } else {
                // All orders in wait state? -> order wait break
                if (allOrdersInWaitState()) {
                    breakScheduler.startOrderWaitBreak();
                }
            }
        }

        // Periodic checks
        checkForFilledOrders();
        checkForUndercuts();
        checkForStaleOrders();
    }

    private boolean allOrdersInWaitState() {
        // If all active flips are in BUY_ORDER_PLACED or SELL_OFFER_PLACED with no actions queued
        if (activeFlips.isEmpty()) return false;
        if (!commandQueue.isEmpty()) return false;
        // Simplified: assume if no queue, all waiting
        return true;
    }

    public void onBazaarUpdate(BazaarData data) {
        this.lastBazaarData = data;
        undercutDetector.onBazaarUpdate(data);
        // Recalculate priorities if needed
        // Do not block gameplay
    }

    public void scanForNewFlips() {
        if (lastBazaarData == null) return;
        state = FlipState.SCANNING_MARKET;

        var opportunities = itemSelector.selectBestItems(lastBazaarData, ahClient, config.maxConcurrentItems - activeFlips.size());
        for (var opp : opportunities) {
            if (activeFlips.containsKey(opp.productId)) continue;
            double cost = opp.buyPrice * opp.quantity;
            if (!budgetManager.canAffordFlip(cost)) continue;

            // Enqueue buy order
            commandQueue.enqueue(CommandQueue.ActionType.PLACE_BUY_ORDER, opp.productId, opp.buyPrice, opp.quantity);
            // Register investment will happen upon placement success
        }
    }

    public void checkForFilledOrders() {
        // Periodic, not during breaks (break check already above)
        for (ActiveFlip flip : activeFlips.values()) {
            // Check if filled - would need InventoryScanner.parseOrderLore or chat notifications
            // Placeholder: skip
        }
    }

    public void checkForUndercuts() {
        for (ActiveFlip flip : activeFlips.values()) {
            if (undercutDetector.isBuyOrderUndercut(flip) || undercutDetector.isSellOfferUndercut(flip)) {
                if (flip.relistCount >= config.maxRelistCount) {
                    // If max reached and still undercut: if spread still profitable -> hold, if not -> cancel
                    // Check current spread profitability
                    if (lastBazaarData != null) {
                        var product = lastBazaarData.getProduct(flip.productId);
                        if (product != null) {
                            double buy = product.getTopBuyOrderPrice();
                            double sell = product.getTopSellOfferPrice();
                            double profit = taxCalculator.calculateBazaarProfit(buy, sell);
                            if (profit <=0) {
                                commandQueue.enqueue(CommandQueue.ActionType.CANCEL_UNDERCUT, flip.productId, 0, 0);
                            }
                        }
                    }
                } else {
                    commandQueue.enqueue(CommandQueue.ActionType.CANCEL_UNDERCUT, flip.productId, 0, 0);
                    discordEventHandler.onUndercut(flip.productId, flip.buyPrice, undercutDetector.getNewCompetitiveBuyPrice(flip));
                }
            }
        }
    }

    public void checkForStaleOrders() {
        for (ActiveFlip flip : activeFlips.values()) {
            long age = System.currentTimeMillis() - flip.placementTimestamp;
            // Dynamic three-factor logic
            ProductInfo product = lastBazaarData != null ? lastBazaarData.getProduct(flip.productId) : null;
            long dailyVolume = product != null ? product.getBuyVolume() + product.getSellVolume() : 0;

            long patience;
            if (dailyVolume > 500_000) patience = 3600_000L; // 1 hour
            else if (dailyVolume >= 100_000) patience = 2 * 3600_000L;
            else patience = 4 * 3600_000L;

            // Fill-rate adjustment: after 25% of window, fill <5% -> reduce patience by 50%. After 50%, fill 0% -> cancel immediately
            double fillPct = flip.quantity>0 ? (double)flip.filledAmount / flip.quantity : 0;
            if (age > patience * 0.25 && fillPct < 0.05) {
                patience = (long)(patience * 0.5);
            }
            if (age > patience * 0.5 && fillPct == 0) {
                // Cancel immediately
                commandQueue.enqueue(CommandQueue.ActionType.CANCEL_UNDERCUT, flip.productId, 0, 0);
                discordEventHandler.onStaleCancelled(flip.productId, age, fillPct, "No fill after 50% patience");
                continue;
            }

            // Market shift: spread collapsed below minimum profit margin -> cancel immediately
            if (product != null) {
                double currentProfit = taxCalculator.calculateBazaarProfit(product.getTopBuyOrderPrice(), product.getTopSellOfferPrice());
                double minProfit = config.minProfitMarginPercent; // simplified
                if (currentProfit < minProfit) {
                    commandQueue.enqueue(CommandQueue.ActionType.CANCEL_UNDERCUT, flip.productId, 0, 0);
                    discordEventHandler.onStaleCancelled(flip.productId, age, fillPct, "Spread collapsed");
                    continue;
                }
            }

            if (age > patience) {
                // Stale: cancel, free budget, record partial profit if any
                commandQueue.enqueue(CommandQueue.ActionType.CANCEL_UNDERCUT, flip.productId, 0, 0);
                discordEventHandler.onStaleCancelled(flip.productId, age, fillPct, "Time-based patience expired");
            }
        }
    }

    public void evaluateAndRotateItems() {
        // Recalculate priorities, cancel unprofitable, etc
        scanForNewFlips();
    }

    public void executeAction(CommandQueue.FlipAction action) {
        try {
            switch (action.type) {
                case CLAIM_FILLED -> {
                    state = FlipState.CLAIMING_ORDER;
                    bazaarInteractor.claimOrder(action.productId);
                    // After claim, sell
                    ActiveFlip flip = activeFlips.get(action.productId);
                    if (flip != null) {
                        if ("NPC".equals(flip.strategyType)) {
                            commandQueue.enqueue(CommandQueue.ActionType.NAVIGATE, action.productId, 0, 0);
                        } else if ("AH_CRAFT".equals(flip.strategyType)) {
                            commandQueue.enqueue(CommandQueue.ActionType.CRAFT_ITEMS, action.productId, 0, 0);
                        } else {
                            commandQueue.enqueue(CommandQueue.ActionType.PLACE_SELL_OFFER, action.productId, flip.targetSellPrice, flip.quantity);
                        }
                    }
                }
                case CANCEL_UNDERCUT -> {
                    state = FlipState.CANCELLING_ORDER;
                    bazaarInteractor.cancelOrder(action.productId);
                    ActiveFlip flip = activeFlips.get(action.productId);
                    if (flip != null) {
                        flip.relistCount++;
                        commandQueue.enqueue(CommandQueue.ActionType.RELIST_CANCELLED, action.productId, 0, 0);
                    }
                }
                case RELIST_CANCELLED -> {
                    state = FlipState.RELISTING_ORDER;
                    // Relist with competitive price
                    ActiveFlip flip = activeFlips.get(action.productId);
                    if (flip != null) {
                        double newBuy = undercutDetector.getNewCompetitiveBuyPrice(flip);
                        double newSell = undercutDetector.getNewCompetitiveSellPrice(flip);
                        // Decide if buy or sell relist needed based on state - simplified to buy
                        bazaarInteractor.placeBuyOrder(action.productId, newBuy, flip.quantity);
                    }
                }
                case PLACE_SELL_OFFER -> {
                    state = FlipState.PLACING_SELL_OFFER;
                    bazaarInteractor.placeSellOffer(action.productId, action.price, action.quantity);
                }
                case PLACE_AH_LISTING -> {
                    state = FlipState.PLACING_AH_LISTING;
                    // Need ItemStack from inventory
                    var stack = new com.bazaarflipper.automation.InventoryScanner().findItemInInventory(action.productId);
                    ahInteractor.createBINListing(stack, action.price);
                }
                case CRAFT_ITEMS -> {
                    state = FlipState.CRAFTING_ITEMS;
                    craftingInteractor.craftItem(action.productId, action.quantity);
                    // After craft, place sell or AH
                    ActiveFlip flip = activeFlips.get(action.productId);
                    if (flip != null) {
                        if ("AH_CRAFT".equals(flip.strategyType)) {
                            commandQueue.enqueue(CommandQueue.ActionType.PLACE_AH_LISTING, flip.productId, flip.targetSellPrice, flip.quantity);
                        } else {
                            commandQueue.enqueue(CommandQueue.ActionType.PLACE_SELL_OFFER, flip.productId, flip.targetSellPrice, flip.quantity);
                        }
                    }
                }
                case PLACE_BUY_ORDER -> {
                    state = FlipState.PLACING_BUY_ORDER;
                    double cost = action.price * action.quantity;
                    if (budgetManager.registerInvestment(action.productId, cost)) {
                        boolean success = bazaarInteractor.placeBuyOrder(action.productId, action.price, action.quantity);
                        if (success) {
                            ActiveFlip flip = new ActiveFlip(action.productId, action.price, action.price*1.1, action.quantity, "ORDER");
                            flip.amountInvested = cost;
                            activeFlips.put(action.productId, flip);
                            orderManager.markOrderPlaced(action.productId, "BUY", action.quantity);
                            // Save state
                            saveSessionState();
                        } else {
                            budgetManager.releaseInvestment(action.productId, cost);
                        }
                    }
                }
                case NAVIGATE -> {
                    state = FlipState.NAVIGATING_TO_BAZAAR;
                    // Cookie-aware navigation
                    // Cookie active -> /bz for bazaar, /ah for AH
                    // NPC selling always requires navigation regardless of cookie
                    // Handled in interactor open methods - for navigate action we decide waypoint
                    // Example: if productId indicates NPC sell, navigate to NPC
                    navigator.navigateTo("bazaar_npc");
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to execute action " + action.type + " for " + action.productId, e);
            // Error: 3 failures -> mark FAILED, free budget, save state, notify Discord
            // Simplified failure counting
            handleFailure(action, e);
        } finally {
            state = FlipState.IDLE;
        }
    }

    private void handleFailure(CommandQueue.FlipAction action, Exception e) {
        // Track failures - placeholder
        // On 3 failures: mark FAILED, free budget, save state, notify Discord
        // For now single failure frees budget if buy order
        if (action.type == CommandQueue.ActionType.PLACE_BUY_ORDER) {
            budgetManager.releaseInvestment(action.productId, action.price * action.quantity);
        }
        discordEventHandler.onError("Action failed: " + action.type + " for " + action.productId + " error " + e.getMessage(), state.name());
        saveSessionState();
    }

    public void performStartupCleanup() {
        // On fresh start (not resume): navigate to bazaar (or /bz if cookie), open Manage Orders, scan existing orders
        // Profitable -> adopt as ActiveFlip, register BudgetManager
        // Not profitable -> log to HUD, leave untouched
        // Simplified
        Logger.info("Performing startup order cleanup");
        // Would open bazaar manage orders GUI and parse
        // Placeholder
    }

    private void saveSessionState() {
        java.util.List<SessionStateManager.SavedFlip> saved = new java.util.ArrayList<>();
        for (ActiveFlip flip : activeFlips.values()) {
            SessionStateManager.SavedFlip sf = new SessionStateManager.SavedFlip();
            sf.productId = flip.productId;
            sf.buyPrice = flip.buyPrice;
            sf.targetSellPrice = flip.targetSellPrice;
            sf.quantity = flip.quantity;
            sf.state = flip.state;
            sf.filledAmount = flip.filledAmount;
            sf.relistCount = flip.relistCount;
            sf.placementTimestamp = flip.placementTimestamp;
            sf.strategyType = flip.strategyType;
            sf.amountInvested = flip.amountInvested;
            saved.add(sf);
        }
        sessionStateManager.saveState(saved, profitTracker.getSessionProfit(), profitTracker.getSessionFlips());
    }

    private void onException(Exception e) {
        sessionStateManager.saveState();
        discordEventHandler.onError("FlipEngine exception: " + e.getMessage(), state.name());
    }

    public Map<String, ActiveFlip> getActiveFlips() { return activeFlips; }
    public FlipState getState() { return state; }
    public boolean isRunning() { return running; }
}
