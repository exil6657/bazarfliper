package com.bazaarflipper.api;

import com.bazaarflipper.mayor.MayorData;
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

public class MayorAPIClient {

    private static final String ELECTION_ENDPOINT = "https://api.hypixel.net/v2/resources/skyblock/election";

    private final HttpClient httpClient;
    private final APIRateLimiter rateLimiter;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile MayorData lastMayor;
    private volatile long lastFetch = 0;
    private final long pollInterval = 10 * 60 * 1000; // 10 min

    public interface MayorUpdateListener {
        void onMayorUpdate(MayorData newMayor, MayorData oldMayor);
    }

    private final java.util.List<MayorUpdateListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public MayorAPIClient(APIRateLimiter limiter) {
        this.rateLimiter = limiter;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void startPolling() {
        scheduler.scheduleAtFixedRate(this::fetchElectionData, 0, pollInterval, TimeUnit.MILLISECONDS);
        Logger.info("MayorAPIClient polling started");
    }

    public void stopPolling() {
        scheduler.shutdownNow();
    }

    public void addListener(MayorUpdateListener l) { listeners.add(l); }

    public void fetchElectionData() {
        // Must never block gameplay
        if (!rateLimiter.canMakeRequest()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ELECTION_ENDPOINT))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            rateLimiter.recordRequest();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 429) { rateLimiter.handle429(); return; }
            if (resp.statusCode() != 200) { Logger.warn("Mayor API non-200: " + resp.statusCode()); return; }

            MayorData parsed = parseElectionJson(resp.body());
            if (parsed != null) {
                MayorData old = lastMayor;
                lastMayor = parsed;
                lastFetch = System.currentTimeMillis();
                if (old == null || !old.getName().equals(parsed.getName())) {
                    for (MayorUpdateListener l : listeners) {
                        try { l.onMayorUpdate(parsed, old); } catch (Exception e) { Logger.error("Mayor listener error", e); }
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to fetch mayor data", e);
        }
    }

    private MayorData parseElectionJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("mayor")) {
                JsonObject mayorObj = root.getAsJsonObject("mayor");
                String name = mayorObj.has("name") ? mayorObj.get("name").getAsString() : "Unknown";
                MayorData data = new MayorData(name);
                if (mayorObj.has("perks")) {
                    JsonArray perks = mayorObj.getAsJsonArray("perks");
                    for (JsonElement el : perks) {
                        JsonObject perkObj = el.getAsJsonObject();
                        String perkName = perkObj.has("name") ? perkObj.get("name").getAsString() : "Unknown";
                        String desc = perkObj.has("description") ? perkObj.get("description").getAsString() : "";
                        data.addPerk(new MayorData.Perk(perkName, desc, java.util.List.of()));
                    }
                }
                if (mayorObj.has("election")) {
                    JsonObject election = mayorObj.getAsJsonObject("election");
                    // parse term timestamps if available
                }
                return data;
            }
            // Fallback: check current mayor structure
            if (root.has("current")) {
                JsonObject cur = root.getAsJsonObject("current");
                String name = cur.has("name") ? cur.get("name").getAsString() : "Unknown";
                return new MayorData(name);
            }
        } catch (Exception e) {
            Logger.error("Mayor parse error", e);
        }
        return null;
    }

    public MayorData getLastMayor() { return lastMayor; }
}
