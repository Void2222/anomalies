package net.void_.anomalies.anomaly;

import net.minecraft.world.level.Level;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
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

        if (definition.trigger() != null && definition.behavior() != null) {
            var b = definition.behavior();
            var t = definition.trigger();

            DamageComponent damageComp = !b.damage().isZero()
                    ? new DamageComponent(b.damage(), anomaly.level().damageSources().inFire())
                    : null;
            ImpulseComponent impulseComp = (b.impulseX() != 0 || b.impulseY() != 0 || b.impulseZ() != 0)
                    ? new ImpulseComponent(b.impulseX(), b.impulseY(), b.impulseZ(), b.pullToCenter())
                    : null;

            anomaly.addComponent(new TriggerComponent(t.expandRadius(), t.interval(), (anom, target) -> {
                if (b.ignoreOtherAnomalies() && target instanceof AnomalyEntity) return;

                net.void_.anomalies.api.event.AnomalyTriggerEvent event =
                        new net.void_.anomalies.api.event.AnomalyTriggerEvent(anom, target, anom.getAnomalyType());

                if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) {
                    return;
                }

                boolean isItem = target instanceof net.minecraft.world.entity.item.ItemEntity;

                if (!isItem && b.fireSeconds() > 0) {
                    target.setSecondsOnFire(b.fireSeconds());
                }
                if (!isItem && damageComp != null) {
                    damageComp.inflictDamage(target);
                }
                if (impulseComp != null) {
                    impulseComp.applyImpulse(anom, target);
                }
            }));
        }
    }

    // 🌟 Универсальный метод создания аномалий любого типа
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