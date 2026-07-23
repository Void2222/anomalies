package net.void_.anomalies.components;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.void_.anomalies.anomaly.data.MinMaxRange;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class DamageComponent implements IAnomalyComponent {

    private final MinMaxRange damageRange;
    private final DamageSource customDamageSource;

    public DamageComponent(MinMaxRange damageRange, DamageSource customDamageSource) {
        this.damageRange = damageRange;
        this.customDamageSource = customDamageSource;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {}

    public void inflictDamage(Entity target) {
        if (target != null && target.isAlive()) {
            float finalDamage = (float) damageRange.getDouble(); // Каждый раз случайный урон из диапазона!
            target.hurt(customDamageSource, finalDamage);
        }
    }
}