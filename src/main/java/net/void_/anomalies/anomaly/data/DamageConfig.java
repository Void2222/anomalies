package net.void_.anomalies.anomaly.data;

public record DamageConfig(
        MinMaxRange outerAmount,
        MinMaxRange innerAmount,
        int fireSeconds,
        String damageType
) {
    // Конструктор на случай, если в JSON старого типа написано просто "amount"
    public DamageConfig(MinMaxRange amount, int fireSeconds, String damageType) {
        this(new MinMaxRange(0, 0), amount, fireSeconds, damageType);
    }
}