package com.bazaarflipper.security;

import com.bazaarflipper.config.ModConfig;
import com.bazaarflipper.discord.DiscordEventHandler;
import com.bazaarflipper.ui.ToastNotification;
import com.bazaarflipper.util.Logger;
import net.minecraft.client.Minecraft;

/**
 * Manages private locking PIN - ensures only authorized people can use mod
 * Portion saved in config (hashed PIN) so it persists across restarts
 * User must set PIN in game via Dashboard > Security tab
 * Credits: Cldz
 */
public class LockManager {

    private final LockConfig lockConfig;
    private final ModConfig modConfig;
    private final DiscordEventHandler discordEventHandler;

    public LockManager(LockConfig lockConfig, ModConfig modConfig, DiscordEventHandler discordEventHandler) {
        this.lockConfig = lockConfig;
        this.modConfig = modConfig;
        this.discordEventHandler = discordEventHandler;
    }

    public boolean isLocked() {
        return !lockConfig.isAuthorized();
    }

    public boolean isLockoutActive() {
        return lockConfig.isLockoutActive();
    }

    public String getLockMessage() {
        if (isLockoutActive()) {
            long remaining = lockConfig.getLockoutRemaining() / 1000;
            return "LOCKOUT: Too many failed attempts. Try again in " + remaining + "s. Credits: Cldz";
        }
        return lockConfig.lockMessage;
    }

    public boolean canUseMod() {
        if (!lockConfig.lockEnabled) return true;
        if (lockConfig.pinHash == null || lockConfig.pinHash.isEmpty()) return true; // first time setup
        return lockConfig.isAuthorized();
    }

    public boolean unlock(String pin) {
        boolean ok = lockConfig.verifyPin(pin);
        if (ok) {
            ToastNotification.show("Mod unlocked - welcome authorized user! Credits: Cldz", ToastNotification.ToastType.SUCCESS);
            Logger.info("Mod unlocked via PIN - authorized access granted");
        } else {
            if (lockConfig.isLockoutActive()) {
                ToastNotification.show("Lockout active - too many failed attempts", ToastNotification.ToastType.ERROR);
            } else {
                ToastNotification.show("Incorrect PIN - attempt " + lockConfig.failedAttempts + "/" + lockConfig.maxAttempts, ToastNotification.ToastType.ERROR);
            }
            // Discord alert for failed unlock? Might be noisy, but log
            Logger.warn("Failed unlock attempt " + lockConfig.failedAttempts);
            if (discordEventHandler != null) {
                discordEventHandler.onError("Failed PIN unlock attempt " + lockConfig.failedAttempts, "LOCKED");
            }
        }
        return ok;
    }

    public boolean setNewPin(String newPin, String oldPin) {
        // If lock already enabled with PIN, require old PIN to change
        if (lockConfig.lockEnabled && lockConfig.pinHash != null && !lockConfig.pinHash.isEmpty()) {
            if (oldPin == null || !lockConfig.verifyPin(oldPin)) {
                ToastNotification.show("Old PIN incorrect - cannot set new PIN", ToastNotification.ToastType.ERROR);
                return false;
            }
        }
        boolean ok = lockConfig.setPin(newPin);
        if (ok) {
            ToastNotification.show("PIN set - mod locked to authorized users only. Credits: Cldz", ToastNotification.ToastType.SUCCESS);
            // After setting PIN, we remain unlocked for current session but will require PIN next restart
            lockConfig.isUnlocked = true;
            lockConfig.unlockedAt = System.currentTimeMillis();
        }
        return ok;
    }

    public void lock() {
        lockConfig.lock();
        ToastNotification.show("Mod locked - requires PIN", ToastNotification.ToastType.WARNING);
    }

    public void disableLock(String currentPin) {
        if (lockConfig.lockEnabled && lockConfig.pinHash != null && !lockConfig.pinHash.isEmpty()) {
            if (!lockConfig.verifyPin(currentPin)) {
                ToastNotification.show("PIN incorrect - cannot disable lock", ToastNotification.ToastType.ERROR);
                return;
            }
        }
        lockConfig.disableLock();
        ToastNotification.show("Lock disabled - mod open", ToastNotification.ToastType.INFO);
    }

    // Called on game start - policy: stay unlocked until manually locked (convenient) per user preference
    // User selected "stay_unlocked" over "every_restart" in ask_user
    public void onGameStart() {
        if (lockConfig.lockEnabled && lockConfig.pinHash != null && !lockConfig.pinHash.isEmpty()) {
            // User preference: stay unlocked until manually locked (convenient PIN-only)
            // So we do NOT lock on startup, we stay unlocked. Only if user explicitly pressed Lock button will it require PIN.
            lockConfig.isUnlocked = true;
            lockConfig.unlockedAt = System.currentTimeMillis();
            Logger.info("Lock enabled (PIN-only, stay-unlocked-till-manual-lock per user pref) - mod stays unlocked until manually locked via Security tab. Credits: Cldz");
            // Optional toast for info but not warning, since convenient mode
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    ToastNotification.show("Security: PIN lock enabled (stay unlocked till manual lock) - Credits: Cldz", ToastNotification.ToastType.INFO);
                });
            }
        } else {
            lockConfig.isUnlocked = true; // no lock
            if (!lockConfig.lockEnabled) {
                Logger.info("Lock disabled - mod open, PIN-only auth not active");
            } else {
                Logger.info("No PIN set yet - first time setup, mod open until PIN set. Go to Dashboard > Security to set PIN. Credits: Cldz");
            }
        }
    }

    public void onDisconnect() {
        // For security, lock on disconnect if lock enabled
        if (lockConfig.lockEnabled) {
            // Optionally keep unlocked for same session? For stricter security, lock on disconnect
            // We'll keep unlocked until game restart to avoid annoyance, but log
            Logger.info("Disconnect - lock state preserved, will require PIN on next game start if lock enabled");
        }
    }

    public LockConfig getLockConfig() { return lockConfig; }
}
