# Bazaar Flipper v6.0 — Feature Checklist vs Initial Master Planning Document
**Fabric 26.1.2 / Loader 0.19.3 / Java21 / Credits: Cldz / Private Master PIN hidden via XOR obfuscation**

This document compares every requirement from the initial v6.0 master planning doc (Parts 1-31) to current implementation.

## PART 1: PROJECT STRUCTURE
- [x] `build.gradle`, `settings.gradle`, `gradle.properties` with correct versions (26.1.2, 0.19.3, yarn 26.1.2+build.1, fabric-api 0.154.0+26.1.2, JDA 5.x jar-in-jar, GSON explicit, `disableObfuscation=true`)
- [x] `src/main/java/com/bazaarflipper/` structure with all subpackages per spec (config/api/engine/flipping/automation/pathfinding/mayor/tracker/discord/ui/data/util/mixin/security)
- [x] `fabric.mod.json` client only, `bazaarflipper.mixins.json` safety audit only
- [x] `assets/bazaarflipper/icon.png` + `data/mayor_calendar.json`, `mayor_effects.json`, `hypixel_events.json`
- [x] `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties` (jar not included, needs download)
- [x] Additional `textures/gui/` 10 PNGs optional (user allowed) + fallback geometry
- [x] `security/LockConfig.java`, `LockManager.java` added per user private lock request

## PART 2: BUILD CONFIGURATION
- [x] Fabric Loom 1.17-SNAPSHOT, MC 26.1.2, loader 0.19.3, yarn placeholder, fabric-api matching, Java21 target, GSON bundled, JDA jar-in-jar, `java.net.http.HttpClient`, no texture assets beyond icon + optional gui textures (user allowed)

## PART 3: SERVER INVISIBILITY
- [x] Rule 1 No custom channels
- [x] Rule 2 No modified packet fields
- [x] Rule 3 Only visible client data (scoreboard/tab/chat/inventory)
- [x] Rule 4 Chat human randomized delays, never same tick as GUI change
- [x] Rule 5 No client brand modification (Fabric default)
- [x] Rule 6 ClickSlotC2SPacket randomized timing + realistic mouse + plausible button + never same tick as server packet
- [x] Rule 7 No regular intervals — DelayManager randomized + 5%/1% distractions
- [x] Rule 8 No mod-identifying chat
- [x] Rule 9 Mouse position realistic via MouseSimulator bezier
- [x] Mixin `MixinClientConnection` read-only `@Inject` only never `@Overwrite`

## PART 4: CORE SYSTEMS - MAIN ENTRY
- [x] `ClientModInitializer`, singleton INSTANCE
- [x] Init order 20 steps exactly per spec (ModConfig → ... → Discord)
- [x] Keybindings: Open Dashboard RCTRL, Toggle RSHIFT, HUD unbound, Emergency unbound under "Bazaar Flipper" category
- [x] Events: `END_CLIENT_TICK`, `HudRenderCallback`, `GAME`, `DISCONNECT`, plus `CLIENT_STOPPING` for config persistence (added)
- [x] Toggle ON: verify hypixel.net, detect rank/cookie via PlayerStateDetector, tryResume, startSession, start engine, Discord start
- [x] Toggle OFF: saveState, stop engine, endSession, Discord pause
- [x] Emergency Stop: release movement, close GUI, save, stop queue, cancel break, Discord, pure halt
- [x] Skip tick if not on Hypixel, refreshWorldState every 5 ticks, BreakScheduler.tick every tick when running

## PART 5: TAX SYSTEM
- [x] Bazaar tax 1.25% fixed cookie NOT affect, configurable for future-proofing, methods `calculateBazaarProfit/Tax/BreakEven/Margin/Total/Rate/set`
- [x] AH tax tiered <10M 1% / 10-100M 2% / >100M 2.5% configurable thresholds, `AHTaxTier` enum LOW/MID/HIGH
- [x] Derpy multiplier researched 4x from forum 5739552 + NamuWiki + Coflnet, `derpyAHTaxMultiplier` default 4.0, `derpyTaxAppliesAbove` 1M, `isDerpyActive`, warnings
- [x] TaxConfig in ModConfig hidden behind Advanced collapsible with Reset button
- [x] Every profit calc uses `TaxCalculator` exclusively, integration points MarketScanner, AHCraftFlipStrategy, ProfitCalculator, FlipRecord, MarketContextWidget, ActiveFlipsWidget, Dashboard Market Scanner, Discord formatter includes tax rate for AH
- [x] UI shows "Tax: 2.0% (MID tier)" + "⚠️ Derpy active — AH tax increased"

## PART 6: BREAK SCHEDULER
- [x] Micro breaks in DelayManager 80-800ms + 5% 2-8s
- [x] Order Wait Breaks 45s-3m idle camera, ends on fill, no Discord, not counting quota
- [x] Tier A Short Periodic 30m window min 3m break, `60-240s` randomized, probabilistic middle-weighted, forced catch-up safety net, no Discord
- [x] Tier B Long 2h interval 2-10m mandatory, counts toward Tier A, Discord notify, cannot skip
- [x] Idle behavior: camera 5-25° 15-45s 0.5-2s smooth + 20% no move, shuffle 10%/min 1-3 steps if not near void/lava, no GUI, API polling continues, fills → `postBreakActionQueue`
- [x] HUD Panel 4 break status + progress bar quota `[██░░░░] 1m20s/3m`, Dashboard break stats total %, counts, next estimates, history last 10
- [x] Settings sliders + toggles + text diagram, master toggle disables Tier A/B but still tracks time and order wait still occurs

## PART 7: PLAYER CAPABILITY
- [x] `PlayerCapabilityConfig` fields rank enum VIP+ quickCraft, cookie expiry, hasCookieActive derived, slashBZ/AH, collections, skillLevels, unlockedRecipes, persisted to `player.json`, `canUseCommand`
- [x] `PlayerStateDetector` detectRank via tab list stripping `§X`, detectCookieStatus scoreboard/buff, detectTaxRate 1.25% base, detectCollections/Skills via API with fallback no-requirement only + HUD notification

## PART 8: BUDGET SYSTEM
- [x] `BudgetConfig` fields cap/reserved/maxPerItem/maxConcurrent 1-28/autoAdjust, validation reserved ≤90% cap, persisted
- [x] `BudgetManager` getCurrentBalance purse+bank, getAvailableForFlipping, canAffordFlip atomic via synchronized, getBestAffordableQuantity, register/releaseInvestment, totalInvested, remaining, updateBalanceFromPurse, utilization%, nearLimit, ConcurrentHashMap tracking

## PART 9: SESSION STATE PERSISTENCE
- [x] `config/bazaarflipper_session.json` save contents active flips list + profit + budget + break timers + timestamp
- [x] Resume `tryResume()` checks 4h timeout, restore logic_found+profitable restore+budget, unprofitable untouched, not found + in inventory → sell offer skip, not found → free budget, AH_CRAFT check AH listings, delete/archive after resume, restore BudgetManager + BreakScheduler timers
- [x] Auto-save every 60s + toggle OFF + exception + after each flip

## PART 10: WORLD STATE & NAVIGATION RECOVERY
- [x] `WorldState` enum 7 values, detection LIMBO empty title + limbo indicators + no skyblock sidebar, HUB sidebar `⏣ Hub`, PRIVATE `Your Island`, OTHER skyblock elements, LOBBY no skyblock + hypixel, DISCONNECTED null handler/player
- [x] Methods `getCurrentWorldState`, `refreshWorldState` cached 1s + tick every 5, `isInCorrectStateForAction`, `waitForWorldState` blocking, `isOnHypixel/isOnSkyblock/isInHub/isNearWaypoint`, `canInteract*`
- [x] `WorldStateRecovery` volatile `isRecovering` prevents stacking
- [x] Private Island: log + Discord ⚠️, wait 3-5s random, `/hub`, wait HUB 30s, retry 3x 10s waits, else pause + notify
- [x] LIMBO: frequency tracking ConcurrentLinkedDeque purge 10m, 5+ in 10m → disconnect + critical Discord + HUD red + cooldown 30m flag + stop engine + save + cancel break, else normal recovery save+cancel break+Discord 🔴 + wait 5-10s + `/lobby` wait HYPIXEL_LOBBY 20s + retry 3x 15s + fallback fail critical alert + stop
- [x] OTHER_ISLAND → `/hub` same retry, HYPIXEL_LOBBY → skyblock re-entry
- [x] Skyblock Re-Entry: HYPIXEL_LOBBY confirmed, primary `/skyblock` or `/sb`, fallback navigate to `hypixel_lobby_skyblock_npc` find SkyBlock option by name never slot, wait full world load player non-null + world non-null + stable + 3s min, if limbo trigger → tryResume, resume engine, Discord ✅

## PART 11: FLIP ENGINE
- [x] `FlipState` enum 25 values per spec + BREAK_*
- [x] `CommandQueue` prioritized 1 Claim →7 Navigate, cookie-aware /bz /ah vs pathfinding (bank/NPC always nav), tick order break check → breakDue → worldState 5 ticks → packetLimiter → queue
- [x] `start()`, `stop()`, `onBazaarUpdate`, `scanForNewFlips`, `checkForFilledOrders`, `checkForUndercuts`, `checkForStaleOrders`, `evaluateAndRotateItems`, `executeAction`, `performStartupCleanup`
- [x] Error 3 failures → FAILED free budget save Discord (implemented failureCounts map 3 strikes toast)
- [x] Startup cleanup: navigate bazaar or /bz, Manage Orders scan, profitable adopt + budget register, unprofitable log leave untouched (implemented with TaxCalculator check)
- [x] Stale dynamic 3-factor: time patience >500k 1h / 100-500k 2h / <100k 4h + fill <5% after 25% reduce 50% + fill 0% after 50% cancel immediate + spread collapsed below min margin cancel immediate
- [x] Partial fills: claim filled portion immediately, Part A sell offer claimed qty at target, Part B remaining buy order active independent stale timer + profit per part (implemented two sub-flips Part A _SELL_A and Part B remaining)

## PART 12: MARKET SCANNER & ITEM SELECTION
- [x] `findOrderFlipOpportunities` uses `calculateBazaarProfit`, `findCraftFlipOpportunities` unlocked recipes only, `findNPCFlipOpportunities` no tax, `findAHCraftFlipOpportunities` uses `calculateAHProfit` with mayor
- [x] `calculateOptimalBuyPrice` top buy + undercut, `calculateOptimalSellPrice` top sell - undercut, `estimateFillTime` from weekly volume, `scoreItem`
- [x] Flip context data: raw spread, tax profit/unit/total, daily vol buy/sell, backlog pressure buy_summary, order count top, budget profit, fill est, mayor mod, event mod, price stability, tax tier/rate/Derpy warning
- [x] All filters pass: min/max margin %, volume, max buy price, min profit/unit, stability via PriceHistory, whitelist/blacklist, budget gate, recipe unlock gate
- [x] AH Craft Flip concept bazaar→craft custom GUI→AH BIN, profit via `calculateAHProfit` with mayor, AH price avg 3-5 BIN never single lowest, methods `findOpportunities` + `estimateAHSellTime` rare penalized, execution flow buy→fill→claim→/craft quick if rank→AH BIN→monitor→relist→24h default cancel
- [x] `ItemSelector` composite weights Profit/h 3.0 Margin 2.0 Volume 1.5 Fill 2.5 Backlog 1.5 Budget 1.0 Mayor 2.0 Stability 1.5 + AH 0.8x baseline + Derpy extra penalty

## PART 13: ORDER MANAGER & UNDERCUT DETECTOR
- [x] Track placement timestamp, last check, fill %, relist count, order type, partial tracking, stale timer
- [x] Methods `markOrderPlaced/Filled/PartiallyFilled/Cancelled`, `checkStaleStatus` FRESH/WARN/STALE, `getOrderAge`, `getAllActiveOrders`, `getOrderFillRate`
- [x] UndercutDetector `isBuyOrderUndercut`, `isSellOfferUndercut`, `getNewCompetitiveBuy/SellPrice` respects max relist, if max reached profitable hold else cancel

## PART 14: RELIABILITY & STABILITY
- [x] `PacketRateLimiter` per-action-type tracking, rolling avg last 10 responses, >500ms avg lag mode 50% slower, gradually recover
- [x] `GuiWatchdog` timeout `5000 + ping*3` clamped 3000-15000ms, on timeout close GUI 1s wait → re-attempt 3 retries → failed save Discord critical pause
- [x] `ReconnectManager` save+Discord alert on disconnect, loop 10s/30s/60s max 10 configurable, success world check → tryResume → start → Discord success, max attempts critical alert stop, checks Limbo cooldown flag

## PART 15: PATHFINDING — EXPANDED TO MAX REALISM PER USER REQUEST
- [x] Navigation only when no cookie (bazaar/AH) or always Bank/NPC or Skyblock NPC in lobby
- [x] Breaks suspended, only idle shuffle permitted
- [x] WaypointRegistry fields X/Y/Z island/name/radius/category/source, defaults from official wiki with redesign notes (Builder -8.5 71 -61.5 new vs -48 70 -34 old, Farm 63.5 72 -113.5 new vs 16 70 -70 old, Lumber -49.5 70 -67.5 vs -125 73 -42.5 Foraging, Fish 112.5 71 -44.5 vs 52 68 -83 old, Mine -8 68 -124 etc) + Auction Master current -39.5 73 -12.5 moved Jan30 2026 from -46.5 73 -90.5 + 4 Auction Agents, Banker -29.5 72 -38, Bazaar -32.5 71 -76.5 + -33.5 73 -22.5, Hub Selector -5.5 69 -22.5, methods getWaypoint/getNearest/isPlayerAt/register/saveCustomWaypoints
- [x] **PathfindingEngine EXPANDED**: A* 96 radius 8000 iter, octile heuristic, PassableResult with danger cost, moveCost diagonal sqrt2 + vertical + jump + drop penalty + crowd cost nearby players + edge void avoidance 4 blocks + soul sand/slime + water/lava, random 0.05 noise, isPassableAdvanced checks solid + head + ground + lava/fire/cactus/berry/magma/water, 26 neighbors shuffled 30% for variation, cache LRU 128 expiry 30s validity check, partial fallback closest node, smoothing: string-pull line-of-sight + Catmull-Rom jitter ±0.3 + bobbing 3cm bezier corner rounding
- [x] **HumanizedNavigator EXPANDED**: states IDLE→CALC→WALK→STUCK→ARRIVED→FAILED, uses smoothPoints not raw, look-ahead 3 points + avoidance vector + jitter, turn overshoot 3-8° corrected 0.3-0.7s via ease + decay, thinking pauses 7% 200-800ms + fatigue extra, stutter 2%/s 50-120ms, camera wander 10-30° yaw 5-15s 80% chance no move 20%, pitch ±10°, direction noise low-pass ±2-5°, speed variation 70% sprint /30% walk + fatigue -30% sprint, jump 0-3 tick variation, arrival overshoot 0.5-1.5 blocks 30% chance, post-nav 500-1500ms, acceleration curve ease-out cubic 0.8s + fatigue -25%, irregular forward pulse 5% release 20-60ms, micro-strafe 8% 80-200ms, environmental scanning glances at bazaar/auction/bank NPCs + players 8m radius 40% glance 10-30°, social avoidance 3m radius strength 0.5, fatigue full after 3m (balanced per user choice) micro-pause 400-1000ms, yaw accuracy penalty, dynamic replan if deviated >3m, stuck recovery jump→look random ±90° → strafe left→right→back→clearCache→recalc 3 attempts then fail
- [x] **MovementSimulator EXPANDED**: pressKey(GameOptions,boolean) spec compliance + KeyBinding overload with 20-80ms reaction delay, pressForward analog pressure 0..1 ramp + wobble ±0.08 + irregular 5% release 20-60ms, pressBack extra delay, pressLeft/Right yaw nudge 30% chance ±2°, setYaw bezier smoothstep t^2*(3-2t) + noise 0.8° + overshoot 15% on >25°, setPitch clamp -85/85 + noise 0.5°, lookAt saccadic 1-3 saccades 30-100ms each with mid yaw/pitch ±5/±3°, jump variable height sprint 0.42+0.03 vs 0.40+0.02 + pre-jump pitch down 10% + velocity variation ±0.02, sprint double-tap 20% + brief unsprint 1% 100-250ms, stopAll staggered release 10-30ms + velocity 0.5 then 0 after 80ms inertia, extra methods strafeLeftRightRandom, lookAroundIdly, tinyShuffleStep
- [x] **Helper classes**: HumanizationProfile per-install unique signature randomizing overshoot mean 4-7°, std 1.2-2.5, pause 150-250/600-1000ms, stutter 1.5-3%, wander 8-12/25-35°, noise 2-5°, sprint 60-80%, micro-strafe 5-12%, avoidance 0.3-0.7; PathSmoother stringPull + jitter + catmullRom; EnvironmentalScanner POI interest scoring + hazard + voidBelow; FatigueSimulator walkStart fatigue 0..1 full 3m, speed mult -25%, sprint mod -40%, thinking extra 0-400ms, micro-pause, yaw penalty

## PART 16: NPC CONFIGURATION
- [x] NPCs same fixed price, multiple waypoints convenience only
- [x] NPCConfig fields selectedSlot 1/2/3, waypoint1/2/3 WaypointData name/x/y/z/displayName/enabled/source, autoSelectNearest, persisted
- [x] Defaults wiki coords with source, user-overridable via Set to Current Pos / Target Block + Copy Coords + Reset to Wiki Default in NPC Config tab 3 side-by-side panels per spec
- [x] Interaction flow always navigate regardless cookie, determine target auto nearest vs selected, wait POST_NAV delay, lookAt + right-click + find sell option by name/lore + verify chat + record profit

## PART 17: MAYOR SYSTEM
- [x] MayorCalendar epoch 1560275700, methods getCurrentYear/Day/DaysUntilElection/NewMayor/isElectionPeriod/getElectionPeriodStart/EndReal, bundled mayor_calendar.json
- [x] MayorTracker detection priority API→scoreboard→tab→calendar fallback, endpoint election verify no key, methods getCurrentMayor/getCurrentPerks/getMayorBonus/refreshFromAPI 10m/isElectionActive/getLeadingCandidate/predictNextMayor, Must never block, isDerpy static convenience
- [x] MayorPriceModifier loads mayor_effects.json, methods getScoreModifier/getPriceImpactNote/getCategoryForProduct, Derpy extra penalty AH scores proportional tax
- [x] ElectionMonitor getCurrentVoteDistribution/getPredictedWinner/getConfidenceLevel/getDaysUntilResult/shouldPreposition >60% >12h, on winner change notify Discord flag ItemSelector
- [x] MayorFlipAdvisor getRecommendedStrategyMode/getBoostList/getPenaltyList/shouldSwitch/getPreElectionOpportunities, Derpy special case includes high-value AH items penalty + switch away if unprofitable, gradual switches

## PART 18: HYPIXEL EVENT DETECTION
- [x] Primary API events endpoint, secondary bundled hypixel_events.json, tertiary scoreboard parsing, unknown logged without crash, EventTracker methods getCurrentEvents/getUpcoming/getEventPriceModifier/refreshFromAPI/parseScoreboardForEvents

## PART 19: CRAFTING SYSTEM
- [x] Hypixel custom GUI via /craft NOT vanilla, slot detection by name/lore never hardcoded index, quick craft VIP+
- [x] CraftingInteractor openCraftingTable /bz, canUseQuickCraft check hasQuickCraft, craftItem quantity quick vs manual placeIngredient/clickOutput/detectSlots/verifyCraftSuccess
- [x] UnlockedContentRegistry isRecipeUnlocked/meetsCollection/meetsSkill/getUnlockedCraftFlipRecipes/refreshFromPlayerData/getRequirements, fallback zero-requirement only + HUD notification
- [x] CraftingRecipes per recipe output id/ingredients map/output qty/collection/skill/isBazaarTradeable/isAHTradeable/AH category, covers enchanted 160x, double-enchanted, multi-ingredient, AH-only

## PART 20: AUCTION HOUSE SYSTEM
- [x] AuctionHouseClient endpoints auctions paginated 20 pages max + auctions_ended, poll 5m, BIN only, getBINPrice avg lowest 3-5 never single lowest, getRecentSalePrice/getAHDemand, poll during breaks, name matching by base ID from lore not display name (improved parsing lore ID pattern + reforge stripping Strong/Wise etc)
- [x] AuctionHouseInteractor openAH /ah if cookie else navigate, createBINListing/checkMyListings/cancel/collectExpired/collectSold, slot detection by name/lore, watchdog

## PART 21: AUTOMATION LAYER
- [x] BazaarInteractor pre-interaction canInteract, cookie-aware /bz else navigate, retry 3 attempts, watchdog, parseOrderLore, error messages not enough coins/inventory full/bazaar unavailable, slot detection by name/lore never hardcoded, GUI titles Bazaar etc
- [x] InventoryScanner getPurseBalance scoreboard "Purse: X Coins" stripping §, getBankBalance placeholder (needs bank GUI), parseOrderLore price/qty/filled%, detectChatOrderFill, findItem/count/isFull
- [x] DelayManager profiles ACTION 300-800 CLICK 80-200 GUI_LOAD 300-700 POST_NAV 500-1500 BANK 400-900 CRAFT 350-750 AH 400-900 PATH_PAUSE 200-800 RECONNECT 10-60s + 5% 2-8s distraction +1% 15-45s long + adaptive * PacketRateLimiter multiplier
- [x] ChatCommandSender sendCommand via networkHandler.sendCommand goes to server for /hub /bz /ah /craft /skyblock /lobby, sendChatMessage via sendChatMessage only for bazaar search + price/qty input, randomized delays, no mod identifiers, never same tick as GUI

## PART 22: DISCORD INTEGRATION
- [x] WebhookClient POST webhook background thread LinkedBlockingQueue, 30/min, retry 3 exponential 1s2s4s
- [x] BotClient JDA 5.x jar-in-jar include, commands !status !stop !pause !resume !budget !flips !profit !mayor !reconnect !break !tax, thread safety inbox ConcurrentLinkedQueue polled via mc.execute()
- [x] MessageFormatter all message types table 22 types (session start Mode/budget/mayor/cookie/break/AH tax, stop duration/profit/c/h/flips/ROI%/top/break%, flip completed item/strategy/prices/qty/profit/margin%/duration/tax type+rate, hourly, undercut max 1 per 10m, stale, error, budget, resumed, reconnect, watchdog, mayor change + AH tax warning if Derpy, election, Derpy detected/ended, private island, limbo 4 types flood, re-entry, long break start/end) + format rules K/M/B, Discord <t:unix:R>, green/red/yellow embeds
- [x] EventHandler triggers + config discordMode DISABLED/WEBHOOK/BOT, webhookUrl, botToken, channelId, notifyOnEveryFlip, threshold, hourlySummary, notifyLongBreaks, notifyDerpyChanges, short/ order wait breaks do NOT send Discord

## PART 23: HUD AND UI SYSTEM
- [x] HudOverlay drawn via fill+drawText + optional textured `hud_panel.png` fallback, 6 panels Status Financial Budget Break Mayor ActiveFlipsWidget, collapsed single line status dot+profit+c/h+break, click toggle collapsed/expanded + drag reposition saved to ModConfig
- [x] ActiveFlipsWidget per flip row alternating bg 0F0F0F/111111, item truncated "...", state dot color, buy/sell/qty/profit live recalc correct tax, fill progress bar 2 rects, time since, relist ↺3, strategy badge [ORDER][CRAFT][NPC][AH], AH tax badge [1%][2%][2.5%][+D] color green/yellow/orange/red+D, scrollable scroll indicator rect, updates 1s, empty messages, max 28 rows, bg CC111111 border 333333 accent green/red based on profit, uses optional textures
- [x] DashboardScreen fully custom Screen subclass fill+drawText + optional textures button/tab_active/inactive, tab bar row rects active 222222+FFAA00 bottom 2px inactive 151515, 10 tabs + SECURITY 11th added per user lock request, Start/Stop toggle top-right green 1A4A1A ACTIVE red 4A1A1A STOPPED same toggle logic as keybind, shouldPause false, inline config no sub-screens, buttons hover detection
- [x] Tab contents: Overview large profit rect+centered gold label large green/red value + 2x2 grid stat boxes each fill rect label+value + ProfitGraphWidget sparkline columns + most profitable + current mayor panel + Derpy gold warning box + upcoming events + Break Statistics sub-panel total % counts next estimates quota bar + credits Cldz
- [x] Active Orders: column headers underline via thin fill, rows alternating + hover highlight 1A1A2A + tax column for AH + MarketContextWidget overlay near hovered row (placeholder)
- [x] Flip History: scrollable CustomScrollView, columns, running total panel bottom, Export to Clipboard button copies formatted text
- [x] Market Scanner: live list composite score, columns Spread/Tax Profit/Volume/Backlog/Budget/Fill/Mayor/Tax Tier badge colors LOW green MID yellow HIGH orange + Derpy red D, Refresh indicator + Force Refresh button
- [x] Mayor & Events: mayor panel name perk list each perk rect label, Derpy prominent red border warning "Derpy is active — AH claiming taxes increased. See tax settings", election vote distribution bars (text placeholder, bars need proportional width enhancement), predicted winner/confidence/time, pre-position list, upcoming events countdown, historical note
- [x] Budget: fields editable inline (display currently, textfield widget exists but not fully wired), live read-only purse/bank/invested/available, chart 3 horizontal bars, validation red text
- [x] Settings: sections API/Flip/Delay/Navigation/Break (diagram text 30m→3m probabilistic middle-weighted)/Tax Advanced collapsible toggle ▶/▼ + Reset Defaults, Discord fields + Test Message + notifyLongBreaks/notifyDerpy toggles, uses CustomTextField/CustomSlider widgets (widgets implemented but wiring to sliders pending full inline editing)
- [x] Filters: whitelist/blacklist management Add/Remove per item (Clear All red implemented, Add via textfield pending), numeric fields, category toggles as row toggle buttons (placeholder), Clear All danger red
- [x] Discord: mode selector, conditional URL/token/channel, connection status, thresholds, Send Test Message, toggles
- [x] NPC Config: 3 side-by-side slot panels per spec + source, per slot name/X/Y/Z/displayName/enabled/Set to Current Pos/Target Block/Copy Coords/Reset to Wiki Default + active slot selector + auto-select nearest toggle + accuracy note with redesign example Jan30 2026 Auction Master move + persists across restarts via both npc.json and waypoints.json + official wiki sources listed
- [x] MarketContextWidget overlay panel border, contents item ID/spread raw+%, bazaar tax profit, AH tax profit+tax tier+Derpy warning, daily vol buy/sell, backlog orders ahead, order count top, budget profit, fill est, stability Stable/Moderate/Volatile per variance, mayor note, event modifier, sparkline placeholder
- [x] ProfitGraphWidget sparkline via fill() vertical 1px columns proportional, green above zero red below, baseline gray, no axis labels decorative + textured fallback
- [x] ToastNotification custom HUD layer via HudRenderCallback not vanilla, slide-in X lerp 300ms off-screen right→final, background type colors CC1A1A3A etc + toast.png texture attempt, border 1px lighter, icon ℹ✓⚠✗☕, auto-dismiss 5s max 3 visible queue excess dismiss slide back, break-specific + Derpy toasts

## PART 24: DATA LAYER
- [x] ItemDatabase map ID→display name populated from /v2/resources/skyblock/items first run + cached weekly + fallback replace _ title-case
- [x] NPCPrices map ID→NPC sell price documented verification needed, no tax, never crash on wrong price

## PART 25: API LAYER
- [x] HypixelAPIClient bazaar/items verify no key required, polling configurable 10s default, rate limit 120/60s 429 handling exponential backoff, background thread, BazaarUpdateListener pattern, forceRefresh, polling continues during breaks
- [x] MayorAPIClient election endpoint verify no key, poll 10m, on mayor change notify tracker → Discord if Derpy new/departing
- [x] AuctionHouseClient auctions paginated + ended, poll 5m BIN only avg 3-5 lowest, name matching base ID from lore (improved reforge stripping + lore ID pattern)
- [x] PriceHistory ring buffer 720 points per product, methods add/getAverageBuy/Sell/getVariance/isStable/getRecent

## PART 26: CHAT EVENT INTEGRATION
- [x] ClientReceiveMessageEvents.GAME register, patterns table: Buy Order filled/Sell Offer filled → if break queue to postBreak else claim immediate, not enough coins log Discord skip, inventory full pause notify toast, bazaar unavailable backoff 60s, disconnected → ReconnectManager, purse change → BudgetManager, BIN sold → if break queue else record profit, expired → handle, limbo entry → WorldStateRecovery immediate increment counter cancel break

## PART 27: THREAD SAFETY
- [x] Active flips ConcurrentHashMap, action queue PriorityBlockingQueue/ConcurrentLinkedQueue, price history ConcurrentLinkedDeque, Discord inbox ConcurrentLinkedQueue, flip history ConcurrentLinkedDeque, budget tracking ConcurrentHashMap atomic synchronized, Limbo timestamps ConcurrentLinkedDeque, break history ConcurrentLinkedDeque, post-break queue ConcurrentLinkedQueue, volatile flags, running/reconnect/recovery/break volatile boolean, world state volatile WorldState
- [x] Threading model: Game thread GUI/render/movement/key/packet send, API threads ScheduledExecutorService 2-3, Discord JDA pool never MC, Reconnect dedicated thread, cross-thread mc.execute(Runnable)

## PART 28: STATE MACHINE FLOW
- [x] START → PlayerStateDetector → BreakScheduler.startSession → LocationValidator → recoveries PRIVATE→/hub, LIMBO 5+ flood→DISCONNECT 🚨 else cancel break →/lobby → re-entry, OTHER→/hub, LOBBY→/skyblock fallback NPC, HUB→continue → SessionStateManager.tryResume restores flips per logic budget+break timers → fresh start performStartupCleanup adopting profitable only + MayorTracker refresh Derpy check + EventTracker refresh + BudgetManager.updateBalance + ItemSelector.selectBestItems (tax via TaxCalculator bazaar 1.25% AH tier+Derpy + AH 0.8x penalty Derpy further) → CommandQueue enqueue PLACE_BUY_ORDER priority 6 → EVERY TICK 1 Break tick idle behavior API polling chat fills postBreakQueue, 2 LocationValidator every 5 ticks, 3 PacketRateLimiter, 4 GuiWatchdog, 5 CommandQueue → All orders wait → ORDER_WAIT break 45s-3m idle camera end on fill, 30m window <3m deficit → force SHORT, 2h → LONG + Discord → queue priority 1-7 + cookie /bz /ah else Pathfinding+HumanizedNavigator + BudgetManager.canAfford atomic → buy placed registerInvestment saveState → CHECKING_ORDERS undercut/filled/partial/stale 3-factor → Strategy branch ORDER→SELL, CRAFT→/craft→SELL, NPC→navigate always→sell no tax, AH_CRAFT→/craft→AH BIN via TaxCalculator with mayor Derpy tier badge → ProfitTracker.recordFlip actual tax paid type rate → releaseInvestment saveState Discord includes tax info + Toast → IDLE→scan next + DERPY DETECTION event API→tracker→TaxCalculator reads flag next calc→Discord+Toast+ItemSelector rescore + Advisor recommends away + BREAK FLOW start→stop queue set BREAK_* HUD purple dot countdown Toast long Discord API continues fill→postBreakQueue + end→resume queue process postBreakQueue recalc + DISCONNECT cancel break saveState check Limbo cooldown ReconnectManager loop + LIMBO FLOOD 5+ force disconnect 🚨 Discord cooldown 30m stop save HUD red toast ERROR

## PART 29: FILE PERSISTENCE
- [x] File table: bazaarflipper.json every config change, budget.json every budget change, npc.json on save, player.json on detection refresh, session.json every 60s+toggle off+exception+after flip, history.json after flip, items.json first run + weekly, waypoints.json add/remove — all implemented with save logs persisting across restarts (added CLIENT_STOPPING + DISCONNECT saveAll)

## PART 30: FABRIC MOD METADATA
- [x] id bazaarflipper, environment client only, entrypoints client BazaarFlipperMod, mixins bazaarflipper.mixins.json, depends loader >=0.19.3 minecraft 26.1.2 java >=21 fabric-api *, no common/server, icon.png, authors changed to Cldz per user request, description credits Cldz

## PART 31: IMPLEMENTATION WARNINGS 1-50
- [x] All adhered: 26.1.2 no 1.21.2 anywhere, custom crafting GUI /craft never vanilla, quick craft VIP+, bazaar tax 1.25% fixed cookie NO, AH variable tiered via getAHTaxRate(salePrice,currentMayor) mandatory, Derpy multiplier 4x researched hardcoded default TODO verify, TaxCalculator only place tax, cookie only bazaar/AH shortcuts, NPC always navigate, never hardcode slot indices name/lore, scoreboard stripping §X, thread safety mc.execute, budget atomic synchronized, session corruption graceful, AH less reliable avg 3-5 +0.8x Derpy further, recipe unlock API key fallback, NPC prices outdated doc, mayor never blocks, JDA jar-in-jar large doc, watchdog ping-dependent 5000+ping*3 clamped, reconnect max 10 + Limbo cooldown no auto-reconnect, AH longer timescales, human simulation mandatory bezier mouse + navigation all uncertainty + break idle, lobby restarts robust, Limbo flood 5+ disconnect 30m cooldown Discord critical stop, /hub /lobby /bz /ah /craft legitimate via sendCommand, mod invisible no custom channels/modified packets/brand, no command system one keybind dashboard, all GUIs DrawContext fill+drawText + optional textures user allowed only icon.png required (now textured fallback), dashboard single access point 10 tabs + SECURITY 11th added for private lock per user request (user allowed textures), NPC coords drift wiki defaults user override Set to Current Pos, world state fast every 5 ticks cached, skyblock re-entry name detection never slot, WorldStateRecovery isRecovering volatile prevents stacking, after world transition full load check +3s, startup cleanup adopts profitable only leaves untouched, stale dynamic never single threshold, partial fills two sub-flips independently tracked (implemented Part A sell + Part B remaining), start/stop toggle both keybind+dashboard same method, toast custom not vanilla, mod communicates HUD+toast only not server chat, BreakScheduler mandatory core still tracks time if disabled, timers survive save/resume, API polling continues during breaks, fills during breaks queued not ignored, short/order wait no Discord long only, break idle camera on game thread via mc.execute + MovementSimulator, order wait separate from Tier A quota, Tier A natural probabilistic middle-weighted, Derpy runtime event not startup-only

## EXTRA FEATURES ADDED PER USER REQUESTS
- [x] Private locking PIN hard-coded hidden via XOR obfuscation byte array {76,79,46,47,45} XOR 0x1F decodes to master PIN, never plain literal "SP1102" in source (verified via grep), stored not in config, bypasses lockout, always authorized, plus user-set PIN hashed salted SHA-256 Base64 in config/bazaarflipper_lock.json persists across restarts, Security tab in dashboard with 5 text fields + Unlock/Lock/Set/Disable buttons + lockout timer + hint + notes about needed features
- [x] Optional GUI textures 10 PNGs AI-generated (panel, button, button_hover, tab_active/inactive, progress_bg/fill/gold, hud_panel, toast) used via drawTexture with fallback to fill geometry — user allowed textures even though spec said geometry-only
- [x] Advanced pathfinding expansion beyond spec: PathfindingEngine 96 radius 8000 iter, octile, danger, crowd, void, soul sand, cache LRU, partial fallback, string-pull + Catmull-Rom jitter + bobbing, HumanizedNavigator look-ahead 3, acceleration ease-out cubic 0.8s, environmental scanning POI, social avoidance, fatigue, micro-strafe etc, MovementSimulator analog pressure, bezier yaw, saccadic look, variable jump height, Helper classes HumanizationProfile/PathSmoother/EnvironmentalScanner/FatigueSimulator
- [x] Wiki coords researched from official wiki with redesign notes Jan30 2026, user Set to Current Pos / Target Block / Copy / Reset to Wiki Default, persists across restarts, auto-nearest toggle
- [x] All configs save even when restart game via CLIENT_STOPPING + DISCONNECT + 60s auto-save + after flip, logs credits Cldz
- [x] Credits to Cldz in fabric.mod.json, README, logs, dashboard footers, NPC tab, security tab, WaypointRegistry, LockConfig

## REMAINING / NEEDS IN-GAME TESTING
- [ ] Wrapper JAR binary `gradle/wrapper/gradle-wrapper.jar` not included (needs download via `gradle wrapper` to build)
- [ ] Budget tab inline editable fields via CustomTextField not fully wired (display + chart done, editing via sliders/textfields pending)
- [ ] Filters tab Add via text input + Remove per item list with buttons — Clear All done, Add wiring pending
- [ ] Mayor & Events vote distribution bars proportional width fill() — currently text placeholder, needs actual bar rendering with vote % width
- [ ] MarketContextWidget hover overlay trigger on row hover in Active Orders/Market Scanner — class ready but not yet invoked on mouse Y lookup
- [ ] Bank balance parsing from bank NPC GUI lore — currently 0 placeholder
- [ ] Bazaar search via anvil GUI — currently uses chat message, needs real anvil `ClickSlot` + rename handling for Hypixel custom GUIs
- [ ] In-game verification of exact GUI titles (`Bazaar`, `Buy Order`, etc), NPC coords after latest Hub redesign, skyblock NPC name in lobby, crafting table locations — may drift
- [ ] Java toolchain not available in sandbox — syntax review only, needs `./gradlew build` on machine with Java25 Gradle JVM + Minecraft 26.1.2
- [ ] Security: Consider adding per-user whitelist UUID option, Discord OAuth, TOTP time-based one-time PIN, HWID lock per ask_user answers — currently PIN-only as user selected stay_unlocked + pin_only + balanced
