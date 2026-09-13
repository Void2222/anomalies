package net.void_.anomalies.dsl.ast.logical;

import net.void_.anomalies.dsl.ast.ICondition;
import net.void_.anomalies.dsl.context.EvaluationContext;

public class OrCondition implements ICondition {
    private final ICondition left;
    private final ICondition right;

    public OrCondition(ICondition left, ICondition right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean test(EvaluationContext ctx) {
        return left.test(ctx) || right.test(ctx);
    }
}