package net.void_.anomalies.api.behavior;

import java.util.HashMap;
import java.util.Map;

public class AnomalyBehaviorRegistry {

    private static final Map<String, IAnomalyStateBehavior> REGISTRY = new HashMap<>();

    /**
     * Зарегистрировать Java-логику для типа аномалии
     */
    public static void register(String type, IAnomalyStateBehavior behavior) {
        if (type != null && behavior != null) {
            REGISTRY.put(type.toLowerCase(), behavior);
        }
    }

    /**
     * Получить поведение. Если логика не зарегистрирована — возвращает безобидный DefaultBehavior.
     */
    public static IAnomalyStateBehavior get(String type) {
        if (type == null) return DefaultBehavior.INSTANCE;
        return REGISTRY.getOrDefault(type.toLowerCase(), DefaultBehavior.INSTANCE);
    }
}