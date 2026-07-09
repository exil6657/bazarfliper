package com.bazaarflipper.api;

import com.bazaarflipper.config.ModConfig;
import com.bazaarflipper.util.Logger;
import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class HypixelAPIClient {

    public interface BazaarUpdateListener {
        void onBazaarUpdate(BazaarData data);
    }

    private static final String BAZAAR_ENDPOINT = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final String ITEMS_ENDPOINT = "https://api.hypixel.net/v2/resources/skyblock/items";

    private final HttpClient httpClient;
    private final ModConfig config;
    private final APIRateLimiter rateLimiter;
    private final PriceHistory priceHistory;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final List<BazaarUpdateListener> listeners = new CopyOnWriteArrayList<>();

    private volatile BazaarData lastData;
    private volatile boolean running = false;

    public HypixelAPIClient(ModConfig config, APIRateLimiter limiter, PriceHistory priceHistory) {
        this.config = config;
        this.rateLimiter = limiter;
        this.priceHistory = priceHistory;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void startPolling() {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::fetchBazaarDataAsync, 0, config.apiRefreshIntervalMs, TimeUnit.MILLISECONDS);
        Logger.info("HypixelAPIClient polling started (interval " + config.apiRefreshIntervalMs + "ms)");
    }

    public void stopPolling() {
        running = false;
        scheduler.shutdownNow();
    }

    public void addListener(BazaarUpdateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(BazaarUpdateListener listener) {
        listeners.remove(listener);
    }

    public void fetchBazaarDataAsync() {
        // API polling continues during breaks - independent
        if (!rateLimiter.canMakeRequest()) {
            Logger.debug("Rate limit hit, skipping bazaar fetch");
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BAZAAR_ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            rateLimiter.recordRequest();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> {
                        if (resp.statusCode() == 429) {
                            Logger.warn("429 rate limited from bazaar API");
                            rateLimiter.handle429();
                            return;
                        }
                        if (resp.statusCode() != 200) {
                            Logger.warn("Bazaar API non-200: " + resp.statusCode());
                            return;
                        }
                        try {
                            BazaarData data = parseBazaarJson(resp.body());
                            lastData = data;
                            for (BazaarUpdateListener l : listeners) {
                                try { l.onBazaarUpdate(data); } catch (Exception e) { Logger.error("Listener error", e); }
                            }
                        } catch (Exception e) {
                            Logger.error("Failed to parse bazaar data", e);
                        }
                    })
                    .exceptionally(ex -> {
                        Logger.error("Bazaar fetch exception", ex);
                        return null;
                    });
        } catch (Exception e) {
            Logger.error("Failed to send bazaar request", e);
        }
    }

    public void forceRefresh() {
        fetchBazaarDataAsync();
    }

    private BazaarData parseBazaarJson(String json) {
        BazaarData data = new BazaarData();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("products")) return data;
        JsonObject products = root.getAsJsonObject("products");
        for (String productId : products.keySet()) {
            JsonObject prodObj = products.getAsJsonObject(productId);
            ProductInfo pInfo = new ProductInfo(productId);

            // quick_status
            if (prodObj.has("quick_status")) {
                JsonObject qs = prodObj.getAsJsonObject("quick_status");
                ProductInfo.QuickStatus quick = new ProductInfo.QuickStatus();
                quick.productId = productId;
                quick.sellPrice = qs.has("sellPrice") ? qs.get("sellPrice").getAsDouble() : 0;
                quick.buyPrice = qs.has("buyPrice") ? qs.get("buyPrice").getAsDouble() : 0;
                quick.buyVolume = qs.has("buyVolume") ? qs.get("buyVolume").getAsDouble() : 0;
                quick.sellVolume = qs.has("sellVolume") ? qs.get("sellVolume").getAsDouble() : 0;
                quick.buyMovingWeek = qs.has("buyMovingWeek") ? qs.get("buyMovingWeek").getAsLong() : 0;
                quick.sellMovingWeek = qs.has("sellMovingWeek") ? qs.get("sellMovingWeek").getAsLong() : 0;
                quick.buyOrders = qs.has("buyOrders") ? qs.get("buyOrders").getAsLong() : 0;
                quick.sellOrders = qs.has("sellOrders") ? qs.get("sellOrders").getAsLong() : 0;
                pInfo.quickStatus = quick;

                // Add to price history
                priceHistory.addDataPoint(productId, quick.buyPrice, quick.sellPrice, quick.buyVolume, quick.sellVolume);
            }

            // sell_summary & buy_summary
            if (prodObj.has("sell_summary")) {
                JsonArray arr = prodObj.getAsJsonArray("sell_summary");
                pInfo.sellSummary = parseSummary(arr);
            }
            if (prodObj.has("buy_summary")) {
                JsonArray arr = prodObj.getAsJsonArray("buy_summary");
                pInfo.buySummary = parseSummary(arr);
            }

            data.products.put(productId, pInfo);
        }
        data.timestamp = System.currentTimeMillis();
        return data;
    }

    private List<OrderSummary> parseSummary(JsonArray arr) {
        List<OrderSummary> list = new java.util.ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            double amount = o.has("amount") ? o.get("amount").getAsDouble() : 0;
            double price = o.has("pricePerUnit") ? o.get("pricePerUnit").getAsDouble() : 0;
            int orders = o.has("orders") ? o.get("orders").getAsInt() : 0;
            list.add(new OrderSummary(amount, price, orders));
        }
        return list;
    }

    public BazaarData getLastData() { return lastData; }
}
