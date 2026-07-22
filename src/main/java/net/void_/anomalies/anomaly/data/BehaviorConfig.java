package net.void_.anomalies.anomaly.data;

public record BehaviorConfig(
        float damage,
        int fireSeconds,
        double impulseX,
        double impulseY,
        double impulseZ,
        boolean pullToCenter,
        boolean ignoreOtherAnomalies
) {}