package com.bazaarflipper.pathfinding;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Vec3d;

public class MovementSimulator {

    public void pressKey(GameOptions options, boolean pressed) {
        // Generic pressKey via options binding - caller should pass specific key binding
        // This method signature per spec: pressKey(GameOptions option, boolean pressed) - but GameOptions contains key bindings
        // We interpret as pressing forward etc: We'll use direct key bindings via options
        // Since spec says MinecraftClient.getInstance().options key bindings .setPressed()
        // So we need specific key: e.g., options.forwardKey
        // Placeholder
    }

    public void pressForward(boolean pressed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(pressed);
        }
    }

    public void pressBack(boolean pressed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.backKey.setPressed(pressed);
        }
    }

    public void pressLeft(boolean pressed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.leftKey.setPressed(pressed);
        }
    }

    public void pressRight(boolean pressed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.rightKey.setPressed(pressed);
        }
    }

    public void setYaw(float targetYaw, float speed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            float current = mc.player.getYaw();
            // Simple interpolation
            float diff = targetYaw - current;
            // Normalize -180..180
            while (diff > 180) diff -= 360;
            while (diff < -180) diff += 360;
            float step = Math.signum(diff) * Math.min(Math.abs(diff), speed);
            mc.player.setYaw(current + step);
        }
    }

    public void setPitch(float targetPitch, float speed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            float current = mc.player.getPitch();
            float diff = targetPitch - current;
            float step = Math.signum(diff) * Math.min(Math.abs(diff), speed);
            mc.player.setPitch(current + step);
        }
    }

    public void lookAt(Vec3d targetPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d diff = targetPos.subtract(playerPos);
        double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, dist));
        setYaw(yaw, 10F);
        setPitch(pitch, 10F);
    }

    public void jump() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.jump();
        }
    }

    public void sprint(boolean sprinting) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.setSprinting(sprinting);
        }
    }

    public void stopAllMovement() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        if (mc.player != null) {
            mc.player.setVelocity(Vec3d.ZERO);
        }
    }
}
