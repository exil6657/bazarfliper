package com.bazaarflipper.ui;

import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.engine.ActiveFlip;
import com.bazaarflipper.engine.FlipEngine;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorTracker;
import com.bazaarflipper.util.ColorUtils;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ActiveFlipsWidget {

    private final FlipEngine flipEngine;
    private final TaxCalculator taxCalculator;
    private final MayorTracker mayorTracker;

    private int scrollOffset = 0;

    public ActiveFlipsWidget(FlipEngine engine, TaxCalculator taxCalculator, MayorTracker mayorTracker) {
        this.flipEngine = engine;
        this.taxCalculator = taxCalculator;
        this.mayorTracker = mayorTracker;
    }

    public void render(DrawContext context, int x, int y, int width, int height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Border accent: green if session profit positive, red if negative per spec
        // We don't have ProfitTracker here but we can infer from estimated profits? For now use positive green default, will be overridden by actual profit logic in HudOverlay if needed
        // Background with border accent based on last rendered profit sign - simplified green if any profitable flips
        boolean anyPositive = false;
        try {
            for (var f : flipEngine.getActiveFlips().values()) {
                double est = taxCalculator.calculateBazaarProfit(f.buyPrice, f.targetSellPrice) * f.quantity;
                if (est > 0) { anyPositive = true; break; }
            }
        } catch (Exception ignored) {}
        int borderAccent = anyPositive ? ColorUtils.PROFIT_POSITIVE : ColorUtils.PROFIT_NEGATIVE;
        // Outer border using accent? Spec says border accent green if profit positive else red - we draw accent as top border 2px
        context.fill(x-1, y-1, x+width+1, y+height+1, ColorUtils.PANEL_BORDER);
        context.fill(x, y, x+width, y+height, ColorUtils.PANEL_BG);
        // Accent line at top 2px
        context.fill(x, y, x+width, y+2, borderAccent);

        Map<String, ActiveFlip> flips = flipEngine.getActiveFlips();
        if (flips.isEmpty()) {
            String msg = flipEngine.getState().name().contains("BREAK") ? "No active flips — on break" : "No active flips — scanning...";
            context.drawText(mc.textRenderer, msg, x+5, y+5, ColorUtils.SECONDARY_TEXT, false);
            return;
        }

        List<ActiveFlip> list = new ArrayList<>(flips.values());
        // Sort? Maybe by profit or time

        int rowHeight = 20;
        int visibleRows = height / rowHeight;
        int maxRows = Math.min(28, list.size());

        for (int i=0; i<visibleRows && (i+scrollOffset)<maxRows; i++) {
            int idx = i+scrollOffset;
            ActiveFlip flip = list.get(idx);
            int rowY = y + i*rowHeight;

            int bgColor = (idx %2 ==0) ? 0xFF0F0F0F : 0xFF111111;
            context.fill(x, rowY, x+width, rowY+rowHeight, bgColor);

            // Item display name truncated
            String displayName = flip.productId;
            if (displayName.length() > 18) displayName = displayName.substring(0,15)+"...";
            context.drawText(mc.textRenderer, displayName, x+5, rowY+5, ColorUtils.ITEM_NAME, false);

            // State dot
            int dotColor = getDotColorForState(flip.state);
            context.drawText(mc.textRenderer, "●", x+80, rowY+5, dotColor, false);

            // Buy/Sell/Qty
            context.drawText(mc.textRenderer, MathUtils.formatCoins(flip.buyPrice), x+90, rowY+5, ColorUtils.PRIMARY_TEXT, false);
            context.drawText(mc.textRenderer, MathUtils.formatCoins(flip.targetSellPrice), x+130, rowY+5, ColorUtils.PRIMARY_TEXT, false);
            context.drawText(mc.textRenderer, String.valueOf(flip.quantity), x+170, rowY+5, ColorUtils.SECONDARY_TEXT, false);

            // Estimated profit live recalculated using correct tax
            double estProfit = 0;
            MayorData mayor = mayorTracker.getCurrentMayor();
            if ("AH_CRAFT".equals(flip.strategyType)) {
                estProfit = taxCalculator.calculateAHProfit(flip.buyPrice, flip.targetSellPrice, mayor) * flip.quantity;
            } else {
                estProfit = taxCalculator.calculateBazaarProfit(flip.buyPrice, flip.targetSellPrice) * flip.quantity;
            }
            int profitColor = estProfit>=0 ? ColorUtils.PROFIT_POSITIVE : ColorUtils.PROFIT_NEGATIVE;
            context.drawText(mc.textRenderer, MathUtils.formatCoins(estProfit), x+190, rowY+5, profitColor, false);

            // Fill progress bar
            int barX = x+90;
            int barY = rowY+15;
            int barW = 60;
            int barH = 3;
            context.fill(barX, barY, barX+barW, barY+barH, ColorUtils.PROGRESS_BG);
            double fillPct = flip.quantity>0 ? (double)flip.filledAmount / flip.quantity : 0;
            int fillW = (int)(barW * fillPct);
            context.fill(barX, barY, barX+fillW, barY+barH, ColorUtils.PROGRESS_FILL);

            // Time since order
            long age = System.currentTimeMillis() - flip.placementTimestamp;
            context.drawText(mc.textRenderer, formatDuration(age), x+160, rowY+15, ColorUtils.SECONDARY_TEXT, false);

            // Relist count
            if (flip.relistCount>0) {
                context.drawText(mc.textRenderer, "↺"+flip.relistCount, x+5, rowY+15, ColorUtils.WARNING, false);
            }

            // Strategy badge
            String badge = switch (flip.strategyType) {
                case "ORDER" -> "[ORDER]";
                case "CRAFT" -> "[CRAFT]";
                case "NPC" -> "[NPC]";
                case "AH_CRAFT" -> "[AH]";
                default -> "[?]";
            };
            context.drawText(mc.textRenderer, badge, x+220, rowY+5, ColorUtils.SECONDARY_TEXT, false);

            // For AH flips: small tax tier badge [1%], [2%], [2.5%] etc
            if ("AH_CRAFT".equals(flip.strategyType)) {
                MayorData curMayor = mayorTracker.getCurrentMayor();
                String tierBadge = formatTaxBadge(flip.targetSellPrice, curMayor);
                int badgeColor = getTaxBadgeColor(flip.targetSellPrice, curMayor);
                context.drawText(mc.textRenderer, tierBadge, x+220, rowY+15, badgeColor, false);
            }
        }

        // Scroll indicator rectangle on right edge
        if (list.size() > visibleRows) {
            int scrollBarHeight = (int)(height * (visibleRows / (double)list.size()));
            int scrollBarY = y + (int)(height * (scrollOffset / (double)list.size()));
            context.fill(x+width-3, scrollBarY, x+width, scrollBarY+scrollBarHeight, ColorUtils.SCROLL_BAR);
        }

        // Border accent green if profit positive else red
        // For simplicity use profitTracker profit
    }

    private String formatTaxBadge(double salePrice, MayorData mayor) {
        TaxCalculator.AHTaxTier tier = taxCalculator.getAHTaxTier(salePrice);
        double rate = taxCalculator.getAHTaxRate(salePrice, mayor);
        boolean derpy = taxCalculator.isDerpyActive(mayor) && salePrice >= 1_000_000;
        if (derpy) {
            return String.format("[%.0f%%+D]", rate*100);
        } else {
            return String.format("[%.0f%%]", rate*100);
        }
    }

    private int getTaxBadgeColor(double salePrice, MayorData mayor) {
        TaxCalculator.AHTaxTier tier = taxCalculator.getAHTaxTier(salePrice);
        boolean derpy = taxCalculator.isDerpyActive(mayor) && salePrice >= 1_000_000;
        if (derpy) return ColorUtils.TAX_DERPY;
        return switch (tier) {
            case LOW -> ColorUtils.TAX_LOW;
            case MID -> ColorUtils.TAX_MID;
            case HIGH -> ColorUtils.TAX_HIGH;
        };
    }

    private int getDotColorForState(String state) {
        if (state == null) return ColorUtils.SECONDARY_TEXT;
        if (state.contains("BREAK")) return ColorUtils.BREAK_PURPLE;
        if (state.contains("PLACE")) return ColorUtils.WARNING;
        if (state.contains("CLAIM")) return ColorUtils.PROFIT_POSITIVE;
        return ColorUtils.PRIMARY_TEXT;
    }

    private String formatDuration(long ms) {
        long sec = ms/1000;
        long min = sec/60;
        sec%=60;
        if (min>0) return min+"m "+sec+"s";
        return sec+"s";
    }

    public void scroll(int amount) {
        scrollOffset = Math.max(0, scrollOffset+amount);
    }
}
