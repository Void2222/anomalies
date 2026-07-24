package net.void_.anomalies.api.event;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.void_.anomalies.core.AnomalyEntity;

@Cancelable // Позволяет другим модам отменять эффект аномалии (например, через защитный костюм)
public class AnomalyTriggerEvent extends Event {

    private final AnomalyEntity anomaly;
    private final Entity target;
    private final String anomalyType;

    public AnomalyTriggerEvent(AnomalyEntity anomaly, Entity target, String anomalyType) {
        this.anomaly = anomaly;
        this.target = target;
        this.anomalyType = anomalyType;
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
}