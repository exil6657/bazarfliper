package com.bazaarflipper.ui;

import com.bazaarflipper.util.ColorUtils;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Simple sparkline graph drawn entirely via GuiGraphics.fill()
 */
public class ProfitGraphWidget {

    public void render(GuiGraphics context, int x, int y, int width, int height, List<Double> values) {
        // Background filled rectangle in dark color
        context.fill(x, y, x+width, y+height, ColorUtils.PANEL_BG);
        // Border 1px
        context.fill(x-1, y-1, x+width+1, y, ColorUtils.PANEL_BORDER);
        context.fill(x-1, y+height, x+width+1, y+height+1, ColorUtils.PANEL_BORDER);
        context.fill(x-1, y, x, y+height, ColorUtils.PANEL_BORDER);
        context.fill(x+width, y, x+width+1, y+height, ColorUtils.PANEL_BORDER);

        if (values == null || values.isEmpty()) return;

        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double range = max - min;
        if (range == 0) range = 1;

        double zeroBaseline = 0;
        // Map zero to Y position
        int zeroY = y + height - (int)((0 - min)/range * height);

        // For each data point draw vertical 1px column proportional to value
        int n = values.size();
        double stepX = (double)width / Math.max(1, n);
        for (int i=0; i<n; i++) {
            double val = values.get(i);
            int colHeight = (int)((Math.abs(val - min)/range) * height * 0.8); // proportional
            int colX = x + (int)(i*stepX);
            int colY = y + height - colHeight;

            int color = val >= 0 ? ColorUtils.PROFIT_POSITIVE : ColorUtils.PROFIT_NEGATIVE;
            context.fill(colX, colY, colX+1, y+height, color);
        }

        // Baseline zero line thin horizontal gray
        if (zeroBaseline >= min && zeroBaseline <= max) {
            context.fill(x, zeroY, x+width, zeroY+1, ColorUtils.BORDER_ACCENT);
        }
    }
}
