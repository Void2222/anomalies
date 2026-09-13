package net.void_.anomalies.dsl.ast.logical;

import net.void_.anomalies.dsl.ast.ICondition;
import net.void_.anomalies.dsl.context.EvaluationContext;

public class AndCondition implements ICondition {
    private final ICondition left;
    private final ICondition right;

    public AndCondition(ICondition left, ICondition right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean test(EvaluationContext ctx) {
        // Short-circuit evaluation: если лево false, право не вычисляем
        return left.test(ctx) && right.test(ctx);
    }
}


