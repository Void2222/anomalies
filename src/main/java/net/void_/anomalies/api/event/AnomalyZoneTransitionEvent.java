package net.void_.anomalies.api.event;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.IAnomalyHolder;

import javax.annotation.Nullable;

@Cancelable
public class AnomalyZoneTransitionEvent extends Event {

    private final IAnomalyHolder anomaly;
    private final Entity target;
    private final String anomalyType;
    @Nullable
    private final ZoneConfig previousZone;
    @Nullable
    private final ZoneConfig currentZone;

    public AnomalyZoneTransitionEvent(IAnomalyHolder anomaly, Entity target, String anomalyType,
                                      @Nullable ZoneConfig previousZone, @Nullable ZoneConfig currentZone) {
        this.anomaly = anomaly;
        this.target = target;
        this.anomalyType = anomalyType;
        this.previousZone = previousZone;
        this.currentZone = currentZone;
    }

    public IAnomalyHolder getAnomaly() {
        return anomaly;
    }

    public Entity getTarget() {
        return target;
    }

    public String getAnomalyType() {
        return anomalyType;
    }

    @Nullable
    public ZoneConfig getPreviousZone() {
        return previousZone;
    }

    @Nullable
    public ZoneConfig getCurrentZone() {
        return currentZone;
    }

    public boolean isEntering() {
        return previousZone == null && currentZone != null;
    }

    public boolean isLeaving() {
        return previousZone != null && currentZone == null;
    }
}