package net.void_.anomalies.anomaly.data;

public record PhysicsConfig(
        double impulseX,
        double impulseY,
        double impulseZ,
        boolean pullToCenter,

        Double outerRadius,
        Double innerRadius,
        Double pullForce,
        Double spinForce
) {}