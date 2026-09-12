package net.void_.anomalies.api.behavior;

import net.void_.anomalies.core.AnomalyEntity;

public class DefaultBehavior implements IAnomalyStateBehavior {

    public static final DefaultBehavior INSTANCE = new DefaultBehavior();

    private DefaultBehavior() {}

    @Override
    public String onTick(AnomalyEntity anomaly, int ticksInState) {
        return null; // Ничего не происходит, состояние вечно "idle"
    }
}