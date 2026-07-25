package net.void_.anomalies.anomaly.data;

public record PhysicsConfig(
        double impulseX,
        double impulseY,
        double impulseZ,
        boolean pullToCenter,
        double pullForce,
        double spinForce
) {}