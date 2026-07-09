package com.bazaarflipper.api;

import java.util.List;

public class ProductInfo {
    public String productId;
    public List<OrderSummary> sellSummary; // sell offers (players selling)
    public List<OrderSummary> buySummary; // buy orders (players buying)
    public QuickStatus quickStatus;

    public static class QuickStatus {
        public String productId;
        public double sellPrice; // lowest sell offer (insta-buy)
        public double buyPrice; // highest buy order (insta-sell)
        public double sellVolume; // weekly?
        public double buyVolume;
        public long sellMovingWeek;
        public long buyMovingWeek;
        public long sellOrders;
        public long buyOrders;
    }

    public ProductInfo(String id) {
        this.productId = id;
    }

    public double getTopSellOfferPrice() {
        if (sellSummary != null && !sellSummary.isEmpty()) {
            return sellSummary.get(0).pricePerUnit;
        }
        if (quickStatus != null) return quickStatus.sellPrice;
        return 0;
    }

    public double getTopBuyOrderPrice() {
        if (buySummary != null && !buySummary.isEmpty()) {
            return buySummary.get(0).pricePerUnit;
        }
        if (quickStatus != null) return quickStatus.buyPrice;
        return 0;
    }

    public double getSpread() {
        return getTopSellOfferPrice() - getTopBuyOrderPrice();
    }

    public long getBuyVolume() {
        if (quickStatus != null) return quickStatus.buyMovingWeek;
        return 0;
    }

    public long getSellVolume() {
        if (quickStatus != null) return quickStatus.sellMovingWeek;
        return 0;
    }

    public int getBuyOrdersCountAtTop() {
        if (buySummary != null && !buySummary.isEmpty()) return buySummary.get(0).orders;
        return 0;
    }

    public int getSellOrdersCountAtTop() {
        if (sellSummary != null && !sellSummary.isEmpty()) return sellSummary.get(0).orders;
        return 0;
    }

    // Backlog pressure: number of orders ahead at our price from buy_summary
    public double getBacklogPressure() {
        if (buySummary == null) return 0;
        double total = 0;
        for (OrderSummary o : buySummary) total += o.amount;
        return total;
    }
}
