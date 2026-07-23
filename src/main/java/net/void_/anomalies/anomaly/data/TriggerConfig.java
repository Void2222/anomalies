package net.void_.anomalies.anomaly.data;

public record TriggerConfig(
        double expandRadius,
        MinMaxRange interval // Интервал проверки триггера тоже может быть рандомным!
) {}