package com.bazaarflipper.automation;

import com.bazaarflipper.util.Logger;
import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.MinecraftClient;

/**
 * Two distinct methods:
 * - sendCommand -> real server command via networkHandler.sendCommand()
 * - sendChatMessage -> chat via networkHandler.sendChatMessage() for bazaar search and price input
 *
 * All server-bound messages: randomized human-like delays, no mod identifiers, never in same tick as GUI state changes.
 */
public class ChatCommandSender {

    private final DelayManager delayManager;
    private long lastCommandTime = 0;

    public ChatCommandSender(DelayManager delayManager) {
        this.delayManager = delayManager;
    }

    public void sendCommand(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;

        // Ensure not same tick as GUI state changes: wait random delay
        long now = System.currentTimeMillis();
        long sinceLast = now - lastCommandTime;
        long minGap = MathUtils.randomInt(200, 600); // ensure gap from last GUI close/open
        if (sinceLast < minGap) {
            try { Thread.sleep(minGap - sinceLast); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // Randomized human-like delay before sending
        try {
            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.ACTION));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // No mod identifiers
        String cleanCommand = command.startsWith("/") ? command.substring(1) : command;
        Logger.info("Sending command: /" + cleanCommand);
        mc.execute(() -> {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendCommand(cleanCommand);
            }
        });
        lastCommandTime = System.currentTimeMillis();
    }

    public void sendChatMessage(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;

        // Randomized delay
        try {
            Thread.sleep(delayManager.getDelay(DelayManager.DelayType.CLICK));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Logger.info("Sending chat message: " + message);
        mc.execute(() -> {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendChatMessage(message);
            }
        });
        lastCommandTime = System.currentTimeMillis();
    }
}
