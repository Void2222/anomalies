package net.void_.anomalies.dsl.cache;

import net.minecraft.world.entity.player.Player;
import net.void_.anomalies.api.event.AnomalyZoneTransitionEvent;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.core.IAnomalyHolder;
import net.void_.anomalies.dsl.context.EvaluationContext.ZoneEvent;
import net.void_.anomalies.dsl.context.EvaluationContext.ZoneEventType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TransientZoneCache {

    private static final Map<UUID, AnomalyCacheData> CACHE = new ConcurrentHashMap<>();

    private static class AnomalyCacheData {
        final Set<ZoneEvent> tickEvents = new HashSet<>(4);
        final Set<String> activeZones = new HashSet<>(4);
    }

    public static void recordTransition(AnomalyZoneTransitionEvent event) {
        if (!(event.getTarget() instanceof Player)) {
            return;
        }

        IAnomalyHolder anomaly = event.getAnomaly();
        if (anomaly == null) return;

        AnomalyCacheData data = CACHE.computeIfAbsent(anomaly.getUUID(), k -> new AnomalyCacheData());

        // Выход из предыдущей зоны
        if (event.getPreviousZone() != null) {
            String prevName = getZoneIdentifier(anomaly, event.getPreviousZone());
            if (!prevName.isEmpty()) {
                data.activeZones.remove(prevName);
                data.tickEvents.add(new ZoneEvent(ZoneEventType.EXITED, prevName));
            } else {
                data.activeZones.clear();
            }
        } else if (event.getCurrentZone() == null) {
            data.activeZones.clear();
        }

        // Вход в новую зону
        if (event.getCurrentZone() != null) {
            String currName = getZoneIdentifier(anomaly, event.getCurrentZone());
            if (!currName.isEmpty()) {
                data.activeZones.add(currName);
                data.tickEvents.add(new ZoneEvent(ZoneEventType.ENTERED, currName));
            }
        }
    }

    public static Set<ZoneEvent> getSnapshotAndFlush(IAnomalyHolder anomaly) {
        AnomalyCacheData data = CACHE.get(anomaly.getUUID());
        if (data == null) {
            return Collections.emptySet();
        }

        Set<ZoneEvent> snapshot = new HashSet<>(data.tickEvents.size() + data.activeZones.size());
        snapshot.addAll(data.tickEvents);

        for (String zoneName : data.activeZones) {
            snapshot.add(new ZoneEvent(ZoneEventType.IN_ZONE, zoneName));
        }

        data.tickEvents.clear();
        return snapshot;
    }

    public static void flushTickEvents(IAnomalyHolder anomaly) {
        AnomalyCacheData data = CACHE.get(anomaly.getUUID());
        if (data != null) {
            data.tickEvents.clear();
        }
    }

    public static void clear(IAnomalyHolder anomaly) {
        CACHE.remove(anomaly.getUUID());
    }

    private static String getZoneIdentifier(IAnomalyHolder anomaly, ZoneConfig zone) {
        if (zone == null) return "";

        AnomalyDefinition definition = AnomalyReloadListener.get(anomaly.getAnomalyType(), anomaly.getCurrentState());
        if (definition != null && definition.zones() != null) {
            List<ZoneConfig> sortedZones = definition.zones().stream()
                    .sorted(Comparator.comparingDouble(ZoneConfig::radius))
                    .toList();

            for (int i = 0; i < sortedZones.size(); i++) {
                if (Math.abs(sortedZones.get(i).radius() - zone.radius()) < 0.0001) {
                    return String.valueOf(i);
                }
            }
        }
        return "";
    }
}