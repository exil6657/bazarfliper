package com.bazaarflipper.engine.flipping;

import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.api.AuctionHouseClient;
import com.bazaarflipper.api.ProductInfo;
import com.bazaarflipper.config.FilterConfig;
import com.bazaarflipper.data.CraftingRecipes;
import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.data.UnlockedContentRegistry;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorPriceModifier;
import com.bazaarflipper.mayor.MayorTracker;

import java.util.ArrayList;
import java.util.List;

public class CraftFlipStrategy implements FlipStrategy {

    private final TaxCalculator taxCalculator;
    private final FilterConfig filterConfig;
    private final CraftingRecipes recipes;
    private final UnlockedContentRegistry unlockedRegistry;
    private final MayorPriceModifier mayorPriceModifier;
    private final MayorTracker mayorTracker;

    public CraftFlipStrategy(TaxCalculator taxCalculator, FilterConfig filterConfig, CraftingRecipes recipes,
                             UnlockedContentRegistry unlockedRegistry, MayorPriceModifier mayorPriceModifier, MayorTracker mayorTracker) {
        this.taxCalculator = taxCalculator;
        this.filterConfig = filterConfig;
        this.recipes = recipes;
        this.unlockedRegistry = unlockedRegistry;
        this.mayorPriceModifier = mayorPriceModifier;
        this.mayorTracker = mayorTracker;
    }

    @Override public String getName() { return "CRAFT"; }

    @Override
    public List<FlipOpportunity> findOpportunities(BazaarData bazaarData, AuctionHouseClient ahClient, double availableBudget, int maxItems) {
        List<FlipOpportunity> opps = new ArrayList<>();
        if (bazaarData == null) return opps;
        MayorData mayor = mayorTracker.getCurrentMayor();

        for (CraftingRecipes.Recipe recipe : recipes.getBazaarCraftable()) {
            if (!filterConfig.isAllowed(recipe.outputProductId)) continue;
            if (!unlockedRegistry.isRecipeUnlocked(recipe.outputProductId)) continue;

            double materialCost = 0;
            boolean allAvailable = true;
            for (var entry : recipe.ingredients.entrySet()) {
                ProductInfo ingProd = bazaarData.getProduct(entry.getKey());
                if (ingProd == null) { allAvailable = false; break; }
                double ingPrice = ingProd.getTopSellOfferPrice(); // we buy materials via sell offers (insta-buy) or buy orders? Use sell price
                materialCost += ingPrice * entry.getValue();
            }
            if (!allAvailable) continue;

            ProductInfo outputProd = bazaarData.getProduct(recipe.outputProductId);
            if (outputProd == null) continue;
            double outputSellPrice = outputProd.getTopBuyOrderPrice(); // we sell via buy orders? Actually sell offer price? Let's use buy order price as target sell? Simpler use top buy order for flip: we place sell offer near top sell? But profit calc uses buy->sell
            // For craft flip: buy materials, craft, then place sell offer
            // So sell price = top sell offer? Actually we want sell price = top buy order for quick? Let's use optimal sell price = top sell offer - undercut
            double sellPrice = outputProd.getTopSellOfferPrice();
            if (sellPrice <=0) continue;

            double profit = taxCalculator.calculateBazaarProfit(materialCost / recipe.outputQuantity, sellPrice);
            if (profit <= 0) continue;

            int qty = (int) (availableBudget / materialCost);
            if (qty <=0) continue;

            FlipOpportunity opp = new FlipOpportunity(recipe.outputProductId, "CRAFT");
            opp.buyPrice = materialCost / recipe.outputQuantity;
            opp.sellPrice = sellPrice;
            opp.profitPerUnitAfterTax = profit;
            opp.totalProfitAfterTax = profit * qty * recipe.outputQuantity;
            opp.quantity = qty;
            opp.budgetProfit = opp.totalProfitAfterTax;
            opp.mayorModifier = mayorPriceModifier.getScoreModifier(recipe.outputProductId, mayor);
            opp.taxRate = taxCalculator.getBazaarTaxRate();
            opp.taxTier = "BAZAAR_1.25%";

            opps.add(opp);
        }

        opps.sort((a,b) -> Double.compare(b.budgetProfit, a.budgetProfit));
        if (opps.size() > maxItems) return opps.subList(0, maxItems);
        return opps;
    }
}
