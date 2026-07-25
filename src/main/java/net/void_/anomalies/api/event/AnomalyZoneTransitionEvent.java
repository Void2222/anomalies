package net.void_.anomalies.api.event;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.AnomalyEntity;

import javax.annotation.Nullable;

@Cancelable // Позволяет другим модам отменять реакцию на вход/выход из зоны (например, игнорирование эффектов костюмом)
public class AnomalyZoneTransitionEvent extends Event {

    private final AnomalyEntity anomaly;
    private final Entity target;
    private final String anomalyType;
    @Nullable
    private final ZoneConfig previousZone;
    @Nullable
    private final ZoneConfig currentZone;

    public AnomalyZoneTransitionEvent(AnomalyEntity anomaly, Entity target, String anomalyType,
                                      @Nullable ZoneConfig previousZone, @Nullable ZoneConfig currentZone) {
        this.anomaly = anomaly;
        this.target = target;
        this.anomalyType = anomalyType;
        this.previousZone = previousZone;
        this.currentZone = currentZone;
    }

    public AnomalyEntity getAnomaly() {
        return anomaly;
    }

    public Entity getTarget() {
        return target;
    }

    public String getAnomalyType() {
        return anomalyType;
    }

    /**
     * @return Зона, из которой вышла сущность. Может быть null, если сущность только что вошла в радиус аномалии извне.
     */
    @Nullable
    public ZoneConfig getPreviousZone() {
        return previousZone;
    }

    /**
     * @return Зона, в которую вошла сущность. Может быть null, если сущность полностью покинула пределы аномалии.
     */
    @Nullable
    public ZoneConfig getCurrentZone() {
        return currentZone;
    }

    /**
     * Удобный метод для проверки: вошла ли сущность в аномалию с нуля (снаружи внутрь).
     */
    public boolean isEntering() {
        return previousZone == null && currentZone != null;
    }

    /**
     * Удобный метод для проверки: полностью ли сущность покинула аномалию.
     */
    public boolean isLeaving() {
        return previousZone != null && currentZone == null;
    }
}