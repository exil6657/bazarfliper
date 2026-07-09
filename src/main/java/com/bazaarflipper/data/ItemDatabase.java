package com.bazaarflipper.data;

import com.bazaarflipper.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ItemDatabase {
    private static final String FILE = "config/bazaarflipper_items.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, String> productIdToName = new ConcurrentHashMap<>();

    public void load() {
        File f = new File(FILE);
        if (f.exists()) {
            try (FileReader r = new FileReader(f)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> map = GSON.fromJson(r, type);
                if (map != null) productIdToName.putAll(map);
                Logger.info("Loaded " + productIdToName.size() + " item names from cache.");
            } catch (Exception e) {
                Logger.error("Failed to load item database", e);
            }
        }
    }

    public void save() {
        try {
            File f = new File(FILE);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(productIdToName, w);
            }
        } catch (Exception e) {
            Logger.error("Failed to save item database", e);
        }
    }

    public String getDisplayName(String productId) {
        String name = productIdToName.get(productId);
        if (name != null) return name;
        // Fallback: replace _ with spaces, title-case
        String fallback = productId.replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : fallback.toCharArray()) {
            if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
            if (c == ' ') cap = true;
        }
        return sb.toString();
    }

    public void put(String productId, String displayName) {
        productIdToName.put(productId, displayName);
    }

    public void populateFromApi(Map<String, String> apiItems) {
        productIdToName.putAll(apiItems);
        save();
    }
}
