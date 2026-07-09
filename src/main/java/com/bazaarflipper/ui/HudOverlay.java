package com.bazaarflipper.ui;

import com.bazaarflipper.config.ModConfig;
import com.bazaarflipper.engine.ActiveFlip;
import com.bazaarflipper.engine.BreakScheduler;
import com.bazaarflipper.engine.BudgetManager;
import com.bazaarflipper.engine.FlipEngine;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorTracker;
import com.bazaarflipper.tracker.ProfitTracker;
import com.bazaarflipper.util.ColorUtils;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Map;

public class HudOverlay {

    private final ModConfig config;
    private final FlipEngine flipEngine;
    private final ProfitTracker profitTracker;
    private final BudgetManager budgetManager;
    private final BreakScheduler breakScheduler;
    private final MayorTracker mayorTracker;

    private boolean collapsed = false;
    private int x, y;

    public HudOverlay(ModConfig config, FlipEngine engine, ProfitTracker tracker, BudgetManager budget, BreakScheduler breakScheduler, MayorTracker mayorTracker) {
        this.config = config;
        this.flipEngine = engine;
        this.profitTracker = tracker;
        this.budgetManager = budget;
        this.breakScheduler = breakScheduler;
        this.mayorTracker = mayorTracker;
        this.x = config.hudX;
        this.y = config.hudY;
        this.collapsed = config.hudCollapsed;
    }

    public void render(DrawContext context, float tickDelta) {
        if (!config.hudEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int currentX = x;
        int currentY = y;

        if (collapsed) {
            renderCollapsed(context, currentX, currentY);
            return;
        }

        // Panel dimensions
        int panelWidth = 220;
        int panelHeight = 400; // will expand

        // Draw background panels
        // Panel 1 - Status Bar
        drawPanel(context, currentX, currentY, panelWidth, 30);
        // Status dot color based on engine state
        int dotColor = getStatusDotColor(flipEngine.getState().name());
        String stateText = flipEngine.getState().name();
        String dot = "●";
        context.drawText(mc.textRenderer, dot, currentX+5, currentY+5, dotColor, false);
        context.drawText(mc.textRenderer, stateText, currentX+15, currentY+5, ColorUtils.PRIMARY_TEXT, false);
        context.drawText(mc.textRenderer, "Session: " + formatDuration(System.currentTimeMillis() - profitTracker.getSessionStartTime()), currentX+5, currentY+15, ColorUtils.SECONDARY_TEXT, false);
        currentY += 35;

        // Panel 2 - Financial Summary
        drawPanel(context, currentX, currentY, panelWidth, 60);
        double profit = profitTracker.getSessionProfit();
        int profitColor = profit >=0 ? ColorUtils.PROFIT_POSITIVE : ColorUtils.PROFIT_NEGATIVE;
        context.drawText(mc.textRenderer, "Profit: " + MathUtils.formatCoins(profit), currentX+5, currentY+5, profitColor, false);
        double cph = profitTracker.getSessionStats().coinsPerHour;
        context.drawText(mc.textRenderer, "C/h: " + MathUtils.formatCoins(cph), currentX+5, currentY+15, ColorUtils.PRIMARY_TEXT, false);
        context.drawText(mc.textRenderer, "Flips: " + profitTracker.getSessionFlips(), currentX+5, currentY+25, ColorUtils.PRIMARY_TEXT, false);
        // ROI
        context.drawText(mc.textRenderer, "Top: " + profitTracker.getMostProfitableItem(), currentX+5, currentY+35, ColorUtils.SECONDARY_TEXT, false);
        currentY += 65;

        // Panel 3 - Budget Status
        drawPanel(context, currentX, currentY, panelWidth, 50);
        double available = budgetManager.getAvailableForFlipping();
        double invested = budgetManager.getTotalCurrentlyInvested();
        double reserved = 0; // from config
        context.drawText(mc.textRenderer, "Avail: " + MathUtils.formatCoins(available), currentX+5, currentY+5, ColorUtils.PRIMARY_TEXT, false);
        context.drawText(mc.textRenderer, "Invested: " + MathUtils.formatCoins(invested), currentX+5, currentY+15, ColorUtils.PRIMARY_TEXT, false);
        // Progress bar
        int barX = currentX+5;
        int barY = currentY+30;
        int barW = panelWidth-10;
        int barH = 8;
        context.fill(barX, barY, barX+barW, barY+barH, ColorUtils.PROGRESS_BG);
        double utilization = budgetManager.getBudgetUtilizationPercent() / 100.0;
        int fillW = (int)(barW * utilization);
        context.fill(barX, barY, barX+fillW, barY+barH, ColorUtils.PROGRESS_BUDGET);
        context.drawText(mc.textRenderer, String.format("%.0f%% invested", utilization*100), barX, barY+10, ColorUtils.TITLE_TEXT, false);
        currentY += 55;

        // Panel 4 - Break Status
        drawPanel(context, currentX, currentY, panelWidth, 60);
        if (breakScheduler.isOnBreak()) {
            String breakText = "On Break (" + formatDuration(breakScheduler.getRemainingBreakTime()) + " remaining)";
            context.drawText(mc.textRenderer, breakText, currentX+5, currentY+5, ColorUtils.BREAK_PURPLE, false);
            // Progress bar
            long totalBreak = 60_000; // placeholder, actual duration from break record? We'll estimate
            long remaining = breakScheduler.getRemainingBreakTime();
            long elapsed = totalBreak - remaining;
            int breakBarW = panelWidth-10;
            context.fill(currentX+5, currentY+20, currentX+5+breakBarW, currentY+28, ColorUtils.PROGRESS_BG);
            int breakFill = (int)(breakBarW * (elapsed / (double)totalBreak));
            context.fill(currentX+5, currentY+20, currentX+5+breakFill, currentY+28, ColorUtils.BREAK_PURPLE);
        } else {
            context.drawText(mc.textRenderer, "Active", currentX+5, currentY+5, ColorUtils.PROFIT_POSITIVE, false);
            context.drawText(mc.textRenderer, "Next forced: " + formatDuration(breakScheduler.getTimeUntilNextForcedBreak()), currentX+5, currentY+15, ColorUtils.SECONDARY_TEXT, false);
            context.drawText(mc.textRenderer, "Next long: " + formatDuration(breakScheduler.getTimeUntilLongBreak()), currentX+5, currentY+25, ColorUtils.SECONDARY_TEXT, false);
            // Window quota
            long quota = config.shortBreakWindowMinBreakMinutes * 60_000L;
            long current = breakScheduler.getBreakTimeInCurrentWindow();
            int quotaBarW = panelWidth-10;
            context.fill(currentX+5, currentY+35, currentX+5+quotaBarW, currentY+43, ColorUtils.PROGRESS_BG);
            int quotaFill = (int)(quotaBarW * (current / (double)quota));
            context.fill(currentX+5, currentY+35, currentX+5+quotaFill, currentY+43, ColorUtils.PROGRESS_FILL);
            context.drawText(mc.textRenderer, String.format("[quota] %s / %s", formatDuration(current), formatDuration(quota)), currentX+5, currentY+45, ColorUtils.SECONDARY_TEXT, false);
        }
        currentY += 65;

        // Panel 5 - Mayor Context
        drawPanel(context, currentX, currentY, panelWidth, 50);
        MayorData mayor = mayorTracker.getCurrentMayor();
        String mayorName = mayor != null ? mayor.getName() : "Unknown";
        context.drawText(mc.textRenderer, "Mayor: " + mayorName, currentX+5, currentY+5, ColorUtils.TITLE_TEXT, false);
        if (mayor != null && !mayor.getPerks().isEmpty()) {
            String perk = mayor.getPerks().get(0).name;
            context.drawText(mc.textRenderer, perk, currentX+5, currentY+15, ColorUtils.SECONDARY_TEXT, false);
        }
        if (mayor != null && mayor.isDerpy()) {
            context.drawText(mc.textRenderer, "⚠ Derpy: AH tax increased", currentX+5, currentY+25, ColorUtils.WARNING, false);
        }
        context.drawText(mc.textRenderer, "Election in: " + mayorTracker.getLeadingCandidate(), currentX+5, currentY+35, ColorUtils.SECONDARY_TEXT, false);
        currentY += 55;

        // Panel 6 - Active Flips Widget
        // ActiveFlipsWidget renders within this panel - call its render method
        // For now placeholder - actual widget rendered separately
    }

    private void renderCollapsed(DrawContext context, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        drawPanel(context, x, y, 250, 20);
        int dotColor = getStatusDotColor(flipEngine.getState().name());
        String dot = "●";
        String profit = MathUtils.formatCoins(profitTracker.getSessionProfit());
        String cph = MathUtils.formatCoins(profitTracker.getSessionStats().coinsPerHour);
        String breakInd = breakScheduler.isOnBreak() ? " [Break]" : "";
        String text = dot + " " + profit + " (" + cph + "/h)" + breakInd;
        context.drawText(mc.textRenderer, text, x+5, y+5, ColorUtils.PRIMARY_TEXT, false);
    }

    private int getStatusDotColor(String state) {
        if (state.contains("BREAK")) return ColorUtils.BREAK_PURPLE;
        if (state.equals("IDLE")) return ColorUtils.STATUS_PAUSED;
        if (state.contains("NAVIGATING")) return ColorUtils.STATUS_NAVIGATING;
        if (state.equals("RECONNECTING")) return ColorUtils.STATUS_RECONNECTING;
        if (state.contains("RECOVERING")) return ColorUtils.STATUS_RECOVERING;
        return ColorUtils.STATUS_ACTIVE;
    }

    private void drawPanel(DrawContext context, int x, int y, int w, int h) {
        // Border
        context.fill(x-1, y-1, x+w+1, y+h+1, ColorUtils.PANEL_BORDER);
        // Background
        context.fill(x, y, x+w, y+h, ColorUtils.PANEL_BG);
        // Inner highlight top
        context.fill(x, y, x+w, y+1, ColorUtils.PANEL_INNER);
    }

    private String formatDuration(long ms) {
        long sec = ms/1000;
        long min = sec/60;
        long hr = min/60;
        sec%=60; min%=60;
        if (hr>0) return String.format("%dh %dm", hr, min);
        if (min>0) return String.format("%dm %ds", min, sec);
        return String.format("%ds", sec);
    }

    public void setPosition(int x, int y) {
        this.x = x; this.y = y;
        config.hudX = x; config.hudY = y;
        config.save();
    }

    public void toggleCollapsed() {
        collapsed = !collapsed;
        config.hudCollapsed = collapsed;
        config.save();
    }

    public boolean isCollapsed() { return collapsed; }
}
