package net.void_.anomalies.components;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.void_.anomalies.api.event.AnomalyPhysicsEvent;
import net.void_.anomalies.api.event.AnomalyZoneTransitionEvent;
import net.void_.anomalies.anomaly.data.PhysicsConfig;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.anomaly.util.ZoneUtils;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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

    private final Map<UUID, ZoneConfig> entityZones = new HashMap<>();
    private int cleanupTimer = 0;

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

    public List<ZoneConfig> getZones() {
        return zones;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        cleanupTimer++;
        if (cleanupTimer >= 20) {
            cleanupTimer = 0;
            cleanupStaleEntities(anomaly);
        }
    }

    @Override
    public void clientTick(AnomalyEntity anomaly) {}

    private void cleanupStaleEntities(AnomalyEntity anomaly) {
        if (entityZones.isEmpty()) return;
        if (!(anomaly.level() instanceof ServerLevel serverLevel)) return;

        Iterator<Map.Entry<UUID, ZoneConfig>> iterator = entityZones.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ZoneConfig> entry = iterator.next();
            UUID uuid = entry.getKey();
            ZoneConfig previousZone = entry.getValue();

            Entity target = serverLevel.getEntity(uuid);
            ZoneConfig currentZone = (target != null) ? ZoneUtils.getActiveZone(zones, anomaly, target) : null;

            if (target == null || !target.isAlive() || target.level() != anomaly.level() || currentZone == null) {
                iterator.remove();

                if (target != null && target.isAlive()) {
                    AnomalyZoneTransitionEvent zoneEvent = new AnomalyZoneTransitionEvent(
                            anomaly,
                            target,
                            anomaly.getAnomalyType(),
                            previousZone,
                            null
                    );
                    MinecraftForge.EVENT_BUS.post(zoneEvent);
                }
            }
        }
    }

    public boolean applyImpulse(AnomalyEntity anomaly, Entity target) {
        if (target == null || !target.isAlive()) return false;

        UUID targetUuid = target.getUUID();
        ZoneConfig currentZone = ZoneUtils.getActiveZone(zones, anomaly, target);
        ZoneConfig previousZone = entityZones.get(targetUuid);

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

            double maxSpeed = 1.0;
            Vec3 horiz = new Vec3(calculatedMovement.x, 0, calculatedMovement.z);
            if (horiz.lengthSqr() > maxSpeed * maxSpeed) {
                horiz = horiz.normalize().scale(maxSpeed);
            }
            calculatedMovement = new Vec3(horiz.x, calculatedMovement.y, horiz.z);

        } else {
            double iX = pConfig != null ? pConfig.impulseX() : xMultiplier;
            double iY = pConfig != null ? pConfig.impulseY() : yMultiplier;
            double iZ = pConfig != null ? pConfig.impulseZ() : zMultiplier;
            calculatedMovement = calculatedMovement.add(iX, iY, iZ);
        }

        AnomalyPhysicsEvent physicsEvent = new AnomalyPhysicsEvent(
                anomaly,
                target,
                anomaly.getAnomalyType(),
                currentZone,
                calculatedMovement
        );

        if (MinecraftForge.EVENT_BUS.post(physicsEvent)) {
            return false;
        }

        target.setDeltaMovement(physicsEvent.getDeltaMovement());
        target.hurtMarked = true;
        return true;
    }
}