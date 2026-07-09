package com.bazaarflipper.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BazaarData {
    public long timestamp = System.currentTimeMillis();
    public Map<String, ProductInfo> products = new ConcurrentHashMap<>(); // productId -> ProductInfo

    public BazaarData() {}

    public ProductInfo getProduct(String productId) {
        return products.get(productId);
    }

    public boolean hasProduct(String id) {
        return products.containsKey(id);
    }
}
