package net.void_.anomalies.anomaly.data;

public record ZoneConfig(
        double radius,         // Радиус этой зоны
        DamageConfig damage,   // Урон этой зоны (outer/inner или просто amount + огонь)
        PhysicsConfig physics  // Физика этой зоны (pullForce, spinForce и т.д.)
) {}