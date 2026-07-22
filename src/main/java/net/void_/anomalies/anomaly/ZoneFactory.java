package net.void_.anomalies.anomaly;

import net.minecraft.world.level.Level;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.components.*;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.setup.EntityInit;

public class ZoneFactory {

    public static void applyComponents(AnomalyEntity anomaly, String type) {
        // Каждая аномалия всегда выравнивается по сетке
        anomaly.addComponent(new SnapToGridComponent());

        // Получаем определение из нашего JSON-реестра
        AnomalyDefinition definition = AnomalyReloadListener.get(type);
        if (definition == null) return;

        // 1. Добавляем партиклы
        if (definition.particles() != null) {
            definition.particles().forEach(pConfig -> anomaly.addComponent(pConfig.toComponent()));
        }

        // 2. Добавляем звук
        if (definition.sound() != null) {
            anomaly.addComponent(definition.sound().toComponent());
        }

        // 3. Добавляем триггер и поведение (урон, импульс, поджог)
        if (definition.trigger() != null && definition.behavior() != null) {
            var b = definition.behavior();
            var t = definition.trigger();

            // Создаем компоненты эффектов, если они нужны
            DamageComponent damageComp = b.damage() > 0 ? new DamageComponent(b.damage(), anomaly.level().damageSources().inFire()) : null;
            ImpulseComponent impulseComp = (b.impulseX() != 0 || b.impulseY() != 0 || b.impulseZ() != 0)
                    ? new ImpulseComponent(b.impulseX(), b.impulseY(), b.impulseZ(), b.pullToCenter())
                    : null;

            anomaly.addComponent(new TriggerComponent(t.expandRadius(), t.interval(), (anom, target) -> {
                // Игнорируем другие аномалии, если в JSON включен флаг
                if (b.ignoreOtherAnomalies() && target instanceof AnomalyEntity) return;

                if (b.fireSeconds() > 0) {
                    target.setSecondsOnFire(b.fireSeconds());
                }
                if (damageComp != null) {
                    damageComp.inflictDamage(target);
                }
                if (impulseComp != null) {
                    impulseComp.applyImpulse(anom, target);
                }
            }));
        }
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