package net.void_.anomalies.components;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.void_.anomalies.api.event.AnomalyPhysicsEvent;
import net.void_.anomalies.api.event.AnomalyZoneTransitionEvent;
import net.void_.anomalies.anomaly.data.PhysicsConfig;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ImpulseComponent implements IAnomalyComponent {

    private final double xMultiplier;
    private final double yMultiplier;
    private final double zMultiplier;
    private final boolean pullToCenter;
    private final PhysicsConfig generalPhysics;
    private final List<ZoneConfig> zones;

    // Карта для отслеживания текущей зоны каждой сущности в этой конкретной аномалии
    private final Map<UUID, ZoneConfig> entityZones = new HashMap<>();

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

        UUID targetUuid = target.getUUID();
        ZoneConfig currentZone = getActiveZone(anomaly, target);
        ZoneConfig previousZone = entityZones.get(targetUuid);

        // 🌟 Фиксируем смену зоны
        if (currentZone != previousZone) {
            AnomalyZoneTransitionEvent zoneEvent = new AnomalyZoneTransitionEvent(
                    anomaly,
                    target,
                    anomaly.getAnomalyType(),
                    previousZone,
                    currentZone
            );

            if (MinecraftForge.EVENT_BUS.post(zoneEvent)) {
                return false;
            }

            if (currentZone != null) {
                entityZones.put(targetUuid, currentZone);
            } else {
                entityZones.remove(targetUuid);
            }
        }

        if (currentZone == null) return false;

        PhysicsConfig pConfig = (currentZone.physics() != null) ? currentZone.physics() : generalPhysics;

        double pForce = pConfig != null ? pConfig.pullForce() : 0.05;
        double sForce = pConfig != null ? pConfig.spinForce() : 0.0;
        double impY = pConfig != null ? pConfig.impulseY() : yMultiplier;

        // Рассчитываем планируемое изменение дельта-движения
        Vec3 calculatedMovement = target.getDeltaMovement();

        if (pullToCenter) {
            Vec3 anomalyPos = anomaly.position().add(0, anomaly.getBbHeight() / 2.0, 0);
            Vec3 targetPos = target.position();
            double distance = anomalyPos.distanceTo(targetPos);

            Vec3 direction = anomalyPos.subtract(targetPos).normalize();

            if (zones.size() > 1 && currentZone == zones.get(0)) {
                double closenessFactor = 1.0 - (distance / currentZone.radius());
                double aggressiveSpin = sForce * (1.5 + (closenessFactor * 3.0));
                Vec3 spinVector = new Vec3(-direction.z, 0, direction.x).normalize().scale(aggressiveSpin);

                calculatedMovement = calculatedMovement
                        .add(direction.scale(pForce))
                        .add(spinVector)
                        .add(0, impY, 0);
            } else {
                calculatedMovement = calculatedMovement
                        .add(direction.scale(pForce))
                        .add(0, impY, 0);
            }

            // Защита от катапульты (лимит скорости)
            double maxSpeed = 1.0;
            if (calculatedMovement.horizontalDistanceSqr() > maxSpeed * maxSpeed) {
                calculatedMovement = calculatedMovement.normalize().scale(maxSpeed)
                        .add(0, calculatedMovement.y - calculatedMovement.normalize().scale(maxSpeed).y, 0); // Сохраняем вертикаль
                // Более чистый вариант ограничения горизонтали с сохранением вертикальной составляющей:
                Vec3 horiz = new Vec3(calculatedMovement.x, 0, calculatedMovement.z);
                if (horiz.lengthSqr() > maxSpeed * maxSpeed) {
                    horiz = horiz.normalize().scale(maxSpeed);
                }
                calculatedMovement = new Vec3(horiz.x, calculatedMovement.y, horiz.z);
            }

        } else {
            double iX = pConfig != null ? pConfig.impulseX() : xMultiplier;
            double iY = pConfig != null ? pConfig.impulseY() : yMultiplier;
            double iZ = pConfig != null ? pConfig.impulseZ() : zMultiplier;
            calculatedMovement = calculatedMovement.add(iX, iY, iZ);
        }

        // 🌟 Постим событие физики перед применением движения
        AnomalyPhysicsEvent physicsEvent = new AnomalyPhysicsEvent(
                anomaly,
                target,
                anomaly.getAnomalyType(),
                currentZone,
                calculatedMovement
        );

        if (MinecraftForge.EVENT_BUS.post(physicsEvent)) {
            return false; // Сторонний мод полностью отменил физику аномалии для этой сущности
        }

        // Применяем финальный вектор (который сторонний мод мог изменить через сеттер)
        target.setDeltaMovement(physicsEvent.getDeltaMovement());
        target.hurtMarked = true;
        return true;
    }
}