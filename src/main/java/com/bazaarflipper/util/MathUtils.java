package com.bazaarflipper.util;

import java.util.Random;

public class MathUtils {
    private static final Random RANDOM = new Random();

    public static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public static double randomDouble(double min, double max) {
        return min + (max - min) * RANDOM.nextDouble();
    }

    public static int randomInt(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // Format coins with K/M/B suffix
    public static String formatCoins(double coins) {
        if (coins >= 1_000_000_000) {
            return String.format("%.2fB", coins / 1_000_000_000.0);
        } else if (coins >= 1_000_000) {
            return String.format("%.2fM", coins / 1_000_000.0);
        } else if (coins >= 1_000) {
            return String.format("%.1fK", coins / 1_000.0);
        } else {
            return String.format("%.0f", coins);
        }
    }

    public static String formatCoinsDetailed(double coins) {
        return String.format("%,.0f", coins);
    }

    // Gaussian noise for humanization
    public static double gaussianNoise(double mean, double stdDev) {
        return mean + RANDOM.nextGaussian() * stdDev;
    }
}
