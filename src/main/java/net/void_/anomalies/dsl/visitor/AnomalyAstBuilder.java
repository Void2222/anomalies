package net.void_.anomalies.dsl.visitor;

import net.void_.anomalies.grammar.AnomalyDSLBaseVisitor;
import net.void_.anomalies.grammar.AnomalyDSLParser;
import net.void_.anomalies.dsl.ast.*;
import net.void_.anomalies.dsl.ast.leaf.*;
import net.void_.anomalies.dsl.ast.logical.*;
import net.void_.anomalies.dsl.context.EvaluationContext.ZoneEventType;
import net.void_.anomalies.dsl.model.AnomalyScriptModel;
import net.void_.anomalies.dsl.model.TransitionRule;

public class AnomalyAstBuilder extends AnomalyDSLBaseVisitor<Object> {

    @Override
    public AnomalyScriptModel visitScript(AnomalyDSLParser.ScriptContext ctx) {
        AnomalyScriptModel model = new AnomalyScriptModel();

        for (AnomalyDSLParser.StatementContext stmt : ctx.statement()) {
            if (stmt.bindClause() != null) {
                String state = stmt.bindClause().stateName.getText();
                String rawPath = stmt.bindClause().jsonPath.getText();
                // Удаляем кавычки из STRING_LITERAL
                String path = rawPath.substring(1, rawPath.length() - 1);
                model.addBind(state, path);
            } else if (stmt.initialStateClause() != null) {
                model.setInitialState(stmt.initialStateClause().stateName.getText());
            } else if (stmt.stateBlock() != null) {
                String stateName = stmt.stateBlock().stateName.getText();
                for (AnomalyDSLParser.TransitionRuleContext tr : stmt.stateBlock().transitionRule()) {
                    ICondition cond = (ICondition) visit(tr.expr);
                    String target = tr.targetState.getText();
                    model.addTransition(stateName, new TransitionRule(cond, target));
                }
            }
        }
        return model;
    }

    @Override
    public ICondition visitParenExpr(AnomalyDSLParser.ParenExprContext ctx) {
        return (ICondition) visit(ctx.expr);
    }

    @Override
    public ICondition visitNotExpr(AnomalyDSLParser.NotExprContext ctx) {
        return new NotCondition((ICondition) visit(ctx.expr));
    }

    @Override
    public ICondition visitAndExpr(AnomalyDSLParser.AndExprContext ctx) {
        return new AndCondition(
                (ICondition) visit(ctx.left),
                (ICondition) visit(ctx.right)
        );
    }

    @Override
    public ICondition visitOrExpr(AnomalyDSLParser.OrExprContext ctx) {
        return new OrCondition(
                (ICondition) visit(ctx.left),
                (ICondition) visit(ctx.right)
        );
    }

    @Override
    public ICondition visitTimerCondition(AnomalyDSLParser.TimerConditionContext ctx) {
        int ticks = Integer.parseInt(ctx.ticks.getText());
        return new TimerCondition(ticks);
    }

    @Override
    public ICondition visitChanceCondition(AnomalyDSLParser.ChanceConditionContext ctx) {
        double chance = Double.parseDouble(ctx.chanceVal.getText());
        return new ChanceCondition(chance);
    }

    @Override
    public ICondition visitPlayerZoneCondition(AnomalyDSLParser.PlayerZoneConditionContext ctx) {
        String event = ctx.eventName.getText().toLowerCase();
        String rawZone = ctx.zone.getText();
        String zoneName = rawZone.substring(1, rawZone.length() - 1);

        ZoneEventType type = switch (event) {
            case "entered_zone" -> ZoneEventType.ENTERED;
            case "exited_zone"  -> ZoneEventType.EXITED;
            case "in_zone"      -> ZoneEventType.IN_ZONE;
            default -> throw new IllegalArgumentException("Unknown player event: " + event);
        };

        return new PlayerZoneCondition(type, zoneName);
    }
}