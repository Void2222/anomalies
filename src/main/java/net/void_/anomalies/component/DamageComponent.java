package net.void_.anomalies.component;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class DamageComponent implements IAnomalyComponent {

    private final float damageAmount;
    private final DamageSource customDamageSource; // Если хотим кастомный источник (например, от огня или электричества)

    /**
     * @param damageAmount       Количество урона за одно срабатывание (в полусердцах)
     * @param customDamageSource Источник урона (можно брать из level.damageSources())
     */
    public DamageComponent(float damageAmount, DamageSource customDamageSource) {
        this.damageAmount = damageAmount;
        this.customDamageSource = customDamageSource;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        // Сам по себе DamageComponent может просто хранить логику,
        // но наносить урон он будет через триггер, когда сущность попадает в зону.
        // Либо можно сделать так, чтобы он вызывался прямо из TriggerComponent.
    }

    /**
     * Метод, который мы будем вызывать внутри TriggerComponent,
     * когда сущность наступает на аномалию.
     */
    public void inflictDamage(Entity target) {
        if (target != null && target.isAlive()) {
            target.hurt(customDamageSource, damageAmount);
        }
    }
}