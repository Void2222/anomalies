package net.void_.anomalies.anomaly.data;

import java.util.List;

public record AnomalyDefinition(
        SizeConfig size,
        List<ParticleConfig> particles,
        SoundConfig sound,
        TriggerConfig trigger,
        DamageConfig damage,       // 🌟 Вынесли урон в отдельный блок
        PhysicsConfig physics,     // 🌟 Вынесли физику в отдельный блок
        Boolean ignoreOtherAnomalies // Или оставим верхнеуровневым правилом
) {}