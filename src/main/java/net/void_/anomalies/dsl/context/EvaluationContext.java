package net.void_.anomalies.dsl.context;

import net.void_.anomalies.core.AnomalyEntity;
import java.util.Collections;
import java.util.Set;

public record EvaluationContext(
        AnomalyEntity anomaly,
        int ticksInState,
        Set<ZoneEvent> activeZoneEvents
) {
    public enum ZoneEventType {
        ENTERED, EXITED, IN_ZONE
    }

    public record ZoneEvent(ZoneEventType type, String zoneName) {}

    public static EvaluationContext simple(AnomalyEntity anomaly, int ticksInState) {
        return new EvaluationContext(anomaly, ticksInState, Collections.emptySet());
    }
}