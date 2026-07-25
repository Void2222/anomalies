package net.void_.anomalies.anomaly;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.void_.anomalies.anomaly.data.*;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.components.*;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.setup.EntityInit;

import java.util.List;

public class ZoneFactory {

    public static void applyComponents(AnomalyEntity anomaly, String type) {
        anomaly.addComponent(new SnapToGridComponent());

        AnomalyDefinition definition = AnomalyReloadListener.get(type);
        if (definition == null) return;

        CompoundTag overrides = anomaly.getCustomOverrides();

        // 🌟 1. РАЗМЕРЫ (SIZE)
        if (definition.size() != null) {
            float width = overrides.contains("width") ? (float) overrides.getDouble("width") : definition.size().width();
            float height = overrides.contains("height") ? (float) overrides.getDouble("height") : definition.size().height();
            anomaly.setAnomalyDimensions(width, height);
        }

        // 🌟 2. ЧАСТИЦЫ (PARTICLES)
        if (definition.particles() != null) {
            for (ParticleConfig pConfig : definition.particles()) {
                double radius = overrides.contains("particleRadius") ? overrides.getDouble("particleRadius") : pConfig.radius();
                double height = overrides.contains("particleHeight") ? overrides.getDouble("particleHeight") : pConfig.height();

                // Интервал
                double pIntMin = overrides.contains("particleIntervalMin") ? overrides.getDouble("particleIntervalMin") :
                        (pConfig.interval() != null ? pConfig.interval().getDouble() : 1);
                double pIntMax = overrides.contains("particleIntervalMax") ? overrides.getDouble("particleIntervalMax") : pIntMin;
                MinMaxRange interval = new MinMaxRange(pIntMin, pIntMax);

                // Количество
                double pCntMin = overrides.contains("particleCountMin") ? overrides.getDouble("particleCountMin") :
                        (pConfig.count() != null ? pConfig.count().getDouble() : 1);
                double pCntMax = overrides.contains("particleCountMax") ? overrides.getDouble("particleCountMax") : pCntMin;
                MinMaxRange count = new MinMaxRange(pCntMin, pCntMax);

                ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(pConfig.type()));
                ParticleOptions options = (particleType instanceof ParticleOptions opt) ? opt : ParticleTypes.FLAME;

                ParticleComponent.Shape parsedShape;
                try {
                    parsedShape = ParticleComponent.Shape.valueOf(pConfig.shape().toUpperCase());
                } catch (IllegalArgumentException e) {
                    parsedShape = ParticleComponent.Shape.SPHERE;
                }

                anomaly.addComponent(new ParticleComponent(options, interval, parsedShape, radius, height, count));
            }
        }

        // 🌟 3. ЗВУК (SOUND)
        if (definition.sound() != null) {
            SoundConfig s = definition.sound();
            float volume = overrides.contains("soundVolume") ? (float) overrides.getDouble("soundVolume") : s.volume();
            float pitch = overrides.contains("soundPitch") ? (float) overrides.getDouble("soundPitch") : s.pitch();

            double sIntMin = overrides.contains("soundIntervalMin") ? overrides.getDouble("soundIntervalMin") :
                    (s.interval() != null ? s.interval().getDouble() : 20);
            double sIntMax = overrides.contains("soundIntervalMax") ? overrides.getDouble("soundIntervalMax") : sIntMin;
            MinMaxRange soundInterval = new MinMaxRange(sIntMin, sIntMax);

            ResourceLocation loc = ResourceLocation.parse(s.event());
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(loc);
            if (soundEvent == null) soundEvent = SoundEvent.createVariableRangeEvent(loc);

            SoundSource soundSource;
            try {
                soundSource = SoundSource.valueOf(s.source().toUpperCase());
            } catch (IllegalArgumentException e) {
                soundSource = SoundSource.BLOCKS;
            }

            anomaly.addComponent(new SoundComponent(soundEvent, soundInterval, soundSource, volume, pitch));
        }

        // 🌟 4. ТРИГГЕР, ЗОНЫ И ФИЗИКА
        if (definition.trigger() != null) {
            TriggerConfig t = definition.trigger();
            List<ZoneConfig> zones = definition.zones();
            PhysicsConfig generalPhysics = definition.physics();
            boolean ignoreAnomalies = definition.ignoreOtherAnomalies();

            double expandRadius = overrides.contains("expandRadius") ? overrides.getDouble("expandRadius") : t.expandRadius();

            // Интервал триггера
            double tIntMin = overrides.contains("triggerInterval") ? overrides.getDouble("triggerInterval") :
                    (t.interval() != null ? t.interval().getDouble() : 5);
            MinMaxRange triggerInterval = new MinMaxRange(tIntMin, tIntMin);

            // Общие параметры физики
            double impX = generalPhysics != null ? generalPhysics.impulseX() : 0;
            double impY = generalPhysics != null ? generalPhysics.impulseY() : 0;
            double impZ = generalPhysics != null ? generalPhysics.impulseZ() : 0;
            boolean pullToCenter = generalPhysics != null && generalPhysics.pullToCenter();

            final ImpulseComponent impulseComp;
            if (zones != null && !zones.isEmpty()) {
                impulseComp = new ImpulseComponent(impX, impY, impZ, pullToCenter, zones);
            } else {
                impulseComp = null;
            }

            anomaly.addComponent(new TriggerComponent(expandRadius, triggerInterval, (anom, target) -> {
                if (ignoreAnomalies && target instanceof AnomalyEntity) return;

                // Применяем физику импульса
                if (impulseComp != null) {
                    impulseComp.applyImpulse(anom, target);
                }

                // Определяем активную зону по расстоянию
                ZoneConfig activeZone = impulseComp != null ? impulseComp.getActiveZone(anom, target) : null;
                if (activeZone == null) return; // Вне зон

                boolean isItem = target instanceof net.minecraft.world.entity.item.ItemEntity;

                if (isItem) {
                    net.void_.anomalies.api.event.AnomalyItemInteractEvent itemEvent =
                            new net.void_.anomalies.api.event.AnomalyItemInteractEvent(anom, (net.minecraft.world.entity.item.ItemEntity) target, anom.getAnomalyType());
                    if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(itemEvent)) return;
                } else {
                    net.void_.anomalies.api.event.AnomalyTriggerEvent event =
                            new net.void_.anomalies.api.event.AnomalyTriggerEvent(anom, target, anom.getAnomalyType());
                    if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) return;

                    // Наносим урон и поджог из параметров активной зоны
                    if (activeZone.damage() != null) {
                        DamageConfig dConfig = activeZone.damage();
                        if (dConfig.fireSeconds() > 0) {
                            target.setSecondsOnFire(dConfig.fireSeconds());
                        }

                        double dmgAmount = (dConfig.innerAmount() != null) ? dConfig.innerAmount().getDouble() : 0.0;
                        if (dmgAmount > 0) {
                            String dType = dConfig.damageType() != null ? dConfig.damageType().toLowerCase() : "generic";
                            var dmgComp = new DamageComponent(new MinMaxRange(dmgAmount, dmgAmount), getDamageSource(anom, dType));
                            dmgComp.inflictDamage(target);
                        }
                    }
                }
            }));
        }
    }

    private static net.minecraft.world.damagesource.DamageSource getDamageSource(AnomalyEntity anomaly, String dType) {
        return switch (dType) {
            case "fire" -> anomaly.level().damageSources().inFire();
            case "lightning" -> anomaly.level().damageSources().lightningBolt();
            case "magic" -> anomaly.level().damageSources().magic();
            default -> anomaly.level().damageSources().generic();
        };
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