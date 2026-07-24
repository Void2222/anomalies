package net.void_.anomalies.anomaly;

import net.minecraft.world.level.Level;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.data.DamageConfig;
import net.void_.anomalies.anomaly.data.PhysicsConfig;
import net.void_.anomalies.anomaly.data.TriggerConfig;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.components.*;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.setup.EntityInit;

public class ZoneFactory {

    public static void applyComponents(AnomalyEntity anomaly, String type) {
        anomaly.addComponent(new SnapToGridComponent());

        AnomalyDefinition definition = AnomalyReloadListener.get(type);
        if (definition == null) return;

        if (definition.size() != null) {
            anomaly.setAnomalyDimensions(definition.size().width(), definition.size().height());
        }

        if (definition.particles() != null) {
            definition.particles().forEach(pConfig -> anomaly.addComponent(pConfig.toComponent()));
        }

        if (definition.sound() != null) {
            anomaly.addComponent(definition.sound().toComponent());
        }

        if (definition.trigger() != null) {
            TriggerConfig t = definition.trigger();
            DamageConfig d = definition.damage();
            PhysicsConfig p = definition.physics();
            boolean ignoreAnomalies = definition.ignoreOtherAnomalies();

            // 🌟 Делаем переменные effectively final, вычисляя их сразу
            final DamageComponent damageComp;
            if (d != null && d.amount() != null && !d.amount().isZero()) {
                String dType = d.damageType() != null ? d.damageType().toLowerCase() : "generic";
                net.minecraft.world.damagesource.DamageSource damageSource = switch (dType) {
                    case "fire" -> anomaly.level().damageSources().inFire();
                    case "lightning" -> anomaly.level().damageSources().lightningBolt();
                    case "magic" -> anomaly.level().damageSources().magic();
                    default -> anomaly.level().damageSources().generic();
                };
                damageComp = new DamageComponent(d.amount(), damageSource);
            } else {
                damageComp = null;
            }

            final ImpulseComponent impulseComp;
            if (p != null && (p.impulseX() != 0 || p.impulseY() != 0 || p.impulseZ() != 0)) {
                impulseComp = new ImpulseComponent(p.impulseX(), p.impulseY(), p.impulseZ(), p.pullToCenter());
            } else {
                impulseComp = null;
            }

            final int fireSecs = (d != null) ? d.fireSeconds() : 0;

            anomaly.addComponent(new TriggerComponent(t.expandRadius(), t.interval(), (anom, target) -> {
                if (ignoreAnomalies && target instanceof AnomalyEntity) return;

                boolean isItem = target instanceof net.minecraft.world.entity.item.ItemEntity;

                if (isItem) {
                    // 🌟 1. ПРЕДМЕТЫ: создаем и отправляем событие взаимодействия с предметом
                    net.minecraft.world.entity.item.ItemEntity itemEntity = (net.minecraft.world.entity.item.ItemEntity) target;
                    net.void_.anomalies.api.event.AnomalyItemInteractEvent itemEvent =
                            new net.void_.anomalies.api.event.AnomalyItemInteractEvent(anom, itemEntity, anom.getAnomalyType());

                    // Если другой мод перехватил предмет и отменил событие — прерываемся
                    if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(itemEvent)) {
                        return;
                    }
                } else {
                    // 🌟 2. ИГРОКИ И МОБЫ: отправляем стандартное событие триггера
                    net.void_.anomalies.api.event.AnomalyTriggerEvent event =
                            new net.void_.anomalies.api.event.AnomalyTriggerEvent(anom, target, anom.getAnomalyType());

                    if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) {
                        return;
                    }

                    // Поджог и урон применяем только к живым существам
                    if (fireSecs > 0) {
                        target.setSecondsOnFire(fireSecs);
                    }
                    if (damageComp != null) {
                        damageComp.inflictDamage(target);
                    }
                }

                // 🌟 3. ФИЗИКА (Импульсы): работает и для игроков, и для предметов!
                // Предметы теперь тоже будут затягиваться в центр или подбрасываться.
                if (impulseComp != null) {
                    impulseComp.applyImpulse(anom, target);
                }
            }));
        }
    }

    public static AnomalyEntity create(Level level, double x, double y, double z, String type) {
        if (!AnomalyReloadListener.exists(type)) return null;

        AnomalyEntity anomaly = EntityInit.ANOMALY.get().create(level);
        if (anomaly != null) {
            anomaly.setPos(x, y, z);
            anomaly.setAnomalyType(type.toLowerCase());
        }
        return anomaly;
    }
}