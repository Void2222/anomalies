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

    private final MinMaxRange damageRange;
    private final DamageSource customDamageSource;

    public DamageComponent(MinMaxRange damageRange, DamageSource customDamageSource) {
        this.damageRange = damageRange;
        this.customDamageSource = customDamageSource;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {}
    @Override
    public void clientTick(AnomalyEntity anomaly) {}

    /**
     * Базовый вызов нанесения урона
     */
    public void inflictDamage(Entity target) {
        inflictDamage(null, target, null, damageRange);
    }

    /**
     * Расширенный вызов с передачей аномалии и активной зоны
     */
    public void inflictDamage(@Nullable AnomalyEntity anomaly, Entity target, @Nullable ZoneConfig zone, @Nullable MinMaxRange specificRange) {
        if (target == null || !target.isAlive()) return;

        MinMaxRange rangeToUse = (specificRange != null) ? specificRange : damageRange;
        float calculatedDamage = (float) rangeToUse.getDouble();

        String anomalyType = (anomaly != null) ? anomaly.getAnomalyType() : "unknown";

        // 🌟 Создаем и постим событие нанесения урона
        AnomalyDamageEvent damageEvent = new AnomalyDamageEvent(
                anomaly,
                target,
                anomalyType,
                zone,
                customDamageSource,
                calculatedDamage
        );

        // Если сторонний мод (например, абсолютный щит) отменил событие — урон не наносится
        if (MinecraftForge.EVENT_BUS.post(damageEvent)) {
            return;
        }

        // Берем итоговый урон (который сторонний мод мог урезать костюмом/бронёй)
        float finalDamage = damageEvent.getAmount();

        if (finalDamage > 0) {
            target.hurt(customDamageSource, finalDamage);
        }
    }
}