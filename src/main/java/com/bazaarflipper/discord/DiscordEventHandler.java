package com.bazaarflipper.discord;

import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.tracker.FlipRecord;
import com.bazaarflipper.tracker.SessionStats;
import com.bazaarflipper.config.ModConfig;
import com.bazaarflipper.util.Logger;

public class DiscordEventHandler {

    private final ModConfig config;
    private final DiscordWebhookClient webhookClient;
    private final DiscordBotClient botClient;
    private final DiscordMessageFormatter formatter;

    private long lastUndercutNotify = 0;
    private final java.util.Map<String, Long> lastUndercutPerItem = new java.util.concurrent.ConcurrentHashMap<>();

    public DiscordEventHandler(ModConfig config, DiscordWebhookClient webhook, DiscordBotClient bot, DiscordMessageFormatter formatter) {
        this.config = config;
        this.webhookClient = webhook;
        this.botClient = bot;
        this.formatter = formatter;
    }

    private void send(String messageJson) {
        if ("DISABLED".equalsIgnoreCase(config.discordMode)) return;
        if ("WEBHOOK".equalsIgnoreCase(config.discordMode)) {
            webhookClient.sendMessage(messageJson);
        } else if ("BOT".equalsIgnoreCase(config.discordMode)) {
            botClient.sendMessage(messageJson);
        }
    }

    public void onSessionStart(String mode, double budgetCap, double reserved, int maxItems, MayorData mayor, boolean cookie, String breakSchedule, String ahTaxInfo) {
        String msg = formatter.formatSessionStart(mode, budgetCap, reserved, maxItems, mayor, cookie, breakSchedule, ahTaxInfo);
        send(msg);
    }

    public void onSessionStop(SessionStats stats, long totalBreakTime) {
        String msg = formatter.formatSessionStop(stats, totalBreakTime);
        send(msg);
    }

    public void onSessionPause(SessionStats stats) {
        // reuse stop with pause wording? For now similar to stop
        send(formatter.formatSessionStop(stats, 0));
    }

    public void onFlipComplete(FlipRecord rec) {
        // Check threshold
        if (!config.notifyOnEveryFlip && rec.profit < config.notifyFlipProfitThreshold) return;
        String msg = formatter.formatFlipCompleted(rec);
        send(msg);
    }

    public void onHourlySummary(SessionStats stats, int activeFlips, MayorData mayor, long breakThisHour) {
        if (!config.hourlySummaryEnabled) return;
        send(formatter.formatHourlySummary(stats, activeFlips, mayor, breakThisHour));
    }

    public void onUndercut(String item, double oldPrice, double newPrice) {
        long now = System.currentTimeMillis();
        Long last = lastUndercutPerItem.get(item);
        if (last != null && now - last < 10*60*1000L) return; // max 1 per item per 10 min
        lastUndercutPerItem.put(item, now);
        send(formatter.formatUndercut(item, oldPrice, newPrice));
    }

    public void onStaleCancelled(String item, long age, double fillRate, String reason) {
        send(formatter.formatStaleCancel(item, age, fillRate, reason));
    }

    public void onError(String desc, String state) {
        send(formatter.formatError(desc, state, "N/A"));
    }

    public void onBudgetWarning(double available, double pct) {
        send(formatter.formatBudgetWarning(available, pct));
    }

    public void onSessionResumed(int restored, int abandoned, double budget, String breakInfo) {
        send(formatter.formatSessionResumed(restored, abandoned, budget, breakInfo));
    }

    public void onReconnectAttempt(int attempt) {
        send(formatter.formatReconnect(attempt, false));
    }

    public void onReconnectSuccess(int attempt) {
        send(formatter.formatReconnect(attempt, true));
    }

    public void onGuiWatchdog(String action, int retries, boolean pausing) {
        send(formatter.formatGuiWatchdog(action, retries, pausing));
    }

    public void onMayorChange(MayorData newMayor, String strategyRec) {
        String warning = null;
        if (newMayor.isDerpy()) warning = "⚠️ Derpy active — AH claiming tax increased";
        send(formatter.formatMayorChange(newMayor, strategyRec, warning));
        if (newMayor.isDerpy() && config.notifyDerpyChanges) {
            onDerpyDetected();
        }
    }

    public void onDerpyDetected() {
        if (!config.notifyDerpyChanges) return;
        send(formatter.formatDerpyDetected());
    }

    public void onDerpyEnded() {
        if (!config.notifyDerpyChanges) return;
        send(formatter.formatDerpyEnded());
    }

    public void onElectionUpdate(String leading, double votePct, long timeToResult, String prePos) {
        send(formatter.formatElectionUpdate(leading, votePct, timeToResult, prePos));
    }

    public void onPrivateIsland() { send(formatter.formatPrivateIsland()); }
    public void onLimboDetected() { send(formatter.formatLimboDetected()); }
    public void onLimboRecoverySuccess() { send(formatter.formatLimboRecoverySuccess()); }
    public void onLimboRecoveryFailed() { send(formatter.formatLimboRecoveryFailed()); }
    public void onLimboFlood() { send(formatter.formatLimboFlood()); }
    public void onSkyblockReentrySuccess() { send(formatter.formatSkyblockReentry()); }

    public void onLongBreakStarted(long duration) {
        if (!config.notifyLongBreaks) return;
        send(formatter.formatLongBreakStarted(duration));
    }

    public void onLongBreakEnded() {
        if (!config.notifyLongBreaks) return;
        send(formatter.formatLongBreakEnded());
    }
}
