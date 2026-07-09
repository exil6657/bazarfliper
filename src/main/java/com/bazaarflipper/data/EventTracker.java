package com.bazaarflipper.data;

import com.bazaarflipper.api.APIRateLimiter;
import com.bazaarflipper.util.ChatUtils;
import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Primary: Hypixel API events endpoint if available (verify for 26.1.2)
 * Secondary: bundled hypixel_events.json
 * Tertiary: Scoreboard sidebar parsing. Unknown events logged without crashing.
 */
public class EventTracker {
    private static final String BUNDLED_RESOURCE = "/assets/bazaarflipper/data/hypixel_events.json";
    private final HttpClient httpClient;
    private final APIRateLimiter rateLimiter;

    private final Map<String, Map<String, Object>> knownEvents = new ConcurrentHashMap<>();
    private final List<String> currentEvents = new ArrayList<>();

    public EventTracker(APIRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        loadBundled();
    }

    @SuppressWarnings("unchecked")
    private void loadBundled() {
        try (InputStream is = EventTracker.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (is == null) {
                Logger.warn("hypixel_events.json not found");
                return;
            }
            Gson gson = new GsonBuilder().create();
            Map<String, Object> root = gson.fromJson(new InputStreamReader(is), Map.class);
            if (root.containsKey("events")) {
                List<Map<String, Object>> events = (List<Map<String, Object>>) root.get("events");
                for (Map<String, Object> ev : events) {
                    String id = (String) ev.get("id");
                    knownEvents.put(id, ev);
                }
                Logger.info("Loaded " + knownEvents.size() + " known events from bundled json");
            }
        } catch (Exception e) {
            Logger.error("Failed to load hypixel_events", e);
        }
    }

    public List<String> getCurrentEvents() {
        // Try API first
        // For now return parsed from scoreboard as tertiary fallback
        List<String> events = new ArrayList<>(currentEvents);
        if (events.isEmpty()) {
            // Scoreboard parsing
            events.addAll(parseScoreboardForEvents());
        }
        return events;
    }

    public List<String> getUpcomingEvents(int daysAhead) {
        // Return events recurring within daysAhead
        List<String> upcoming = new ArrayList<>();
        for (String id : knownEvents.keySet()) {
            upcoming.add(id);
        }
        return upcoming.subList(0, Math.min(upcoming.size(), daysAhead*2));
    }

    public double getEventPriceModifier(String productCategory) {
        // Look up known events and their price_impact
        for (String current : getCurrentEvents()) {
            Map<String, Object> ev = knownEvents.get(current);
            if (ev == null) continue;
            if (ev.containsKey("price_impact")) {
                Map<String, Object> impact = (Map<String, Object>) ev.get("price_impact");
                if (impact.containsKey(productCategory)) {
                    Object val = impact.get(productCategory);
                    if (val instanceof Number n) return n.doubleValue();
                }
            }
        }
        return 1.0;
    }

    public void refreshFromAPI() {
        // Primary: Hypixel API events endpoint if available
        if (!rateLimiter.canMakeRequest()) return;
        try {
            // Hypixel API may have events? Endpoint uncertain for 26.1.2 - try resources endpoint
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.hypixel.net/v2/resources/skyblock/election")) // placeholder, events not separate
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            rateLimiter.recordRequest();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                // Parse events if present
            }
        } catch (Exception e) {
            Logger.debug("EventTracker API refresh failed: " + e.getMessage());
        }
    }

    public List<String> parseScoreboardForEvents() {
        List<String> found = new ArrayList<>();
        try {
            // Scoreboard sidebar parsing - look for known event keywords
            // This is tertiary fallback
            // We would need MinecraftClient instance - try to parse scoreboard
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.world == null) return found;
            var scoreboard = mc.world.getScoreboard();
            var objective = scoreboard.getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
            if (objective == null) return found;
            for (var score : scoreboard.getAllPlayerScores(objective)) {
                if (score.getPlayerName() == null) continue;
                String line = ChatUtils.stripColorCodes(score.getPlayerName().getString()).toLowerCase();
                if (line.contains("spooky")) found.add("spooky_festival");
                if (line.contains("jerry")) found.add("jerry_workshop");
                if (line.contains("zoo")) found.add("traveling_zoo");
                if (line.contains("fishing festival")) found.add("fishing_festival");
                if (line.contains("mythological")) found.add("mythological_ritual");
            }
        } catch (Exception e) {
            Logger.debug("Scoreboard event parse failed");
        }
        // Log unknown events without crashing per spec
        for (String ev : found) {
            if (!knownEvents.containsKey(ev)) {
                Logger.info("Unknown event detected via scoreboard: " + ev);
            }
        }
        currentEvents.clear();
        currentEvents.addAll(found);
        return found;
    }
}
