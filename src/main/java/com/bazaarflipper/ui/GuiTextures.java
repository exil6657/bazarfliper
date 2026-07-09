package com.bazaarflipper.ui;

import net.minecraft.util.Identifier;

/**
 * Optional GUI textures — user allowed adding textures despite original spec saying geometry-only.
 * All panels, buttons, etc can now be textured for enhanced visuals while maintaining geometry fallback.
 * Textures generated via AI, located in assets/bazaarflipper/textures/gui/
 */
public class GuiTextures {
    public static final Identifier PANEL_BG = Identifier.of("bazaarflipper", "textures/gui/panel_bg.png");
    public static final Identifier HUD_PANEL = Identifier.of("bazaarflipper", "textures/gui/hud_panel.png");
    public static final Identifier BUTTON = Identifier.of("bazaarflipper", "textures/gui/button.png");
    public static final Identifier BUTTON_HOVER = Identifier.of("bazaarflipper", "textures/gui/button_hover.png");
    public static final Identifier TAB_ACTIVE = Identifier.of("bazaarflipper", "textures/gui/tab_active.png");
    public static final Identifier TAB_INACTIVE = Identifier.of("bazaarflipper", "textures/gui/tab_inactive.png");
    public static final Identifier PROGRESS_BG = Identifier.of("bazaarflipper", "textures/gui/progress_bg.png");
    public static final Identifier PROGRESS_FILL = Identifier.of("bazaarflipper", "textures/gui/progress_fill.png");
    public static final Identifier PROGRESS_GOLD = Identifier.of("bazaarflipper", "textures/gui/progress_gold.png");
    public static final Identifier TOAST = Identifier.of("bazaarflipper", "textures/gui/toast.png");

    // Additional colored variants could be generated, but we reuse base with tint via color multiplication
    // For budget, break, tax colors we still use fill() over texture for tinting
}
