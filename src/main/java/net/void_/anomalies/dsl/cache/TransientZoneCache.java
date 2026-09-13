package net.void_.anomalies.dsl.cache;

import net.minecraft.world.entity.player.Player;
import net.void_.anomalies.api.event.AnomalyZoneTransitionEvent;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.components.ImpulseComponent;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.dsl.context.EvaluationContext.ZoneEvent;
import net.void_.anomalies.dsl.context.EvaluationContext.ZoneEventType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TransientZoneCache {

    private static final Map<UUID, AnomalyCacheData> CACHE = new ConcurrentHashMap<>();

    private static class AnomalyCacheData {
        // Одноразовые триггеры текущего тика (ENTERED, EXITED)
        final Set<ZoneEvent> tickEvents = new HashSet<>(4);
        // Долгосрочное нахождение в зонах (IN_ZONE)
        final Set<String> activeZones = new HashSet<>(4);
    }

    public static void recordTransition(AnomalyZoneTransitionEvent event) {
        if (!(event.getTarget() instanceof Player)) {
            return;
        }

        AnomalyEntity anomaly = event.getAnomaly();
        if (anomaly == null) return;

        AnomalyCacheData data = CACHE.computeIfAbsent(anomaly.getUUID(), k -> new AnomalyCacheData());

        // Выход из предыдущей зоны
        if (event.getPreviousZone() != null) {
            String prevName = getZoneIdentifier(anomaly, event.getPreviousZone());
            data.activeZones.remove(prevName);
            data.tickEvents.add(new ZoneEvent(ZoneEventType.EXITED, prevName));
        }

        // Вход в новую зону
        if (event.getCurrentZone() != null) {
            String currName = getZoneIdentifier(anomaly, event.getCurrentZone());
            data.activeZones.add(currName);
            data.tickEvents.add(new ZoneEvent(ZoneEventType.ENTERED, currName));
        }
    }

    public static Set<ZoneEvent> getSnapshotAndFlush(AnomalyEntity anomaly) {
        AnomalyCacheData data = CACHE.get(anomaly.getUUID());
        if (data == null) {
            return Collections.emptySet();
        }

        Set<ZoneEvent> snapshot = new HashSet<>(data.tickEvents.size() + data.activeZones.size());

        // 1. Копируем одноразовые ивенты тика
        snapshot.addAll(data.tickEvents);

        // 2. Генерируем активные состояния IN_ZONE
        for (String zoneName : data.activeZones) {
            snapshot.add(new ZoneEvent(ZoneEventType.IN_ZONE, zoneName));
        }

        // 3. Сбрасываем одноразовые ивенты (ENTERED/EXITED сгорают до следующего тика)
        data.tickEvents.clear();

        return snapshot;
    }

    public static void clear(AnomalyEntity anomaly) {
        CACHE.remove(anomaly.getUUID());
    }

    /**
     * Вычисляет строковый идентификатор зоны на основе ее индекса в ImpulseComponent (0, 1, 2...).
     */
    private static String getZoneIdentifier(AnomalyEntity anomaly, ZoneConfig zone) {
        if (zone == null) return "0";

        ImpulseComponent impulse = anomaly.getComponent(ImpulseComponent.class);
        if (impulse != null) {
            int index = impulse.getZones().indexOf(zone);
            if (index >= 0) {
                return String.valueOf(index);
            }
        }
        return "0";
    }
}