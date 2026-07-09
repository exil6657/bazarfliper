package com.bazaarflipper.engine;

public class ActiveFlip {
    public String productId;
    public double buyPrice;
    public double targetSellPrice;
    public int quantity;
    public int filledAmount;
    public int relistCount;
    public String state; // FlipState enum string
    public long placementTimestamp;
    public String strategyType;
    public double amountInvested;
    public String taxType;
    public double taxRate;

    public ActiveFlip() {}

    public ActiveFlip(String productId, double buyPrice, double sellPrice, int quantity, String strategy) {
        this.productId = productId;
        this.buyPrice = buyPrice;
        this.targetSellPrice = sellPrice;
        this.quantity = quantity;
        this.strategyType = strategy;
        this.placementTimestamp = System.currentTimeMillis();
        this.state = FlipState.PLACING_BUY_ORDER.name();
    }

    public enum FlipState {
        IDLE,
        SCANNING_MARKET,
        RECOVERING_WORLD_STATE,
        REENTERING_SKYBLOCK,
        WAITING_FOR_WORLD_LOAD,
        NAVIGATING_TO_BAZAAR,
        PLACING_BUY_ORDER,
        CHECKING_ORDERS,
        CLAIMING_ORDER,
        NAVIGATING_TO_BANK,
        DEPOSITING_COINS,
        WITHDRAWING_COINS,
        NAVIGATING_TO_NPC,
        SELLING_TO_NPC,
        NAVIGATING_TO_AH,
        PLACING_AH_LISTING,
        PLACING_SELL_OFFER,
        CANCELLING_ORDER,
        RELISTING_ORDER,
        CRAFTING_ITEMS,
        WAITING,
        ERROR_RECOVERY,
        RECONNECTING,
        BREAK_ORDER_WAIT,
        BREAK_SHORT_PERIODIC,
        BREAK_LONG_SESSION
    }
}
