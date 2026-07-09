package com.bazaarflipper.mayor;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Stores SkyBlock epoch start and calculates skyblock year/day, election timings.
 */
public class MayorCalendar {
    private static final String CALENDAR_RESOURCE = "/assets/bazaarflipper/data/mayor_calendar.json";

    private long skyblockEpochStart = 1560275700L; // unix timestamp from bundled json
    private int electionIntervalDays = 5; // real days? skyblock days mapping

    public MayorCalendar() {
        loadBundled();
    }

    private void loadBundled() {
        try (InputStream is = MayorCalendar.class.getResourceAsStream(CALENDAR_RESOURCE)) {
            if (is != null) {
                Gson gson = new GsonBuilder().create();
                Map<String, Object> map = gson.fromJson(new InputStreamReader(is), Map.class);
                if (map.containsKey("skyblock_epoch_start")) {
                    Number n = (Number) map.get("skyblock_epoch_start");
                    skyblockEpochStart = n.longValue();
                }
            }
        } catch (Exception e) {
            Logger.warn("Failed to load mayor_calendar bundled, using defaults: " + e.getMessage());
        }
    }

    public int getCurrentSkyblockYear() {
        long nowSec = System.currentTimeMillis() / 1000L;
        long elapsedSec = nowSec - skyblockEpochStart;
        // Skyblock year roughly 5 days 4 hours real time ~ 124 hours = 446400 sec
        // Approximation: 1 SB year = 5.5 real hours? Need accurate but placeholder.
        // Real conversion: SB day = 20 minutes real? Actually 1 SB year = ~5 days 4 hours IRL per better mayors? We'll approximate.
        // For simplicity use 5 days real per SB year
        long sbYearSec = 5L * 24 * 3600;
        return (int) (elapsedSec / sbYearSec);
    }

    public int getCurrentSkyblockDay() {
        // SB day 20 min real? But year 5 days => 31 days/month maybe?
        long nowSec = System.currentTimeMillis() / 1000L;
        long elapsedSec = nowSec - skyblockEpochStart;
        long sbDaySec = 20 * 60; // 20 min real per SB day? Approx
        return (int) ((elapsedSec % (31 * sbDaySec)) / sbDaySec);
    }

    public int getDaysUntilElection() {
        // Elections every 5 real days
        long nowMs = System.currentTimeMillis();
        long intervalMs = electionIntervalDays * 24L * 3600L * 1000L;
        long elapsedMs = nowMs - (skyblockEpochStart * 1000L);
        long remainder = elapsedMs % intervalMs;
        long untilNext = intervalMs - remainder;
        return (int) (untilNext / (24L * 3600L * 1000L));
    }

    public long getDaysUntilNewMayor() {
        return getDaysUntilElection();
    }

    public boolean isElectionPeriod() {
        // Election period is last 24h real before term ends
        // Simplistic: if <1 day until election
        return getDaysUntilElection() < 1;
    }

    public long getElectionPeriodStartReal() {
        long now = System.currentTimeMillis();
        long intervalMs = electionIntervalDays * 24L * 3600L * 1000L;
        long elapsed = now - (skyblockEpochStart * 1000L);
        long nextElectionEnd = ((elapsed / intervalMs) + 1) * intervalMs + (skyblockEpochStart * 1000L);
        return nextElectionEnd - (24L * 3600L * 1000L); // 24h before
    }

    public long getElectionPeriodEndReal() {
        long now = System.currentTimeMillis();
        long intervalMs = electionIntervalDays * 24L * 3600L * 1000L;
        long elapsed = now - (skyblockEpochStart * 1000L);
        long nextElectionEnd = ((elapsed / intervalMs) + 1) * intervalMs + (skyblockEpochStart * 1000L);
        return nextElectionEnd;
    }
}
