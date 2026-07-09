package com.bazaarflipper.ui.widgets;

import com.bazaarflipper.ui.GuiTextures;
import com.bazaarflipper.util.ColorUtils;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Slider: track rectangle + handle rectangle at proportional position
 * Drawn via fill() + optional texture, per spec
 */
public class CustomSlider {
    public int x, y, width, height;
    public double min, max;
    public double value;
    public String label;
    public boolean dragging = false;

    public CustomSlider(int x, int y, int width, int height, double min, double max, double initial, String label) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.min = min; this.max = max; this.value = initial; this.label = label;
    }

    public boolean isMouseOver(double mx, double my) {
        return mx>=x && mx<=x+width && my>=y && my<=y+height;
    }

    public void mouseClicked(double mx, double my) {
        if (isMouseOver(mx,my)) {
            dragging = true;
            updateValueFromMouse(mx);
        }
    }

    public void mouseDragged(double mx, double my) {
        if (dragging) {
            updateValueFromMouse(mx);
        }
    }

    public void mouseReleased() {
        dragging = false;
    }

    private void updateValueFromMouse(double mx) {
        double t = (mx - x) / (double)width;
        t = MathUtils.clamp(t, 0, 1);
        value = min + t * (max - min);
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int trackY = y + height/2 - 2;
        int trackH = 4;

        // Track rectangle
        try {
            context.drawTexture(GuiTextures.PROGRESS_BG, x, trackY, 0,0, width, trackH, width, trackH);
        } catch (Exception e) {
            context.fill(x, trackY, x+width, trackY+trackH, ColorUtils.PROGRESS_BG);
        }

        // Fill proportional? Actually handle only, but we can draw fill up to handle
        double t = (value - min) / (max - min);
        int fillW = (int)(width * t);
        try {
            context.drawTexture(GuiTextures.PROGRESS_FILL, x, trackY, 0,0, fillW, trackH, fillW, trackH);
        } catch (Exception e) {
            context.fill(x, trackY, x+fillW, trackY+trackH, ColorUtils.PROGRESS_FILL);
        }

        // Handle rectangle at proportional position
        int handleW = 6;
        int handleH = height;
        int handleX = x + (int)(t * (width - handleW));
        context.fill(handleX, y, handleX+handleW, y+handleH, ColorUtils.BUTTON_BORDER);
        context.fill(handleX+1, y+1, handleX+handleW-1, y+handleH-1, dragging ? ColorUtils.BUTTON_HOVER : ColorUtils.BUTTON_NORMAL);

        // Label + value
        String display = label + ": " + String.format("%.1f", value);
        context.drawText(mc.textRenderer, display, x, y-10, ColorUtils.SECONDARY_TEXT, false);
    }
}
