package com.bazaarflipper.discord;

import com.bazaarflipper.tracker.FlipRecord;
import com.bazaarflipper.tracker.SessionStats;
import com.bazaarflipper.util.MathUtils;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.config.ModConfig;

import java.util.List;

/**
 * Formats messages per spec table.
 */
public class DiscordMessageFormatter {

    private final ModConfig config;

    public DiscordMessageFormatter(ModConfig config) {
        this.config = config;
    }

    private String embedJson(String title, String description, int color, List<Field> fields) {
        // Minimal embed JSON for webhook
        StringBuilder sb = new StringBuilder();
        sb.append("{\"embeds\":[{\"title\":\"").append(escape(title)).append("\",");
        sb.append("\"description\":\"").append(escape(description)).append("\",");
        sb.append("\"color\":").append(color).append(",");
        if (fields != null && !fields.isEmpty()) {
            sb.append("\"fields\":[");
            for (int i=0;i<fields.size();i++) {
                Field f = fields.get(i);
                sb.append("{\"name\":\"").append(escape(f.name)).append("\",\"value\":\"").append(escape(f.value)).append("\",\"inline\":").append(f.inline).append("}");
                if (i<fields.size()-1) sb.append(",");
            }
            sb.append("],");
        }
        sb.append("\"timestamp\":\"").append(java.time.Instant.now().toString()).append("\"");
        sb.append("}]}");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\"","\\\"").replace("\n","\\n");
    }

    private static class Field {
        String name, value; boolean inline;
        Field(String n, String v, boolean i) { name=n; value=v; inline=i; }
    }

    public String formatSessionStart(String mode, double budgetCap, double reserved, int maxItems, MayorData mayor, boolean cookie, String breakScheduleSummary, String ahTaxInfo) {
        String desc = String.format("Mode: %s\nBudget: %s cap, %s reserved\nMaxItems: %d\nMayor: %s\nCookie: %s\nBreaks: %s\nAH Tax: %s",
                mode, MathUtils.formatCoins(budgetCap), MathUtils.formatCoins(reserved), maxItems,
                mayor != null ? mayor.getName() : "Unknown",
                cookie ? "Active" : "Inactive",
                breakScheduleSummary,
                ahTaxInfo);
        return embedJson("▶️ Session Started", desc, 0x00FF00, null);
    }

    public String formatSessionStop(SessionStats stats, long totalBreakTimeMs) {
        String desc = String.format("Duration: %s\nProfit: %s\nCoins/Hour: %s\nFlips: %d\nROI: %.2f%%\nTop Item: %s\nBreak Time: %s (%.1f%%)",
                formatDuration(stats.totalDurationMs),
                MathUtils.formatCoins(stats.totalProfit),
                MathUtils.formatCoins(stats.coinsPerHour),
                stats.totalFlipsCompleted,
                stats.roiPercent,
                stats.topItem,
                formatDuration(totalBreakTimeMs),
                stats.totalDurationMs>0 ? (totalBreakTimeMs*100.0/stats.totalDurationMs) : 0);
        return embedJson("⏹️ Session Stopped", desc, 0xFF0000, null);
    }

    public String formatFlipCompleted(FlipRecord rec) {
        String desc = String.format("Item: %s\nStrategy: %s\nBought: %s @ %s\nSold: %s @ %s\nQty: %d\nProfit: %s\nMargin: %.2f%%\nDuration: %s\nTax: %s %.2f%%",
                rec.productId, rec.strategyType,
                MathUtils.formatCoins(rec.buyPrice), MathUtils.formatCoins(rec.buyPrice*rec.quantity),
                MathUtils.formatCoins(rec.sellPrice), MathUtils.formatCoins(rec.sellPrice*rec.quantity),
                rec.quantity,
                MathUtils.formatCoins(rec.profit),
                rec.marginPercent,
                formatDuration(rec.durationMs),
                rec.taxType, rec.taxRate*100);
        int color = rec.profit >=0 ? 0x00FF00 : 0xFF0000;
        return embedJson("💰 Flip Completed", desc, color, null);
    }

    public String formatHourlySummary(SessionStats stats, int activeFlips, MayorData mayor, long breakThisHour) {
        String desc = String.format("Profit this hour: %s\nTotal: %s\nCoins/h: %s\nActive: %d\nTop: %s\nMayor: %s\nBreak this hour: %s",
                MathUtils.formatCoins(stats.totalProfit), // simplified per hour?
                MathUtils.formatCoins(stats.totalProfit),
                MathUtils.formatCoins(stats.coinsPerHour),
                activeFlips,
                stats.topItem,
                mayor!=null?mayor.getName():"Unknown",
                formatDuration(breakThisHour));
        return embedJson("📊 Hourly Summary", desc, 0x0000FF, null);
    }

    public String formatUndercut(String item, double oldPrice, double newPrice) {
        return embedJson("⚠️ Undercut", String.format("%s old %.0f -> new %.0f", item, oldPrice, newPrice), 0xFFFF00, null);
    }

    public String formatStaleCancel(String item, long ageMs, double fillRate, String reason) {
        return embedJson("♻️ Stale Cancelled", String.format("%s age %s fill %.1f%% reason %s", item, formatDuration(ageMs), fillRate*100, reason), 0xFFAA00, null);
    }

    public String formatError(String desc, String state, String action) {
        return embedJson("❌ Error", String.format("%s\nState: %s\nAction: %s", desc, state, action), 0xFF0000, null);
    }

    public String formatBudgetWarning(double available, double remainingPercent) {
        return embedJson("⚠️ Budget Warning", String.format("Available: %s %.1f%% remaining", MathUtils.formatCoins(available), remainingPercent), 0xFFFF00, null);
    }

    public String formatSessionResumed(int restored, int abandoned, double budgetRestored, String breakRestored) {
        return embedJson("🔄 Session Resumed", String.format("Restored: %d Abandoned: %d Budget: %s Breaks: %s", restored, abandoned, MathUtils.formatCoins(budgetRestored), breakRestored), 0x00FFFF, null);
    }

    public String formatReconnect(int attempt, boolean success) {
        return embedJson(success?"✅ Reconnect Success":"🔄 Reconnect Attempt", "Attempt #" + attempt + (success?" succeeded":""), success?0x00FF00:0xFFFF00, null);
    }

    public String formatGuiWatchdog(String action, int retries, boolean pausing) {
        return embedJson("🐶 GUI Watchdog", String.format("Action %s retries %d pausing %b", action, retries, pausing), 0xFFAA00, null);
    }

    public String formatMayorChange(MayorData newMayor, String strategyRec, String ahTaxWarning) {
        String perks = newMayor.getPerks().isEmpty()?"None":newMayor.getPerks().stream().map(p->p.name).reduce((a,b)->a+", "+b).orElse("");
        String desc = String.format("New Mayor: %s\nPerks: %s\nStrategy: %s\n%s", newMayor.getName(), perks, strategyRec, ahTaxWarning!=null?ahTaxWarning:"");
        return embedJson("👑 Mayor Change", desc, 0xFFAA00, null);
    }

    public String formatDerpyDetected() {
        return embedJson("⚠️ Derpy Detected", "⚠️ Derpy is now active — AH claiming taxes increased. AH craft flip profitability reduced.", 0xFF0000, null);
    }

    public String formatDerpyEnded() {
        return embedJson("✅ Derpy Ended", "✅ Derpy's term has ended — AH taxes returned to normal", 0x00FF00, null);
    }

    public String formatElectionUpdate(String leading, double votePct, long timeToResult, String prePos) {
        return embedJson("🗳️ Election Update", String.format("Leading: %s %.1f%% Time to result: %s Pre-pos: %s", leading, votePct*100, formatDuration(timeToResult), prePos), 0x0000FF, null);
    }

    public String formatLimboDetected() { return embedJson("🔴 Limbo Detected", "🔴 Player entered Limbo — recovering", 0xFF0000, null); }
    public String formatLimboRecoverySuccess() { return embedJson("✅ Limbo Recovery", "✅ Limbo recovery successful", 0x00FF00, null); }
    public String formatLimboRecoveryFailed() { return embedJson("❌ Limbo Failed", "❌ Limbo recovery FAILED — manual intervention required", 0xFF0000, null); }
    public String formatLimboFlood() { return embedJson("🚨 CRITICAL Limbo Flood", "🚨 CRITICAL: Player entered Limbo 5+ times in 10 minutes — disconnecting. Manual intervention required.", 0xFF0000, null); }
    public String formatPrivateIsland() { return embedJson("⚠️ Private Island", "⚠️ Lobby restart — recovering to Hub", 0xFFAA00, null); }
    public String formatSkyblockReentry() { return embedJson("✅ Re-entered Skyblock", "✅ Re-entered Skyblock — resuming", 0x00FF00, null); }
    public String formatLongBreakStarted(long durationMs) { return embedJson("☕ Long Break Started", String.format("Long break started (%s) — automatic resume after", formatDuration(durationMs)), 0xAA55FF, null); }
    public String formatLongBreakEnded() { return embedJson("▶️ Long Break Ended", "Long break complete — resuming flipping", 0x00FF00, null); }

    private String formatDuration(long ms) {
        long sec = ms/1000;
        long min = sec/60;
        long hr = min/60;
        sec %=60; min%=60;
        if (hr>0) return String.format("%dh %dm %ds", hr, min, sec);
        if (min>0) return String.format("%dm %ds", min, sec);
        return String.format("%ds", sec);
    }
}
