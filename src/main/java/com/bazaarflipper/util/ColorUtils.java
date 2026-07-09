package com.bazaarflipper.util;

public class ColorUtils {
    // Theme colors from spec as ARGB
    public static final int PANEL_BG = 0xCC111111;
    public static final int PANEL_BORDER = 0xFF333333;
    public static final int PANEL_INNER = 0xFF1A1A1A;
    public static final int TAB_ACTIVE_BG = 0xFF222222;
    public static final int TAB_INACTIVE_BG = 0xFF151515;
    public static final int TAB_ACTIVE_BORDER = 0xFFFFAA00;
    public static final int BUTTON_NORMAL = 0xFF1E1E1E;
    public static final int BUTTON_HOVER = 0xFF2A2A2A;
    public static final int BUTTON_ACTIVE = 0xFF333333;
    public static final int BUTTON_BORDER = 0xFF444444;
    public static final int TITLE_TEXT = 0xFFFFAA00;
    public static final int PRIMARY_TEXT = 0xFFEEEEEE;
    public static final int SECONDARY_TEXT = 0xFF888888;
    public static final int PROFIT_POSITIVE = 0xFF55FF55;
    public static final int PROFIT_NEGATIVE = 0xFFFF5555;
    public static final int ITEM_NAME = 0xFF55FFFF;
    public static final int WARNING = 0xFFFFFF55;
    public static final int BORDER_ACCENT = 0xFF333333;
    public static final int PROGRESS_BG = 0xFF1A1A1A;
    public static final int PROGRESS_FILL = 0xFF4CAF50;
    public static final int PROGRESS_BUDGET = 0xFFFFAA00;
    public static final int BREAK_PURPLE = 0xFFAA55FF;
    public static final int SCROLL_BAR = 0xFF444444;
    public static final int INPUT_BG = 0xFF0D0D0D;
    public static final int INPUT_BORDER_VALID = 0xFF333333;
    public static final int INPUT_BORDER_INVALID = 0xFFFF4444;

    // Status dot colors
    public static final int STATUS_ACTIVE = 0xFF55FF55; // green
    public static final int STATUS_PAUSED = 0xFFFF5555; // red
    public static final int STATUS_NAVIGATING = 0xFFFFFF55; // yellow
    public static final int STATUS_RECONNECTING = 0xFF5555FF; // blue
    public static final int STATUS_RECOVERING = 0xFFFFAA00; // orange
    public static final int STATUS_BREAK = 0xFFAA55FF; // purple

    // Tax tier colors
    public static final int TAX_LOW = 0xFF55FF55; // green 1%
    public static final int TAX_MID = 0xFFFFFF55; // yellow 2%
    public static final int TAX_HIGH = 0xFFFFAA00; // orange 2.5%
    public static final int TAX_DERPY = 0xFFFF5555; // red + Derpy

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
