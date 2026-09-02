package net.void_.anomalies.anomaly.data;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.void_.anomalies.core.AnomalyEntity;

public record ZoneConfig(
        double radius,         // Радиус этой зоны
        DamageConfig damage,   // Урон этой зоны
        PhysicsConfig physics  // Физика этой зоны
) {
    /**
     * Проверяет, находится ли сущность в пределах цилиндрического объема данной зоны.
     */
    public boolean contains(AnomalyEntity anomaly, Entity target) {
        if (target == null || !target.isAlive()) return false;

        Vec3 anomalyPos = anomaly.position();
        Vec3 targetPos = target.position();
        double anomalyHeight = anomaly.getBbHeight();

        double dx = targetPos.x - anomalyPos.x;
        double dz = targetPos.z - anomalyPos.z;
        double horizDistSq = dx * dx + dz * dz;

        if (horizDistSq <= radius * radius) {
            double minY = anomalyPos.y;
            double maxY = anomalyPos.y + anomalyHeight;
            return targetPos.y >= minY - 0.5 && targetPos.y <= maxY + 0.5;
        }

        return false;
    }
}