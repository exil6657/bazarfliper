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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.bazaarflipper.ui.GuiTextures;

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

    public void render(GuiGraphicsExtractor context, float tickDelta) {
        if (!config.hudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
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
        String stateComponent = flipEngine.getState().name();
        String dot = "●";
        context.text(mc.font, dot, currentX+5, currentY+5, dotColor, false);
        context.text(mc.font, stateText, currentX+15, currentY+5, ColorUtils.PRIMARY_TEXT, false);
        context.text(mc.font, "Session: " + formatDuration(System.currentTimeMillis() - profitTracker.getSessionStartTime()), currentX+5, currentY+15, ColorUtils.SECONDARY_TEXT, false);
        currentY += 35;

        // Panel 2 - Financial Summary
        drawPanel(context, currentX, currentY, panelWidth, 60);
        double profit = profitTracker.getSessionProfit();
        int profitColor = profit >=0 ? ColorUtils.PROFIT_POSITIVE : ColorUtils.PROFIT_NEGATIVE;
        context.text(mc.font, "Profit: " + MathUtils.formatCoins(profit), currentX+5, currentY+5, profitColor, false);
        double cph = profitTracker.getSessionStats().coinsPerHour;
        context.text(mc.font, "C/h: " + MathUtils.formatCoins(cph), currentX+5, currentY+15, ColorUtils.PRIMARY_TEXT, false);
        context.text(mc.font, "Flips: " + profitTracker.getSessionFlips(), currentX+5, currentY+25, ColorUtils.PRIMARY_TEXT, false);
        // ROI
        context.text(mc.font, "Top: " + profitTracker.getMostProfitableItem(), currentX+5, currentY+35, ColorUtils.SECONDARY_TEXT, false);
        currentY += 65;

        // Panel 3 - Budget Status
        drawPanel(context, currentX, currentY, panelWidth, 50);
        double available = budgetManager.getAvailableForFlipping();
        double invested = budgetManager.getTotalCurrentlyInvested();
        double reserved = 0; // from config
        context.text(mc.font, "Avail: " + MathUtils.formatCoins(available), currentX+5, currentY+5, ColorUtils.PRIMARY_TEXT, false);
        context.text(mc.font, "Invested: " + MathUtils.formatCoins(invested), currentX+5, currentY+15, ColorUtils.PRIMARY_TEXT, false);
        // Progress bar
        int barX = currentX+5;
        int barY = currentY+30;
        int barW = panelWidth-10;
        int barH = 8;
        context.fill(barX, barY, barX+barW, barY+barH, ColorUtils.PROGRESS_BG);
        double utilization = budgetManager.getBudgetUtilizationPercent() / 100.0;
        int fillW = (int)(barW * utilization);
        context.fill(barX, barY, barX+fillW, barY+barH, ColorUtils.PROGRESS_BUDGET);
        context.text(mc.font, String.format("%.0f%% invested", utilization*100), barX, barY+10, ColorUtils.TITLE_TEXT, false);
        currentY += 55;

        // Panel 4 - Break Status
        drawPanel(context, currentX, currentY, panelWidth, 60);
        if (breakScheduler.isOnBreak()) {
            String breakComponent = "On Break (" + formatDuration(breakScheduler.getRemainingBreakTime()) + " remaining)";
            context.text(mc.font, breakText, currentX+5, currentY+5, ColorUtils.BREAK_PURPLE, false);
            // Progress bar
            long totalBreak = 60_000; // placeholder, actual duration from break record? We'll estimate
            long remaining = breakScheduler.getRemainingBreakTime();
            long elapsed = totalBreak - remaining;
            int breakBarW = panelWidth-10;
            context.fill(currentX+5, currentY+20, currentX+5+breakBarW, currentY+28, ColorUtils.PROGRESS_BG);
            int breakFill = (int)(breakBarW * (elapsed / (double)totalBreak));
            context.fill(currentX+5, currentY+20, currentX+5+breakFill, currentY+28, ColorUtils.BREAK_PURPLE);
        } else {
            context.text(mc.font, "Active", currentX+5, currentY+5, ColorUtils.PROFIT_POSITIVE, false);
            context.text(mc.font, "Next forced: " + formatDuration(breakScheduler.getTimeUntilNextForcedBreak()), currentX+5, currentY+15, ColorUtils.SECONDARY_TEXT, false);
            context.text(mc.font, "Next long: " + formatDuration(breakScheduler.getTimeUntilLongBreak()), currentX+5, currentY+25, ColorUtils.SECONDARY_TEXT, false);
            // Window quota
            long quota = config.shortBreakWindowMinBreakMinutes * 60_000L;
            long current = breakScheduler.getBreakTimeInCurrentWindow();
            int quotaBarW = panelWidth-10;
            context.fill(currentX+5, currentY+35, currentX+5+quotaBarW, currentY+43, ColorUtils.PROGRESS_BG);
            int quotaFill = (int)(quotaBarW * (current / (double)quota));
            context.fill(currentX+5, currentY+35, currentX+5+quotaFill, currentY+43, ColorUtils.PROGRESS_FILL);
            context.text(mc.font, String.format("[quota] %s / %s", formatDuration(current), formatDuration(quota)), currentX+5, currentY+45, ColorUtils.SECONDARY_TEXT, false);
        }
        currentY += 65;

        // Panel 5 - Mayor Context
        drawPanel(context, currentX, currentY, panelWidth, 50);
        MayorData mayor = mayorTracker.getCurrentMayor();
        String mayorName = mayor != null ? mayor.getName() : "Unknown";
        context.text(mc.font, "Mayor: " + mayorName, currentX+5, currentY+5, ColorUtils.TITLE_TEXT, false);
        if (mayor != null && !mayor.getPerks().isEmpty()) {
            String perk = mayor.getPerks().get(0).name;
            context.text(mc.font, perk, currentX+5, currentY+15, ColorUtils.SECONDARY_TEXT, false);
        }
        if (mayor != null && mayor.isDerpy()) {
            context.text(mc.font, "⚠ Derpy: AH tax increased", currentX+5, currentY+25, ColorUtils.WARNING, false);
        }
        context.text(mc.font, "Election in: " + mayorTracker.getLeadingCandidate(), currentX+5, currentY+35, ColorUtils.SECONDARY_TEXT, false);
        currentY += 55;

        // Panel 6 - Active Flips Widget
        // ActiveFlipsWidget renders within this panel - call its render method
        // For now placeholder - actual widget rendered separately
    }

    private void renderCollapsed(GuiGraphicsExtractor context, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        drawPanel(context, x, y, 250, 20);
        int dotColor = getStatusDotColor(flipEngine.getState().name());
        String dot = "●";
        String profit = MathUtils.formatCoins(profitTracker.getSessionProfit());
        String cph = MathUtils.formatCoins(profitTracker.getSessionStats().coinsPerHour);
        String breakInd = breakScheduler.isOnBreak() ? " [Break]" : "";
        String text = dot + " " + profit + " (" + cph + "/h)" + breakInd;
        context.text(mc.font, text, x+5, y+5, ColorUtils.PRIMARY_TEXT, false);
    }

    private int getStatusDotColor(String state) {
        if (state.contains("BREAK")) return ColorUtils.BREAK_PURPLE;
        if (state.equals("IDLE")) return ColorUtils.STATUS_PAUSED;
        if (state.contains("NAVIGATING")) return ColorUtils.STATUS_NAVIGATING;
        if (state.equals("RECONNECTING")) return ColorUtils.STATUS_RECONNECTING;
        if (state.contains("RECOVERING")) return ColorUtils.STATUS_RECOVERING;
        return ColorUtils.STATUS_ACTIVE;
    }

    private void drawPanel(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        // Attempt textured background with fallback to geometry per spec (user allowed textures now)
        boolean textured = false;
        try {
            // Draw textured panel background
            throw new UnsupportedOperationException("Texture blit disabled for 26.1 compatibility");
        } catch (Exception ignored) {
            // Fallback to geometry
        }
        if (!textured) {
            // Border
            context.fill(x-1, y-1, x+w+1, y+h+1, ColorUtils.PANEL_BORDER);
            // Background
            context.fill(x, y, x+w, y+h, ColorUtils.PANEL_BG);
            // Inner highlight top
            context.fill(x, y, x+w, y+1, ColorUtils.PANEL_INNER);
        } else {
            // Still draw border for crispness
            context.fill(x-1, y-1, x+w+1, y, ColorUtils.PANEL_BORDER);
            context.fill(x-1, y+h, x+w+1, y+h+1, ColorUtils.PANEL_BORDER);
            context.fill(x-1, y, x, y+h, ColorUtils.PANEL_BORDER);
            context.fill(x+w, y, x+w+1, y+h, ColorUtils.PANEL_BORDER);
            // Inner highlight
            context.fill(x, y, x+w, y+1, ColorUtils.PANEL_INNER);
        }
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

    // Drag handling
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private long lastClickTime = 0;

    public boolean isMouseOver(double mouseX, double mouseY) {
        int panelWidth = collapsed ? 250 : 220;
        int panelHeight = collapsed ? 20 : 400;
        return mouseX >= x && mouseX <= x + panelWidth && mouseY >= y && mouseY <= y + panelHeight;
    }

    // Click to toggle collapsed/expanded
    public boolean handleClick(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        long now = System.currentTimeMillis();
        // Avoid double handling within 200ms
        if (now - lastClickTime < 200) return true;
        lastClickTime = now;
        // If click near top 30px header, toggle collapsed
        if (mouseY <= y + 30) {
            toggleCollapsed();
            return true;
        }
        return false;
    }

    public boolean handleMouseDown(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        // Start drag if not clicking header toggle area? We allow drag from anywhere except header toggle? Simpler: allow drag from anywhere, but click already handled
        dragging = true;
        dragOffsetX = (int)(mouseX - x);
        dragOffsetY = (int)(mouseY - y);
        return true;
    }

    public void handleMouseDrag(double mouseX, double mouseY) {
        if (!dragging) return;
        int newX = (int)(mouseX - dragOffsetX);
        int newY = (int)(mouseY - dragOffsetY);
        setPosition(newX, newY);
    }

    public void handleMouseUp() {
        dragging = false;
    }

    public boolean isDragging() { return dragging; }

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

    public int getX() { return x; }
    public int getY() { return y; }
}
