package net.void_.anomalies.dsl.ast;

import net.void_.anomalies.dsl.context.EvaluationContext;

@FunctionalInterface
public interface ICondition {
    boolean test(EvaluationContext ctx);
}