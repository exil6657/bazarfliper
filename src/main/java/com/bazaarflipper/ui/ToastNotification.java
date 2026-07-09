package com.bazaarflipper.ui;

import com.bazaarflipper.util.ColorUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Custom toast drawn in HUD layer via HudRenderCallback. No vanilla toast system.
 * Drawing slide-in animation, type-specific colors.
 */
public class ToastNotification {

    public enum ToastType {
        INFO, SUCCESS, WARNING, ERROR, BREAK
    }

    public static class Toast {
        public String message;
        public ToastType type;
        public long startTime;
        public long durationMs = 5000;
        public float slideProgress = 0; // 0..1 for slide-in
        public boolean dismissing = false;

        public Toast(String msg, ToastType type) {
            this.message = msg;
            this.type = type;
            this.startTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - startTime > durationMs && !dismissing;
        }
    }

    private static final List<Toast> activeToasts = new ArrayList<>();
    private static final int MAX_VISIBLE = 3;

    public static void show(String message, ToastType type) {
        synchronized (activeToasts) {
            if (activeToasts.size() >= MAX_VISIBLE) {
                // Queue excess - for simplicity drop oldest
                activeToasts.remove(0);
            }
            activeToasts.add(new Toast(message, type));
        }
    }

    public static void render(GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = 320;
        // int screenHeight = mc.getWindow().getScaledHeight();

        synchronized (activeToasts) {
            Iterator<Toast> it = activeToasts.iterator();
            int index = 0;
            while (it.hasNext()) {
                Toast toast = it.next();
                long elapsed = System.currentTimeMillis() - toast.startTime;

                // Slide-in animation X position interpolates from off-screen right to final position using linear interpolation on timer
                // Duration 300ms slide-in
                float slideDuration = 300;
                float slideOutDuration = 300;
                float progress = 0;
                int toastWidth = 200;
                int toastHeight = 30;
                int finalX = screenWidth - toastWidth - 10;
                int finalY = 10 + index * (toastHeight + 5);

                if (elapsed < slideDuration) {
                    progress = elapsed / slideDuration;
                } else if (elapsed > toast.durationMs - slideOutDuration) {
                    progress = 1.0f - (elapsed - (toast.durationMs - slideOutDuration)) / slideOutDuration;
                    toast.dismissing = true;
                } else {
                    progress = 1.0f;
                }
                progress = Math.max(0, Math.min(1, progress));

                int offScreenX = screenWidth;
                int currentX = (int)(offScreenX + (finalX - offScreenX) * progress);

                // Background in type-appropriate color semi-transparent
                int bgColor = switch (toast.type) {
                    case INFO -> ColorUtils.withAlpha(0xFF1A1A3A, 0xCC);
                    case SUCCESS -> ColorUtils.withAlpha(0xFF1A3A1A, 0xCC);
                    case WARNING -> ColorUtils.withAlpha(0xFF3A3A1A, 0xCC);
                    case ERROR -> ColorUtils.withAlpha(0xFF3A1A1A, 0xCC);
                    case BREAK -> ColorUtils.withAlpha(0xFF2A1A3A, 0xCC);
                };
                // Actually ColorUtils already has CC alpha versions
                int actualBg = switch (toast.type) {
                    case INFO -> 0xCC1A1A3A;
                    case SUCCESS -> 0xCC1A3A1A;
                    case WARNING -> 0xCC3A3A1A;
                    case ERROR -> 0xCC3A1A1A;
                    case BREAK -> 0xCC2A1A3A;
                };

                // Draw border and background
                context.fill(currentX, finalY, currentX+toastWidth, finalY+toastHeight, actualBg);
                context.fill(currentX-1, finalY-1, currentX+toastWidth+1, finalY, ColorUtils.PANEL_BORDER);
                context.fill(currentX-1, finalY+toastHeight, currentX+toastWidth+1, finalY+toastHeight+1, ColorUtils.PANEL_BORDER);
                context.fill(currentX-1, finalY, currentX, finalY+toastHeight, ColorUtils.PANEL_BORDER);
                context.fill(currentX+toastWidth, finalY, currentX+toastWidth+1, finalY+toastHeight, ColorUtils.PANEL_BORDER);

                String icon = switch (toast.type) {
                    case INFO -> "ℹ";
                    case SUCCESS -> "✓";
                    case WARNING -> "⚠";
                    case ERROR -> "✗";
                    case BREAK -> "☕";
                };
                context.text(mc.font, icon + " " + toast.message, currentX+5, finalY+10, ColorUtils.PRIMARY_TEXT, false);

                if (toast.isExpired()) {
                    it.remove();
                } else {
                    index++;
                }
            }
        }
    }

    // Break-specific toasts helpers
    public static void showShortBreak(long durationMs) {
        show("Short break starting (" + formatDuration(durationMs) + ")", ToastType.BREAK);
    }

    public static void showLongBreak(long durationMs) {
        show("Long break starting — automatic resume after " + formatDuration(durationMs), ToastType.BREAK);
    }

    public static void showBreakEnd() {
        show("Break complete — resuming", ToastType.SUCCESS);
    }

    public static void showOrderFillDuringBreak() {
        show("Order filled during break — will claim after break ends", ToastType.WARNING);
    }

    public static void showDerpyDetected() {
        show("⚠️ Derpy active — AH claiming tax increased", ToastType.WARNING);
    }

    private static String formatDuration(long ms) {
        long sec = ms/1000;
        long min = sec/60;
        sec%=60;
        if (min>0) return min+"m "+sec+"s";
        return sec+"s";
    }
}
