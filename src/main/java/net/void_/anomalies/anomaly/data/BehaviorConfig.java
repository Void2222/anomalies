package net.void_.anomalies.anomaly.data;

public record BehaviorConfig(
        MinMaxRange damage, // Урон теперь тоже может плавать в диапазоне [min, max]
        int fireSeconds,
        double impulseX,
        double impulseY,
        double impulseZ,
        boolean pullToCenter,
        boolean ignoreOtherAnomalies,
        String damageType
) {}