package net.void_.anomalies.anomaly.data;

import java.util.List;

public record AnomalyDefinition(
        SizeConfig size,
        List<ParticleConfig> particles,
        SoundConfig sound,
        TriggerConfig trigger,
        List<ZoneConfig> zones,          // 🌟 Все слои аномалии (от центра к периферии)
        PhysicsConfig physics,           // Общие импульсы (если нужны, типа impulseY)
        Boolean ignoreOtherAnomalies
) {}