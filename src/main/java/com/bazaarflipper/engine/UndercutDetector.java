package com.bazaarflipper.engine;

import com.bazaarflipper.api.BazaarData;
import com.bazaarflipper.api.ProductInfo;
import com.bazaarflipper.config.ModConfig;

public class UndercutDetector {

    private final ModConfig config;
    private BazaarData bazaarData;

    public UndercutDetector(ModConfig config) {
        this.config = config;
    }

    public void onBazaarUpdate(BazaarData data) {
        this.bazaarData = data;
    }

    public boolean isBuyOrderUndercut(ActiveFlip flip) {
        if (bazaarData == null) return false;
        ProductInfo product = bazaarData.getProduct(flip.productId);
        if (product == null) return false;
        double topBuy = product.getTopBuyOrderPrice();
        return topBuy > flip.buyPrice + 0.01;
    }

    public boolean isSellOfferUndercut(ActiveFlip flip) {
        if (bazaarData == null) return false;
        ProductInfo product = bazaarData.getProduct(flip.productId);
        if (product == null) return false;
        double topSell = product.getTopSellOfferPrice();
        return topSell < flip.targetSellPrice - 0.01;
    }

    public double getNewCompetitiveBuyPrice(ActiveFlip flip) {
        if (bazaarData == null) return flip.buyPrice;
        ProductInfo product = bazaarData.getProduct(flip.productId);
        if (product == null) return flip.buyPrice;
        return product.getTopBuyOrderPrice() + config.undercutAmount;
    }

    public double getNewCompetitiveSellPrice(ActiveFlip flip) {
        if (bazaarData == null) return flip.targetSellPrice;
        ProductInfo product = bazaarData.getProduct(flip.productId);
        if (product == null) return flip.targetSellPrice;
        return product.getTopSellOfferPrice() - config.undercutAmount;
    }
}
