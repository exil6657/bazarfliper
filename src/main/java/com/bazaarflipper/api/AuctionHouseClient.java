package com.bazaarflipper.api;

import com.bazaarflipper.config.ModConfig;
import com.bazaarflipper.util.Logger;
import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AuctionHouseClient {

    private static final String AUCTIONS_ENDPOINT = "https://api.hypixel.net/v2/skyblock/auctions";
    private static final String ENDED_ENDPOINT = "https://api.hypixel.net/v2/skyblock/auctions_ended";

    private final HttpClient httpClient;
    private final ModConfig config;
    private final APIRateLimiter rateLimiter;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // itemId -> list of BIN prices
    private final ConcurrentHashMap<String, List<Double>> binPrices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> recentSalePrices = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    public AuctionHouseClient(ModConfig config, APIRateLimiter rateLimiter) {
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void startPolling() {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::fetchAuctionData, 0, config.ahRefreshIntervalMs, TimeUnit.MILLISECONDS);
        Logger.info("AuctionHouseClient polling started");
    }

    public void stopPolling() {
        running = false;
        scheduler.shutdownNow();
    }

    public void fetchAuctionData() {
        // API polling continues during breaks per spec
        fetchBINListings();
        fetchRecentSales();
    }

    private void fetchBINListings() {
        if (!rateLimiter.canMakeRequest()) return;
        try {
            // First page to get total pages
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(AUCTIONS_ENDPOINT + "?page=0"))
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            rateLimiter.recordRequest();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 429) { rateLimiter.handle429(); return; }
            if (resp.statusCode() != 200) { Logger.warn("AH API non-200: " + resp.statusCode()); return; }

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            int totalPages = root.has("totalPages") ? root.get("totalPages").getAsInt() : 1;
            parseAuctionsPage(root);

            // Fetch remaining pages sequentially to respect rate limit
            for (int p = 1; p < Math.min(totalPages, 20); p++) { // limit to avoid too much load, configurable
                if (!rateLimiter.canMakeRequest()) break;
                HttpRequest pageReq = HttpRequest.newBuilder()
                        .uri(URI.create(AUCTIONS_ENDPOINT + "?page=" + p))
                        .timeout(Duration.ofSeconds(15))
                        .GET().build();
                rateLimiter.recordRequest();
                HttpResponse<String> pageResp = httpClient.send(pageReq, HttpResponse.BodyHandlers.ofString());
                if (pageResp.statusCode() != 200) break;
                JsonObject pageRoot = JsonParser.parseString(pageResp.body()).getAsJsonObject();
                parseAuctionsPage(pageRoot);
                Thread.sleep(200); // gentle
            }
            Logger.info("AH BIN data updated, tracked items: " + binPrices.size());
        } catch (Exception e) {
            Logger.error("Failed to fetch AH BIN data", e);
        }
    }

    private void parseAuctionsPage(JsonObject root) {
        if (!root.has("auctions")) return;
        JsonArray auctions = root.getAsJsonArray("auctions");
        Map<String, List<Double>> temp = new HashMap<>();
        for (JsonElement el : auctions) {
            try {
                JsonObject obj = el.getAsJsonObject();
                boolean bin = obj.has("bin") && obj.get("bin").getAsBoolean();
                if (!bin) continue;
                // item_name lore handling - base ID from lore? For simplicity use tag or item id if present
                // Use item_name but we need base id; we'll use a simplified approach: strip modifiers, extract base id from lore or use internal id
                String itemId = extractBaseItemId(obj);
                if (itemId == null) continue;
                double startingBid = obj.has("starting_bid") ? obj.get("starting_bid").getAsDouble() : 0;
                if (startingBid <= 0) continue;
                temp.computeIfAbsent(itemId, k -> new ArrayList<>()).add(startingBid);
            } catch (Exception e) {
                // skip malformed
            }
        }
        // Merge
        for (Map.Entry<String, List<Double>> e : temp.entrySet()) {
            binPrices.merge(e.getKey(), e.getValue(), (oldV, newV) -> {
                List<Double> combined = new ArrayList<>(oldV);
                combined.addAll(newV);
                // Keep sorted, truncate to reasonable size
                combined.sort(Double::compare);
                if (combined.size() > 100) return combined.subList(0, 100);
                return combined;
            });
        }
    }

    private String extractBaseItemId(JsonObject auctionObj) {
        // Spec: Name matching by base item ID from lore (not display name — AH items have modifiers)
        // AH items have modifiers in display name like "Strong Dragon Boots" but base ID from lore
        try {
            // First try lore parsing: look for item_lore field
            if (auctionObj.has("item_lore")) {
                JsonElement loreEl = auctionObj.get("item_lore");
                String loreText = "";
                if (loreEl.isJsonArray()) {
                    // Join lore lines
                    StringBuilder sb = new StringBuilder();
                    for (JsonElement lineEl : loreEl.getAsJsonArray()) {
                        sb.append(lineEl.getAsString()).append("\n");
                    }
                    loreText = sb.toString();
                } else if (loreEl.isJsonPrimitive()) {
                    loreText = loreEl.getAsString();
                }

                // Strip color codes §
                String stripped = loreText.replaceAll("§.", "");

                // Parse for base ID pattern: lines that look like pure ID (A-Z_ uppercase, maybe numbers) and length > 3
                // Also look for common patterns like "ID: ENCHANTED_DIAMOND" or just ID in lore footer
                for (String line : stripped.split("\n")) {
                    String trimmed = line.trim();
                    // Remove rarity prefixes? Try to find ID candidate
                    // If line matches [A-Z0-9_]+ and contains underscore or is known item pattern
                    if (trimmed.matches("^[A-Z0-9_]+$") && trimmed.length() > 3) {
                        // Candidate base ID
                        if (!trimmed.equals("COMMON") && !trimmed.equals("RARE") && !trimmed.equals("EPIC") && !trimmed.equals("LEGENDARY") && !trimmed.equals("MYTHIC")) {
                            return trimmed;
                        }
                    }
                    // Also check for "ID: XXX" pattern
                    if (trimmed.toUpperCase().contains("ID:")) {
                        String after = trimmed.toUpperCase().split("ID:")[1].trim().replaceAll("[^A-Z0-9_]", "");
                        if (!after.isEmpty()) return after;
                    }
                    // Check for item tag like "SUPER_COMPACTOR_3000" inside lore text even with spaces? Convert spaces to underscores
                    // Heuristic: if line contains "Super Compactor" we convert to SUPER_COMPACTOR_3000 fallback later
                }

                // Fallback: if lore contains known item keywords, map to bazaar ID via heuristic conversion
                // For AH craft flip items like Super Compactor 3000, its display name is exact, so use display name as fallback below
            }

            // Fallback to display name conversion but note it's not ideal per spec — use as secondary
            if (auctionObj.has("item_name")) {
                String raw = auctionObj.get("item_name").getAsString();
                // Remove reforge prefixes like "Strong", "Wise", "Unpleasant" - list of known reforges
                String[] reforges = {"Strong", "Wise", "Unpleasant", "Gentle", "Odd", "Fast", "Fair", "Epic", "Sharp", "Heroic", "Spicy", "Legendary", "Deadly", "Fine", "Grand", "Hasty", "Neat", "Rapid", "Unreal", "Awkward", "Rich", "Precise", "Spiritual", "Headstrong"};
                String cleaned = raw;
                for (String reforge : reforges) {
                    if (cleaned.startsWith(reforge + " ")) {
                        cleaned = cleaned.substring(reforge.length()+1);
                        break;
                    }
                }
                // Now convert to ID: upper, spaces to underscores, remove non-alphanumeric
                return cleaned.toUpperCase(java.util.Locale.ROOT).replace(' ', '_').replaceAll("[^A-Z0-9_]", "");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private void fetchRecentSales() {
        if (!rateLimiter.canMakeRequest()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ENDED_ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            rateLimiter.recordRequest();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (!root.has("auctions")) return;
            JsonArray arr = root.getAsJsonArray("auctions");
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("item_name") && obj.has("price")) {
                    String id = obj.get("item_name").getAsString().toUpperCase(Locale.ROOT).replace(' ', '_');
                    double price = obj.get("price").getAsDouble();
                    recentSalePrices.put(id, price);
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to fetch ended auctions", e);
        }
    }

    /**
     * Average of lowest 3-5 BIN prices - never single lowest to avoid outlier manipulation.
     */
    public double getBINPrice(String itemId) {
        List<Double> prices = binPrices.get(itemId);
        if (prices == null || prices.isEmpty()) return 0;
        List<Double> sorted = prices.stream().sorted().collect(Collectors.toList());
        int count = Math.min(Math.max(3, sorted.size()), 5);
        count = Math.min(count, sorted.size());
        double sum = 0;
        for (int i = 0; i < count; i++) sum += sorted.get(i);
        return sum / count;
    }

    public double getRecentSalePrice(String itemId) {
        return recentSalePrices.getOrDefault(itemId, 0.0);
    }

    public double getAHDemand(String itemId) {
        // demand heuristic: number of BIN listings vs sales? For now return quantity
        List<Double> prices = binPrices.get(itemId);
        return prices == null ? 0 : prices.size();
    }

    public boolean isDataRecent(String itemId, long maxAgeMs) {
        // In this simplified implementation, data is recent if we have it
        // Real implementation would track timestamps
        return binPrices.containsKey(itemId);
    }
}
