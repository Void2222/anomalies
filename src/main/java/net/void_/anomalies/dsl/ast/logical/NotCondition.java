package net.void_.anomalies.dsl.ast.logical;

import net.void_.anomalies.dsl.ast.ICondition;
import net.void_.anomalies.dsl.context.EvaluationContext;

public class NotCondition implements ICondition {
    private final ICondition target;

    public NotCondition(ICondition target) {
        this.target = target;
    }

    @Override
    public boolean test(EvaluationContext ctx) {
        return !target.test(ctx);
    }
}