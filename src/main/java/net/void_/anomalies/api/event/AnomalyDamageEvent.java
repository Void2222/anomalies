package net.void_.anomalies.api.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.AnomalyEntity;

import javax.annotation.Nullable;

@Cancelable // Позволяет полностью отменить урон (например, если сработало абсолютное поле защиты)
public class AnomalyDamageEvent extends Event {

    private final AnomalyEntity anomaly;
    private final Entity target;
    private final String anomalyType;
    @Nullable
    private final ZoneConfig zone;
    private final DamageSource damageSource;
    private float amount; // Изменяемое количество урона

    public AnomalyDamageEvent(AnomalyEntity anomaly, Entity target, String anomalyType,
                              @Nullable ZoneConfig zone, DamageSource damageSource, float amount) {
        this.anomaly = anomaly;
        this.target = target;
        this.anomalyType = anomalyType;
        this.zone = zone;
        this.damageSource = damageSource;
        this.amount = amount;
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

    /**
     * Позволяет сторонним модам на броню/костюмы/артефакты модифицировать урон.
     * Например, уменьшить его на 80% или установить в 0.
     */
    public void setAmount(float amount) {
        this.amount = Math.max(0, amount); // Не позволяем делать урон отрицательным
    }
}