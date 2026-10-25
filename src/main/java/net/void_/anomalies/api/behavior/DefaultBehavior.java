package net.void_.anomalies.api.behavior;

import net.void_.anomalies.core.IAnomalyHolder;

public class DefaultBehavior implements IAnomalyStateBehavior {

    public static final DefaultBehavior INSTANCE = new DefaultBehavior();

    private DefaultBehavior() {}

    @Override
    public String onTick(IAnomalyHolder anomaly, int ticksInState) {
        return null;
    }
}