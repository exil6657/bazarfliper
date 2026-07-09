package com.bazaarflipper.engine;

import com.bazaarflipper.automation.InventoryScanner;
import com.bazaarflipper.config.BudgetConfig;
import com.bazaarflipper.util.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BudgetManager {
    private final BudgetConfig config;
    private final InventoryScanner scanner;

    private final ConcurrentHashMap<String, Double> investments = new ConcurrentHashMap<>();
    private volatile double currentBalance = 0;
    private final Object lock = new Object();

    public BudgetManager(BudgetConfig config, InventoryScanner scanner) {
        this.config = config;
        this.scanner = scanner;
        updateBalanceFromPurse();
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public double getAvailableForFlipping() {
        return Math.max(0, currentBalance - config.reservedBalance);
    }

    public boolean canAffordFlip(double cost) {
        synchronized (lock) {
            if (cost > config.maxInvestmentPerItem) return false;
            if (cost > getAvailableForFlipping()) return false;
            double totalInvested = getTotalCurrentlyInvested();
            if (totalInvested + cost > config.totalBudgetCap) return false;
            return true;
        }
    }

    public int getBestAffordableQuantity(double pricePerUnit, String productId) {
        if (pricePerUnit <= 0) return 0;
        double maxAfford = Math.min(getAvailableForFlipping(), config.maxInvestmentPerItem);
        double remainingCap = config.totalBudgetCap - getTotalCurrentlyInvested();
        maxAfford = Math.min(maxAfford, remainingCap);
        int qty = (int) (maxAfford / pricePerUnit);
        return Math.max(0, qty);
    }

    public boolean registerInvestment(String productId, double amount) {
        synchronized (lock) {
            if (!canAffordFlip(amount)) return false;
            investments.merge(productId, amount, Double::sum);
            Logger.info("Registered investment " + productId + " amount " + amount + " total invested " + getTotalCurrentlyInvested());
            return true;
        }
    }

    public void releaseInvestment(String productId, double amount) {
        synchronized (lock) {
            Double current = investments.get(productId);
            if (current == null) return;
            double newAmt = current - amount;
            if (newAmt <= 0) investments.remove(productId);
            else investments.put(productId, newAmt);
            Logger.info("Released investment " + productId + " amount " + amount);
        }
    }

    public double getTotalCurrentlyInvested() {
        double sum = 0;
        for (Double v : investments.values()) sum += v;
        return sum;
    }

    public double getRemainingBudget() {
        return config.totalBudgetCap - getTotalCurrentlyInvested();
    }

    public void updateBalanceFromPurse() {
        try {
            double purse = scanner.getPurseBalance();
            // For simplicity, currentBalance = purse + known bank? Bank ignored for now
            // If autoAdjust, set current to purse?
            if (config.autoAdjustToBalance) {
                // If purse bigger than cap, maybe adjust? For now use purse as current if less than cap? We'll keep purse as balance
                currentBalance = purse > 0 ? purse : currentBalance;
            } else {
                currentBalance = purse > 0 ? purse : currentBalance;
            }
        } catch (Exception e) {
            Logger.error("Failed to update balance from purse", e);
        }
    }

    public void restoreInvestments(Map<String, Double> saved) {
        investments.clear();
        if (saved != null) investments.putAll(saved);
    }

    public Map<String, Double> getInvestmentsSnapshot() {
        return new ConcurrentHashMap<>(investments);
    }

    public double getBudgetUtilizationPercent() {
        if (config.totalBudgetCap == 0) return 0;
        return (getTotalCurrentlyInvested() / config.totalBudgetCap) * 100.0;
    }

    public boolean isNearBudgetLimit() {
        return getBudgetUtilizationPercent() > 80;
    }
}
