package com.bazaarflipper.engine.flipping;

import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.api.AuctionHouseClient;
import com.bazaarflipper.api.ProductInfo;

import java.util.List;

public interface FlipStrategy {
    String getName();
    List<FlipOpportunity> findOpportunities(BazaarData bazaarData, AuctionHouseClient ahClient, double availableBudget, int maxItems);

    class FlipOpportunity {
        public String productId;
        public String strategyType; // ORDER, CRAFT, NPC, AH_CRAFT
        public double buyPrice;
        public double sellPrice;
        public int quantity;
        public double rawSpread;
        public double profitPerUnitAfterTax;
        public double totalProfitAfterTax;
        public double dailyVolume;
        public double backlogPressure;
        public int orderCountAtTop;
        public double budgetProfit;
        public long fillTimeEstimateMs;
        public double mayorModifier;
        public double eventModifier;
        public double priceStabilityScore;
        public double compositeScore;

        // AH specific
        public String taxTier; // LOW, MID, HIGH
        public double taxRate;
        public boolean derpyWarning;

        public FlipOpportunity(String productId, String strategy) {
            this.productId = productId;
            this.strategyType = strategy;
        }
    }
}
