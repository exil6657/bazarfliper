package com.bazaarflipper.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ChatUtils {
    public static String stripColorCodes(String input) {
        if (input == null) return "";
        // Strip §X color codes
        return input.replaceAll("§.", "");
    }

    public static void sendClientMessage(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(message), false);
        }
    }

    public static void sendClientMessage(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(text, false);
        }
    }

    public static String getScoreboardTitle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return "";
        try {
            var scoreboard = mc.world.getScoreboard();
            var objective = scoreboard.getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
            if (objective == null) return "";
            return stripColorCodes(objective.getDisplayName().getString());
        } catch (Exception e) {
            return "";
        }
    }
}
