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

import javax.annotation.Nullable;
import java.util.*;

public class ImpulseComponent implements IAnomalyComponent {

    private final double xMultiplier;
    private final double yMultiplier;
    private final double zMultiplier;
    private final boolean pullToCenter;
    private final PhysicsConfig generalPhysics;
    private final List<ZoneConfig> zones;

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

    public List<ZoneConfig> getZones() {
        return zones;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        // Проверяем покинувших зону сущностей каждый тик без задержки в 20 тиков
        cleanupStaleEntities(anomaly);
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
        return applyImpulse(anomaly, target, null);
    }

    public boolean applyImpulse(AnomalyEntity anomaly, Entity target, @Nullable ZoneConfig precalculatedZone) {
        if (target == null || !target.isAlive()) return false;

        UUID targetUuid = target.getUUID();

        ZoneConfig currentZone = (precalculatedZone != null)
                ? precalculatedZone
                : ZoneUtils.getActiveZone(zones, anomaly, target);

        ZoneConfig previousZone = entityZones.get(targetUuid);

        // Безопасное сравнение по радиусу вместо ссылки !=
        if (!isSameZone(currentZone, previousZone)) {
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

        Vec3 currentMovement = target.getDeltaMovement();
        double moveX = currentMovement.x;
        double moveY = currentMovement.y;
        double moveZ = currentMovement.z;

        if (pullToCenter) {
            double ax = anomaly.getX();
            double ay = anomaly.getY() + anomaly.getBbHeight() * 0.5D;
            double az = anomaly.getZ();

            double tx = target.getX();
            double ty = target.getY();
            double tz = target.getZ();

            double dx = ax - tx;
            double dy = ay - ty;
            double dz = az - tz;

            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > 1.0E-6D) {
                double invDist = 1.0D / Math.sqrt(distSq);

                double dirX = dx * invDist;
                double dirZ = dz * invDist;

                moveX += dirX * pForce;
                moveZ += dirZ * pForce;

                if (zones.size() > 1 && isSameZone(currentZone, zones.get(0))) {
                    double dist = distSq * invDist;
                    double closenessFactor = Math.max(0.0D, 1.0D - (dist / currentZone.radius()));
                    double aggressiveSpin = sForce * (1.5D + (closenessFactor * 3.0D));

                    moveX += (-dirZ) * aggressiveSpin;
                    moveZ += dirX * aggressiveSpin;
                }

                moveY += impY;

                double horizSqr = moveX * moveX + moveZ * moveZ;
                if (horizSqr > 1.0D) {
                    double invHoriz = 1.0D / Math.sqrt(horizSqr);
                    moveX *= invHoriz;
                    moveZ *= invHoriz;
                }
            }
        } else {
            double iX = pConfig != null ? pConfig.impulseX() : xMultiplier;
            double iY = pConfig != null ? pConfig.impulseY() : yMultiplier;
            double iZ = pConfig != null ? pConfig.impulseZ() : zMultiplier;

            moveX += iX;
            moveY += iY;
            moveZ += iZ;
        }

        Vec3 calculatedMovement = new Vec3(moveX, moveY, moveZ);

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

    private boolean isSameZone(ZoneConfig a, ZoneConfig b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Math.abs(a.radius() - b.radius()) < 0.0001;
    }
}