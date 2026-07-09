package com.bazaarflipper.engine.flipping;

import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.api.AuctionHouseClient;
import com.bazaarflipper.api.ProductInfo;
import com.bazaarflipper.config.FilterConfig;
import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorPriceModifier;
import com.bazaarflipper.mayor.MayorTracker;

import java.util.ArrayList;
import java.util.List;

public class OrderFlipStrategy implements FlipStrategy {

    private final TaxCalculator taxCalculator;
    private final FilterConfig filterConfig;
    private final MayorPriceModifier mayorPriceModifier;
    private final MayorTracker mayorTracker;
    private final com.bazaarflipper.api.PriceHistory priceHistory;
    private final double minMarginPercent;
    private final double minVolume;

    public OrderFlipStrategy(TaxCalculator taxCalculator, FilterConfig filterConfig, MayorPriceModifier mayorPriceModifier,
                             MayorTracker mayorTracker, com.bazaarflipper.api.PriceHistory priceHistory,
                             double minMarginPercent, double minVolume) {
        this.taxCalculator = taxCalculator;
        this.filterConfig = filterConfig;
        this.mayorPriceModifier = mayorPriceModifier;
        this.mayorTracker = mayorTracker;
        this.priceHistory = priceHistory;
        this.minMarginPercent = minMarginPercent;
        this.minVolume = minVolume;
    }

    @Override public String getName() { return "ORDER"; }

    @Override
    public List<FlipOpportunity> findOpportunities(BazaarData bazaarData, AuctionHouseClient ahClient, double availableBudget, int maxItems) {
        List<FlipOpportunity> opps = new ArrayList<>();
        if (bazaarData == null) return opps;

        MayorData mayor = mayorTracker.getCurrentMayor();

        for (ProductInfo product : bazaarData.products.values()) {
            if (!filterConfig.isAllowed(product.productId)) continue;

            double buyPrice = product.getTopBuyOrderPrice();
            double sellPrice = product.getTopSellOfferPrice();
            if (buyPrice <=0 || sellPrice <=0) continue;

            double rawSpread = sellPrice - buyPrice;
            double profitPerUnit = taxCalculator.calculateBazaarProfit(buyPrice, sellPrice);
            if (profitPerUnit <= 0) continue;

            double marginPct = taxCalculator.calculateBazaarMarginPercent(buyPrice, sellPrice);
            if (marginPct < minMarginPercent) continue;

            long volume = product.getBuyVolume() + product.getSellVolume();
            if (volume < minVolume) continue;

            // Price stability via PriceHistory
            boolean stable = priceHistory.isStable(product.productId, 20, 10.0);
            if (!stable) continue; // skip volatile? Or allow with penalty - spec says filter must pass stability

            // Budget profit constrained by available budget
            int affordableQty = (int) (availableBudget / buyPrice);
            if (affordableQty <=0) continue;
            double budgetProfit = profitPerUnit * affordableQty;

            FlipOpportunity opp = new FlipOpportunity(product.productId, "ORDER");
            opp.buyPrice = buyPrice;
            opp.sellPrice = sellPrice;
            opp.rawSpread = rawSpread;
            opp.profitPerUnitAfterTax = profitPerUnit;
            opp.totalProfitAfterTax = profitPerUnit * affordableQty;
            opp.dailyVolume = volume;
            opp.backlogPressure = product.getBacklogPressure();
            opp.orderCountAtTop = product.getBuyOrdersCountAtTop();
            opp.budgetProfit = budgetProfit;
            opp.fillTimeEstimateMs = estimateFillTime(product, affordableQty);
            opp.mayorModifier = mayorPriceModifier.getScoreModifier(product.productId, mayor);
            opp.priceStabilityScore = priceHistory.getPriceVariance(product.productId, 20);
            opp.quantity = affordableQty;
            opp.taxRate = taxCalculator.getBazaarTaxRate();
            opp.taxTier = "BAZAAR_1.25%";

            opps.add(opp);
        }

        opps.sort((a,b) -> Double.compare(b.budgetProfit, a.budgetProfit));
        if (opps.size() > maxItems) return opps.subList(0, maxItems);
        return opps;
    }

    private long estimateFillTime(ProductInfo product, int qty) {
        // From weekly moving volume
        long weeklyVolume = product.quickStatus != null ? product.quickStatus.buyMovingWeek : 1000;
        if (weeklyVolume <=0) return 3600_000L;
        double hourlyVolume = weeklyVolume / (7.0 * 24.0);
        if (hourlyVolume <=0) return 3600_000L;
        double hours = qty / hourlyVolume;
        return (long)(hours * 3600_000L);
    }
}
