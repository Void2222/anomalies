package net.void_.anomalies.dsl.ast.leaf;

import net.void_.anomalies.dsl.ast.ICondition;
import net.void_.anomalies.dsl.context.EvaluationContext;

public record PlayerZoneCondition(
        EvaluationContext.ZoneEventType eventType,
        String zoneName
) implements ICondition {
    @Override
    public boolean test(EvaluationContext ctx) {
        return ctx.activeZoneEvents().contains(
                new EvaluationContext.ZoneEvent(eventType, zoneName)
        );
    }
}