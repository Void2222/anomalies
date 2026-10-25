package net.void_.anomalies.api.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.IAnomalyHolder;

import javax.annotation.Nullable;

@Cancelable
public class AnomalyDamageEvent extends Event {

    private final IAnomalyHolder anomaly;
    private final Entity target;
    private final String anomalyType;
    @Nullable
    private final ZoneConfig zone;
    private final DamageSource damageSource;
    private float amount;

    public AnomalyDamageEvent(IAnomalyHolder anomaly, Entity target, String anomalyType,
                              @Nullable ZoneConfig zone, DamageSource damageSource, float amount) {
        this.anomaly = anomaly;
        this.target = target;
        this.anomalyType = anomalyType;
        this.zone = zone;
        this.damageSource = damageSource;
        this.amount = amount;
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

    public DamageSource getDamageSource() {
        return damageSource;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }
}