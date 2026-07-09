package com.bazaarflipper.ui;

import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.engine.flipping.FlipStrategy;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorTracker;
import com.bazaarflipper.util.ColorUtils;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Rendered as overlay panel near hovered row. All via fill and drawText.
 */
public class MarketContextWidget {

    private final TaxCalculator taxCalculator;
    private final MayorTracker mayorTracker;

    public MarketContextWidget(TaxCalculator taxCalculator, MayorTracker mayorTracker) {
        this.taxCalculator = taxCalculator;
        this.mayorTracker = mayorTracker;
    }

    public void render(DrawContext context, int x, int y, FlipStrategy.FlipOpportunity opp) {
        if (opp == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        int width = 200;
        int height = 180;

        // Border panel
        context.fill(x-1, y-1, x+width+1, y+height+1, ColorUtils.PANEL_BORDER);
        context.fill(x, y, x+width, y+height, ColorUtils.PANEL_BG);

        int curY = y+5;
        int lineH = 10;

        context.drawText(mc.textRenderer, opp.productId, x+5, curY, ColorUtils.TITLE_TEXT, false);
        curY+=lineH;

        context.drawText(mc.textRenderer, "Spread: " + MathUtils.formatCoins(opp.rawSpread) + " ("+String.format("%.2f%%", opp.sellPrice>0? (opp.rawSpread/opp.buyPrice*100):0)+")", x+5, curY, ColorUtils.PRIMARY_TEXT, false);
        curY+=lineH;

        // Tax-adjusted profit
        if (opp.strategyType.equals("AH_CRAFT")) {
            context.drawText(mc.textRenderer, "AH Profit: " + MathUtils.formatCoins(opp.profitPerUnitAfterTax) + " / " + MathUtils.formatCoins(opp.totalProfitAfterTax), x+5, curY, ColorUtils.PROFIT_POSITIVE, false);
            curY+=lineH;
            context.drawText(mc.textRenderer, "Tax: " + opp.taxTier + " " + String.format("%.2f%%", opp.taxRate*100), x+5, curY, getTaxColor(opp.taxTier), false);
            curY+=lineH;
            if (opp.derpyWarning) {
                context.drawText(mc.textRenderer, "⚠️ Derpy: AH tax increased — profit reduced", x+5, curY, ColorUtils.WARNING, false);
                curY+=lineH;
            }
        } else {
            context.drawText(mc.textRenderer, "Profit: " + MathUtils.formatCoins(opp.profitPerUnitAfterTax) + " / " + MathUtils.formatCoins(opp.totalProfitAfterTax), x+5, curY, ColorUtils.PROFIT_POSITIVE, false);
            curY+=lineH;
            context.drawText(mc.textRenderer, "Bazaar Tax: " + String.format("%.2f%%", opp.taxRate*100), x+5, curY, ColorUtils.SECONDARY_TEXT, false);
            curY+=lineH;
        }

        context.drawText(mc.textRenderer, "Volume: " + MathUtils.formatCoins(opp.dailyVolume), x+5, curY, ColorUtils.SECONDARY_TEXT, false);
        curY+=lineH;

        context.drawText(mc.textRenderer, "Backlog: " + (int)opp.backlogPressure + " orders ahead", x+5, curY, ColorUtils.SECONDARY_TEXT, false);
        curY+=lineH;

        context.drawText(mc.textRenderer, "Orders at top: " + opp.orderCountAtTop, x+5, curY, ColorUtils.SECONDARY_TEXT, false);
        curY+=lineH;

        context.drawText(mc.textRenderer, "Budget Profit: " + MathUtils.formatCoins(opp.budgetProfit), x+5, curY, ColorUtils.PROFIT_POSITIVE, false);
        curY+=lineH;

        context.drawText(mc.textRenderer, "Fill Est: " + formatDuration(opp.fillTimeEstimateMs), x+5, curY, ColorUtils.SECONDARY_TEXT, false);
        curY+=lineH;

        String stability = opp.priceStabilityScore < 5 ? "Stable" : opp.priceStabilityScore < 15 ? "Moderate" : "Volatile";
        context.drawText(mc.textRenderer, "Stability: " + stability, x+5, curY, ColorUtils.SECONDARY_TEXT, false);
        curY+=lineH;

        if (opp.mayorModifier !=0 && opp.mayorModifier !=1.0) {
            context.drawText(mc.textRenderer, "Mayor Mod: " + String.format("%.2fx", opp.mayorModifier), x+5, curY, ColorUtils.TITLE_TEXT, false);
            curY+=lineH;
        }

        MayorData mayor = mayorTracker.getCurrentMayor();
        if (mayor != null && mayor.isDerpy() && opp.strategyType.equals("AH_CRAFT")) {
            context.drawText(mc.textRenderer, "Derpy active!", x+5, curY, ColorUtils.WARNING, false);
            curY+=lineH;
        }

        // Sparkline would be ProfitGraphWidget - placeholder not rendered here
    }

    private int getTaxColor(String tier) {
        if (tier == null) return ColorUtils.SECONDARY_TEXT;
        if (tier.contains("LOW")) return ColorUtils.TAX_LOW;
        if (tier.contains("MID")) return ColorUtils.TAX_MID;
        if (tier.contains("HIGH")) return ColorUtils.TAX_HIGH;
        return ColorUtils.SECONDARY_TEXT;
    }

    private String formatDuration(long ms) {
        long sec = ms/1000;
        long min = sec/60;
        long hr = min/60;
        sec%=60; min%=60;
        if (hr>0) return hr+"h "+min+"m";
        if (min>0) return min+"m "+sec+"s";
        return sec+"s";
    }
}
