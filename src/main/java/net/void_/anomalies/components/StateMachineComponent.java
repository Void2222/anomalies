package net.void_.anomalies.components;

import net.void_.anomalies.api.behavior.AnomalyBehaviorRegistry;
import net.void_.anomalies.api.behavior.IAnomalyStateBehavior;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;
import net.void_.anomalies.dsl.registry.AnomalyScriptRegistry;

public class StateMachineComponent implements IAnomalyComponent {

    private final IAnomalyStateBehavior behavior;
    private int ticksInState = 0;
    private boolean initialized = false;

    public StateMachineComponent(String anomalyType) {
        // Приоритет DSL-скриптам: если есть скрипт в реестре, берем его. Если нет — падаем на старый Java Registry.
        this.behavior = AnomalyScriptRegistry.get(anomalyType)
                .map(script -> (IAnomalyStateBehavior) script)
                .orElseGet(() -> AnomalyBehaviorRegistry.get(anomalyType));
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        // Единоразовый вызов onEnter при первом тике нового состояния
        if (!initialized) {
            behavior.onEnter(anomaly);
            initialized = true;
        }

        ticksInState++;

        // Вызов DSL-логики переходов с передачей тиков
        String nextState = behavior.onTick(anomaly, ticksInState);

        // Переключение состояния при возврате имени следующей фазы
        if (nextState != null && !nextState.equalsIgnoreCase(anomaly.getCurrentState())) {
            behavior.onExit(anomaly);
            anomaly.setCurrentState(nextState);
        }
    }

    @Override
    public void clientTick(AnomalyEntity anomaly) {
        // На клиенте логика переходов не исполняется (Server Authority)
    }
}