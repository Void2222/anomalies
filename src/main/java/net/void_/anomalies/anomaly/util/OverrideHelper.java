package net.void_.anomalies.anomaly.util;

import net.minecraft.nbt.CompoundTag;
import net.void_.anomalies.anomaly.data.*;

import java.util.ArrayList;
import java.util.List;

public class OverrideHelper {

    public static float[] getDimensions(CompoundTag tag, SizeConfig defaultConfig) {
        float width = defaultConfig != null ? defaultConfig.width() : 1.0f;
        float height = defaultConfig != null ? defaultConfig.height() : 1.0f;

        if (tag.contains("width")) width = (float) tag.getDouble("width");
        if (tag.contains("height")) height = (float) tag.getDouble("height");

        return new float[]{width, height};
    }

    public static List<ZoneConfig> getZones(CompoundTag tag, List<ZoneConfig> defaultZones) {
        if (tag.contains("zones_count")) {
            List<ZoneConfig> zones = new ArrayList<>();
            int count = tag.getInt("zones_count");
            for (int i = 0; i < count; i++) {
                double radius = tag.getDouble("zone_" + i + "_radius");
                double dmgAmount = tag.getDouble("zone_" + i + "_damage");
                double pullForce = tag.getDouble("zone_" + i + "_pullForce");
                double spinForce = tag.getDouble("zone_" + i + "_spinForce");
                double impY = tag.getDouble("zone_" + i + "_impulseY");

                DamageConfig dConfig = new DamageConfig(
                        null,
                        new MinMaxRange(dmgAmount, dmgAmount),
                        0,
                        "generic"
                );
                PhysicsConfig pConfig = new PhysicsConfig(0, impY, 0, true, pullForce, spinForce);
                zones.add(new ZoneConfig(radius, dConfig, pConfig));
            }
            return zones;
        }
        return defaultZones != null ? defaultZones : List.of();
    }

    public static MinMaxRange getParticleInterval(CompoundTag tag, ParticleConfig pConfig) {
        double min = tag.contains("particleIntervalMin") ? tag.getDouble("particleIntervalMin") :
                (pConfig.interval() != null ? pConfig.interval().getDouble() : 1);
        double max = tag.contains("particleIntervalMax") ? tag.getDouble("particleIntervalMax") : min;
        return new MinMaxRange(min, max);
    }

    public static MinMaxRange getParticleCount(CompoundTag tag, ParticleConfig pConfig) {
        double min = tag.contains("particleCountMin") ? tag.getDouble("particleCountMin") :
                (pConfig.count() != null ? pConfig.count().getDouble() : 1);
        double max = tag.contains("particleCountMax") ? tag.getDouble("particleCountMax") : min;
        return new MinMaxRange(min, max);
    }

    public static MinMaxRange getSoundInterval(CompoundTag tag, SoundConfig s) {
        double min = tag.contains("soundIntervalMin") ? tag.getDouble("soundIntervalMin") :
                (s.interval() != null ? s.interval().getDouble() : 20);
        double max = tag.contains("soundIntervalMax") ? tag.getDouble("soundIntervalMax") : min;
        return new MinMaxRange(min, max);
    }

    public static MinMaxRange getTriggerInterval(CompoundTag tag, TriggerConfig t) {
        double min = tag.contains("triggerInterval") ? tag.getDouble("triggerInterval") :
                (t.interval() != null ? t.interval().getDouble() : 5);
        return new MinMaxRange(min, min);
    }
}