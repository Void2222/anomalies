package net.void_.anomalies.dsl.ast.leaf;

import net.void_.anomalies.dsl.ast.ICondition;
import net.void_.anomalies.dsl.context.EvaluationContext;
import net.minecraft.util.RandomSource;

public record TimerCondition(int targetTicks) implements ICondition {
    @Override
    public boolean test(EvaluationContext ctx) {
        return ctx.ticksInState() >= targetTicks;
    }
}



