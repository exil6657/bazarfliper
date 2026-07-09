package com.bazaarflipper.engine;

import com.bazaarflipper.api.AuctionHouseClient;
import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.engine.flipping.FlipStrategy;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorPriceModifier;
import com.bazaarflipper.mayor.MayorTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Composite score weights per spec
 */
public class ItemSelector {

    private final MarketScanner marketScanner;
    private final TaxCalculator taxCalculator;
    private final MayorPriceModifier mayorPriceModifier;
    private final MayorTracker mayorTracker;
    private final BudgetManager budgetManager;

    // Weights
    private static final double W_PROFIT_PER_HOUR = 3.0;
    private static final double W_MARGIN = 2.0;
    private static final double W_VOLUME = 1.5;
    private static final double W_FILL_TIME = 2.5;
    private static final double W_BACKLOG = 1.5;
    private static final double W_BUDGET_EFF = 1.0;
    private static final double W_MAYOR = 2.0;
    private static final double W_STABILITY = 1.5;

    public ItemSelector(MarketScanner scanner, TaxCalculator taxCalculator, MayorPriceModifier mayorPriceModifier,
                        MayorTracker mayorTracker, BudgetManager budgetManager) {
        this.marketScanner = scanner;
        this.taxCalculator = taxCalculator;
        this.mayorPriceModifier = mayorPriceModifier;
        this.mayorTracker = mayorTracker;
        this.budgetManager = budgetManager;
    }

    public List<FlipStrategy.FlipOpportunity> selectBestItems(BazaarData bazaarData, AuctionHouseClient ahData, int maxItems) {
        double available = budgetManager.getAvailableForFlipping();
        List<FlipStrategy.FlipOpportunity> all = marketScanner.findAllOpportunities(bazaarData, ahData, available, maxItems*2);

        // Score each
        MayorData mayor = mayorTracker.getCurrentMayor();
        for (FlipStrategy.FlipOpportunity opp : all) {
            opp.compositeScore = computeScore(opp, mayor);
        }

        // Sort by composite score descending
        all.sort(Comparator.comparingDouble((FlipStrategy.FlipOpportunity o) -> o.compositeScore).reversed());

        if (all.size() > maxItems) return all.subList(0, maxItems);
        return all;
    }

    private double computeScore(FlipStrategy.FlipOpportunity opp, MayorData mayor) {
        // Estimated profit per hour after tax
        double profitPerHour = 0;
        if (opp.fillTimeEstimateMs >0) {
            double hours = opp.fillTimeEstimateMs / 3600000.0;
            profitPerHour = hours>0 ? opp.budgetProfit / hours : opp.budgetProfit;
        } else {
            profitPerHour = opp.budgetProfit;
        }

        double marginPct = opp.buyPrice>0 ? (opp.profitPerUnitAfterTax / opp.buyPrice)*100 : 0;

        double volume = opp.dailyVolume / 100000.0; // normalize
        double fillTimeScore = opp.fillTimeEstimateMs>0 ? (1.0 / (opp.fillTimeEstimateMs / 3600000.0 + 1)) * 100 : 0;
        double backlogScore = opp.backlogPressure>0 ? (1.0 / (opp.backlogPressure +1))*100 : 100;

        double budgetEff = opp.budgetProfit / Math.max(1, opp.buyPrice*opp.quantity) * 100;

        double mayorMod = opp.mayorModifier !=0 ? opp.mayorModifier : mayorPriceModifier.getScoreModifier(opp.productId, mayor);

        double stability = 100 - Math.min(100, opp.priceStabilityScore);

        double score = profitPerHour * W_PROFIT_PER_HOUR
                + marginPct * W_MARGIN
                + volume * W_VOLUME
                + fillTimeScore * W_FILL_TIME
                + backlogScore * W_BACKLOG
                + budgetEff * W_BUDGET_EFF
                + (mayorMod*100) * W_MAYOR
                + stability * W_STABILITY;

        // AH craft flips receive baseline penalty 0.8x
        if ("AH_CRAFT".equals(opp.strategyType)) {
            score *= 0.8;

            // If Derpy active, further reduce AH flip scores proportional to increased claiming tax
            if (taxCalculator.isDerpyActive(mayor)) {
                double derpyPenalty = 0.5; // proportional to increased tax
                score *= derpyPenalty;
            }
        }

        return score;
    }
}
