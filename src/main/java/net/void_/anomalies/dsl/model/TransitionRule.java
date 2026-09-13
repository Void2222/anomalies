package net.void_.anomalies.dsl.model;

import net.void_.anomalies.dsl.ast.ICondition;

public record TransitionRule(ICondition condition, String targetState) {}
