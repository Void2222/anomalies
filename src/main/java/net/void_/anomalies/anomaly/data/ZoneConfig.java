package net.void_.anomalies.anomaly.data;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.void_.anomalies.core.AnomalyEntity;

public record ZoneConfig(
        double radius,
        DamageConfig damage,
        PhysicsConfig physics
) {
    public boolean contains(AnomalyEntity anomaly, Entity target, AnomalyShape shape) {
        if (target == null || !target.isAlive()) return false;

        Vec3 anomalyPos = anomaly.position();
        Vec3 targetPos = target.position();

        Vec3 center = anomaly.getBoundingBox().getCenter();
        double dx3d = targetPos.x - center.x;
        double dy3d = targetPos.y - center.y;
        double dz3d = targetPos.z - center.z;

        return switch (shape != null ? shape : AnomalyShape.CYLINDER) {
            case SPHERE -> {
                double distSq = dx3d * dx3d + dy3d * dy3d + dz3d * dz3d;
                yield distSq <= radius * radius;
            }
            case CUBE -> {
                yield Math.abs(dx3d) <= radius && Math.abs(dy3d) <= radius && Math.abs(dz3d) <= radius;
            }
            case CYLINDER -> {
                double dx = targetPos.x - anomalyPos.x;
                double dz = targetPos.z - anomalyPos.z;
                double horizDistSq = dx * dx + dz * dz;

                if (horizDistSq <= radius * radius) {
                    double anomalyHeight = anomaly.getBbHeight();
                    double minY = anomalyPos.y;
                    double maxY = anomalyPos.y + anomalyHeight;
                    yield targetPos.y >= minY - 0.5 && targetPos.y <= maxY + 0.5;
                }
                yield false;
            }
        };
    }

    public boolean contains(AnomalyEntity anomaly, Entity target) {
        return contains(anomaly, target, anomaly.getShape());
    }
}