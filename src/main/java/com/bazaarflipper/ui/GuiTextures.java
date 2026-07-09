package com.bazaarflipper.ui;

import net.minecraft.resources.ResourceLocation;

/**
 * Optional GUI textures — user allowed adding textures despite original spec saying geometry-only.
 * All panels, buttons, etc can now be textured for enhanced visuals while maintaining geometry fallback.
 * Textures generated via AI, located in assets/bazaarflipper/textures/gui/
 */
public class GuiTextures {
    public static final ResourceLocation PANEL_BG = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/panel_bg.png");
    public static final ResourceLocation HUD_PANEL = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/hud_panel.png");
    public static final ResourceLocation BUTTON = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/button.png");
    public static final ResourceLocation BUTTON_HOVER = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/button_hover.png");
    public static final ResourceLocation TAB_ACTIVE = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/tab_active.png");
    public static final ResourceLocation TAB_INACTIVE = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/tab_inactive.png");
    public static final ResourceLocation PROGRESS_BG = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/progress_bg.png");
    public static final ResourceLocation PROGRESS_FILL = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/progress_fill.png");
    public static final ResourceLocation PROGRESS_GOLD = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/progress_gold.png");
    public static final ResourceLocation TOAST = ResourceLocation.fromNamespaceAndPath("bazaarflipper", "textures/gui/toast.png");

    // Additional colored variants could be generated, but we reuse base with tint via color multiplication
    // For budget, break, tax colors we still use fill() over texture for tinting
}
