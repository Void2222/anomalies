package net.void_.anomalies.anomaly.util;

import net.minecraft.world.entity.Entity;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.AnomalyEntity;

import javax.annotation.Nullable;
import java.util.List;

public class ZoneUtils {

    /**
     * Находит наименьшую (внутреннюю) активную зону аномалии для указанной сущности.
     * Список зон должен быть отсортирован по возрастанию радиуса.
     */
    @Nullable
    public static ZoneConfig getActiveZone(List<ZoneConfig> zones, AnomalyEntity anomaly, Entity target) {
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