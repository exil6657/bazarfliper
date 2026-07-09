# Packaging Bazaar Flipper into .jar — Complete Guide (Credits: Cldz)

This guide explains how to turn the GitHub export into a working `.jar` mod file for Minecraft 26.1.2 Fabric Loader 0.19.3, based on official wiki research for in-game testing alternatives.

## Research Summary for In-Game Testing Alternatives (Wiki/Fandom)

Since you cannot easily confirm in-game via the mod, all needed data was researched from official sources:

### Bazaar GUI Flow (from wiki.hypixel.net/Bazaar + fandom Bazaar + Hypixel forum guide)
- **Access:** Speak Bazaar NPC at Bazaar Alley `-32.5 71 -76.5` (also `-33.5 73 -22.5`) or `/bz` / `/bazaar` with Booster Cookie active. Bonus: `/bz <item>` (e.g. `/bz enchanted coal`) **quickly finds item** — bypasses search sign.
- **Main GUI:** 5 sections Farming/Mining/Combat/Woods & Fishes/Oddities. Bottom row historically Search sign (Oak Sign) in Auction House, but Bazaar uses direct item icons.
- **Buy Instantly:** Click button → choose amount up to **71,680** for commodity (256 for unstackable). 3 presets: `64, 160, 1024` for stackable, `1,4,64` for unstackable. Confirming buys from cheapest sell offers at price 4% higher than cheapest to avoid restart, refund if goes below.
- **Sell Instantly:** Sells all Bazaar items in inventory (Essence excluded). Right-click allows quantity selection. `Sell Sacks Now` grabs from Sacks.
- **Create Buy Order:** First quantity via sign (click sign → sign GUI → first line = quantity up to 71,680). After quantity, unit price must be set via 3 presets: **Same as current highest buy order**, **0.1 coins more than highest buy order**, **5% of difference between lowest sell and highest buy**. Custom price also via sign (first line price). Limited to realistic range else error refund.
- **Create Sell Offer:** Same flow but Sell Offer. Pre-defined price options: **Same as Best Offer**, **Best Offer -0.1**, **10% of Spread** (lowest sell - 10% of spread lowest sell - highest buy). Custom price via sign, limited to 50% above lowest existing sell offer unless none.
- **Manage Orders:** Title `Your Bazaar Orders` per spec. View % completion, top vendors, collected items/coins. Left-click to claim, Right-click for options! → **Flip Order** if fully filled (easily create Sell Offer from Buy Order items). Right-click cancel if nothing to claim.
- **Sign GUI:** Hypixel uses fake sign at `0,0,0` with `UpdateSignC2SPacket`. Our mod's `SignInteractor` waits for `SignEditScreen` (or any screen class name containing "sign") after clicking sign slot (`Custom Amount` / `Custom Price`), types human-like 80-200ms/char, then closes to submit packet. This matches Bazaar Utils mod behavior (clicks sign, enters text automatically, closes after 1.5s). Implemented fallback to chat message if sign fails.

### Auction House GUI (fandom Auction House)
- **NPCs:** Auction Master current `-39.5 73 -12.5` after Jan 30 2026 redesign (old `-46.5 73 -90.5`), 4 Auction Agents `-31 73 -85.5`, `-36 73 -85.5`, `-31 73 -95.5`, `-36 73 -95.5`.
- **Bottom row:** `Search (Oak Wood Sign)` → click → sign GUI → type name of item (our `searchAH(query)` does exactly this). Then `Item Tier (Eye of Ender)` for COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC/DIVINE/SPECIAL/VERY SPECIAL, `Sort (Hopper)` Highest Bid/Lowest Bid/Ending Soon/Most Bids, `BIN Filter` Powered Rail=Show All, Gold Ingot=BIN only, Gold Block=Auctions Only.
- **BIN Price:** When creating auction, place item in empty slot, set BIN price via sign or anvil (we handle sign first, fallback chat). Then Create BIN + Confirm.
- Our implementation: `AuctionHouseInteractor.openAH()` uses `/ah` if cookie else navigate to `auction_house_npc` wiki coords, `searchAH` uses sign per fandom, `createBINListing` handles sign price.

### Scoreboard / Purse / Bank
- **Purse:** Scoreboard sidebar line `Purse: %vault_eco_balance_commas%` or `Piggy` if Piggy Bank talisman owned. Format: `Purse: 1,234,567 Coins` or `Purse: 1,234,567` with `§6` color. Our `InventoryScanner.getPurseBalance()` strips `§.` color codes via regex `§.`, removes `Purse:` and `Coins` and commas, parses double via regex fallback `[^0-9.]`.
- **Bank:** Accessed via Banker NPC `-29.5 72 -38`. Bank GUI lore contains `Coins in bank: X` or similar. Real implementation opens Bank GUI and parses lore line containing bank balance. Currently placeholder returns 0 but structure ready. Update `getBankBalance()` to open bank waypoint and parse via `parseOrderLore` style.
- **Objective:** Scoreboard line `Objective` below purse.

### Tax & Derpy Research (already used)
- Bazaar tax 1.25% fixed per Coflnet guide, reducible to 1.125% free community upgrade + 1% paid gems via Elizabeth NPC — spec says 1.25% fixed cookie NOT affect, configurable for future.
- AH tiers: Under 10M 1%, 10M-100M 2%, Over 100M 2.5% per SkyCofl guide. Derpy quadruples taxes 4x per forum thread 5739552 + NamuWiki + Coflnet (AH claiming tax increased). During Derpy, AH claiming: 1%→4%, 2%→8%, 2.5%→10%. BZ 1.25%→5%. We implemented multiplier 4.0 configurable.

## How to Package into .jar After GitHub Export

### Option 1: Automatic via GitHub Actions (easiest, no local Java needed)

1. **Push to GitHub:** Your repo already has `.github/workflows/build.yml`. Every push to `main` or `arena/*` triggers build.
2. **Wait for Action:** Go to GitHub → `Actions` tab → click latest `Build Bazaar Flipper` run → wait 2-4 minutes (downloads Minecraft 26.1.2, Loom, Fabric API, JDA).
3. **Download Artifact:** At bottom of action page, `Artifacts` section → `bazaar-flipper-jar` → download ZIP → extract → inside `build/libs/` you get `bazaar-flipper-6.0.0.jar` (or version from `gradle.properties` `mod_version`).
4. **That's your mod jar** — already includes JDA 5.x via `include` jar-in-jar, GSON, etc.

### Option 2: Local Build (full control)

#### Prerequisites
- **Java:** 21+ (compile target 21), but Gradle JVM for 26.1.2 per Fabric docs requires **Java 25+** for Gradle daemon. Install Temurin JDK 21 for compile + JDK 25 for Gradle (or just 25 which can compile 21 target). Set `JAVA_HOME` to JDK 25 path.
- **Gradle:** 9.4.0 (per wrapper properties). Wrapper `gradlew` will download if you run it, no manual install needed if you have wrapper jar (currently properties only, not jar — run `gradle wrapper --gradle-version 9.4.0` once to generate jar, or use system Gradle).
- **Git:** to clone repo.

#### Steps
```bash
# 1. Clone your export
git clone https://github.com/exil6657/bazarfliper.git
cd bazarfliper
git checkout arena/019f46ad-bazarfliper   # your working branch

# 2. Make wrapper executable (Linux/Mac)
chmod +x gradlew

# 3. Generate wrapper jar if missing (requires local gradle install)
# If you have gradle installed:
gradle wrapper --gradle-version 9.4.0
# Else download wrapper jar manually from https://github.com/gradle/gradle/raw/master/gradle/wrapper/gradle-wrapper.jar into gradle/wrapper/

# 4. Build
./gradlew build --stacktrace
# On Windows: gradlew.bat build

# 5. Find jar
ls build/libs/
# You should see:
# bazaar-flipper-6.0.0.jar        <- remapped mod jar with JDA included
# bazaar-flipper-6.0.0-sources.jar
```

#### Why `remapJar` and `include` matters
- `build.gradle` uses `loom` + `mappings loom.officialMojangMappings()` + `fabric.loom.disableObfuscation=true` for 26.1+ unobfuscated.
- `implementation "net.dv8tion:JDA:5.2.1"` + `include` → Loom puts JDA inside mod jar via jar-in-jar, avoiding classpath conflicts.
- `minecraft "com.mojang:minecraft:26.1.2"` + `modImplementation "net.fabricmc:fabric-loader:0.19.3"` + `modImplementation "fabric-api:0.154.0+26.1.2"` must match `fabric.mod.json` depends.

#### Troubleshooting
- **`No such file gradle/wrapper/gradle-wrapper.jar`**: Run `gradle wrapper` locally or download jar from Gradle GitHub.
- **`Unsupported Java`**: Fabric 26.1 requires Gradle JVM 25+ → set `org.gradle.java.home` in `gradle.properties` or `JAVA_HOME` to JDK 25.
- **`Could not resolve minecraft:minecraft:26.1.2`**: Check internet, Fabric Maven https://maven.fabricmc.net/ reachable, `minecraft_version` correct (no 1.21.2 anywhere).
- **`Mixin apply failed`**: Ensure you use IntelliJ IDEA 2025.3+ for mixins per Fabric 26.1 notes, or run with `--no-daemon`.
- **Large JDA dependency**: Jar will be ~15-20MB due to JDA + OkHttp etc included. This is documented in README.

### Option 3: Manual Jar via IDE (IntelliJ)

1. Open project in IntelliJ IDEA 2025.3+ (required for 26.1 mixins).
2. Set Project SDK to 21, Gradle JVM to 25 in `Settings → Build Tools → Gradle`.
3. Sync Gradle (`Reload All Gradle Projects`).
4. Run Gradle task `build` from Gradle panel.
5. Jar in `build/libs/`.

## Installing the Built Jar

1. Install Fabric Loader 0.19.3 for 26.1.2 from https://fabricmc.net/ or via official launcher: Java Edition → dropdown beside Play → `fabric-loader-26.1.2` (see sportskeeda guide How to download Minecraft Fabric 26.1.2).
2. Install Fabric API 0.154.0+26.1.2 jar into `mods/` folder (download from Modrinth).
3. Put `bazaar-flipper-6.0.0.jar` into `mods/` folder (`%appdata%\.minecraft\mods` on Windows, `~/.minecraft/mods` on Linux/Mac, or game directory if custom).
4. Launch Fabric 26.1.2 profile.
5. In-game: Press Right Control → Dashboard. First time: Go to `Security` tab → Set PIN (hardcoded master PIN hidden via XOR is also valid, per your request) → Set to current pos for NPCs if coords drifted → Settings → configure API key, Discord, taxes, breaks → Toggle HUD visibility unbound key → Right Shift to start flipping.
6. All configs saved to `config/bazaarflipper*.json` + `bazaarflipper_waypoints.json` persisting across restarts. Credits: Cldz.

## Distribution

- When sharing, provide `bazaar-flipper-6.0.0.jar` + note requires Fabric Loader 0.19.3 + Fabric API.
- Do not share your `config/bazaarflipper_lock.json` if it contains your private PIN hash (though hashed, still private). Master PIN hidden in code works for your authorized distribution.
- License MIT, authors Cldz per `fabric.mod.json`.

## Credits
All research from official wikis:
- https://wiki.hypixel.net/Bazaar, https://hypixel-skyblock.fandom.com/wiki/Bazaar
- https://wiki.hypixel.net/Auction_House, https://hypixel-skyblock.fandom.com/wiki/Auction_House
- https://hypixelskyblock.minecraft.wiki/w/NPC/List/Hub, https://wiki.hypixel.net/Bazaar_(NPC), https://wiki.hypixel.net/Auction_Master history (Jan30 2026 redesign)
- https://hypixel.net/threads/bazaar-how-it-works-in-depth.5493906/, https://hypixel.net/threads/wip-guide-the-full-guide-to-bazaar-flipping.2943414/
- Coflnet guides for tax, forum thread 5739552 for Derpy 4x tax
- Packaging docs per Fabric 26.1 blog https://fabricmc.net/2026/03/14/261.html + Loom 1.15/1.17 + Gradle 9.4.0

— Cldz
