package com.bazaarflipper.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ChatUtils {
    public static String stripColorCodes(String input) {
        if (input == null) return "";
        // Strip §X color codes
        return input.replaceAll("§.", "");
    }

    public static void sendClientMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }

    public static void sendClientMessage(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(text);
        }
    }

    public static String getScoreboardTitle() {
        try {
            Object objective = getSidebarObjective();
            if (objective == null) return "";
            Object displayName = invokeAny(objective, "getDisplayName", "displayName");
            return stripColorCodes(componentToString(displayName));
        } catch (Exception e) {
            return "";
        }
    }

    public static List<String> getSidebarLines() {
        List<String> lines = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return lines;
            Object scoreboard = mc.level.getScoreboard();
            Object objective = getSidebarObjective(scoreboard);
            if (objective == null) return lines;
            Object scores = invokeAny(scoreboard, objective, "getPlayerScores", "listPlayerScores", "getAllPlayerScores");
            if (scores instanceof Iterable<?> iterable) {
                for (Object score : iterable) {
                    String line = scoreToLine(score);
                    if (!line.isBlank()) lines.add(stripColorCodes(line));
                }
            }
        } catch (Exception ignored) {}
        return lines;
    }

    private static Object getSidebarObjective() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return getSidebarObjective(mc.level.getScoreboard());
    }

    private static Object getSidebarObjective(Object scoreboard) throws Exception {
        return invokeAny(scoreboard, DisplaySlot.SIDEBAR, "getDisplayObjective", "getObjectiveForSlot");
    }

    private static String scoreToLine(Object score) {
        try {
            Object owner = invokeAny(score, "getPlayerName", "owner", "getOwner", "getOwnerName");
            if (owner == null) return "";
            return componentToString(owner);
        } catch (Exception ignored) {
            return score == null ? "" : score.toString();
        }
    }

    private static String componentToString(Object component) {
        if (component == null) return "";
        if (component instanceof Component c) return c.getString();
        if (component instanceof String s) return s;
        try {
            Method m = component.getClass().getMethod("getString");
            Object v = m.invoke(component);
            return v == null ? "" : String.valueOf(v);
        } catch (Exception ignored) {
            return String.valueOf(component);
        }
    }

    private static Object invokeAny(Object target, String... names) throws Exception {
        Exception last = null;
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Exception e) { last = e; }
        }
        if (last != null) throw last;
        return null;
    }

    private static Object invokeAny(Object target, Object arg, String... names) throws Exception {
        Exception last = null;
        for (String name : names) {
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 1) continue;
                try { return m.invoke(target, arg); } catch (Exception e) { last = e; }
            }
        }
        if (last != null) throw last;
        return null;
    }
}
