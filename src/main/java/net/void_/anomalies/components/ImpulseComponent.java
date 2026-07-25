package net.void_.anomalies.components;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
    private final List<ZoneConfig> zones; // 🌟 Список слоев зон

    public ImpulseComponent(double xMultiplier, double yMultiplier, double zMultiplier, boolean pullToCenter, List<ZoneConfig> zones) {
        this.xMultiplier = xMultiplier;
        this.yMultiplier = yMultiplier;
        this.zMultiplier = zMultiplier;
        this.pullToCenter = pullToCenter;

        // Сортируем зоны по радиусу (от центра к периферии)
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

    /**
     * Находит текущую зону, в которой находится цель (по расстоянию).
     * Возвращает ZoneConfig или null, если цель вне всех зон.
     */
    public ZoneConfig getActiveZone(AnomalyEntity anomaly, Entity target) {
        if (target == null || !target.isAlive() || zones.isEmpty()) return null;

        Vec3 anomalyPos = anomaly.position().add(0, anomaly.getBbHeight() / 2.0, 0);
        double distance = anomalyPos.distanceTo(target.position());

        // Ищем первый слой, в радиус которого попадает цель
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
        if (activeZone == null) return false; // Вне зон

        if (pullToCenter) {
            Vec3 anomalyPos = anomaly.position().add(0, anomaly.getBbHeight() / 2.0, 0);
            Vec3 targetPos = target.position();
            double distance = anomalyPos.distanceTo(targetPos);
            double maxRadius = zones.get(zones.size() - 1).radius();
            double normalizedDist = Math.min(distance / maxRadius, 1.0);

            Vec3 direction = anomalyPos.subtract(targetPos).normalize();

            double pullForce = 0;
            double spinForce = 0;

            if (activeZone.physics() != null) {
                // Если у зоны заданы свои силы из PhysicsConfig
                // (Можем задействовать поля или оставить дефолт)
            }

            // Берем параметры сил из конфига зоны (или дефолтные)
            // Допустим, мы берем pullForce / spinForce из физики зоны:
            // (Сделаем мягкий расчет на базе полей PhysicsConfig внутри зоны)

            // Для примера затягивания и вращения:
            double pForce = 0.05; // Можно вытащить из activeZone.physics()
            double sForce = 0.1;

            // Если это самый внутренний слой (эпицентр)
            if (activeZone == zones.get(0) && zones.size() > 1) {
                double closenessFactor = 1.0 - (distance / activeZone.radius());
                double aggressiveSpin = sForce * (1.5 + (closenessFactor * 3.0));
                Vec3 spinVector = new Vec3(-direction.z, 0, direction.x).normalize().scale(aggressiveSpin);

                target.setDeltaMovement(target.getDeltaMovement()
                        .add(direction.scale(pForce * 2.5))
                        .add(spinVector)
                        .add(0, yMultiplier * 2.0, 0));
            } else {
                // Внешние слои: плавное притяжение
                target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(pForce)));
            }
        } else {
            target.setDeltaMovement(target.getDeltaMovement().add(xMultiplier, yMultiplier, zMultiplier));
        }

        target.hurtMarked = true;
        return true;
    }
}