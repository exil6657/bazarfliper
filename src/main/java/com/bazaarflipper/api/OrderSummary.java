package com.bazaarflipper.api;

public class OrderSummary {
    public double amount;
    public double pricePerUnit;
    public int orders;

    public OrderSummary() {}

    public OrderSummary(double amount, double pricePerUnit, int orders) {
        this.amount = amount;
        this.pricePerUnit = pricePerUnit;
        this.orders = orders;
    }
}
