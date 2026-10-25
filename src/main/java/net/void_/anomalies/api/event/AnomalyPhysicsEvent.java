package net.void_.anomalies.api.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.IAnomalyHolder;

import javax.annotation.Nullable;

@Cancelable
public class AnomalyPhysicsEvent extends Event {

    private final IAnomalyHolder anomaly;
    private final Entity target;
    private final String anomalyType;
    @Nullable
    private final ZoneConfig zone;
    private Vec3 deltaMovement;

    public AnomalyPhysicsEvent(IAnomalyHolder anomaly, Entity target, String anomalyType, @Nullable ZoneConfig zone, Vec3 deltaMovement) {
        this.anomaly = anomaly;
        this.target = target;
        this.anomalyType = anomalyType;
        this.zone = zone;
        this.deltaMovement = deltaMovement;
    }

    public AnomalyPhysicsEvent(IAnomalyHolder anomaly, Entity target, Vec3 deltaMovement) {
        this(anomaly, target, anomaly.getAnomalyType(), null, deltaMovement);
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
    public ZoneConfig getZone() {
        return zone;
    }

    public Vec3 getDeltaMovement() {
        return deltaMovement;
    }

    public void setDeltaMovement(Vec3 deltaMovement) {
        this.deltaMovement = deltaMovement;
    }
}