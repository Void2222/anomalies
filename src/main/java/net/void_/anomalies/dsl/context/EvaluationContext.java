package net.void_.anomalies.dsl.context;

import net.void_.anomalies.core.IAnomalyHolder;
import java.util.Collections;
import java.util.Set;

public record EvaluationContext(
        IAnomalyHolder anomaly,
        int ticksInState,
        Set<ZoneEvent> activeZoneEvents
) {
    public enum ZoneEventType {
        ENTERED, EXITED, IN_ZONE
    }

    public record ZoneEvent(ZoneEventType type, String zoneName) {}

    public static EvaluationContext simple(IAnomalyHolder anomaly, int ticksInState) {
        return new EvaluationContext(anomaly, ticksInState, Collections.emptySet());
    }
}