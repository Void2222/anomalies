package net.void_.anomalies.anomaly.data;

import java.util.List;

public record AnomalyDefinition(
        AnomalyShape shape,
        SizeConfig size,
        List<ParticleConfig> particles,
        SoundConfig sound,
        TriggerConfig trigger,
        List<ZoneConfig> zones,          // 🌟 Все слои аномалии (от центра к периферии)
        PhysicsConfig physics,           // Общие импульсы (если нужны, типа impulseY)
        Boolean ignoreOtherAnomalies
) {
    public AnomalyShape getShapeOrDefault() {
        return shape != null ? shape : AnomalyShape.CYLINDER;
    }
}