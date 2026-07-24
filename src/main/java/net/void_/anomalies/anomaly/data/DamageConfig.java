package net.void_.anomalies.anomaly.data;

public record DamageConfig(
        MinMaxRange amount,
        int fireSeconds,
        String damageType
) {}