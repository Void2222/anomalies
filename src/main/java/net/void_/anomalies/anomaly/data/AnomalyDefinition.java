package net.void_.anomalies.anomaly.data;

import java.util.List;

public record AnomalyDefinition(
        List<ParticleConfig> particles,
        SoundConfig sound,
        TriggerConfig trigger,
        BehaviorConfig behavior
) {}

