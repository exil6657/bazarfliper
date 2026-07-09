package com.bazaarflipper.engine;

import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.mayor.MayorData;

/**
 * All methods delegate to TaxCalculator - no inline tax calculations anywhere.
 */
public class ProfitCalculator {

    private final TaxCalculator taxCalculator;

    public ProfitCalculator(TaxCalculator taxCalculator) {
        this.taxCalculator = taxCalculator;
    }

    public double calculateBazaarProfit(double buy, double sell) {
        return taxCalculator.calculateBazaarProfit(buy, sell);
    }

    public double calculateAHProfit(double cost, double salePrice, MayorData mayor) {
        return taxCalculator.calculateAHProfit(cost, salePrice, mayor);
    }

    public double calculateBazaarTax(double sellPrice, int qty) {
        return taxCalculator.calculateBazaarTax(sellPrice, qty);
    }

    public double calculateAHTax(double salePrice, MayorData mayor) {
        return taxCalculator.calculateAHTax(salePrice, mayor);
    }

    public double getBazaarBreakEven(double buy) {
        return taxCalculator.getBazaarBreakEvenSellPrice(buy);
    }

    public double getAHBreakEven(double cost, MayorData mayor) {
        return taxCalculator.getAHBreakEvenSalePrice(cost, mayor);
    }
}
