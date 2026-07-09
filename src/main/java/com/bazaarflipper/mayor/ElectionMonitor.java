package com.bazaarflipper.mayor;

import com.bazaarflipper.api.MayorAPIClient;
import com.bazaarflipper.util.Logger;
import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ElectionMonitor {
    private final MayorAPIClient apiClient;
    private final HttpClient httpClient;

    private final Map<String, Integer> currentVoteDistribution = new ConcurrentHashMap<>();
    private String predictedWinner = "Unknown";
    private double confidenceLevel = 0.0; // 0-1

    public ElectionMonitor(MayorAPIClient apiClient) {
        this.apiClient = apiClient;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Map<String, Integer> getCurrentVoteDistribution() {
        // Fetch from election API - same as mayor API but includes votes
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.hypixel.net/v2/resources/skyblock/election"))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("mayor") && root.getAsJsonObject("mayor").has("election")) {
                    // Sometimes election contains candidates? New API includes "current" election "candidates"
                }
                if (root.has("current") && root.getAsJsonObject("current").has("candidates")) {
                    JsonArray cands = root.getAsJsonObject("current").getAsJsonArray("candidates");
                    Map<String, Integer> map = new HashMap<>();
                    for (JsonElement el : cands) {
                        JsonObject obj = el.getAsJsonObject();
                        String name = obj.has("name") ? obj.get("name").getAsString() : "Unknown";
                        int votes = obj.has("votes") ? obj.get("votes").getAsInt() : 0;
                        map.put(name, votes);
                    }
                    currentVoteDistribution.clear();
                    currentVoteDistribution.putAll(map);
                    computePrediction();
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to get vote distribution", e);
        }
        return new HashMap<>(currentVoteDistribution);
    }

    private void computePrediction() {
        if (currentVoteDistribution.isEmpty()) return;
        int total = currentVoteDistribution.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return;
        Map.Entry<String, Integer> max = currentVoteDistribution.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        if (max != null) {
            predictedWinner = max.getKey();
            confidenceLevel = (double) max.getValue() / total;
        }
    }

    public String getPredictedWinner() {
        if (predictedWinner.equals("Unknown")) getCurrentVoteDistribution();
        return predictedWinner;
    }

    public double getConfidenceLevel() {
        return confidenceLevel;
    }

    public long getDaysUntilResult() {
        // Election results after 24h real period, simplified
        return 1; // placeholder: need MayorCalendar
    }

    public boolean shouldPrepositionForCandidate(String name) {
        return confidenceLevel > 0.6 && getDaysUntilResult() > 12; // >60% and >12h before end per spec (but days field simplified)
    }
}
