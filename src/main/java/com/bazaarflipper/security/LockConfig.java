package com.bazaarflipper.security;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Private locking PIN - portion saved in config (in game) so only authorized people can use it
 * PIN is hashed with salt and stored, never plaintext
 * User must enter PIN in Dashboard to unlock mod
 * All config persists across game restarts
 * Credits: Cldz
 */
public class LockConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "config/bazaarflipper_lock.json";

    public boolean lockEnabled = false; // master toggle - if false, no lock required
    public String pinHash = ""; // SHA-256 hash of PIN + salt
    public String salt = ""; // base64 salt
    public int failedAttempts = 0;
    public long lockoutUntil = 0; // timestamp ms
    public int maxAttempts = 5;
    public long lockoutDurationMs = 5 * 60 * 1000L; // 5 minutes
    public List<String> authorizedUsers = new ArrayList<>(); // optional list of usernames/UUIDs allowed even without PIN? For future
    public String lockMessage = "Mod locked - enter PIN in Dashboard > Security to unlock. Credits: Cldz";
    public long pinCreatedAt = 0;
    public String pinHint = ""; // optional hint

    // Transient - not saved
    public transient boolean isUnlocked = false;
    public transient long unlockedAt = 0;

    public static LockConfig load() {
        File f = new File(FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                LockConfig cfg = GSON.fromJson(r, LockConfig.class);
                if (cfg != null) {
                    if (cfg.pinHash == null) cfg.pinHash = "";
                    if (cfg.salt == null) cfg.salt = "";
                    Logger.info("LockConfig loaded from " + FILE + " lockEnabled=" + cfg.lockEnabled + " hasPin=" + !cfg.pinHash.isEmpty() + " credits Cldz");
                    return cfg;
                }
            } catch (Exception e) {
                Logger.error("Failed to load lock config", e);
            }
        }
        LockConfig cfg = new LockConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            File f = new File(FILE);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                // Don't save transient fields
                LockConfig toSave = new LockConfig();
                toSave.lockEnabled = this.lockEnabled;
                toSave.pinHash = this.pinHash;
                toSave.salt = this.salt;
                toSave.failedAttempts = this.failedAttempts;
                toSave.lockoutUntil = this.lockoutUntil;
                toSave.maxAttempts = this.maxAttempts;
                toSave.lockoutDurationMs = this.lockoutDurationMs;
                toSave.authorizedUsers = this.authorizedUsers;
                toSave.lockMessage = this.lockMessage;
                toSave.pinCreatedAt = this.pinCreatedAt;
                toSave.pinHint = this.pinHint;
                GSON.toJson(toSave, w);
            }
            Logger.info("LockConfig saved to " + FILE + " - persists across restarts, credits Cldz");
        } catch (Exception e) {
            Logger.error("Failed to save lock config", e);
        }
    }

    public boolean isLockoutActive() {
        if (lockoutUntil == 0) return false;
        if (System.currentTimeMillis() > lockoutUntil) {
            // Lockout expired, reset
            lockoutUntil = 0;
            failedAttempts = 0;
            save();
            return false;
        }
        return true;
    }

    public long getLockoutRemaining() {
        if (!isLockoutActive()) return 0;
        return Math.max(0, lockoutUntil - System.currentTimeMillis());
    }

    public String hashPin(String pin, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = pin + salt;
            byte[] hash = digest.digest(salted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Logger.error("Failed to hash PIN", e);
            return "";
        }
    }

    public String generateSalt() {
        try {
            SecureRandom random = new SecureRandom();
            byte[] saltBytes = new byte[16];
            random.nextBytes(saltBytes);
            return Base64.getEncoder().encodeToString(saltBytes);
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    public boolean setPin(String newPin) {
        if (newPin == null || newPin.length() < 4) {
            Logger.warn("PIN too short, must be at least 4 characters");
            return false;
        }
        if (newPin.length() > 32) {
            Logger.warn("PIN too long, max 32");
            return false;
        }
        this.salt = generateSalt();
        this.pinHash = hashPin(newPin, this.salt);
        this.pinCreatedAt = System.currentTimeMillis();
        this.lockEnabled = true;
        this.failedAttempts = 0;
        this.lockoutUntil = 0;
        save();
        Logger.info("PIN set successfully, lock enabled, hash stored (never plaintext) - credits Cldz");
        return true;
    }

    public boolean verifyPin(String pin) {
        if (!lockEnabled) return true;
        if (pinHash == null || pinHash.isEmpty()) return true; // no pin set yet, allow
        if (isLockoutActive()) {
            Logger.warn("PIN entry blocked - lockout active for " + (getLockoutRemaining()/1000) + "s");
            return false;
        }
        String hash = hashPin(pin, salt);
        boolean matches = hash.equals(pinHash);
        if (matches) {
            failedAttempts = 0;
            lockoutUntil = 0;
            isUnlocked = true;
            unlockedAt = System.currentTimeMillis();
            save();
            Logger.info("PIN verified successfully - mod unlocked");
            return true;
        } else {
            failedAttempts++;
            if (failedAttempts >= maxAttempts) {
                lockoutUntil = System.currentTimeMillis() + lockoutDurationMs;
                Logger.warn("Too many failed PIN attempts (" + failedAttempts + "), lockout for " + (lockoutDurationMs/1000) + "s");
            }
            save();
            Logger.warn("PIN verification failed attempt " + failedAttempts + "/" + maxAttempts);
            return false;
        }
    }

    public void lock() {
        isUnlocked = false;
        unlockedAt = 0;
        Logger.info("Mod locked - requires PIN to unlock");
    }

    public void disableLock() {
        lockEnabled = false;
        pinHash = "";
        salt = "";
        failedAttempts = 0;
        lockoutUntil = 0;
        isUnlocked = true;
        save();
        Logger.info("Lock disabled - mod open to all");
    }

    public boolean isAuthorized() {
        if (!lockEnabled) return true;
        if (pinHash == null || pinHash.isEmpty()) return true; // no pin set yet, allow first time setup
        return isUnlocked;
    }
}
