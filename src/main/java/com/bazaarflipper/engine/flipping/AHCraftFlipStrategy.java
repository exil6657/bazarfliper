package com.bazaarflipper.engine.flipping;

import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.api.AuctionHouseClient;
import com.bazaarflipper.api.ProductInfo;
import com.bazaarflipper.config.FilterConfig;
import com.bazaarflipper.data.CraftingRecipes;
import com.bazaarflipper.data.TaxCalculator;
import com.bazaarflipper.data.UnlockedContentRegistry;
import com.bazaarflipper.mayor.MayorData;
import com.bazaarflipper.mayor.MayorTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Concept: Buy bazaar ingredients -> craft via Hypixel custom GUI -> sell on AH as BIN
 * Profit uses TaxCalculator.calculateAHProfit with current mayor for Derpy adjustment.
 * AH price: average of lowest 3-5 BIN prices (not single lowest)
 */
public class AHCraftFlipStrategy implements FlipStrategy {

    private final TaxCalculator taxCalculator;
    private final FilterConfig filterConfig;
    private final CraftingRecipes recipes;
    private final UnlockedContentRegistry unlockedRegistry;
    private final MayorTracker mayorTracker;

    public AHCraftFlipStrategy(TaxCalculator taxCalculator, FilterConfig filterConfig, CraftingRecipes recipes,
                               UnlockedContentRegistry unlockedRegistry, MayorTracker mayorTracker) {
        this.taxCalculator = taxCalculator;
        this.filterConfig = filterConfig;
        this.recipes = recipes;
        this.unlockedRegistry = unlockedRegistry;
        this.mayorTracker = mayorTracker;
    }

    @Override public String getName() { return "AH_CRAFT"; }

    @Override
    public List<FlipOpportunity> findOpportunities(BazaarData bazaarData, AuctionHouseClient ahClient, double availableBudget, int maxItems) {
        List<FlipOpportunity> opps = new ArrayList<>();
        if (bazaarData == null || ahClient == null) return opps;

        MayorData mayor = mayorTracker.getCurrentMayor();

        for (CraftingRecipes.Recipe recipe : recipes.getAHCraftable()) {
            if (!filterConfig.isAllowed(recipe.outputProductId)) continue;
            if (!unlockedRegistry.isRecipeUnlocked(recipe.outputProductId)) continue;

            double materialCost = 0;
            boolean allAvailable = true;
            for (var entry : recipe.ingredients.entrySet()) {
                ProductInfo ingProd = bazaarData.getProduct(entry.getKey());
                if (ingProd == null) { allAvailable = false; break; }
                double ingPrice = ingProd.getTopSellOfferPrice();
                materialCost += ingPrice * entry.getValue();
            }
            if (!allAvailable) continue;

            double ahPrice = ahClient.getBINPrice(recipe.outputProductId);
            if (ahPrice <= 0) continue;

            // Check AH price data recent enough (configurable max age, default 5 minutes) -> simplified
            if (!ahClient.isDataRecent(recipe.outputProductId, 5*60*1000L)) {
                // Skip if not recent? For now allow
            }

            // Must use TaxCalculator.isAHProfitableAfterTax
            if (!taxCalculator.isAHProfitableAfterTax(materialCost / recipe.outputQuantity, ahPrice, mayor)) continue;

            double profit = taxCalculator.calculateAHProfit(materialCost / recipe.outputQuantity, ahPrice, mayor);

            int qty = (int) (availableBudget / materialCost);
            if (qty <=0) continue;

            FlipOpportunity opp = new FlipOpportunity(recipe.outputProductId, "AH_CRAFT");
            opp.buyPrice = materialCost / recipe.outputQuantity;
            opp.sellPrice = ahPrice;
            opp.profitPerUnitAfterTax = profit;
            opp.totalProfitAfterTax = profit * qty * recipe.outputQuantity;
            opp.budgetProfit = opp.totalProfitAfterTax;
            opp.quantity = qty;
            opp.taxTier = taxCalculator.getAHTaxTier(ahPrice).name();
            opp.taxRate = taxCalculator.getAHTaxRate(ahPrice, mayor);
            opp.derpyWarning = taxCalculator.isDerpyActive(mayor) && ahPrice >= 1_000_000;

            // If Derpy active and item price >1M: show reduced profit and warning
            if (opp.derpyWarning) {
                // Profit already reduced via taxCalculator, but note warning
            }

            opp.fillTimeEstimateMs = estimateAHSellTime(recipe.outputProductId, ahClient);

            opps.add(opp);
        }

        opps.sort((a,b) -> Double.compare(b.budgetProfit, a.budgetProfit));
        if (opps.size() > maxItems) return opps.subList(0, maxItems);
        return opps;
    }

    public long estimateAHSellTime(String itemId, AuctionHouseClient ahData) {
        // From recent sale history. Rarely-selling items get heavy score penalty
        double demand = ahData.getAHDemand(itemId);
        if (demand < 1) return 24 * 3600 * 1000L; // 24h default for rare
        if (demand < 5) return 12 * 3600 * 1000L;
        if (demand < 20) return 4 * 3600 * 1000L;
        return 1 * 3600 * 1000L;
    }
}
