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

    // Карты отслеживания сущностей в зонах для фиксации переходов
    private final Map<UUID, ZoneConfig> entityZones = new HashMap<>();
    private int cleanupTimer = 0;

    public ImpulseComponent(double xMultiplier, double yMultiplier, double zMultiplier, boolean pullToCenter, PhysicsConfig generalPhysics, List<ZoneConfig> zones) {
        this.xMultiplier = xMultiplier;
        this.yMultiplier = yMultiplier;
        this.zMultiplier = zMultiplier;
        this.pullToCenter = pullToCenter;
        this.generalPhysics = generalPhysics;

        // Сортируем зоны по возрастанию радиуса для корректного определения слоев
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
        // Раз в 20 тиков (1 секунда) очищаем застрявшие сущности, покинувшие аномалию
        cleanupTimer++;
        if (cleanupTimer >= 20) {
            cleanupTimer = 0;
            cleanupStaleEntities(anomaly);
        }
    }

    @Override
    public void clientTick(AnomalyEntity anomaly) {}

    /**
     * Периодическая проверка сущностей, покинувших радиус взаимодействия
     */
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

            // Если сущность умерла, удалена из мира или вышла из всех зон
            if (target == null || !target.isAlive() || target.level() != anomaly.level() || currentZone == null) {
                iterator.remove();

                // Оповещаем систему о полной выходе сущности из аномалии
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

    /**
     * Стандартный метод применения импульса (вычисляет активную зону автоматически)
     */
    public boolean applyImpulse(AnomalyEntity anomaly, Entity target) {
        return applyImpulse(anomaly, target, null);
    }

    /**
     * ⚡ ОПТИМИЗИРОВАННЫЙ МЕТОД: Принимает предрассчитанную зону, избегая повторных итераций по списку зон
     */
    public boolean applyImpulse(AnomalyEntity anomaly, Entity target, @Nullable ZoneConfig precalculatedZone) {
        if (target == null || !target.isAlive()) return false;

        UUID targetUuid = target.getUUID();

        // Используем переданную зону или ищем активную, если она не была передана
        ZoneConfig currentZone = (precalculatedZone != null)
                ? precalculatedZone
                : ZoneUtils.getActiveZone(zones, anomaly, target);

        ZoneConfig previousZone = entityZones.get(targetUuid);

        // 🔄 Обработка смены зоны (вход / переходы между слоями)
        if (currentZone != previousZone) {
            AnomalyZoneTransitionEvent zoneEvent = new AnomalyZoneTransitionEvent(
                    anomaly,
                    target,
                    anomaly.getAnomalyType(),
                    previousZone,
                    currentZone
            );

            // Если событие отменено сторонним модом — прекращаем физику
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

        // Конфигурация физики текущего слоя
        PhysicsConfig pConfig = (currentZone.physics() != null) ? currentZone.physics() : generalPhysics;

        double pForce = pConfig != null ? pConfig.pullForce() : 0.05;
        double sForce = pConfig != null ? pConfig.spinForce() : 0.0;
        double impY = pConfig != null ? pConfig.impulseY() : yMultiplier;

        // ⚡ Zero-GC ВЕКТОРНЫЕ ВЫЧИСЛЕНИЯ: Выполняем логику на примитивах double без создания Vec3
        Vec3 currentMovement = target.getDeltaMovement();
        double moveX = currentMovement.x;
        double moveY = currentMovement.y;
        double moveZ = currentMovement.z;

        if (pullToCenter) {
            // Центр аномалии
            double ax = anomaly.getX();
            double ay = anomaly.getY() + anomaly.getBbHeight() * 0.5D;
            double az = anomaly.getZ();

            // Позиция цели
            double tx = target.getX();
            double ty = target.getY();
            double tz = target.getZ();

            // Вектор от цели к центру
            double dx = ax - tx;
            double dy = ay - ty;
            double dz = az - tz;

            double distSq = dx * dx + dy * dy + dz * dz;

            // Защита от деления на ноль, если сущность ровно в центре
            if (distSq > 1.0E-6D) {
                // ⚡ Единственный Math.sqrt на весь расчет (для нормализации вектора и расстояния)
                double invDist = 1.0D / Math.sqrt(distSq);

                // Единичный вектор направления к центру
                double dirX = dx * invDist;
                double dirZ = dz * invDist;

                // Применяем силу притягивания
                moveX += dirX * pForce;
                moveZ += dirZ * pForce;

                // Если цель во внутренней (самой опасной) зоне — добавляем тангенциальное вращение
                if (zones.size() > 1 && currentZone == zones.get(0)) {
                    double dist = distSq * invDist; // distSq / sqrt(distSq) == dist
                    double closenessFactor = Math.max(0.0D, 1.0D - (dist / currentZone.radius()));
                    double aggressiveSpin = sForce * (1.5D + (closenessFactor * 3.0D));

                    // Перпендикулярный вектор вращения (-dirZ, dirX)
                    moveX += (-dirZ) * aggressiveSpin;
                    moveZ += dirX * aggressiveSpin;
                }

                moveY += impY;

                // ⚡ Ограничение максимальной горизонтальной скорости (1.0 блок/тик)
                double horizSqr = moveX * moveX + moveZ * moveZ;
                double maxSpeedSqr = 1.0D; // 1.0^2
                if (horizSqr > maxSpeedSqr) {
                    double invHoriz = 1.0D / Math.sqrt(horizSqr);
                    moveX *= invHoriz;
                    moveZ *= invHoriz;
                }
            }
        } else {
            // Направленный отталкивающий импульс по осям
            double iX = pConfig != null ? pConfig.impulseX() : xMultiplier;
            double iY = pConfig != null ? pConfig.impulseY() : yMultiplier;
            double iZ = pConfig != null ? pConfig.impulseZ() : zMultiplier;

            moveX += iX;
            moveY += iY;
            moveZ += iZ;
        }

        // ⚡ Создаем единственную итоговую векторную переменную в самом конце
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
}