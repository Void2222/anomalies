package net.void_.anomalies.components;

import net.void_.anomalies.api.behavior.AnomalyBehaviorRegistry;
import net.void_.anomalies.api.behavior.IAnomalyStateBehavior;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class StateMachineComponent implements IAnomalyComponent {

    private final IAnomalyStateBehavior behavior;
    private int ticksInState = 0;
    private boolean initialized = false;

    public StateMachineComponent(String anomalyType) {
        this.behavior = AnomalyBehaviorRegistry.get(anomalyType);
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        // Единоразовый вызов onEnter при первом тике нового состояния
        if (!initialized) {
            behavior.onEnter(anomaly);
            initialized = true;
        }

        ticksInState++;

        // Вызов Java-логики с передачей количества тиков в текущем состоянии
        String nextState = behavior.onTick(anomaly, ticksInState);

        // Если логика вернула новое состояние, отличающееся от текущего
        if (nextState != null && !nextState.equalsIgnoreCase(anomaly.getCurrentState())) {
            behavior.onExit(anomaly);
            // Переключение состояния провоцирует rebuildComponents()
            anomaly.setCurrentState(nextState);
        }
    }

    @Override
    public void clientTick(AnomalyEntity anomaly) {
        // На клиенте логика переходов не исполняется (Server Authority)
    }
}