package net.void_.anomalies.api.behavior;

import net.void_.anomalies.core.AnomalyEntity;

public interface IAnomalyStateBehavior {

    /**
     * Вызывается единоразово при входе в новое состояние.
     */
    default void onEnter(AnomalyEntity anomaly) {}

    /**
     * Вызывается каждый серверный тик.
     * @param anomaly Сущность аномалии
     * @param ticksInState Количество тиков, проведенных в текущем состоянии
     * @return Имя следующего состояния (например, "warmup") или null, если фаза не меняется
     */
    String onTick(AnomalyEntity anomaly, int ticksInState);

    /**
     * Вызывается единоразово перед выходом из состояния.
     */
    default void onExit(AnomalyEntity anomaly) {}
}