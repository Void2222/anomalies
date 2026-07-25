package net.void_.anomalies.components;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.void_.anomalies.anomaly.data.PhysicsConfig;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

import java.util.Comparator;
import java.util.List;

public class ImpulseComponent implements IAnomalyComponent {

    private final double xMultiplier;
    private final double yMultiplier;
    private final double zMultiplier;
    private final boolean pullToCenter;
    private final PhysicsConfig generalPhysics; // Общая физика из AnomalyDefinition
    private final List<ZoneConfig> zones;

    public ImpulseComponent(double xMultiplier, double yMultiplier, double zMultiplier, boolean pullToCenter, PhysicsConfig generalPhysics, List<ZoneConfig> zones) {
        this.xMultiplier = xMultiplier;
        this.yMultiplier = yMultiplier;
        this.zMultiplier = zMultiplier;
        this.pullToCenter = pullToCenter;
        this.generalPhysics = generalPhysics;

        if (zones != null) {
            this.zones = zones.stream()
                    .sorted(Comparator.comparingDouble(ZoneConfig::radius))
                    .toList();
        } else {
            this.zones = List.of();
        }
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {}
    @Override
    public void clientTick(AnomalyEntity anomaly) {}

    public ZoneConfig getActiveZone(AnomalyEntity anomaly, Entity target) {
        if (target == null || !target.isAlive() || zones.isEmpty()) return null;

        Vec3 anomalyPos = anomaly.position().add(0, anomaly.getBbHeight() / 2.0, 0);
        double distance = anomalyPos.distanceTo(target.position());

        for (ZoneConfig zone : zones) {
            if (distance <= zone.radius()) {
                return zone;
            }
        }
        return null;
    }

    public boolean applyImpulse(AnomalyEntity anomaly, Entity target) {
        if (target == null || !target.isAlive()) return false;

        ZoneConfig activeZone = getActiveZone(anomaly, target);
        if (activeZone == null) return false;

        // Берем физику из зоны, если она там есть, иначе из общих настроек аномалии
        PhysicsConfig pConfig = (activeZone.physics() != null) ? activeZone.physics() : generalPhysics;

        double pForce = pConfig != null ? pConfig.pullForce() : 0.05;
        double sForce = pConfig != null ? pConfig.spinForce() : 0.0;
        double impY = pConfig != null ? pConfig.impulseY() : yMultiplier;

        if (pullToCenter) {
            Vec3 anomalyPos = anomaly.position().add(0, anomaly.getBbHeight() / 2.0, 0);
            Vec3 targetPos = target.position();
            double distance = anomalyPos.distanceTo(targetPos);

            // Вектор направления (если pForce отрицательный, знак инвертируется автоматически)
            Vec3 direction = anomalyPos.subtract(targetPos).normalize();

            // Если это самый внутренний слой (эпицентр)
            if (zones.size() > 1 && activeZone == zones.get(0)) {
                double closenessFactor = 1.0 - (distance / activeZone.radius());
                double aggressiveSpin = sForce * (1.5 + (closenessFactor * 3.0));
                Vec3 spinVector = new Vec3(-direction.z, 0, direction.x).normalize().scale(aggressiveSpin);

                target.setDeltaMovement(target.getDeltaMovement()
                        .add(direction.scale(pForce))
                        .add(spinVector)
                        .add(0, impY, 0)); // Убрали умножение на 2.0, чтобы не подкидывало высоко
            } else {
                // Внешние слои
                target.setDeltaMovement(target.getDeltaMovement()
                        .add(direction.scale(pForce))
                        .add(0, impY, 0));
            }

            // Защита от катапульты (лимит скорости)
            Vec3 currentMotion = target.getDeltaMovement();
            double maxSpeed = 1.0;
            if (currentMotion.horizontalDistanceSqr() > maxSpeed * maxSpeed) {
                Vec3 limited = currentMotion.normalize().scale(maxSpeed);
                target.setDeltaMovement(limited.x, currentMotion.y, limited.z);
            }

        } else {
            double iX = pConfig != null ? pConfig.impulseX() : xMultiplier;
            double iY = pConfig != null ? pConfig.impulseY() : yMultiplier;
            double iZ = pConfig != null ? pConfig.impulseZ() : zMultiplier;
            target.setDeltaMovement(target.getDeltaMovement().add(iX, iY, iZ));
        }

        target.hurtMarked = true;
        return true;
    }
}