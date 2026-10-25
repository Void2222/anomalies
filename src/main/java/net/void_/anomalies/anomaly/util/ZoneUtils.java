package net.void_.anomalies.anomaly.util;

import net.minecraft.world.entity.Entity;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.IAnomalyHolder;

import java.util.List;

public class ZoneUtils {

    public static ZoneConfig getActiveZone(List<ZoneConfig> zones, IAnomalyHolder anomaly, Entity target) {
        if (target == null || !target.isAlive() || zones == null || zones.isEmpty()) {
            return null;
        }

        for (ZoneConfig zone : zones) {
            if (zone.contains(anomaly, target)) {
                return zone;
            }
        }

        return null;
    }
}