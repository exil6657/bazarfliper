package com.bazaarflipper.ui;

import com.bazaarflipper.config.*;
import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.engine.*;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorTracker;
import com.bazaarflipper.tracker.HistoryManager;
import com.bazaarflipper.tracker.ProfitTracker;
import com.bazaarflipper.util.ColorUtils;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Fully custom Screen subclass. All drawing via DrawContext.fill() and drawText().
 * Tab bar: row of filled rectangles. Active tab different color with bottom border accent.
 * All configuration inline within tabs - no sub-screens.
 */
public class DashboardScreen extends Screen {

    private final ModConfig modConfig;
    private final BudgetConfig budgetConfig;
    private final NPCConfig npcConfig;
    private final FilterConfig filterConfig;
    private final FlipEngine flipEngine;
    private final ProfitTracker profitTracker;
    private final BudgetManager budgetManager;
    private final BreakScheduler breakScheduler;
    private final MayorTracker mayorTracker;
    private final TaxCalculator taxCalculator;
    private final MarketScanner marketScanner;
    private final com.bazaarflipper.api.BazaarData bazaarData;
    private final HistoryManager historyManager;

    private enum Tab {
        OVERVIEW("Overview"),
        ACTIVE_ORDERS("Active Orders"),
        FLIP_HISTORY("Flip History"),
        MARKET_SCANNER("Market Scanner"),
        MAYOR_EVENTS("Mayor & Events"),
        BUDGET("Budget"),
        SETTINGS("Settings"),
        FILTERS("Filters"),
        DISCORD("Discord"),
        NPC_CONFIG("NPC Config");

        final String display;
        Tab(String d) { display = d; }
    }

    private Tab activeTab = Tab.OVERVIEW;
    private int scrollOffset = 0;

    // Simple button class for handling clicks
    private static class Button {
        int x, y, w, h;
        String text;
        Runnable onClick;
        boolean hovered = false;
        int normalColor = ColorUtils.BUTTON_NORMAL;
        int hoverColor = ColorUtils.BUTTON_HOVER;

        Button(int x, int y, int w, int h, String text, Runnable onClick) {
            this.x=x; this.y=y; this.w=w; this.h=h; this.text=text; this.onClick=onClick;
        }

        boolean isMouseOver(double mx, double my) {
            return mx>=x && mx<=x+w && my>=y && my<=y+h;
        }

        void render(DrawContext ctx, MinecraftClient mc, double mx, double my) {
            hovered = isMouseOver(mx, my);
            int bg = hovered ? hoverColor : normalColor;
            ctx.fill(x, y, x+w, y+h, bg);
            ctx.fill(x-1, y-1, x+w+1, y, ColorUtils.BUTTON_BORDER);
            ctx.fill(x-1, y+h, x+w+1, y+h+1, ColorUtils.BUTTON_BORDER);
            ctx.fill(x-1, y, x, y+h, ColorUtils.BUTTON_BORDER);
            ctx.fill(x+w, y, x+w+1, y+h, ColorUtils.BUTTON_BORDER);
            ctx.drawText(mc.textRenderer, text, x+5, y+5, ColorUtils.PRIMARY_TEXT, false);
        }

        void click() { if (onClick!=null) onClick.run(); }
    }

    private final java.util.List<Button> currentButtons = new java.util.ArrayList<>();

    public DashboardScreen(ModConfig modConfig, BudgetConfig budgetConfig, NPCConfig npcConfig, FilterConfig filterConfig,
                           FlipEngine flipEngine, ProfitTracker profitTracker, BudgetManager budgetManager,
                           BreakScheduler breakScheduler, MayorTracker mayorTracker, TaxCalculator taxCalculator,
                           MarketScanner marketScanner, com.bazaarflipper.api.BazaarData bazaarData, HistoryManager historyManager) {
        super(Text.literal("Bazaar Flipper Dashboard"));
        this.modConfig = modConfig;
        this.budgetConfig = budgetConfig;
        this.npcConfig = npcConfig;
        this.filterConfig = filterConfig;
        this.flipEngine = flipEngine;
        this.profitTracker = profitTracker;
        this.budgetManager = budgetManager;
        this.breakScheduler = breakScheduler;
        this.mayorTracker = mayorTracker;
        this.taxCalculator = taxCalculator;
        this.marketScanner = marketScanner;
        this.bazaarData = bazaarData;
        this.historyManager = historyManager;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        currentButtons.clear();
        int screenW = width;
        int screenH = height;

        // Background
        context.fill(0,0,screenW,screenH, 0xFF0A0A0A);

        // Title
        context.drawText(textRenderer, "Bazaar Flipper Dashboard v6.0 - MC 26.1.2 | Loader 0.19.3", 10, 10, ColorUtils.TITLE_TEXT, false);

        // Start/Stop toggle always visible at top-right large rectangle button green when running red when not
        boolean running = flipEngine.isRunning();
        int toggleW = 100;
        int toggleH = 25;
        int toggleX = screenW - toggleW - 10;
        int toggleY = 5;
        int toggleColor = running ? 0xFF1A4A1A : 0xFF4A1A1A;
        String toggleText = running ? "§aACTIVE" : "§cSTOPPED";
        context.fill(toggleX, toggleY, toggleX+toggleW, toggleY+toggleH, toggleColor);
        context.fill(toggleX-1, toggleY-1, toggleX+toggleW+1, toggleY, ColorUtils.PANEL_BORDER);
        context.fill(toggleX-1, toggleY+toggleH, toggleX+toggleW+1, toggleY+toggleH+1, ColorUtils.PANEL_BORDER);
        context.drawText(textRenderer, toggleText, toggleX+10, toggleY+8, ColorUtils.PRIMARY_TEXT, false);
        // Click handling for toggle
        currentButtons.add(new Button(toggleX, toggleY, toggleW, toggleH, toggleText, () -> {
            // Same toggle logic as keybind
            if (flipEngine.isRunning()) {
                flipEngine.stop();
            } else {
                flipEngine.start();
            }
        }));

        // Tab bar - row of filled rectangles
        int tabBarY = 35;
        int tabX = 10;
        int tabHeight = 20;
        for (Tab tab : Tab.values()) {
            int tabWidth = textRenderer.getWidth(tab.display) + 20;
            boolean isActive = tab == activeTab;
            int bg = isActive ? ColorUtils.TAB_ACTIVE_BG : ColorUtils.TAB_INACTIVE_BG;
            context.fill(tabX, tabBarY, tabX+tabWidth, tabBarY+tabHeight, bg);
            if (isActive) {
                // bottom border accent 2px gold
                context.fill(tabX, tabBarY+tabHeight-2, tabX+tabWidth, tabBarY+tabHeight, ColorUtils.TAB_ACTIVE_BORDER);
            }
            int textColor = isActive ? ColorUtils.PRIMARY_TEXT : ColorUtils.SECONDARY_TEXT;
            context.drawText(textRenderer, tab.display, tabX+10, tabBarY+6, textColor, false);
            // Click area
            int finalTabX = tabX;
            Tab finalTab = tab;
            currentButtons.add(new Button(tabX, tabBarY, tabWidth, tabHeight, tab.display, () -> activeTab = finalTab));
            tabX += tabWidth + 2;
        }

        // Content area per tab
        int contentY = tabBarY + tabHeight + 10;
        int contentX = 10;
        int contentW = screenW - 20;
        int contentH = screenH - contentY - 10;

        // Draw panel for content
        context.fill(contentX-1, contentY-1, contentX+contentW+1, contentY+contentH+1, ColorUtils.PANEL_BORDER);
        context.fill(contentX, contentY, contentX+contentW, contentY+contentH, ColorUtils.PANEL_BG);

        switch (activeTab) {
            case OVERVIEW -> renderOverviewTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case ACTIVE_ORDERS -> renderActiveOrdersTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case FLIP_HISTORY -> renderFlipHistoryTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case MARKET_SCANNER -> renderMarketScannerTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case MAYOR_EVENTS -> renderMayorTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case BUDGET -> renderBudgetTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case SETTINGS -> renderSettingsTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case FILTERS -> renderFiltersTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case DISCORD -> renderDiscordTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
            case NPC_CONFIG -> renderNpcTab(context, contentX, contentY, contentW, contentH, mouseX, mouseY);
        }

        // Render buttons (for hover effect need mouse pos)
        for (Button b : currentButtons) {
            // Only render those that are not tab buttons? Actually we already rendered tabs, but buttons include toggle
            // Render only if not tab? Let's skip tabs as they already rendered, but ensure toggle is rendered with hover already? We rendered toggle separately, so skip re-render?
            // For simplicity, render non-tab buttons via their render method if they overlap content area
            if (b.y >= contentY) {
                b.render(context, MinecraftClient.getInstance(), mouseX, mouseY);
            }
        }
    }

    private void renderOverviewTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        // Large session profit rectangle + text centered gold label large green/red value
        int profitBoxW = w/2 -10;
        int profitBoxH = 50;
        ctx.fill(x+5, y+5, x+5+profitBoxW, y+5+profitBoxH, ColorUtils.PANEL_INNER);
        ctx.fill(x+5-1, y+5-1, x+5+profitBoxW+1, y+5+profitBoxH+1, ColorUtils.PANEL_BORDER);
        String profitLabel = "Session Profit";
        ctx.drawText(textRenderer, profitLabel, x+10, y+10, ColorUtils.TITLE_TEXT, false);
        double profit = profitTracker.getSessionProfit();
        int profitColor = profit>=0? ColorUtils.PROFIT_POSITIVE : ColorUtils.PROFIT_NEGATIVE;
        ctx.drawText(textRenderer, MathUtils.formatCoinsDetailed(profit), x+10, y+25, profitColor, false);

        // 2x2 grid of stat boxes
        int boxW = (w-30)/2;
        int boxH = 40;
        int gridStartY = y+60;
        // Stat 1: Coins/hour
        ctx.fill(x+5, gridStartY, x+5+boxW, gridStartY+boxH, ColorUtils.PANEL_INNER);
        ctx.drawText(textRenderer, "Coins/hour", x+10, gridStartY+5, ColorUtils.SECONDARY_TEXT, false);
        ctx.drawText(textRenderer, MathUtils.formatCoins(profitTracker.getSessionStats().coinsPerHour), x+10, gridStartY+20, ColorUtils.PRIMARY_TEXT, false);

        // Stat 2: Total flips
        ctx.fill(x+15+boxW, gridStartY, x+15+boxW*2, gridStartY+boxH, ColorUtils.PANEL_INNER);
        ctx.drawText(textRenderer, "Flips", x+20+boxW, gridStartY+5, ColorUtils.SECONDARY_TEXT, false);
        ctx.drawText(textRenderer, String.valueOf(profitTracker.getSessionFlips()), x+20+boxW, gridStartY+20, ColorUtils.PRIMARY_TEXT, false);

        // Stat 3: Budget utilization
        int gridRow2Y = gridStartY+boxH+5;
        ctx.fill(x+5, gridRow2Y, x+5+boxW, gridRow2Y+boxH, ColorUtils.PANEL_INNER);
        ctx.drawText(textRenderer, "Budget Used", x+10, gridRow2Y+5, ColorUtils.SECONDARY_TEXT, false);
        ctx.drawText(textRenderer, String.format("%.1f%%", budgetManager.getBudgetUtilizationPercent()), x+10, gridRow2Y+20, ColorUtils.PROGRESS_BUDGET, false);

        // Stat 4: Break time
        ctx.fill(x+15+boxW, gridRow2Y, x+15+boxW*2, gridRow2Y+boxH, ColorUtils.PANEL_INNER);
        ctx.drawText(textRenderer, "Break Time", x+20+boxW, gridRow2Y+5, ColorUtils.SECONDARY_TEXT, false);
        ctx.drawText(textRenderer, formatDuration(breakScheduler.getTotalBreakTime()), x+20+boxW, gridRow2Y+20, ColorUtils.BREAK_PURPLE, false);

        // Most profitable item text
        int nextY = gridRow2Y+boxH+10;
        ctx.drawText(textRenderer, "Top: " + profitTracker.getMostProfitableItem() + " (" + MathUtils.formatCoins(profitTracker.getMostProfitableProfit()) + ")", x+10, nextY, ColorUtils.ITEM_NAME, false);
        nextY+=15;

        // Current mayor panel
        ctx.fill(x+5, nextY, x+w-10, nextY+40, ColorUtils.PANEL_INNER);
        MayorData mayor = mayorTracker.getCurrentMayor();
        String mayorName = mayor!=null? mayor.getName():"Unknown";
        ctx.drawText(textRenderer, "Mayor: " + mayorName, x+10, nextY+5, ColorUtils.TITLE_TEXT, false);
        if (mayor!=null && !mayor.getPerks().isEmpty()) {
            ctx.drawText(textRenderer, mayor.getPerks().get(0).name + " - " + mayor.getPerks().get(0).description, x+10, nextY+20, ColorUtils.SECONDARY_TEXT, false);
        }
        nextY+=45;

        // If Derpy active prominent gold warning box
        if (mayor!=null && mayor.isDerpy()) {
            ctx.fill(x+5, nextY, x+w-10, nextY+20, ColorUtils.TAX_DERPY);
            ctx.drawText(textRenderer, "⚠️ Derpy Active — AH claiming tax increased", x+10, nextY+5, ColorUtils.WARNING, false);
            nextY+=25;
        }

        // Break Statistics sub-panel
        ctx.fill(x+5, nextY, x+w-10, nextY+60, ColorUtils.PANEL_INNER);
        ctx.drawText(textRenderer, "Break Stats: Total " + formatDuration(breakScheduler.getTotalBreakTime()) + " Counts: " + breakScheduler.getBreakHistory().size(), x+10, nextY+5, ColorUtils.SECONDARY_TEXT, false);
        ctx.drawText(textRenderer, "Next break: " + formatDuration(breakScheduler.getTimeUntilNextForcedBreak()) + " Long: " + formatDuration(breakScheduler.getTimeUntilLongBreak()), x+10, nextY+20, ColorUtils.SECONDARY_TEXT, false);
        // Quota progress bar
        long quotaMs = modConfig.shortBreakWindowMinBreakMinutes * 60_000L;
        long curMs = breakScheduler.getBreakTimeInCurrentWindow();
        int barW = w-20;
        ctx.fill(x+10, nextY+35, x+10+barW, nextY+43, ColorUtils.PROGRESS_BG);
        int fill = (int)(barW * (curMs / (double)quotaMs));
        ctx.fill(x+10, nextY+35, x+10+fill, nextY+43, ColorUtils.PROGRESS_FILL);
    }

    private void renderActiveOrdersTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        // Column headers as text with underline simulated by thin fill rect below text
        int headerY = y+5;
        String[] headers = {"Item","Strategy","Buy","Sell","Qty","Profit","Fill%","Tax","Status","Age"};
        int colX = x+5;
        int colWidth = w / headers.length;
        for (String header : headers) {
            ctx.drawText(textRenderer, header, colX, headerY, ColorUtils.TITLE_TEXT, false);
            ctx.fill(colX, headerY+10, colX+textRenderer.getWidth(header), headerY+11, ColorUtils.BORDER_ACCENT);
            colX+=colWidth;
        }
        int rowY = headerY+15;
        for (var entry : flipEngine.getActiveFlips().values()) {
            if (rowY > y+h-20) break;
            boolean hover = mouseY>=rowY && mouseY<=rowY+12 && mouseX>=x && mouseX<=x+w;
            int bg = hover ? 0xFF1A1A2A : (rowY %2==0 ? 0xFF0F0F0F : 0xFF111111);
            ctx.fill(x, rowY, x+w, rowY+12, bg);
            ctx.drawText(textRenderer, entry.productId.length()>12? entry.productId.substring(0,12)+"...":entry.productId, x+5, rowY, ColorUtils.ITEM_NAME, false);
            ctx.drawText(textRenderer, entry.strategyType, x+5+colWidth, rowY, ColorUtils.SECONDARY_TEXT, false);
            ctx.drawText(textRenderer, MathUtils.formatCoins(entry.buyPrice), x+5+colWidth*2, rowY, ColorUtils.PRIMARY_TEXT, false);
            ctx.drawText(textRenderer, MathUtils.formatCoins(entry.targetSellPrice), x+5+colWidth*3, rowY, ColorUtils.PRIMARY_TEXT, false);
            ctx.drawText(textRenderer, String.valueOf(entry.quantity), x+5+colWidth*4, rowY, ColorUtils.SECONDARY_TEXT, false);
            // Est profit with correct tax
            double est = entry.strategyType.equals("AH_CRAFT") ? taxCalculator.calculateAHProfit(entry.buyPrice, entry.targetSellPrice, mayorTracker.getCurrentMayor())*entry.quantity
                    : taxCalculator.calculateBazaarProfit(entry.buyPrice, entry.targetSellPrice)*entry.quantity;
            ctx.drawText(textRenderer, MathUtils.formatCoins(est), x+5+colWidth*5, rowY, est>=0?ColorUtils.PROFIT_POSITIVE:ColorUtils.PROFIT_NEGATIVE, false);
            ctx.drawText(textRenderer, entry.filledAmount+"/"+entry.quantity, x+5+colWidth*6, rowY, ColorUtils.SECONDARY_TEXT, false);
            // Tax tier for AH
            String taxBadge = "";
            if (entry.strategyType.equals("AH_CRAFT")) {
                taxBadge = taxCalculator.getAHTaxTier(entry.targetSellPrice).name() + " " + String.format("%.0f%%", taxCalculator.getAHTaxRate(entry.targetSellPrice, mayorTracker.getCurrentMayor())*100);
            } else {
                taxBadge = "1.25%";
            }
            ctx.drawText(textRenderer, taxBadge, x+5+colWidth*7, rowY, ColorUtils.SECONDARY_TEXT, false);
            ctx.drawText(textRenderer, entry.state, x+5+colWidth*8, rowY, ColorUtils.SECONDARY_TEXT, false);
            ctx.drawText(textRenderer, formatDuration(System.currentTimeMillis()-entry.placementTimestamp), x+5+colWidth*9, rowY, ColorUtils.SECONDARY_TEXT, false);
            rowY+=14;
        }
    }

    private void renderFlipHistoryTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        int headerY = y+5;
        ctx.drawText(textRenderer, "Flip History - Total: " + historyManager.getRunningTotal(), x+5, headerY, ColorUtils.TITLE_TEXT, false);
        int rowY = headerY+15;
        for (var rec : historyManager.getRecentFlips(20)) {
            if (rowY>y+h-20) break;
            ctx.drawText(textRenderer, rec.productId + " " + rec.strategyType + " " + MathUtils.formatCoins(rec.profit) + " " + rec.taxType + " " + String.format("%.2f%%", rec.taxRate*100), x+5, rowY, ColorUtils.PRIMARY_TEXT, false);
            rowY+=12;
        }
        // Export button
        Button export = new Button(x+5, y+h-30, 150, 20, "Export to Clipboard", () -> {
            String data = historyManager.exportToClipboardFormat();
            MinecraftClient.getInstance().keyboard.setClipboard(data);
        });
        export.render(ctx, MinecraftClient.getInstance(), mouseX, mouseY);
        currentButtons.add(export);
    }

    private void renderMarketScannerTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        int headerY = y+5;
        String[] headers = {"Item","Strategy","Spread","Profit/Unit","Vol","Backlog","BudgetProfit","FillEst","MayorMod","TaxTier"};
        int colW = w / headers.length;
        int colX = x+5;
        for (String hd : headers) {
            ctx.drawText(textRenderer, hd, colX, headerY, ColorUtils.TITLE_TEXT, false);
            colX+=colW;
        }
        int rowY = headerY+15;
        // Would get opportunities from marketScanner - placeholder uses last bazaar data
        var opps = marketScanner.findAllOpportunities(bazaarData, null, budgetManager.getAvailableForFlipping(), 20);
        for (var opp : opps) {
            if (rowY>y+h-20) break;
            colX = x+5;
            ctx.drawText(textRenderer, opp.productId.length()>10? opp.productId.substring(0,10):opp.productId, colX, rowY, ColorUtils.ITEM_NAME, false); colX+=colW;
            ctx.drawText(textRenderer, opp.strategyType, colX, rowY, ColorUtils.SECONDARY_TEXT, false); colX+=colW;
            ctx.drawText(textRenderer, MathUtils.formatCoins(opp.rawSpread), colX, rowY, ColorUtils.PRIMARY_TEXT, false); colX+=colW;
            ctx.drawText(textRenderer, MathUtils.formatCoins(opp.profitPerUnitAfterTax), colX, rowY, ColorUtils.PROFIT_POSITIVE, false); colX+=colW;
            ctx.drawText(textRenderer, MathUtils.formatCoins(opp.dailyVolume), colX, rowY, ColorUtils.SECONDARY_TEXT, false); colX+=colW;
            ctx.drawText(textRenderer, String.valueOf((int)opp.backlogPressure), colX, rowY, ColorUtils.SECONDARY_TEXT, false); colX+=colW;
            ctx.drawText(textRenderer, MathUtils.formatCoins(opp.budgetProfit), colX, rowY, ColorUtils.PROFIT_POSITIVE, false); colX+=colW;
            ctx.drawText(textRenderer, formatDuration(opp.fillTimeEstimateMs), colX, rowY, ColorUtils.SECONDARY_TEXT, false); colX+=colW;
            ctx.drawText(textRenderer, String.format("%.2f", opp.mayorModifier), colX, rowY, ColorUtils.SECONDARY_TEXT, false); colX+=colW;
            int taxColor = ColorUtils.SECONDARY_TEXT;
            if (opp.taxTier!=null) {
                if (opp.taxTier.contains("LOW")) taxColor=ColorUtils.TAX_LOW;
                else if (opp.taxTier.contains("MID")) taxColor=ColorUtils.TAX_MID;
                else if (opp.taxTier.contains("HIGH")) taxColor=ColorUtils.TAX_HIGH;
                if (opp.derpyWarning) taxColor=ColorUtils.TAX_DERPY;
            }
            ctx.drawText(textRenderer, opp.taxTier!=null? opp.taxTier:"", colX, rowY, taxColor, false);
            rowY+=12;
        }

        Button refresh = new Button(x+5, y+h-30, 120, 20, "Force Refresh", () -> {
            // Force refresh handled elsewhere
        });
        refresh.render(ctx, MinecraftClient.getInstance(), mouseX, mouseY);
        currentButtons.add(refresh);
    }

    private void renderMayorTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        MayorData mayor = mayorTracker.getCurrentMayor();
        int curY = y+5;
        ctx.drawText(textRenderer, "Current Mayor: " + (mayor!=null?mayor.getName():"Unknown"), x+5, curY, ColorUtils.TITLE_TEXT, false);
        curY+=15;
        if (mayor!=null) {
            for (var perk : mayor.getPerks()) {
                ctx.fill(x+5, curY, x+w-10, curY+15, ColorUtils.PANEL_INNER);
                ctx.drawText(textRenderer, perk.name + ": " + perk.description, x+10, curY+3, ColorUtils.SECONDARY_TEXT, false);
                curY+=17;
            }
            if (mayor.isDerpy()) {
                ctx.fill(x+5-1, curY-1, x+w-10+1, curY+20+1, 0xFFFF0000);
                ctx.fill(x+5, curY, x+w-10, curY+20, ColorUtils.PANEL_BG);
                ctx.drawText(textRenderer, "Derpy is active — AH claiming taxes increased. See tax settings for exact rates.", x+10, curY+5, ColorUtils.WARNING, false);
                curY+=25;
            }
        }
        // Election panel: vote distribution bars (filled rectangles of proportional width)
        ctx.drawText(textRenderer, "Election: Leading " + mayorTracker.getLeadingCandidate(), x+5, curY, ColorUtils.PRIMARY_TEXT, false);
        curY+=15;
        // Upcoming events list with countdown text
        ctx.drawText(textRenderer, "Upcoming events: Spooky Festival, Jerry Workshop etc (placeholder)", x+5, curY, ColorUtils.SECONDARY_TEXT, false);
    }

    private void renderBudgetTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        int curY = y+10;
        ctx.drawText(textRenderer, "Total Budget Cap: " + MathUtils.formatCoins(budgetConfig.totalBudgetCap), x+5, curY, ColorUtils.PRIMARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Reserved: " + MathUtils.formatCoins(budgetConfig.reservedBalance), x+5, curY, ColorUtils.PRIMARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Max per Item: " + MathUtils.formatCoins(budgetConfig.maxInvestmentPerItem), x+5, curY, ColorUtils.PRIMARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Max Concurrent: " + budgetConfig.maxConcurrentItems, x+5, curY, ColorUtils.PRIMARY_TEXT, false); curY+=15;

        // Live stats
        ctx.drawText(textRenderer, "Purse: " + MathUtils.formatCoins(budgetManager.getCurrentBalance()), x+5, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Invested: " + MathUtils.formatCoins(budgetManager.getTotalCurrentlyInvested()), x+5, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Available: " + MathUtils.formatCoins(budgetManager.getAvailableForFlipping()), x+5, curY, ColorUtils.PROFIT_POSITIVE, false); curY+=15;

        // Simple budget chart: three horizontal bars (invested, available, reserved) with labels
        int barW = w-20;
        ctx.fill(x+10, curY, x+10+barW, curY+10, ColorUtils.PROGRESS_BG);
        int investedFill = (int)(barW * budgetManager.getBudgetUtilizationPercent()/100.0);
        ctx.fill(x+10, curY, x+10+investedFill, curY+10, ColorUtils.PROGRESS_BUDGET);
        ctx.drawText(textRenderer, "Invested", x+10, curY+12, ColorUtils.SECONDARY_TEXT, false);
        curY+=25;
    }

    private void renderSettingsTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        int curY = y+5 + scrollOffset;
        // API Settings
        ctx.drawText(textRenderer, "API Settings:", x+5, curY, ColorUtils.TITLE_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "API Key: " + (modConfig.hypixelApiKey.isEmpty()?"Not set":"***"), x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Refresh Interval: " + modConfig.apiRefreshIntervalMs + "ms", x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=15;

        // Flip Settings
        ctx.drawText(textRenderer, "Flip Settings:", x+5, curY, ColorUtils.TITLE_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Mode: " + modConfig.flipMode, x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Min Margin: " + modConfig.minProfitMarginPercent + "% Max: " + modConfig.maxProfitMarginPercent + "%", x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Min Volume: " + modConfig.minDailyVolume, x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=15;

        // Break Settings with text diagram
        ctx.drawText(textRenderer, "Break Settings: (Master: " + modConfig.breaksEnabled + ")", x+5, curY, ColorUtils.TITLE_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Short: " + modConfig.shortBreakMinDuration + "-" + modConfig.shortBreakMaxDuration + "s", x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Long: " + modConfig.longBreakMinDuration + "-" + modConfig.longBreakMaxDuration + "s every " + modConfig.longBreakIntervalHours + "h", x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Window: " + modConfig.shortBreakWindowMinutes + "m quota " + modConfig.shortBreakWindowMinBreakMinutes + "m", x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Order Wait: " + modConfig.orderWaitMinSeconds + "-" + modConfig.orderWaitMaxSeconds + "s", x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Diagram: Active 30m -> need 3m break (probabilistic middle-weighted)", x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=15;

        // Advanced Tax Settings collapsible
        ctx.drawText(textRenderer, "Tax Settings (Advanced):", x+5, curY, ColorUtils.TITLE_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Bazaar Tax: " + String.format("%.3f%%", modConfig.bazaarTaxRate*100) + " (Cookie does not affect)", x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "AH LOW (<10M): " + String.format("%.2f%%", modConfig.ahTaxLowRate*100), x+10, curY, ColorUtils.TAX_LOW, false); curY+=12;
        ctx.drawText(textRenderer, "AH MID (10M-100M): " + String.format("%.2f%%", modConfig.ahTaxMidRate*100), x+10, curY, ColorUtils.TAX_MID, false); curY+=12;
        ctx.drawText(textRenderer, "AH HIGH (>100M): " + String.format("%.2f%%", modConfig.ahTaxHighRate*100), x+10, curY, ColorUtils.TAX_HIGH, false); curY+=12;
        ctx.drawText(textRenderer, "Derpy Multiplier: " + modConfig.derpyAHTaxMultiplier + "x (Researched from wiki — update if Hypixel changes perks)", x+10, curY, ColorUtils.TAX_DERPY, false); curY+=12;
        ctx.drawText(textRenderer, "Derpy Applies Above: " + MathUtils.formatCoins(modConfig.derpyTaxAppliesAbove), x+10, curY, ColorUtils.SECONDARY_TEXT, false); curY+=15;

        Button resetTax = new Button(x+10, curY, 150, 20, "Reset Tax to Defaults", () -> {
            modConfig.bazaarTaxRate = 0.0125;
            modConfig.ahTaxLowRate = 0.01;
            modConfig.ahTaxMidRate = 0.02;
            modConfig.ahTaxHighRate = 0.025;
            modConfig.ahLowMidThreshold = 10_000_000L;
            modConfig.ahMidHighThreshold = 100_000_000L;
            modConfig.derpyAHTaxMultiplier = 4.0;
            modConfig.derpyTaxAppliesAbove = 1_000_000L;
            modConfig.save();
        });
        resetTax.render(ctx, MinecraftClient.getInstance(), mouseX, mouseY);
        currentButtons.add(resetTax);
    }

    private void renderFiltersTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        int curY = y+5;
        ctx.drawText(textRenderer, "Whitelist: " + filterConfig.whitelist.size() + " items", x+5, curY, ColorUtils.PRIMARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Blacklist: " + filterConfig.blacklist.size() + " items", x+5, curY, ColorUtils.PRIMARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Min Profit: " + filterConfig.minProfit + " Max Price: " + filterConfig.maxPrice, x+5, curY, ColorUtils.SECONDARY_TEXT, false); curY+=15;
        Button clear = new Button(x+5, curY, 100, 20, "Clear All", () -> {
            filterConfig.whitelist.clear();
            filterConfig.blacklist.clear();
            filterConfig.save();
        });
        clear.normalColor = 0xFF4A1A1A;
        clear.hoverColor = 0xFF5A2A2A;
        clear.render(ctx, MinecraftClient.getInstance(), mouseX, mouseY);
        currentButtons.add(clear);
    }

    private void renderDiscordTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        int curY = y+5;
        ctx.drawText(textRenderer, "Mode: " + modConfig.discordMode, x+5, curY, ColorUtils.TITLE_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Webhook URL: " + (modConfig.webhookUrl.isEmpty()?"Not set":"Set"), x+5, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Bot Token: " + (modConfig.botToken.isEmpty()?"Not set":"***"), x+5, curY, ColorUtils.SECONDARY_TEXT, false); curY+=12;
        ctx.drawText(textRenderer, "Channel ID: " + modConfig.commandChannelId, x+5, curY, ColorUtils.SECONDARY_TEXT, false); curY+=15;
        Button test = new Button(x+5, curY, 150, 20, "Send Test Message", () -> {
            // Would send test via discord handler
        });
        test.render(ctx, MinecraftClient.getInstance(), mouseX, mouseY);
        currentButtons.add(test);
    }

    private void renderNpcTab(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        int curY = y+5;
        ctx.drawText(textRenderer, "Active Slot: " + npcConfig.selectedNPCSlot + " AutoNearest: " + npcConfig.autoSelectNearestNPC, x+5, curY, ColorUtils.TITLE_TEXT, false); curY+=15;
        for (int i=1;i<=3;i++) {
            NPCConfig.WaypointData wd = switch(i){ case 2->npcConfig.npcWaypoint2; case 3->npcConfig.npcWaypoint3; default->npcConfig.npcWaypoint1; };
            if (wd==null) continue;
            ctx.drawText(textRenderer, "Slot "+i+": "+wd.name+" "+wd.npcDisplayName+" @ "+String.format("%.1f, %.1f, %.1f", wd.x, wd.y, wd.z) + " enabled:"+wd.enabled, x+5, curY, ColorUtils.SECONDARY_TEXT, false);
            curY+=12;
        }
        Button setPos = new Button(x+5, curY+5, 180, 20, "Set to Current Pos", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player!=null) {
                var pos = mc.player.getPos();
                NPCConfig.WaypointData wd = npcConfig.getSelectedWaypoint();
                if (wd!=null) {
                    wd.x = pos.x;
                    wd.y = pos.y;
                    wd.z = pos.z;
                    npcConfig.save();
                }
            }
        });
        setPos.render(ctx, MinecraftClient.getInstance(), mouseX, mouseY);
        currentButtons.add(setPos);
        curY+=25;
        ctx.drawText(textRenderer, "Note: NPC coordinates may drift after Hypixel updates. Verify via wiki.", x+5, curY, ColorUtils.WARNING, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Button b : currentButtons) {
            if (b.isMouseOver(mouseX, mouseY)) {
                b.click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset += (int)(-verticalAmount*10);
        return true;
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
}
