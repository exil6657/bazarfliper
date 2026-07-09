package com.bazaarflipper.engine.flipping;

import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.api.AuctionHouseClient;
import com.bazaarflipper.api.ProductInfo;
import com.bazaarflipper.config.FilterConfig;
import com.bazaarflipper.data.NPCPrices;

import java.util.ArrayList;
import java.util.List;

public class NPCFlipStrategy implements FlipStrategy {

    private final NPCPrices npcPrices;
    private final FilterConfig filterConfig;

    public NPCFlipStrategy(NPCPrices npcPrices, FilterConfig filterConfig) {
        this.npcPrices = npcPrices;
        this.filterConfig = filterConfig;
    }

    @Override public String getName() { return "NPC"; }

    @Override
    public List<FlipOpportunity> findOpportunities(BazaarData bazaarData, AuctionHouseClient ahClient, double availableBudget, int maxItems) {
        List<FlipOpportunity> opps = new ArrayList<>();
        if (bazaarData == null) return opps;

        for (ProductInfo product : bazaarData.products.values()) {
            if (!filterConfig.isAllowed(product.productId)) continue;
            if (!npcPrices.hasPrice(product.productId)) continue;

            double buyPrice = product.getTopSellOfferPrice(); // buy bazaar cheap
            double npcSellPrice = npcPrices.getPrice(product.productId); // fixed NPC price, no tax

            double profitPerUnit = npcSellPrice - buyPrice; // no tax on NPC sells
            if (profitPerUnit <= 0) continue;

            int qty = (int) (availableBudget / buyPrice);
            if (qty <=0) continue;

            FlipOpportunity opp = new FlipOpportunity(product.productId, "NPC");
            opp.buyPrice = buyPrice;
            opp.sellPrice = npcSellPrice;
            opp.profitPerUnitAfterTax = profitPerUnit;
            opp.totalProfitAfterTax = profitPerUnit * qty;
            opp.budgetProfit = opp.totalProfitAfterTax;
            opp.quantity = qty;
            opp.taxRate = 0; // no tax on NPC sells
            opp.taxTier = "NPC_0%";

            opps.add(opp);
        }

        opps.sort((a,b) -> Double.compare(b.budgetProfit, a.budgetProfit));
        if (opps.size() > maxItems) return opps.subList(0, maxItems);
        return opps;
    }
}
