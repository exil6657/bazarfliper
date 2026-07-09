package com.bazaarflipper.data;

import com.bazaarflipper.config.ModConfig;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorTracker;

/**
 * TaxCalculator is the ONLY place tax is calculated.
 * No other class may perform its own tax calculation.
 *
 * Bazaar tax: 1.25% fixed on sell offers, cookie does NOT affect.
 * AH tax: tiered by sale price, Derpy-adjusted.
 *
 * Derpy multiplier researched from Hypixel wiki:
 * - Bazaar and AH taxes quadrupled during Derpy (4x) per Hypixel forum thread 5739552, NamuWiki, Coflnet guide.
 * - TODO: Verify if Hypixel changed Derpy's perks after Better Mayors update. Current default 4.0x is conservative based on multiple sources.
 * - Spec says if exact unavailable, use 2.0x as conservative fallback, but we found 4.0x evidence so using 4.0x.
 * - Applied only to items above derpyTaxAppliesAbove (default 1M) for AH.
 */
public class TaxCalculator {

    public enum AHTaxTier {
        LOW,    // under 10M, 1% standard
        MID,    // 10M-100M, 2% standard
        HIGH    // over 100M, 2.5% standard
    }

    private final ModConfig config;

    public TaxCalculator(ModConfig config) {
        this.config = config;
    }

    // ---------------- BAZAAR ----------------

    /**
     * Bazaar tax: 1.25% fixed, cookie does NOT affect.
     */
    public double calculateBazaarProfit(double buyPrice, double sellPrice) {
        return sellPrice * (1.0 - config.bazaarTaxRate) - buyPrice;
    }

    public double calculateBazaarTax(double sellPrice, int qty) {
        return sellPrice * qty * config.bazaarTaxRate;
    }

    public double getBazaarBreakEvenSellPrice(double buyPrice) {
        return buyPrice / (1.0 - config.bazaarTaxRate);
    }

    public double calculateBazaarMarginPercent(double buyPrice, double sellPrice) {
        if (buyPrice == 0) return 0;
        double profit = calculateBazaarProfit(buyPrice, sellPrice);
        return (profit / buyPrice) * 100.0;
    }

    public double calculateBazaarTotalProfit(double buyPrice, double sellPrice, int qty) {
        return calculateBazaarProfit(buyPrice, sellPrice) * qty;
    }

    public double getBazaarTaxRate() {
        return config.bazaarTaxRate;
    }

    public void setBazaarTaxRate(double rate) {
        config.bazaarTaxRate = rate;
        config.save();
    }

    // ---------------- AH ----------------

    public boolean isDerpyActive(MayorData mayor) {
        if (mayor == null) {
            // Try tracker cached
            return MayorTracker.isDerpyActiveStatic();
        }
        return mayor.isDerpy();
    }

    /**
     * Returns applicable AH tax rate considering price tier and Derpy adjustment.
     * Derpy multiplier applied if Derpy active and salePrice >= derpyTaxAppliesAbove.
     */
    public double getAHTaxRate(double salePrice, MayorData currentMayor) {
        double baseRate;
        if (salePrice < config.ahLowMidThreshold) {
            baseRate = config.ahTaxLowRate;
        } else if (salePrice < config.ahMidHighThreshold) {
            baseRate = config.ahTaxMidRate;
        } else {
            baseRate = config.ahTaxHighRate;
        }

        if (isDerpyActive(currentMayor) && salePrice >= config.derpyTaxAppliesAbove) {
            // During Derpy, taxes quadruple per wiki/forums
            // The multiplier is configurable in ModConfig.derpyAHTaxMultiplier default 4.0
            return baseRate * config.derpyAHTaxMultiplier;
        }
        return baseRate;
    }

    public double calculateAHProfit(double materialCost, double salePrice, MayorData currentMayor) {
        double taxRate = getAHTaxRate(salePrice, currentMayor);
        return salePrice * (1.0 - taxRate) - materialCost;
    }

    public double calculateAHTax(double salePrice, MayorData currentMayor) {
        return salePrice * getAHTaxRate(salePrice, currentMayor);
    }

    /**
     * Calculate minimum sale price to break even given applicable tax tier.
     * This is non-trivial because tax rate changes at thresholds.
     * If break-even price crosses tier boundary, use higher tier's rate.
     */
    public double getAHBreakEvenSalePrice(double cost, MayorData currentMayor) {
        // Try low tier first
        double lowRate = getRateWithDerpyIfNeeded(config.ahTaxLowRate, config.ahLowMidThreshold - 1, currentMayor);
        double breakEvenLow = cost / (1.0 - lowRate);
        if (breakEvenLow < config.ahLowMidThreshold) {
            return breakEvenLow;
        }

        double midRate = getRateWithDerpyIfNeeded(config.ahTaxMidRate, config.ahLowMidThreshold, currentMayor);
        double breakEvenMid = cost / (1.0 - midRate);
        if (breakEvenMid < config.ahMidHighThreshold) {
            // If break-even is still below low threshold but we crossed, pick mid
            // Edge case: breakEvenLow >= threshold means we need mid rate, check if mid's break-even still in mid tier
            return breakEvenMid;
        }

        double highRate = getRateWithDerpyIfNeeded(config.ahTaxHighRate, config.ahMidHighThreshold, currentMayor);
        double breakEvenHigh = cost / (1.0 - highRate);
        return breakEvenHigh;
    }

    private double getRateWithDerpyIfNeeded(double baseRate, double priceForCheck, MayorData mayor) {
        if (isDerpyActive(mayor) && priceForCheck >= config.derpyTaxAppliesAbove) {
            return baseRate * config.derpyAHTaxMultiplier;
        }
        return baseRate;
    }

    public double calculateAHMarginPercent(double cost, double salePrice, MayorData currentMayor) {
        if (cost == 0) return 0;
        double profit = calculateAHProfit(cost, salePrice, currentMayor);
        return (profit / cost) * 100.0;
    }

    public boolean isAHProfitableAfterTax(double cost, double salePrice, MayorData currentMayor) {
        return calculateAHProfit(cost, salePrice, currentMayor) > 0;
    }

    public AHTaxTier getAHTaxTier(double salePrice) {
        if (salePrice < config.ahLowMidThreshold) return AHTaxTier.LOW;
        if (salePrice < config.ahMidHighThreshold) return AHTaxTier.MID;
        return AHTaxTier.HIGH;
    }

    public String formatTaxRateDisplay(double salePrice, MayorData mayor) {
        AHTaxTier tier = getAHTaxTier(salePrice);
        double rate = getAHTaxRate(salePrice, mayor);
        String tierStr = tier.name();
        String base = String.format("%.2f%% (%s tier)", rate * 100.0, tierStr);
        if (isDerpyActive(mayor) && salePrice >= config.derpyTaxAppliesAbove) {
            return base + " + Derpy bonus";
        }
        return base;
    }
}
