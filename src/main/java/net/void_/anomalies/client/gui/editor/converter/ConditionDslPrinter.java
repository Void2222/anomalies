package net.void_.anomalies.client.gui.editor.converter;

import net.void_.anomalies.dsl.ast.ICondition;
import net.void_.anomalies.dsl.ast.leaf.ChanceCondition;
import net.void_.anomalies.dsl.ast.leaf.PlayerZoneCondition;
import net.void_.anomalies.dsl.ast.leaf.TimerCondition;
import net.void_.anomalies.dsl.ast.logical.AndCondition;
import net.void_.anomalies.dsl.ast.logical.NotCondition;
import net.void_.anomalies.dsl.ast.logical.OrCondition;

/**
 * Преобразователь объектов условий AST ICondition в человекочитаемый DSL-текст.
 */
public final class ConditionDslPrinter {

    private ConditionDslPrinter() {}

    public static String print(ICondition condition) {
        if (condition == null) {
            return "always";
        }

        if (condition instanceof TimerCondition tc) {
            return "timer(" + tc.targetTicks() + ")";
        }

        if (condition instanceof ChanceCondition cc) {
            return "chance(" + cc.getChance() + ")";
        }

        if (condition instanceof PlayerZoneCondition pzc) {
            String event = switch (pzc.eventType()) {
                case ENTERED -> "player.entered_zone";
                case EXITED -> "player.exited_zone";
                case IN_ZONE -> "player.in_zone";
            };
            return event + "(" + pzc.zoneName() + ")";
        }

        if (condition instanceof AndCondition ac) {
            return "(" + print(ac.getLeft()) + " and " + print(ac.getRight()) + ")";
        }

        if (condition instanceof OrCondition oc) {
            return "(" + print(oc.getLeft()) + " or " + print(oc.getRight()) + ")";
        }

        if (condition instanceof NotCondition nc) {
            return "not " + print(nc.getTarget());
        }

        return "condition";
    }
}
