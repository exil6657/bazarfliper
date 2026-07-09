package com.bazaarflipper.pathfinding;

import com.bazaarflipper.util.MathUtils;

import java.util.Random;

/**
 * Defines per-player humanization variability - each player has slightly different movement signature
 * Makes bot detection harder by not using same exact values for all users
 * Credits: Cldz
 */
public class HumanizationProfile {
    private final Random random = new Random();

    // Movement style
    public double overshootMean = 5.5; // degrees
    public double overshootStd = 1.8;
    public double thinkingPauseChance = 0.07; // 7%
    public int thinkingPauseMin = 200;
    public int thinkingPauseMax = 800;
    public double stutterChance = 0.02;
    public double cameraWanderChance = 0.8; // 80% chance to actually wander when timer triggers
    public int cameraWanderMinYaw = 10;
    public int cameraWanderMaxYaw = 30;
    public double directionNoiseAmplitude = 3.5;
    public double sprintPreference = 0.7; // 70% sprint
    public double arrivalOvershootChance = 0.3;
    public double arrivalOvershootMin = 0.5;
    public double arrivalOvershootMax = 1.5;
    public double accelerationDuration = 800; // ms to full speed
    public double fatigueRate = 1.0 / 120000.0; // per ms
    public double microStrafeChance = 0.08;
    public double socialAvoidanceStrength = 0.5;

    public HumanizationProfile() {
        randomize();
    }

    public HumanizationProfile(long seed) {
        random.setSeed(seed);
        randomize();
    }

    private void randomize() {
        // Randomize within realistic human ranges to create unique signature per install
        overshootMean = MathUtils.randomDouble(4.0, 7.0);
        overshootStd = MathUtils.randomDouble(1.2, 2.5);
        thinkingPauseChance = MathUtils.randomDouble(0.05, 0.10);
        thinkingPauseMin = MathUtils.randomInt(150, 250);
        thinkingPauseMax = MathUtils.randomInt(600, 1000);
        stutterChance = MathUtils.randomDouble(0.015, 0.03);
        cameraWanderMinYaw = MathUtils.randomInt(8, 12);
        cameraWanderMaxYaw = MathUtils.randomInt(25, 35);
        directionNoiseAmplitude = MathUtils.randomDouble(2.0, 5.0);
        sprintPreference = MathUtils.randomDouble(0.6, 0.8);
        arrivalOvershootChance = MathUtils.randomDouble(0.2, 0.4);
        accelerationDuration = MathUtils.randomDouble(600, 1000);
        fatigueRate = MathUtils.randomDouble(1.0/150000.0, 1.0/100000.0);
        microStrafeChance = MathUtils.randomDouble(0.05, 0.12);
        socialAvoidanceStrength = MathUtils.randomDouble(0.3, 0.7);
    }

    public double sampleOvershoot() {
        return MathUtils.gaussianNoise(overshootMean, overshootStd);
    }

    public long sampleThinkingPause() {
        return MathUtils.randomInt(thinkingPauseMin, thinkingPauseMax);
    }

    public long sampleCameraWanderYaw() {
        return MathUtils.randomInt(cameraWanderMinYaw, cameraWanderMaxYaw);
    }
}
