package net.void_.anomalies.anomaly;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.void_.anomalies.core.AnomalyEntity;

// Импорты компонентов:
import net.void_.anomalies.components.DamageComponent;
import net.void_.anomalies.components.ImpulseComponent;
import net.void_.anomalies.components.ParticleComponent;
import net.void_.anomalies.components.SnapToGridComponent; // Импорт нового компонента
import net.void_.anomalies.components.SoundComponent;
import net.void_.anomalies.components.TriggerComponent;
import net.void_.anomalies.setup.EntityInit;

public class ZoneFactory {

    public static void applyComponents(AnomalyEntity anomaly, String type) {
        // Каждое создание аномалии теперь автоматически выравнивает её по сетке блоков
        anomaly.addComponent(new SnapToGridComponent());

        switch (type.toLowerCase()) {
            case "zharka" -> setupZharka(anomaly);
            case "tramplin" -> setupTramplin(anomaly);
        }
    }

    private static void setupZharka(AnomalyEntity anomaly) {
        anomaly.addComponent(new ParticleComponent(ParticleTypes.FLAME, 2, ParticleComponent.Shape.CYLINDER, 0.8D, 2.0D, 4));
        anomaly.addComponent(new ParticleComponent(ParticleTypes.LARGE_SMOKE, 5, ParticleComponent.Shape.CYLINDER, 0.6D, 1.8D, 2));
        anomaly.addComponent(new SoundComponent(SoundEvents.FIRE_AMBIENT, 1200, SoundSource.BLOCKS));

        DamageComponent damageComp = new DamageComponent(4.0F, anomaly.level().damageSources().inFire());

        anomaly.addComponent(new TriggerComponent(0.3D, 10, (anom, target) -> {
            // Игнорируем другие аномалии, чтобы они не поджигали друг друга!
            if (target instanceof AnomalyEntity) return;

            target.setSecondsOnFire(6);
            damageComp.inflictDamage(target);
        }));
    }

    private static void setupTramplin(AnomalyEntity anomaly) {
        anomaly.addComponent(new ParticleComponent(ParticleTypes.CAMPFIRE_COSY_SMOKE, 3, ParticleComponent.Shape.DISC, 1.2D, 0.2D, 3));
        anomaly.addComponent(new SoundComponent(SoundEvents.AMETHYST_BLOCK_BREAK, 1200, SoundSource.BLOCKS));

        ImpulseComponent impulseComp = new ImpulseComponent(0.0D, 1.2D, 0.0D, false);
        DamageComponent damageComp = new DamageComponent(6.0F, anomaly.level().damageSources().generic());

        anomaly.addComponent(new TriggerComponent(0.5D, 5, (anom, target) -> {
            // Трамплин может подбрасывать и другие аномалии или мобов, но если хочешь исключить — тоже можно добавить `if (target instanceof AnomalyEntity) return;`
            impulseComp.applyImpulse(anom, target);
            damageComp.inflictDamage(target);
        }));
    }

    public static AnomalyEntity createZharka(Level level, double x, double y, double z) {
        AnomalyEntity anomaly = EntityInit.ANOMALY.get().create(level);
        if (anomaly != null) {
            anomaly.setPos(x, y, z);
            anomaly.setAnomalyType("zharka");
        }
        return anomaly;
    }

    public static AnomalyEntity createTramplin(Level level, double x, double y, double z) {
        AnomalyEntity anomaly = EntityInit.ANOMALY.get().create(level);
        if (anomaly != null) {
            anomaly.setPos(x, y, z);
            anomaly.setAnomalyType("tramplin");
        }
        return anomaly;
    }
}