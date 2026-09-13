package net.void_.anomalies.dsl.model;

import net.void_.anomalies.api.behavior.IAnomalyStateBehavior;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.dsl.ast.ICondition;
import net.void_.anomalies.dsl.cache.TransientZoneCache;
import net.void_.anomalies.dsl.context.EvaluationContext;

import java.util.*;

public class AnomalyScriptModel implements IAnomalyStateBehavior {

    private final Map<String, String> binds = new HashMap<>();
    private final Map<String, List<TransitionRule>> transitions = new HashMap<>();
    private String initialState = "idle";

    public void addBind(String state, String jsonPath) {
        binds.put(state, jsonPath);
    }

    public void setInitialState(String state) {
        this.initialState = state;
    }

    public void addTransition(String state, TransitionRule rule) {
        transitions.computeIfAbsent(state, k -> new ArrayList<>()).add(rule);
    }

    public String getInitialState() {
        return initialState;
    }

    public Map<String, String> getBinds() {
        return binds;
    }

    @Override
    public String onTick(AnomalyEntity anomaly, int ticksInState) {
        String currentState = anomaly.getCurrentState();
        List<TransitionRule> rules = transitions.get(currentState);

        if (rules == null || rules.isEmpty()) {
            return null;
        }

        // Подтягиваем кэшированные ивенты зон и автоматически очищаем сгоревшие фреймы
        Set<EvaluationContext.ZoneEvent> zoneEvents = TransientZoneCache.getSnapshotAndFlush(anomaly);
        EvaluationContext ctx = new EvaluationContext(anomaly, ticksInState, zoneEvents);

        for (TransitionRule rule : rules) {
            if (rule.condition().test(ctx)) {
                return rule.targetState();
            }
        }

        return null;
    }
}