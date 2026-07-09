package com.bazaarflipper.automation;

import com.bazaarflipper.util.MathUtils;
import net.minecraft.client.MinecraftClient;

/**
 * Simulates human-like mouse movement via bezier curves.
 * Populate position fields with values consistent with player's actual cursor.
 */
public class MouseSimulator {

    private double currentX = 0;
    private double currentY = 0;

    public void moveMouseTo(double targetX, double targetY, long durationMs) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;

        double startX = currentX;
        double startY = currentY;
        long startTime = System.currentTimeMillis();

        // Bezier curve with 2 control points for human-like movement
        double cp1x = startX + MathUtils.randomDouble(-50, 50);
        double cp1y = startY + MathUtils.randomDouble(-50, 50);
        double cp2x = targetX + MathUtils.randomDouble(-50, 50);
        double cp2y = targetY + MathUtils.randomDouble(-50, 50);

        // Simulate in small steps
        while (System.currentTimeMillis() - startTime < durationMs) {
            double t = (double)(System.currentTimeMillis() - startTime) / durationMs;
            // Cubic bezier
            double inv = 1 - t;
            double x = inv*inv*inv*startX + 3*inv*inv*t*cp1x + 3*inv*t*t*cp2x + t*t*t*targetX;
            double y = inv*inv*inv*startY + 3*inv*inv*t*cp1y + 3*inv*t*t*cp2y + t*t*t*targetY;

            currentX = x;
            currentY = y;

            // Optionally move actual mouse via GLFW? Fabric doesn't expose easily; we simulate logically
            // Real cursor position is tracked via Mouse object but for packet filling we use this simulated position

            try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        currentX = targetX;
        currentY = targetY;
    }

    public double getCurrentX() { return currentX; }
    public double getCurrentY() { return currentY; }

    public void setCurrentPos(double x, double y) {
        currentX = x;
        currentY = y;
    }
}
