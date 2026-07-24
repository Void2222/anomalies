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

        // 🌟 4. ТРИГГЕР, УРОН И ФИЗИКА
        if (definition.trigger() != null) {
            TriggerConfig t = definition.trigger();
            DamageConfig d = definition.damage();
            PhysicsConfig p = definition.physics();
            boolean ignoreAnomalies = definition.ignoreOtherAnomalies();

            double expandRadius = overrides.contains("expandRadius") ? overrides.getDouble("expandRadius") : t.expandRadius();

            // Интервал триггера
            double tIntMin = overrides.contains("triggerInterval") ? overrides.getDouble("triggerInterval") :
                    (t.interval() != null ? t.interval().getDouble() : 5);
            MinMaxRange triggerInterval = new MinMaxRange(tIntMin, tIntMin);

            // Урон
            final DamageComponent damageComp;
            if (overrides.contains("damage")) {
                double customDmg = overrides.getDouble("damage");
                String dType = (d != null && d.damageType() != null) ? d.damageType().toLowerCase() : "generic";
                damageComp = new DamageComponent(new MinMaxRange(customDmg, customDmg), getDamageSource(anomaly, dType));
            } else if (d != null && d.amount() != null && !d.amount().isZero()) {
                String dType = d.damageType() != null ? d.damageType().toLowerCase() : "generic";
                damageComp = new DamageComponent(d.amount(), getDamageSource(anomaly, dType));
            } else {
                damageComp = null;
            }

            // Физика
            double impX = overrides.contains("impulseX") ? overrides.getDouble("impulseX") : (p != null ? p.impulseX() : 0);
            double impY = overrides.contains("impulseY") ? overrides.getDouble("impulseY") : (p != null ? p.impulseY() : 0);
            double impZ = overrides.contains("impulseZ") ? overrides.getDouble("impulseZ") : (p != null ? p.impulseZ() : 0);

            boolean pullToCenter = overrides.contains("pullToCenter") ?
                    overrides.getBoolean("pullToCenter") : (p != null && p.pullToCenter());

            final ImpulseComponent impulseComp;
            if (impX != 0 || impY != 0 || impZ != 0) {
                impulseComp = new ImpulseComponent(impX, impY, impZ, pullToCenter);
            } else {
                impulseComp = null;
            }

            // Поджог
            final int fireSecs = overrides.contains("fireSeconds") ?
                    overrides.getInt("fireSeconds") : ((d != null) ? d.fireSeconds() : 0);

            anomaly.addComponent(new TriggerComponent(expandRadius, triggerInterval, (anom, target) -> {
                if (ignoreAnomalies && target instanceof AnomalyEntity) return;

                boolean isItem = target instanceof net.minecraft.world.entity.item.ItemEntity;

                if (isItem) {
                    net.minecraft.world.entity.item.ItemEntity itemEntity = (net.minecraft.world.entity.item.ItemEntity) target;
                    net.void_.anomalies.api.event.AnomalyItemInteractEvent itemEvent =
                            new net.void_.anomalies.api.event.AnomalyItemInteractEvent(anom, itemEntity, anom.getAnomalyType());

                    if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(itemEvent)) return;
                } else {
                    net.void_.anomalies.api.event.AnomalyTriggerEvent event =
                            new net.void_.anomalies.api.event.AnomalyTriggerEvent(anom, target, anom.getAnomalyType());

                    if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) return;

                    if (fireSecs > 0) target.setSecondsOnFire(fireSecs);
                    if (damageComp != null) damageComp.inflictDamage(target);
                }

                if (impulseComp != null) impulseComp.applyImpulse(anom, target);
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