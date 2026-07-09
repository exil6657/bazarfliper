package com.bazaarflipper.engine;

import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.api.AuctionHouseClient;
import com.bazaarflipper.api.ProductInfo;
import com.bazaarflipper.config.FilterConfig;
import com.bazaarflipper.data.CraftingRecipes;
import com.bazaarflipper.data.NPCPrices;
import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.data.UnlockedContentRegistry;
import com.bazaarflipper.engine.flipping.*;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorPriceModifier;
import com.bazaarflipper.mayor.MayorTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Must implement find methods using TaxCalculator exclusively.
 */
public class MarketScanner {

    private final TaxCalculator taxCalculator;
    private final FilterConfig filterConfig;
    private final CraftingRecipes craftingRecipes;
    private final NPCPrices npcPrices;
    private final UnlockedContentRegistry unlockedRegistry;
    private final MayorPriceModifier mayorPriceModifier;
    private final MayorTracker mayorTracker;
    private final com.bazaarflipper.api.PriceHistory priceHistory;

    private final OrderFlipStrategy orderStrategy;
    private final CraftFlipStrategy craftStrategy;
    private final NPCFlipStrategy npcStrategy;
    private final AHCraftFlipStrategy ahCraftStrategy;

    public MarketScanner(TaxCalculator taxCalculator, FilterConfig filterConfig, CraftingRecipes recipes,
                         NPCPrices npcPrices, UnlockedContentRegistry unlockedRegistry,
                         MayorPriceModifier mayorPriceModifier, MayorTracker mayorTracker,
                         com.bazaarflipper.api.PriceHistory priceHistory,
                         BudgetManager budgetManager) {
        this.taxCalculator = taxCalculator;
        this.filterConfig = filterConfig;
        this.craftingRecipes = recipes;
        this.npcPrices = npcPrices;
        this.unlockedRegistry = unlockedRegistry;
        this.mayorPriceModifier = mayorPriceModifier;
        this.mayorTracker = mayorTracker;
        this.priceHistory = priceHistory;

        this.orderStrategy = new OrderFlipStrategy(taxCalculator, filterConfig, mayorPriceModifier, mayorTracker, priceHistory, 5.0, 100_000);
        this.craftStrategy = new CraftFlipStrategy(taxCalculator, filterConfig, recipes, unlockedRegistry, mayorPriceModifier, mayorTracker);
        this.npcStrategy = new NPCFlipStrategy(npcPrices, filterConfig);
        this.ahCraftStrategy = new AHCraftFlipStrategy(taxCalculator, filterConfig, recipes, unlockedRegistry, mayorTracker);
    }

    public List<FlipStrategy.FlipOpportunity> findOrderFlipOpportunities(BazaarData data, double budget, int maxItems) {
        // uses TaxCalculator.calculateBazaarProfit() internally
        return orderStrategy.findOpportunities(data, null, budget, maxItems);
    }

    public List<FlipStrategy.FlipOpportunity> findCraftFlipOpportunities(BazaarData data, double budget, int maxItems) {
        // uses TaxCalculator.calculateBazaarProfit(), only unlocked recipes
        return craftStrategy.findOpportunities(data, null, budget, maxItems);
    }

    public List<FlipStrategy.FlipOpportunity> findNPCFlipOpportunities(BazaarData data, double budget, int maxItems) {
        // no tax on NPC sells
        return npcStrategy.findOpportunities(data, null, budget, maxItems);
    }

    public List<FlipStrategy.FlipOpportunity> findAHCraftFlipOpportunities(BazaarData bazaarData, AuctionHouseClient ahData, double budget, int maxItems) {
        // uses TaxCalculator.calculateAHProfit() with current mayor data
        return ahCraftStrategy.findOpportunities(bazaarData, ahData, budget, maxItems);
    }

    public List<FlipStrategy.FlipOpportunity> findAllOpportunities(BazaarData bazaarData, AuctionHouseClient ahData, double budget, int maxItems) {
        List<FlipStrategy.FlipOpportunity> all = new ArrayList<>();
        all.addAll(findOrderFlipOpportunities(bazaarData, budget, maxItems));
        all.addAll(findCraftFlipOpportunities(bazaarData, budget, maxItems));
        all.addAll(findNPCFlipOpportunities(bazaarData, budget, maxItems));
        if (ahData != null) {
            all.addAll(findAHCraftFlipOpportunities(bazaarData, ahData, budget, maxItems));
        }
        return all;
    }

    public double calculateOptimalBuyPrice(ProductInfo product) {
        // top buy order + undercut amount
        return product.getTopBuyOrderPrice() + 0.1;
    }

    public double calculateOptimalSellPrice(ProductInfo product) {
        // top sell offer - undercut amount
        return product.getTopSellOfferPrice() - 0.1;
    }

    public long estimateFillTime(ProductInfo product, int qty) {
        long weeklyVol = product.quickStatus != null ? product.quickStatus.buyMovingWeek : 1000;
        if (weeklyVol <=0) return 3600_000L;
        double hourly = weeklyVol / (7.0*24.0);
        if (hourly <=0) return 3600_000L;
        double hours = qty / hourly;
        return (long)(hours*3600_000L);
    }

    public double scoreItem(ProductInfo product, double availableBudget, MayorData mayor) {
        // Composite scoring similar to ItemSelector but single item
        double buy = product.getTopBuyOrderPrice();
        double sell = product.getTopSellOfferPrice();
        double profit = taxCalculator.calculateBazaarProfit(buy, sell);
        double margin = taxCalculator.calculateBazaarMarginPercent(buy, sell);
        double volumeScore = (product.getBuyVolume() + product.getSellVolume()) / 100000.0;
        double mayorMod = mayorPriceModifier.getScoreModifier(product.productId, mayor);
        return profit * 3.0 + margin * 2.0 + volumeScore * 1.5 + mayorMod * 2.0;
    }
}
