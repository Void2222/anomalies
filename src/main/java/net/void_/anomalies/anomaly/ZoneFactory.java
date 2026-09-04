package net.void_.anomalies.anomaly;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.void_.anomalies.api.event.AnomalyItemInteractEvent;
import net.void_.anomalies.api.event.AnomalyTriggerEvent;
import net.void_.anomalies.anomaly.data.*;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.anomaly.util.OverrideHelper;
import net.void_.anomalies.anomaly.util.ZoneUtils;
import net.void_.anomalies.components.*;
import net.void_.anomalies.config.AnomalyIgnoreManager;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.setup.EntityInit;

import java.util.List;

public class ZoneFactory {

    public static void applyComponents(AnomalyEntity anomaly, String type) {
        AnomalyDefinition definition = AnomalyReloadListener.get(type);
        if (definition == null) return;

        anomaly.addComponent(new SnapToGridComponent());

        CompoundTag overrides = anomaly.getCustomOverrides();

        setupDimensions(anomaly, definition.size(), overrides);
        setupParticles(anomaly, definition.particles(), overrides);
        setupSound(anomaly, definition.sound(), overrides);
        setupTriggerAndPhysics(anomaly, definition, overrides);
    }

    private static void setupDimensions(AnomalyEntity anomaly, SizeConfig sizeConfig, CompoundTag overrides) {
        if (sizeConfig == null) return;
        float[] dims = OverrideHelper.getDimensions(overrides, sizeConfig);
        anomaly.setAnomalyDimensions(dims[0], dims[1]);
    }

    private static void setupParticles(AnomalyEntity anomaly, List<ParticleConfig> particleConfigs, CompoundTag overrides) {
        if (particleConfigs == null || particleConfigs.isEmpty()) return;

        for (ParticleConfig pConfig : particleConfigs) {
            double radius = overrides.contains("particleRadius") ? overrides.getDouble("particleRadius") : pConfig.radius();
            double height = overrides.contains("particleHeight") ? overrides.getDouble("particleHeight") : pConfig.height();

            MinMaxRange interval = OverrideHelper.getParticleInterval(overrides, pConfig);
            MinMaxRange count = OverrideHelper.getParticleCount(overrides, pConfig);

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

    private static void setupSound(AnomalyEntity anomaly, SoundConfig soundConfig, CompoundTag overrides) {
        if (soundConfig == null) return;

        float volume = overrides.contains("soundVolume") ? (float) overrides.getDouble("soundVolume") : soundConfig.volume();
        float pitch = overrides.contains("soundPitch") ? (float) overrides.getDouble("soundPitch") : soundConfig.pitch();
        MinMaxRange soundInterval = OverrideHelper.getSoundInterval(overrides, soundConfig);

        ResourceLocation loc = ResourceLocation.parse(soundConfig.event());
        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(loc);
        if (soundEvent == null) soundEvent = SoundEvent.createVariableRangeEvent(loc);

        SoundSource soundSource;
        try {
            soundSource = SoundSource.valueOf(soundConfig.source().toUpperCase());
        } catch (IllegalArgumentException e) {
            soundSource = SoundSource.BLOCKS;
        }

        anomaly.addComponent(new SoundComponent(soundEvent, soundInterval, soundSource, volume, pitch));
    }

    private static void setupTriggerAndPhysics(AnomalyEntity anomaly, AnomalyDefinition definition, CompoundTag overrides) {
        TriggerConfig t = definition.trigger();
        if (t == null) return;

        PhysicsConfig generalPhysics = definition.physics();
        boolean ignoreAnomalies = Boolean.TRUE.equals(definition.ignoreOtherAnomalies());

        double expandRadius = overrides.contains("expandRadius") ? overrides.getDouble("expandRadius") : t.expandRadius();
        MinMaxRange triggerInterval = OverrideHelper.getTriggerInterval(overrides, t);

        List<ZoneConfig> zones = OverrideHelper.getZones(overrides, definition.zones());

        DamageSource defaultDamageSource = anomaly.level().damageSources().generic();
        DamageComponent damageComp = new DamageComponent(new MinMaxRange(0, 0), defaultDamageSource);
        anomaly.addComponent(damageComp);

        final ImpulseComponent impulseComp;
        if (!zones.isEmpty()) {
            double impX = generalPhysics != null ? generalPhysics.impulseX() : 0;
            double impY = generalPhysics != null ? generalPhysics.impulseY() : 0;
            double impZ = generalPhysics != null ? generalPhysics.impulseZ() : 0;
            boolean pullToCenter = generalPhysics != null && generalPhysics.pullToCenter();

            impulseComp = new ImpulseComponent(impX, impY, impZ, pullToCenter, generalPhysics, zones);
            anomaly.addComponent(impulseComp);
        } else {
            impulseComp = null;
        }

        anomaly.addComponent(new TriggerComponent(expandRadius, triggerInterval,
                (anom, target) -> handleTriggerTarget(anom, target, impulseComp, damageComp, zones, ignoreAnomalies)));
    }

    private static void handleTriggerTarget(AnomalyEntity anomaly, Entity target, ImpulseComponent impulseComp, DamageComponent damageComp, List<ZoneConfig> zones, boolean ignoreAnomalies) {
        if (ignoreAnomalies && target instanceof AnomalyEntity) return;

        // 🛑 Проверка списка игнорирования игроков
        if (target instanceof Player player && AnomalyIgnoreManager.isIgnored(player)) return;

        if (impulseComp != null) {
            impulseComp.applyImpulse(anomaly, target);
        }

        ZoneConfig activeZone = ZoneUtils.getActiveZone(zones, anomaly, target);
        if (activeZone == null) return;

        if (target instanceof ItemEntity itemEntity) {
            AnomalyItemInteractEvent itemEvent = new AnomalyItemInteractEvent(anomaly, itemEntity, anomaly.getAnomalyType());
            MinecraftForge.EVENT_BUS.post(itemEvent);
        } else {
            AnomalyTriggerEvent event = new AnomalyTriggerEvent(anomaly, target, anomaly.getAnomalyType());
            if (MinecraftForge.EVENT_BUS.post(event)) return;

            if (activeZone.damage() != null) {
                DamageConfig dConfig = activeZone.damage();

                if (dConfig.fireSeconds() > 0) {
                    target.setSecondsOnFire(dConfig.fireSeconds());
                }

                MinMaxRange damageRange = dConfig.innerAmount();
                if (damageRange != null && damageRange.getDouble() > 0) {
                    String dType = dConfig.damageType() != null ? dConfig.damageType().toLowerCase() : "generic";
                    DamageSource zoneDamageSource = getDamageSource(anomaly, dType);

                    damageComp.inflictDamage(anomaly, target, activeZone, damageRange, zoneDamageSource);
                }
            }
        }
    }

    private static DamageSource getDamageSource(AnomalyEntity anomaly, String dType) {
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