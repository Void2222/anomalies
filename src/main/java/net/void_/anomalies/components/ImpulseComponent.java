package net.void_.anomalies.components;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.void_.anomalies.api.event.AnomalyPhysicsEvent;
import net.void_.anomalies.api.event.AnomalyZoneTransitionEvent;
import net.void_.anomalies.anomaly.data.PhysicsConfig;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.anomaly.util.ZoneUtils;
import net.void_.anomalies.config.AnomalyIgnoreManager;
import net.void_.anomalies.core.IAnomalyComponent;
import net.void_.anomalies.core.IAnomalyHolder;
import net.void_.lightentity.api.ILightEntity;

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
    public void serverTick(ILightEntity entity) {
        if (entity instanceof IAnomalyHolder holder) {
            cleanupStaleEntities(holder);
        }
    }

    private void cleanupStaleEntities(IAnomalyHolder anomaly) {
        if (entityZones.isEmpty()) return;
        if (!(anomaly.getLevel() instanceof ServerLevel serverLevel)) return;

        Iterator<Map.Entry<UUID, ZoneConfig>> iterator = entityZones.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ZoneConfig> entry = iterator.next();
            UUID uuid = entry.getKey();
            ZoneConfig recordedZone = entry.getValue();

            Entity target = serverLevel.getEntity(uuid);

            if (target == null || !target.isAlive() || target.level() != anomaly.getLevel()) {
                iterator.remove();
                continue;
            }

            ZoneConfig currentZone = ZoneUtils.getActiveZone(zones, anomaly, target);

            if (currentZone == null) {
                iterator.remove();

                AnomalyZoneTransitionEvent exitEvent = new AnomalyZoneTransitionEvent(
                        anomaly,
                        target,
                        anomaly.getAnomalyType(),
                        recordedZone,
                        null
                );
                MinecraftForge.EVENT_BUS.post(exitEvent);
            }
        }
    }

    public boolean applyImpulse(IAnomalyHolder anomaly, Entity target) {
        return applyImpulse(anomaly, target, null);
    }

    public boolean applyImpulse(IAnomalyHolder anomaly, Entity target, @Nullable ZoneConfig precalculatedZone) {
        if (target == null || !target.isAlive()) return false;

        // Игнорируем защищенных игроков!
        if (target instanceof Player player && AnomalyIgnoreManager.isIgnored(player)) {
            return false;
        }

        UUID targetUuid = target.getUUID();

        ZoneConfig currentZone = (precalculatedZone != null)
                ? precalculatedZone
                : ZoneUtils.getActiveZone(zones, anomaly, target);

        ZoneConfig previousZone = entityZones.get(targetUuid);

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
            double ay = anomaly.getY() + anomaly.getHeight() * 0.5D;
            double az = anomaly.getZ();

            double dx = ax - target.getX();
            double dy = ay - target.getY();
            double dz = az - target.getZ();

            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > 0.0001) {
                double invDist = 1.0 / Math.sqrt(distSq);
                double dirX = dx * invDist;
                double dirZ = dz * invDist;

                moveX += dirX * pForce;
                moveZ += dirZ * pForce;

                if (zones.size() > 1 && isSameZone(currentZone, zones.get(0))) {
                    double dist = Math.sqrt(distSq);
                    double radius = currentZone.radius();
                    double closenessFactor = Math.max(0.0, 1.0 - (dist / radius));
                    double aggressiveSpin = sForce * (1.5 + closenessFactor * 3.0);

                    double tangentX = -dirZ;
                    double tangentZ = dirX;

                    moveX += tangentX * aggressiveSpin;
                    moveZ += tangentZ * aggressiveSpin;
                }

                double horizontalSpeedSq = moveX * moveX + moveZ * moveZ;
                if (horizontalSpeedSq > 1.0) {
                    double hScale = 1.0 / Math.sqrt(horizontalSpeedSq);
                    moveX *= hScale;
                    moveZ *= hScale;
                }

                moveY = (dy * invDist) * pForce;
            }
        } else {
            moveX += xMultiplier;
            moveZ += zMultiplier;
        }

        moveY += impY;

        Vec3 newMovement = new Vec3(moveX, moveY, moveZ);

        AnomalyPhysicsEvent physicsEvent = new AnomalyPhysicsEvent(anomaly, target, newMovement);
        if (MinecraftForge.EVENT_BUS.post(physicsEvent)) {
            return false;
        }

        Vec3 finalMovement = physicsEvent.getDeltaMovement();
        target.setDeltaMovement(finalMovement);
        target.hurtMarked = true;

        return true;
    }

    private boolean isSameZone(ZoneConfig a, ZoneConfig b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Math.abs(a.radius() - b.radius()) < 0.0001;
    }
}