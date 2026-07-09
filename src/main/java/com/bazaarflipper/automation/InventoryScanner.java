package com.bazaarflipper.automation;

import com.bazaarflipper.util.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;

public class InventoryScanner {

    public double getPurseBalance() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return 0;
            Scoreboard scoreboard = mc.world.getScoreboard();
            ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (objective == null) return 0;
            for (ScoreboardScore score : scoreboard.getAllPlayerScores(objective)) {
                if (score.getPlayerName() == null) continue;
                String line = ChatUtils.stripColorCodes(score.getPlayerName().getString());
                if (line.contains("Purse:")) {
                    // Format: "Purse: 1,234,567 Coins"
                    String stripped = line.replace("Purse:", "").replace("Coins", "").replace(",", "").trim();
                    try {
                        return Double.parseDouble(stripped);
                    } catch (NumberFormatException e) {
                        // Try to extract number via regex
                        String num = stripped.replaceAll("[^0-9.]", "");
                        if (!num.isEmpty()) return Double.parseDouble(num);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    public double getBankBalance() {
        // Parse from bank NPC GUI lore - would need open GUI
        // Placeholder: return 0, real impl would open bank GUI and parse
        return 0;
    }

    public static class OrderLoreInfo {
        public double pricePerUnit;
        public int quantity;
        public int filledQuantity;
        public double filledPercent;
    }

    public OrderLoreInfo parseOrderLore(ItemStack stack) {
        OrderLoreInfo info = new OrderLoreInfo();
        // Parse Hypixel bazaar order lore: need NBT lore lines
        // Example lore contains "Price per unit: 1,234 coins", "Quantity: 1000", "Filled: 500 (50%)"
        // Simplified placeholder
        try {
            // In 26.1.2, lore is in DataComponents? We'll use generic approach
            // This would require checking stack's custom name and lore via DataComponentTypes.LORE
            // For spec compliance, note we parse via lore text - actual implementation would fetch tooltip
            var loreComponent = stack.get(net.minecraft.component.DataComponentTypes.LORE);
            if (loreComponent != null) {
                for (var line : loreComponent.lines()) {
                    String text = ChatUtils.stripColorCodes(line.getString());
                    if (text.toLowerCase().contains("price per unit")) {
                        String num = text.replaceAll("[^0-9.]", "");
                        if (!num.isEmpty()) info.pricePerUnit = Double.parseDouble(num);
                    }
                    if (text.toLowerCase().contains("quantity")) {
                        String num = text.replaceAll("[^0-9]", "");
                        if (!num.isEmpty()) info.quantity = Integer.parseInt(num);
                    }
                    if (text.toLowerCase().contains("filled")) {
                        // e.g. "Filled: 500 (50%)"
                        String[] parts = text.split(":");
                        if (parts.length > 1) {
                            String after = parts[1];
                            String num = after.split("\\(")[0].replaceAll("[^0-9]", "").trim();
                            if (!num.isEmpty()) info.filledQuantity = Integer.parseInt(num);
                        }
                        // Percent
                        if (text.contains("%")) {
                            String pctStr = text.replaceAll(".*\\((.*)%\\).*", "$1");
                            try { info.filledPercent = Double.parseDouble(pctStr) / 100.0; } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return info;
    }

    public boolean detectChatOrderFill(String msg) {
        String stripped = ChatUtils.stripColorCodes(msg);
        return stripped.contains("Your Buy Order for") && stripped.contains("was filled") ||
               stripped.contains("Your Sell Offer for") && stripped.contains("was filled") ||
               stripped.contains("Your BIN Auction for") && stripped.contains("was sold");
    }

    public ItemStack findItemInInventory(String productId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            // Check if matches productId - simplified by name
            String display = stack.getName().getString().toLowerCase();
            if (display.contains(productId.toLowerCase().replace('_', ' '))) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public int countItemInInventory(String productId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String display = stack.getName().getString().toLowerCase();
            if (display.contains(productId.toLowerCase().replace('_', ' '))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean isInventoryFull() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        return mc.player.getInventory().getEmptySlot() == -1;
    }
}
