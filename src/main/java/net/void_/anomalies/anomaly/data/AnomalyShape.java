package net.void_.anomalies.anomaly.data;

public enum AnomalyShape {
    SPHERE,
    CYLINDER,
    CUBE;

    public static AnomalyShape fromString(String name) {
        if (name == null) return CYLINDER;
        try {
            return AnomalyShape.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CYLINDER;
        }
    }
}