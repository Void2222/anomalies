package net.void_.anomalies.components;

import net.void_.anomalies.api.behavior.AnomalyBehaviorRegistry;
import net.void_.anomalies.api.behavior.IAnomalyStateBehavior;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;
import net.void_.anomalies.dsl.cache.TransientZoneCache;
import net.void_.anomalies.dsl.registry.AnomalyScriptRegistry;

public class StateMachineComponent implements IAnomalyComponent {

    private final IAnomalyStateBehavior behavior;
    private int ticksInState = 0;
    private boolean initialized = false;

    public StateMachineComponent(String anomalyType) {
        this.behavior = AnomalyScriptRegistry.get(anomalyType)
                .map(script -> (IAnomalyStateBehavior) script)
                .orElseGet(() -> AnomalyBehaviorRegistry.get(anomalyType));
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        if (!initialized) {
            behavior.onEnter(anomaly);
            initialized = true;
        }

        ticksInState++;

        String nextState = behavior.onTick(anomaly, ticksInState);

        if (nextState != null && !nextState.equalsIgnoreCase(anomaly.getCurrentState())) {
            behavior.onExit(anomaly);

            // Сбрасываем счетчик тиков и флаг инициализации для нового состояния
            this.ticksInState = 0;
            this.initialized = false;

            // Сбрасываем кэш одноразовых ивентов тика при смене фазы
            TransientZoneCache.clear(anomaly);

            anomaly.setCurrentState(nextState);
        }
    }

    @Override
    public void clientTick(AnomalyEntity anomaly) {}
}