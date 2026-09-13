package net.void_.anomalies.dsl.ast.leaf;

import net.minecraft.util.RandomSource;
import net.void_.anomalies.dsl.ast.ICondition;
import net.void_.anomalies.dsl.context.EvaluationContext;

public class ChanceCondition implements ICondition {
    private final double chance;
    private final RandomSource random = RandomSource.create();

    public ChanceCondition(double chance) {
        this.chance = chance;
    }

    @Override
    public boolean test(EvaluationContext ctx) {
        if (chance >= 1.0) return true;
        if (chance <= 0.0) return false;
        return random.nextDouble() < chance;
    }
}
