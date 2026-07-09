# Bazaar Flipper Mod v6.0 — by Cldz

Fabric Loader 0.19.3 | Minecraft 26.1.2 | Java 21+ | Hypixel Skyblock

> **Credits:** All work in this repository is credited to **Cldz**. Original concept, implementation, and maintenance by Cldz. Thank you for developing and supporting this mod.


Automated bazaar, NPC and AH craft flip mod with humanized navigation, mayor awareness, break scheduler, tax handling, and Discord integration.

## Version & Environment

- **Minecraft:** 26.1.2 (new Mojang versioning, do NOT use 1.21.2)
- **Fabric Loader:** 0.19.3
- **Yarn Mappings:** 26.1.2+build.1 (spec requirement; note: from 26.1 onwards Minecraft is unobfuscated, Loom 1.15+ with `fabric.loom.disableObfuscation=true` and Mojang mappings officially required — this project includes fallback handling. Fabric API used older Yarn but migrated via Ravel per https://docs.fabricmc.net/develop/porting/mappings/)
- **Fabric API:** 0.154.0+26.1.2 (matching 26.1.2 release)
- **Java:** 21+ (compile target 21, Gradle JVM requires 25+ for 26.1 per Fabric docs — set in IDE)
- **Hypixel Environment:** All gameplay logic assumes Hypixel Skyblock specifically
- **Crafting System:** Hypixel custom GUI via `/craft` command, NOT vanilla crafting table. Quick crafting requires VIP+ rank, detected via tab list.
- **GUI Rendering:** All mod GUIs fully custom client-side rendered using `DrawContext.fill()` and `DrawContext.drawText()` only. No texture assets except `icon.png`.

## Features

- **Order Flips:** Buy orders / Sell offers with tax-adjusted profit (Bazaar tax 1.25% fixed, cookie does NOT affect)
- **Craft Flips:** Buy bazaar ingredients -> craft via `/craft` -> sell on bazaar
- **NPC Flips:** Buy bazaar -> sell to NPC (always requires physical navigation, no cookie shortcut, no tax)
- **AH Craft Flips:** Buy bazaar ingredients -> craft -> BIN listing on AH with tiered tax and Derpy handling
- **Tax System:** Central `TaxCalculator` only place tax calculated — no inline calculations
  - Bazaar: 1.25% on sell offers (`bazaarTaxRate` configurable default 0.0125)
  - AH: Under 10M 1%, 10M-100M 2%, Over 100M 2.5% — configurable thresholds
  - Derpy: Quadruples AH claiming taxes (research: Hypixel forum thread 5739552 "Derpy's 4x taxes are ridiculous", NamuWiki, Coflnet guide). Multiplier configurable `derpyAHTaxMultiplier` default 4.0, applies above `derpyTaxAppliesAbove` default 1M. See `TaxCalculator` TODO for verification. Runtime event, not startup-only.
- **Break Scheduler:** Two-tier periodic breaks + order wait breaks
  - Tier A: Within rolling 30-min window min 3 min break, probabilistic middle-weighted, forced catch-up safety net
  - Tier B: Every 2h active operation mandatory 2-10 min long break, counts toward Tier A, Discord notification
  - Order Wait: 45s-3m when all orders in wait state, ends immediately on fill
  - Idle behavior: Camera wander 5-25° every 15-45s, 20% chance no movement, tiny shuffle 10%/min 1-3 steps
  - API polling continues during breaks, fills queued to `postBreakActionQueue`
- **World State Recovery:** Limbo flood protection (5+ Limbos in 10 min -> disconnect, 30 min cooldown, critical Discord), Private Island `/hub`, Other Island `/hub`, Lobby re-entry via `/skyblock` fallback NPC nav, full world load wait
- **Mayor System:** API -> scoreboard -> tab -> calendar fallback, Derpy detection with static convenience `MayorTracker.isDerpyActiveStatic()`, price modifiers, pre-position advisor
- **Pathfinding:** A* local grid, HumanizedNavigator with turn overshoot 3-8° corrected 0.3-0.7s, thinking pauses 5-10% 200-800ms, direction noise ±2-5°, speed variation 70% sprint, jump variation, arrival overshoot 0.5-1.5 blocks, camera wander, stutter step 2%/s
- **Discord:** WEBHOOK (HttpClient) and BOT (JDA 5.x jar-in-jar) modes. Commands: `!status !stop !pause !resume !budget !flips !profit !mayor !reconnect !break !tax`
- **Server Invisibility:** No custom channels, no modified packet fields, no mod brand modification, human-like delays, realistic mouse positions, no mod-identifying chat

## Project Structure

See master planning document v6.0 for complete structure. Key files:

- `BazaarFlipperMod.java` — entrypoint, initialization order per spec (20 steps), keybindings (Open Dashboard RCtrl, Toggle RShift, HUD unbound, Emergency Stop unbound), events
- `config/` — ModConfig (including tax & break settings), BudgetConfig, NPCConfig, PlayerCapabilityConfig, FilterConfig, FlipProfile
- `api/` — HypixelAPIClient (bazaar), AuctionHouseClient (BIN avg 3-5 prices), MayorAPIClient, PriceHistory, APIRateLimiter
- `engine/` — FlipEngine state machine, MarketScanner, ItemSelector (weights), OrderManager, UndercutDetector, BudgetManager, SessionStateManager, BreakScheduler, etc + flipping strategies
- `automation/` — BazaarInteractor, AuctionHouseInteractor, CraftingInteractor (custom /craft GUI, slot detection by name/lore never hardcoded), ClickSimulator, MouseSimulator, ChatCommandSender (command vs chat distinction), DelayManager, InventoryScanner (purse parse stripping § codes), PlayerStateDetector
- `pathfinding/` — PathfindingEngine (A*), HumanizedNavigator, WaypointRegistry (wiki coords, user-overridable, "Set to Current Pos"), MovementSimulator (options.setPressed), LocationValidator (WorldState enum), WorldStateRecovery (isRecovering volatile)
- `mayor/` — MayorTracker, MayorCalendar, MayorPriceModifier (Derpy penalty), ElectionMonitor, MayorFlipAdvisor (Derpy category penalty)
- `tracker/` — ProfitTracker, FlipRecord (stores actual tax paid), SessionStats, HistoryManager
- `discord/` — Webhook (30/min, 3 retries exponential), Bot (JDA), Formatter (K/M/B, Discord timestamps, embed colors)
- `ui/` — HudOverlay (6 panels), ActiveFlipsWidget (28 rows, tax badges [1%]/[2%]/[2.5%]/[+D]), DashboardScreen (10 tabs, DrawContext only), MarketContextWidget (overlay near hovered row), ProfitGraphWidget (sparkline via fill columns), OrderStatusWidget, ToastNotification (custom slide-in)
- `data/` — ItemDatabase, CraftingRecipes, NPCPrices, TaxCalculator, UnlockedContentRegistry, EventTracker
- `mixin/MixinClientConnection.java` — read-only packet audit, @Inject only, never @Overwrite

## Build

```bash
./gradlew build
```

Loom 1.17-SNAPSHOT, Fabric Loader 0.19.3, Minecraft 26.1.2, Java 21 target. Dependencies via jar-in-jar to avoid classpath conflicts.

### Dependencies

- Fabric API (matching 26.1.2)
- JDA 5.2.1 — **large dependency ~ large jar**, included via `include` in `build.gradle` for jar-in-jar. Documented here as required. JDA brings its own dependencies (OkHttp, etc).
- GSON (bundled with MC but declared explicitly)
- `java.net.http.HttpClient` for webhook & API

### Gradle Properties

- `minecraft_version=26.1.2`
- `loader_version=0.19.3`
- `yarn_mappings=26.1.2+build.1`
- `fabric_api_version=0.154.0+26.1.2`
- `fabric.loom.disableObfuscation=true` — required for 26.1+ unobfuscated
- From 26.1: `modImplementation` -> `implementation`, Mojang mappings, Loom plugin id `net.fabricmc.fabric-loom` (unobfuscated mode)

## Tax Configuration

All in Settings tab -> Advanced Tax Settings collapsible:

- `bazaarTaxRate` 0.0125 (1.25%)
- `ahTaxLowRate` 0.01 (1% under 10M)
- `ahTaxMidRate` 0.02 (2% 10M-100M)
- `ahTaxHighRate` 0.025 (2.5% over 100M)
- `ahLowMidThreshold` 10M
- `ahMidHighThreshold` 100M
- `derpyAHTaxMultiplier` 4.0 (quadruple, researched from wiki/forums, configurable)
- `derpyTaxAppliesAbove` 1M

Every profit calculation delegates to `TaxCalculator`. Derpy detection via `MayorTracker`. AH flip scores penalized 0.8x baseline + additional 0.5x during Derpy. UI shows warnings: "⚠️ Derpy active — AH claiming tax increased" with red badge.

## Break Configuration

Settings tab -> Break Settings section with sliders, toggles, text diagram.

- `breaksEnabled` master toggle (order wait breaks still occur if false)
- Short 60-240s, Long 120-600s, Window 30m min 3m quota, Long interval 2h, Order wait 45-180s, Idle camera & shuffle toggles

Break flow: `BreakScheduler.startBreak(type)` -> `FlipEngine` stops queue, `FlipState` -> BREAK_*, HUD purple dot + countdown, Toast + Discord (long only), API polling continues, fills -> `postBreakActionQueue`, idle camera via `mc.execute()` + `MovementSimulator`, `endBreak()` -> resume queue + process post-break + recalc priorities.

Timers survive session save/resume via `SessionStateManager` persisting `totalActiveTime`, `timeSinceLastLongBreak`, `breakTimeInCurrentWindow`, `currentWindowStart`.

## Session Persistence

`config/bazaarflipper_session.json` saved every 60s + toggle off + exception + after each flip. Contains active flips, profit, budget invested, break timers. Resume checks file exists within 4h timeout, restores flips per logic (profitable found -> restore, unprofitable -> untouched, not found + in inventory -> sell offer, not found -> free budget). AH_CRAFT checks AH listings. Restores BudgetManager and BreakScheduler.

## Thread Safety

- Active flips `ConcurrentHashMap`
- Command queue `PriorityBlockingQueue` / `ConcurrentLinkedQueue`
- Price history `ConcurrentLinkedDeque`
- Discord inbox `ConcurrentLinkedQueue`
- Budget tracking `ConcurrentHashMap` atomic via synchronized
- Limbo timestamps `ConcurrentLinkedDeque`
- Break history `ConcurrentLinkedDeque`
- Post-break queue `ConcurrentLinkedQueue`
- All volatile flags

Game thread: GUI, rendering, movement, keybindings, packet sending. API threads: HTTP via `ScheduledExecutorService` 2-3 threads. Discord JDA thread pool never touches MC. Reconnect dedicated thread. Cross-thread via `mc.execute(Runnable)`.

## Server Invisibility Rules

1. No custom plugin channels
2. No modified packet fields
3. No server-querying for mod detection beyond already visible (scoreboard, tab, chat, inventory)
4. Chat human-like randomized delays, never same tick as GUI state changes
5. Leave client brand as Fabric sends
6. ClickSlotC2SPacket randomized timing, realistic mouse position, plausible buttons, never same tick as server packet
7. No regular interval patterns — DelayManager randomized
8. No mod-identifying chat messages
9. Mouse position realistic per cursor

Mixin `MixinClientConnection` intercepts outgoing packets read-only, @Inject only, never @Overwrite.

## Controls

- Right Control — Open Dashboard
- Right Shift — Toggle Flipper On/Off
- Unbound — Toggle HUD Visibility
- Unbound — Emergency Stop (instant halt, close GUI, save state, stop queue, cancel break, Discord)

Dashboard single access point, 10 tabs, inline config, Start/Stop toggle top-right green `0xFF1A4A1A` active / red `0xFF4A1A1A` stopped, `shouldPause()` false.

## Notes & Warnings

- NPC coordinates may drift after Hypixel updates — hardcoded wiki-sourced defaults, user-overridable via NPC Config tab + "Set to Current Position"
- Recipe unlock needs API key — without key zero-requirement recipes only, HUD notification
- NPC prices require verification against current game state — never crash on wrong price, no tax on NPC sells
- AH prices less reliable — poll every 5 min, avg 3-5 lowest BIN, 0.8x score penalty, Derpy further reduces
- Mayor data must never block gameplay — continue with last known if API unavailable
- GUI watchdog timeout `5000 + (pingMs*3)` clamped 3000-15000ms, ping from network handler
- Reconnect max 10 attempts (configurable) gaps 10s/30s/60s, if Limbo cooldown active do not auto-reconnect
- Human simulation mandatory everywhere — never bypass
- Lobby restarts common — Private Island detection + /hub recovery robust
- Limbo flood protection critical — 5 in 10 min -> force disconnect + 30m cooldown + critical Discord

## License

MIT
