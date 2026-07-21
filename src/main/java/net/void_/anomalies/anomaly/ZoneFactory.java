package net.void_.anomalies.anomaly;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.void_.anomalies.component.DamageComponent;
import net.void_.anomalies.component.ImpulseComponent;
import net.void_.anomalies.component.ParticleComponent;
import net.void_.anomalies.component.SoundComponent;
import net.void_.anomalies.component.TriggerComponent;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.setup.EntityInit;

public class ZoneFactory {

    /**
     * Создает аномалию «Жарка»:
     * - Спавнит пламя
     * - Гудит огнем
     * - Поджигает и наносит урон при попадании в зону
     */
    public static AnomalyEntity createZharka(Level level, double x, double y, double z) {
        AnomalyEntity anomaly = new AnomalyEntity(EntityInit.ANOMALY.get(), level);
        anomaly.setPos(x, y, z);

        // Столб огня: радиус 0.8 блоков, высота 2.0 блока, спавн каждые 2 тика
        anomaly.addComponent(new ParticleComponent(
                ParticleTypes.FLAME,
                2,
                ParticleComponent.Shape.CYLINDER,
                0.8D, // радиус
                2.0D, // высота столба
                4     // количество частиц
        ));

        // Добавляем дымок внутрь столба для густоты
        anomaly.addComponent(new ParticleComponent(
                ParticleTypes.LARGE_SMOKE,
                5,
                ParticleComponent.Shape.CYLINDER,
                0.1D,
                2D,
                15
        ));

        anomaly.addComponent(new SoundComponent(SoundEvents.FIRE_AMBIENT, 40, SoundSource.BLOCKS));

        DamageComponent damageComp = new DamageComponent(4.0F, level.damageSources().inFire());
        anomaly.addComponent(new TriggerComponent(0.3D, (anom, target) -> {
            target.setSecondsOnFire(6);
            damageComp.inflictDamage(target);
        }));

        anomaly.setAnomalyType("zharka");
        return anomaly;
    }

    /**
     * Создает аномалию «Трамплин»:
     * - Эффект воздуха/взрыва
     * - Резко подбрасывает вверх и наносит урон от падения/разрыва
     */
    public static AnomalyEntity createTramplin(Level level, double x, double y, double z) {
        AnomalyEntity anomaly = new AnomalyEntity(EntityInit.ANOMALY.get(), level);
        anomaly.setPos(x, y, z);

        // Лужа дыма на земле: радиус 1.2 блока, высота 0.2, густой дым
        anomaly.addComponent(new ParticleComponent(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                3,
                ParticleComponent.Shape.DISC,
                0.4D, // радиус лужи
                0.2D, // почти плоская
                5     // количество
        ));

        anomaly.addComponent(new SoundComponent(SoundEvents.ENDER_EYE_LAUNCH, 60, SoundSource.BLOCKS));

        ImpulseComponent impulseComp = new ImpulseComponent(0.0D, 1.0D, 0.0D, false);
        DamageComponent damageComp = new DamageComponent(6.0F, level.damageSources().generic());

        anomaly.addComponent(new TriggerComponent(0.5D, (anom, target) -> {
            impulseComp.applyImpulse(anom, target);
            damageComp.inflictDamage(target);
        }));

        anomaly.setAnomalyType("tramplin");
        return anomaly;
    }
}