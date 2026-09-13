package net.void_.anomalies.dsl.registry;

import net.void_.anomalies.dsl.model.AnomalyScriptModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AnomalyScriptRegistry {

    private static final Map<String, AnomalyScriptModel> SCRIPTS = new HashMap<>();

    public static void register(String type, AnomalyScriptModel script) {
        SCRIPTS.put(type.toLowerCase(), script);
    }

    public static Optional<AnomalyScriptModel> get(String type) {
        if (type == null) return Optional.empty();
        return Optional.ofNullable(SCRIPTS.get(type.toLowerCase()));
    }

    public static boolean hasScript(String type) {
        return type != null && SCRIPTS.containsKey(type.toLowerCase());
    }

    public static void clear() {
        SCRIPTS.clear();
    }
}