package net.void_.anomalies.components;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.void_.anomalies.api.event.AnomalyDamageEvent;
import net.void_.anomalies.anomaly.data.MinMaxRange;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

import javax.annotation.Nullable;

public class DamageComponent implements IAnomalyComponent {

    private final MinMaxRange defaultDamageRange;
    private final DamageSource defaultDamageSource;

    public DamageComponent(MinMaxRange defaultDamageRange, DamageSource defaultDamageSource) {
        this.defaultDamageRange = defaultDamageRange;
        this.defaultDamageSource = defaultDamageSource;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {}

    @Override
    public void clientTick(AnomalyEntity anomaly) {}

    /**
     * Вызов нанесения урона по сущности с учетом конкретной зоны
     */
    public void inflictDamage(@Nullable AnomalyEntity anomaly, Entity target, @Nullable ZoneConfig zone, @Nullable MinMaxRange specificRange, @Nullable DamageSource overrideSource) {
        if (target == null || !target.isAlive()) return;

        MinMaxRange rangeToUse = (specificRange != null) ? specificRange : defaultDamageRange;
        if (rangeToUse == null) return;

        float calculatedDamage = (float) rangeToUse.getDouble();
        if (calculatedDamage <= 0) return;

        DamageSource sourceToUse = (overrideSource != null) ? overrideSource : defaultDamageSource;
        String anomalyType = (anomaly != null) ? anomaly.getAnomalyType() : "unknown";

        AnomalyDamageEvent damageEvent = new AnomalyDamageEvent(
                anomaly,
                target,
                anomalyType,
                zone,
                sourceToUse,
                calculatedDamage
        );

        if (MinecraftForge.EVENT_BUS.post(damageEvent)) {
            return;
        }

        float finalDamage = damageEvent.getAmount();
        if (finalDamage > 0) {
            target.hurt(sourceToUse, finalDamage);
        }
    }
}