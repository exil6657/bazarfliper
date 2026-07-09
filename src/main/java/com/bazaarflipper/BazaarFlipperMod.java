package com.bazaarflipper;

import com.bazaarflipper.api.AuctionHouseClient;
import com.bazaarflipper.api.HypixelAPIClient;
import com.bazaarflipper.api.MayorAPIClient;
import com.bazaarflipper.api.PriceHistory;
import com.bazaarflipper.api.APIRateLimiter;
import com.bazaarflipper.automation.*;
import com.bazaarflipper.config.*;
import com.bazaarflipper.data.*;
import com.bazaarflipper.discord.DiscordBotClient;
import com.bazaarflipper.discord.DiscordEventHandler;
import com.bazaarflipper.discord.DiscordMessageFormatter;
import com.bazaarflipper.discord.DiscordWebhookClient;
import com.bazaarflipper.engine.*;
import com.bazaarflipper.mayor.*;
import com.bazaarflipper.pathfinding.*;
import com.bazaarflipper.security.LockConfig;
import com.bazaarflipper.security.LockManager;
import com.bazaarflipper.tracker.HistoryManager;
import com.bazaarflipper.tracker.ProfitTracker;
import com.bazaarflipper.ui.ActiveFlipsWidget;
import com.bazaarflipper.ui.DashboardScreen;
import com.bazaarflipper.ui.HudOverlay;
import com.bazaarflipper.ui.ToastNotification;
import com.bazaarflipper.util.Logger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Main mod entry point - ClientModInitializer
 * Initialize subsystems in exact order per spec:
 * 1 ModConfig, 2 BudgetConfig, 3 NPCConfig, 4 PlayerCapabilityConfig, 5 HypixelAPIClient, 6 MayorAPIClient+MayorTracker,
 * 7 AuctionHouseClient, 8 ProfitTracker, 9 SessionStateManager, 10 BudgetManager, 11 PacketRateLimiter,
 * 12 CommandQueue, 13 GuiWatchdog, 14 ReconnectManager, 15 BreakScheduler, 16 WorldStateRecovery,
 * 17 FlipEngine, 18 PathfindingEngine+HumanizedNavigator, 19 HudOverlay+ActiveFlipsWidget, 20 Discord
 */
public class BazaarFlipperMod implements ClientModInitializer {

    public static BazaarFlipperMod INSTANCE;

    // Configs
    private ModConfig modConfig;
    private BudgetConfig budgetConfig;
    private NPCConfig npcConfig;
    private PlayerCapabilityConfig playerCapabilityConfig;
    private FilterConfig filterConfig;
    private LockConfig lockConfig;

    // Data
    private ItemDatabase itemDatabase;
    private CraftingRecipes craftingRecipes;
    private NPCPrices npcPrices;
    private TaxCalculator taxCalculator;
    private UnlockedContentRegistry unlockedContentRegistry;

    // API
    private APIRateLimiter apiRateLimiter;
    private PriceHistory priceHistory;
    private HypixelAPIClient hypixelAPIClient;
    private MayorAPIClient mayorAPIClient;
    private AuctionHouseClient auctionHouseClient;

    // Mayor
    private MayorCalendar mayorCalendar;
    private MayorTracker mayorTracker;
    private MayorPriceModifier mayorPriceModifier;
    private ElectionMonitor electionMonitor;
    private MayorFlipAdvisor mayorFlipAdvisor;

    // Tracker
    private ProfitTracker profitTracker;
    private HistoryManager historyManager;

    // Engine
    private PacketRateLimiter packetRateLimiter;
    private CommandQueue commandQueue;
    private GuiWatchdog guiWatchdog;
    private ReconnectManager reconnectManager;
    private BreakScheduler breakScheduler;
    private WorldStateRecovery worldStateRecovery;
    private SessionStateManager sessionStateManager;
    private BudgetManager budgetManager;
    private MarketScanner marketScanner;
    private ItemSelector itemSelector;
    private OrderManager orderManager;
    private UndercutDetector undercutDetector;
    private ProfitCalculator profitCalculator;
    private FlipEngine flipEngine;

    // Pathfinding
    private PathfindingEngine pathfindingEngine;
    private HumanizedNavigator humanizedNavigator;
    private WaypointRegistry waypointRegistry;
    private MovementSimulator movementSimulator;
    private LocationValidator locationValidator;

    // Automation
    private DelayManager delayManager;
    private ClickSimulator clickSimulator;
    private MouseSimulator mouseSimulator;
    private ChatCommandSender chatCommandSender;
    private InventoryScanner inventoryScanner;
    private PlayerStateDetector playerStateDetector;
    private BazaarInteractor bazaarInteractor;
    private AuctionHouseInteractor auctionHouseInteractor;
    private CraftingInteractor craftingInteractor;

    // Discord
    private DiscordWebhookClient webhookClient;
    private DiscordBotClient botClient;
    private DiscordMessageFormatter discordFormatter;
    private DiscordEventHandler discordEventHandler;

    // Security - private locking PIN
    private LockManager lockManager;

    // UI
    private HudOverlay hudOverlay;
    private ActiveFlipsWidget activeFlipsWidget;

    // Keybindings
    private KeyMapping openDashboardKey;
    private KeyMapping toggleFlipperKey;
    private KeyMapping toggleHudKey;
    private KeyMapping emergencyStopKey;

    private volatile boolean isOnHypixelSkyblock = false;
    private volatile long lastAutoSave = 0;
    private EventTracker eventTracker;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        Logger.info("Initializing Bazaar Flipper Mod v6.0 for MC 26.1.2 Loader 0.19.3 — Credits: Cldz");
        Logger.info("All config files (bazaarflipper.json, budget, npc, player, filters, waypoints, items, session, history) will be saved and persist across game restarts per user request");

        // 1. Load ModConfig
        modConfig = ModConfig.load();
        // 2. Load BudgetConfig
        budgetConfig = BudgetConfig.load();
        // 3. Load NPCConfig
        npcConfig = NPCConfig.load();
        // 4. Load PlayerCapabilityConfig
        playerCapabilityConfig = PlayerCapabilityConfig.load();
        filterConfig = FilterConfig.load();
        lockConfig = LockConfig.load();

        // Data layer
        itemDatabase = new ItemDatabase();
        itemDatabase.load();
        craftingRecipes = new CraftingRecipes();
        npcPrices = new NPCPrices();
        taxCalculator = new TaxCalculator(modConfig);
        unlockedContentRegistry = new UnlockedContentRegistry(craftingRecipes);
        unlockedContentRegistry.refreshFromPlayerData(playerCapabilityConfig);

        // APIRateLimiter shared
        apiRateLimiter = new APIRateLimiter();
        priceHistory = new PriceHistory();

        // 5. Initialize HypixelAPIClient
        hypixelAPIClient = new HypixelAPIClient(modConfig, apiRateLimiter, priceHistory);

        // 6. Initialize MayorAPIClient and MayorTracker
        mayorAPIClient = new MayorAPIClient(apiRateLimiter);
        mayorCalendar = new MayorCalendar();
        mayorTracker = new MayorTracker(mayorAPIClient, mayorCalendar);
        mayorPriceModifier = new MayorPriceModifier(taxCalculator);
        electionMonitor = new ElectionMonitor(mayorAPIClient);
        mayorFlipAdvisor = new MayorFlipAdvisor(mayorPriceModifier, taxCalculator);

        // 7. Initialize AuctionHouseClient
        auctionHouseClient = new AuctionHouseClient(modConfig, apiRateLimiter);

        // 8. Initialize ProfitTracker
        profitTracker = new ProfitTracker();
        historyManager = new HistoryManager(profitTracker);

        // EventTracker for Part 18
        eventTracker = new EventTracker(apiRateLimiter);

        // 10. Initialize BudgetManager (needs InventoryScanner)
        inventoryScanner = new InventoryScanner();
        budgetManager = new BudgetManager(budgetConfig, inventoryScanner);

        // 11. Initialize PacketRateLimiter
        packetRateLimiter = new PacketRateLimiter();

        // 12. Initialize CommandQueue
        commandQueue = new CommandQueue();

        // 15. Initialize BreakScheduler needs MovementSimulator early
        movementSimulator = new MovementSimulator();
        breakScheduler = new BreakScheduler(modConfig, movementSimulator);

        // 9. Initialize SessionStateManager (needs BudgetManager, BreakScheduler)
        sessionStateManager = new SessionStateManager(budgetManager, breakScheduler, budgetConfig, modConfig);

        // Automation helpers
        delayManager = new DelayManager(packetRateLimiter);
        mouseSimulator = new MouseSimulator();
        clickSimulator = new ClickSimulator(delayManager, packetRateLimiter);
        chatCommandSender = new ChatCommandSender(delayManager);
        playerStateDetector = new PlayerStateDetector(playerCapabilityConfig);

        // 13. Initialize GuiWatchdog needs Discord later, but create placeholder first then set after discord
        // For now create discord clients first to pass to watchdog

        // 20. Initialize DiscordBotClient or DiscordWebhookClient per config
        webhookClient = new DiscordWebhookClient(modConfig.webhookUrl);
        botClient = new DiscordBotClient(modConfig.botToken, modConfig.commandChannelId);
        discordFormatter = new DiscordMessageFormatter(modConfig);
        discordEventHandler = new DiscordEventHandler(modConfig, webhookClient, botClient, discordFormatter);

        // Security - private locking PIN (credits Cldz) - portion saved in config/bazaarflipper_lock.json hashed
        lockManager = new LockManager(lockConfig, modConfig, discordEventHandler);
        lockManager.onGameStart(); // will lock on startup if PIN set, requiring unlock via Dashboard > Security

        // Now watchdog with discord
        guiWatchdog = new GuiWatchdog(discordEventHandler);

        // LocationValidator
        locationValidator = new LocationValidator();

        // 14. Initialize ReconnectManager and 16 WorldStateRecovery need LocationValidator, etc.
        // WorldStateRecovery needs ChatCommandSender, SessionStateManager, BreakScheduler, DiscordEventHandler
        worldStateRecovery = new WorldStateRecovery(locationValidator, chatCommandSender, sessionStateManager, breakScheduler, discordEventHandler);
        reconnectManager = new ReconnectManager(sessionStateManager, discordEventHandler, modConfig, worldStateRecovery, locationValidator);

        // Pathfinding
        waypointRegistry = new WaypointRegistry(npcConfig);
        pathfindingEngine = new PathfindingEngine();
        humanizedNavigator = new HumanizedNavigator(pathfindingEngine, movementSimulator, waypointRegistry, delayManager);

        // Automation interactors
        bazaarInteractor = new BazaarInteractor(locationValidator, chatCommandSender, clickSimulator, delayManager, guiWatchdog, humanizedNavigator, inventoryScanner, playerCapabilityConfig);
        auctionHouseInteractor = new AuctionHouseInteractor(locationValidator, chatCommandSender, clickSimulator, delayManager, guiWatchdog, humanizedNavigator, playerCapabilityConfig);
        craftingInteractor = new CraftingInteractor(chatCommandSender, clickSimulator, delayManager, guiWatchdog, playerCapabilityConfig, inventoryScanner);

        // Engine core
        profitCalculator = new ProfitCalculator(taxCalculator);
        orderManager = new OrderManager();
        undercutDetector = new UndercutDetector(modConfig);
        marketScanner = new MarketScanner(taxCalculator, filterConfig, craftingRecipes, npcPrices, unlockedContentRegistry, mayorPriceModifier, mayorTracker, priceHistory, budgetManager);
        itemSelector = new ItemSelector(marketScanner, taxCalculator, mayorPriceModifier, mayorTracker, budgetManager);

        // 17. Initialize FlipEngine
        flipEngine = new FlipEngine(modConfig, budgetManager, sessionStateManager, breakScheduler, locationValidator, worldStateRecovery,
                packetRateLimiter, commandQueue, guiWatchdog, profitTracker, marketScanner, itemSelector,
                orderManager, undercutDetector, taxCalculator, mayorTracker, discordEventHandler,
                bazaarInteractor, auctionHouseInteractor, craftingInteractor, humanizedNavigator, auctionHouseClient);

        // 18 already done PathfindingEngine and HumanizedNavigator

        // 19 Initialize HudOverlay and ActiveFlipsWidget
        hudOverlay = new HudOverlay(modConfig, flipEngine, profitTracker, budgetManager, breakScheduler, mayorTracker);
        activeFlipsWidget = new ActiveFlipsWidget(flipEngine, taxCalculator, mayorTracker);

        // Discord start per config
        if ("WEBHOOK".equalsIgnoreCase(modConfig.discordMode)) {
            webhookClient.setWebhookUrl(modConfig.webhookUrl);
            webhookClient.start();
        } else if ("BOT".equalsIgnoreCase(modConfig.discordMode)) {
            botClient.setCallbacks(
                    this::handleEmergencyStop,
                    () -> flipEngine.stop(),
                    () -> flipEngine.start(),
                    () -> reconnectManager.beginReconnectLoop()
            );
            botClient.start();
        }

        // Register keybindings in Minecraft controls screen under "Bazaar Flipper"
        openDashboardKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bazaarflipper.open_dashboard",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_CONTROL,
                KeyMapping.Category.MISC
        ));
        toggleFlipperKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bazaarflipper.toggle_flipper",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
        ));
        toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bazaarflipper.toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC
        ));
        emergencyStopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bazaarflipper.emergency_stop",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC
        ));

        // Register events
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        // HUD registration disabled at compile time for 26.1 API compatibility; dashboard remains available via keybind.
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        // Ensure all config saved even when restart game - save on client stopping
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Logger.info("Client stopping - saving all configs for persistence across restarts. Credits: Cldz");
            saveAllConfigs();
            sessionStateManager.saveState();
            waypointRegistry.saveCustomWaypoints();
        });

        // API polling start
        hypixelAPIClient.startPolling();
        mayorAPIClient.startPolling();
        auctionHouseClient.startPolling();

        // Listen to bazaar updates
        hypixelAPIClient.addListener(flipEngine::onBazaarUpdate);
        hypixelAPIClient.addListener(data -> undercutDetector.onBazaarUpdate(data));

        // Mayor update listener for discord Derpy detection
        mayorAPIClient.addListener((newMayor, oldMayor) -> {
            if (oldMayor == null || !oldMayor.getName().equals(newMayor.getName())) {
                String strategyRec = mayorFlipAdvisor.getRecommendedStrategyMode(newMayor);
                discordEventHandler.onMayorChange(newMayor, strategyRec);
                if (newMayor.isDerpy() && (oldMayor == null || !oldMayor.isDerpy())) {
                    ToastNotification.showDerpyDetected();
                } else if (oldMayor != null && oldMayor.isDerpy() && !newMayor.isDerpy()) {
                    discordEventHandler.onDerpyEnded();
                }
            }
        });

        Logger.info("Bazaar Flipper Mod initialized successfully");
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null) return;

        // Check keybinds
        while (openDashboardKey.consumeClick()) {
            openDashboard();
        }
        while (toggleFlipperKey.consumeClick()) {
            toggleFlipper();
        }
        while (toggleHudKey.consumeClick()) {
            modConfig.hudEnabled = !modConfig.hudEnabled;
            modConfig.save();
        }
        while (emergencyStopKey.consumeClick()) {
            handleEmergencyStop();
        }

        // HUD drag handling disabled for 26.1 compatibility.

        // Skip all tick logic if player is null or not on Hypixel Skyblock
        if (!isPlayerOnHypixelSkyblock(client)) {
            return;
        }

        // Every 5 ticks: call LocationValidator.refreshWorldState()
        locationValidator.onTick();

        // Every tick when running: call BreakScheduler.tick()
        if (flipEngine.isRunning()) {
            breakScheduler.tick();
        }

        // GuiWatchdog tick
        guiWatchdog.tick();

        // HumanizedNavigator tick
        if (humanizedNavigator.isNavigating()) {
            humanizedNavigator.tick();
        }

        // Auto-save triggers: Every 60 seconds, on toggle OFF, on any exception in FlipEngine, after each completed flip.
        long now = System.currentTimeMillis();
        if (now - lastAutoSave > 60_000 && flipEngine.isRunning()) {
            sessionStateManager.saveState();
            lastAutoSave = now;
        }

        // EventTracker refresh periodically
        if (now % 300_000 < 1000) { // every 5 min approx
            eventTracker.refreshFromAPI();
        }

        // Check Discord command inbox polled by game thread via mc.execute() - but we are already on game thread
        var inbox = botClient.getCommandInbox();
        String cmd;
        while ((cmd = inbox.poll()) != null) {
            handleDiscordCommand(cmd);
        }
    }

    private void onHudRender(GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter) {
        // Render HUD overlay and toast notifications
        hudOverlay.render(context, tickCounter.getGameTimeDeltaPartialTick(true));

        // Active flips widget rendering at specific position
        int hudX = modConfig.hudX;
        int hudY = modConfig.hudY + 250; // below other panels
        activeFlipsWidget.render(context, hudX, hudY, 300, 200);

        // Toast notifications custom
        ToastNotification.render(context);
    }

    private void onGameMessage(net.minecraft.network.chat.Component text, boolean overlay) {
        String msg = text.getString();
        String stripped = com.bazaarflipper.util.ChatUtils.stripColorCodes(msg);

        // Chat patterns per spec
        if (stripped.contains("Your Buy Order for") && stripped.contains("was filled")) {
            handleOrderFillChat(stripped, true);
        } else if (stripped.contains("Your Sell Offer for") && stripped.contains("was filled")) {
            handleOrderFillChat(stripped, false);
        } else if (stripped.contains("You don't have enough coins")) {
            Logger.warn("Not enough coins");
            discordEventHandler.onError("Not enough coins", flipEngine.getState().name());
        } else if (stripped.contains("Your inventory is full")) {
            Logger.warn("Inventory full");
            discordEventHandler.onError("Inventory full", flipEngine.getState().name());
            ToastNotification.show("Inventory full - pausing", ToastNotification.ToastType.WARNING);
            flipEngine.stop();
        } else if (stripped.contains("Bazaar is currently unavailable")) {
            Logger.warn("Bazaar unavailable");
            // Back off, retry after 60 seconds - would enqueue delayed retry
        } else if (stripped.contains("You have been disconnected")) {
            reconnectManager.onDisconnect();
        } else if (stripped.contains("Your BIN Auction for") && stripped.contains("was sold")) {
            handleOrderFillChat(stripped, false);
        } else if (stripped.contains("Your Auction") && stripped.contains("has expired")) {
            Logger.info("Auction expired: " + stripped);
        } else if (stripped.toLowerCase().contains("limbo") || stripped.contains("You are in Limbo")) {
            // Trigger WorldStateRecovery immediately
            Logger.warn("Limbo entry detected via chat");
            breakScheduler.cancelBreak();
            worldStateRecovery.recoverFromLimbo();
        }

        // Purse change messages
        if (stripped.contains("Purse:")) {
            budgetManager.updateBalanceFromPurse();
        }

        // InventoryScanner chat detection
        if (inventoryScanner.detectChatOrderFill(msg)) {
            // Already handled above but ensure queue logic
        }
    }

    private void handleOrderFillChat(String msg, boolean isBuy) {
        if (breakScheduler.isOnBreak()) {
            // Queue to postBreakActionQueue per spec
            CommandQueue.FlipAction action = new CommandQueue.FlipAction(
                    CommandQueue.ActionType.CLAIM_FILLED,
                    extractProductIdFromFillMessage(msg),
                    0,0
            );
            breakScheduler.queuePostBreakAction(action);
            ToastNotification.showOrderFillDuringBreak();
        } else {
            // Claim immediately
            CommandQueue.FlipAction action = new CommandQueue.FlipAction(
                    CommandQueue.ActionType.CLAIM_FILLED,
                    extractProductIdFromFillMessage(msg),
                    0,0
            );
            // Enqueue with priority 1
            // Using commandQueue directly
            // For now add to engine
            // flipEngine's commandQueue is private? We have reference via flipEngine
            // Let's use command queue
            commandQueue.enqueue(action);
        }
    }

    private String extractProductIdFromFillMessage(String msg) {
        // Example "Your Buy Order for Enchanted Coal x100 was filled!"
        try {
            String[] parts = msg.split("for");
            if (parts.length <2) return "UNKNOWN";
            String after = parts[1].split("was")[0].trim();
            // Remove quantity part
            String itemPart = after.split(" x")[0].trim();
            return itemPart.toUpperCase().replace(' ', '_');
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private void onDisconnect() {
        Logger.info("Disconnected from server - saving all configs to persist across restarts. Credits: Cldz");
        breakScheduler.cancelBreak();
        sessionStateManager.saveState();
        saveAllConfigs();
        waypointRegistry.saveCustomWaypoints();
        itemDatabase.save();
        if (lockManager != null) lockManager.onDisconnect();
        if (!worldStateRecovery.isLimboCooldownActive()) {
            reconnectManager.onDisconnect();
        }
    }

    private void saveAllConfigs() {
        try {
            modConfig.save();
            budgetConfig.save();
            npcConfig.save();
            playerCapabilityConfig.save();
            filterConfig.save();
            lockConfig.save();
            waypointRegistry.saveCustomWaypoints();
            itemDatabase.save();
            Logger.info("All configs saved (bazaarflipper.json, budget, npc, player, filters, lock, waypoints, items) - persists across restarts, credits Cldz");
        } catch (Exception e) {
            Logger.error("Failed to save all configs on shutdown", e);
        }
    }

    private boolean isPlayerOnHypixelSkyblock(Minecraft client) {
        try {
            if (client.getCurrentServer() == null) return false;
            String address = client.getCurrentServer().ip;
            boolean isHypixel = address != null && address.contains("hypixel.net");
            isOnHypixelSkyblock = isHypixel;
            return isHypixel;
            // Further check Skyblock via locationValidator
        } catch (Exception e) {
            return false;
        }
    }

    private void openDashboard() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            DashboardScreen screen = new DashboardScreen(
                    modConfig, budgetConfig, npcConfig, filterConfig,
                    flipEngine, profitTracker, budgetManager,
                    breakScheduler, mayorTracker, taxCalculator,
                    marketScanner, hypixelAPIClient.getLastData(), historyManager
            );
            mc.setScreen(screen);
        });
    }

    private void toggleFlipper() {
        // Private locking PIN check - only authorized people can use mod
        if (lockManager != null && lockManager.isLocked()) {
            ToastNotification.show(lockManager.getLockMessage(), ToastNotification.ToastType.ERROR);
            Logger.warn("Attempt to toggle flipper while locked - PIN required, credits Cldz");
            // Open dashboard to security tab automatically? For now just toast and open dashboard
            openDashboard();
            return;
        }

        if (flipEngine.isRunning()) {
            // On toggle OFF: save state, stop engine, end break scheduler, notify Discord
            sessionStateManager.saveState();
            flipEngine.stop();
            breakScheduler.endSession();
            discordEventHandler.onSessionPause(profitTracker.getSessionStats());
            ToastNotification.show("Flipper paused", ToastNotification.ToastType.INFO);
        } else {
            // On toggle ON: verify on Hypixel Skyblock, detect rank and cookie, tryResume, start session, start engine, notify Discord
            Minecraft mc = Minecraft.getInstance();
            if (!isPlayerOnHypixelSkyblock(mc)) {
                ToastNotification.show("Not on Hypixel Skyblock", ToastNotification.ToastType.ERROR);
                return;
            }
            playerStateDetector.refreshAll();
            var resumeResult = sessionStateManager.tryResume();
            profitTracker.startSession();
            breakScheduler.startSession();
            flipEngine.start();
            String breakSummary = String.format("%dm window %dm quota long every %dh",
                    modConfig.shortBreakWindowMinutes, modConfig.shortBreakWindowMinBreakMinutes, modConfig.longBreakIntervalHours);
            String ahTaxInfo = String.format("Bazaar %.2f%%, AH LOW %.0f%% MID %.0f%% HIGH %.0f%% Derpy x%.0f",
                    modConfig.bazaarTaxRate*100, modConfig.ahTaxLowRate*100, modConfig.ahTaxMidRate*100, modConfig.ahTaxHighRate*100, modConfig.derpyAHTaxMultiplier);
            discordEventHandler.onSessionStart(modConfig.flipMode, budgetConfig.totalBudgetCap, budgetConfig.reservedBalance,
                    budgetConfig.maxConcurrentItems, mayorTracker.getCurrentMayor(),
                    playerCapabilityConfig.hasCookieActive, breakSummary, ahTaxInfo);
            if (resumeResult.restored>0) {
                discordEventHandler.onSessionResumed(resumeResult.restored, resumeResult.abandoned, resumeResult.budgetRestored,
                        "Break timer restored");
            }
            ToastNotification.show("Flipper started", ToastNotification.ToastType.SUCCESS);
        }
    }

    private void handleEmergencyStop() {
        // Instantly release all held movement keys via MovementSimulator.stopAllMovement()
        movementSimulator.stopAllMovement();
        // Close any open Hypixel GUI
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen != null) {
                mc.setScreen(null);
            }
        });
        // Save session state immediately
        sessionStateManager.saveState();
        // Stop command queue
        commandQueue.stop();
        // Cancel any active break
        breakScheduler.cancelBreak();
        // Notify Discord
        discordEventHandler.onError("Emergency Stop activated", "EMERGENCY");
        // Pure immediate halt
        flipEngine.stop();
        ToastNotification.show("Emergency stop!", ToastNotification.ToastType.ERROR);
    }

    private void handleDiscordCommand(String cmd) {
        switch (cmd) {
            case "!status" -> {
                var stats = profitTracker.getSessionStats();
                botClient.sendMessage("Status: " + flipEngine.getState() + " Profit: " + stats.totalProfit);
            }
            case "!budget" -> botClient.sendMessage("Budget: Available " + budgetManager.getAvailableForFlipping() + " Invested " + budgetManager.getTotalCurrentlyInvested());
            case "!flips" -> botClient.sendMessage("Active flips: " + flipEngine.getActiveFlips().size());
            case "!profit" -> botClient.sendMessage("Profit: " + profitTracker.getSessionProfit());
            case "!mayor" -> {
                var mayor = mayorTracker.getCurrentMayor();
                String msg = "Mayor: " + (mayor!=null?mayor.getName():"Unknown");
                if (mayor!=null && mayor.isDerpy()) msg += " ⚠️ Derpy active — AH tax increased";
                botClient.sendMessage(msg);
            }
            case "!break" -> botClient.sendMessage("Break: OnBreak=" + breakScheduler.isOnBreak() + " Remaining=" + breakScheduler.getRemainingBreakTime());
            case "!tax" -> botClient.sendMessage(String.format("Tax: Bazaar %.2f%% AH LOW %.0f%% MID %.0f%% HIGH %.0f%% Derpy x%.0f threshold %s",
                    modConfig.bazaarTaxRate*100, modConfig.ahTaxLowRate*100, modConfig.ahTaxMidRate*100, modConfig.ahTaxHighRate*100, modConfig.derpyAHTaxMultiplier,
                    String.valueOf(modConfig.derpyTaxAppliesAbove)));
        }
    }

    public static BazaarFlipperMod getInstance() { return INSTANCE; }

    public WaypointRegistry getWaypointRegistry() { return waypointRegistry; }
    public NPCConfig getNpcConfig() { return npcConfig; }
    public ModConfig getModConfig() { return modConfig; }
    public BudgetConfig getBudgetConfig() { return budgetConfig; }
    public FilterConfig getFilterConfig() { return filterConfig; }
    public LockConfig getLockConfig() { return lockConfig; }
    public LockManager getLockManager() { return lockManager; }
    public FlipEngine getFlipEngine() { return flipEngine; }
}
