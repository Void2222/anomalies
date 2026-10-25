package net.void_.anomalies.client.gui.editor.converter;

import com.google.gson.JsonParser;
import net.void_.anomalies.client.gui.editor.model.EditorGraphModel;
import net.void_.anomalies.client.gui.editor.model.EditorStateNode;
import net.void_.anomalies.client.gui.editor.model.EditorTransition;
import net.void_.anomalies.client.gui.editor.model.NodeDiffStatus;
import net.void_.anomalies.dsl.model.AnomalyScriptModel;
import net.void_.anomalies.dsl.model.TransitionRule;

import java.util.*;

/**
 * Двунаправленный конвертер между AST скриптовой моделью (AnomalyScriptModel) и UI-графом (EditorGraphModel).
 */
public final class GraphModelConverter {

    private GraphModelConverter() {}

    /**
     * Преобразование AST модели в UI-граф редактора с опциональным наложением файла координат .editor.json
     */
    public static EditorGraphModel fromScriptModel(AnomalyScriptModel scriptModel, String editorJsonMeta) {
        EditorGraphModel graph = new EditorGraphModel();
        if (scriptModel == null) {
            return graph;
        }

        Set<String> allStateNames = new LinkedHashSet<>();
        allStateNames.add(scriptModel.getInitialState());
        allStateNames.addAll(scriptModel.getBinds().keySet());
        allStateNames.addAll(scriptModel.getTransitions().keySet());
        allStateNames.addAll(scriptModel.getStateRecipes().keySet());

        for (String stateName : allStateNames) {
            if (stateName == null || stateName.isEmpty()) continue;

            EditorStateNode node = new EditorStateNode(stateName, 0, 0);
            node.setInitial(stateName.equals(scriptModel.getInitialState()));
            node.setBoundJsonPath(scriptModel.getBinds().getOrDefault(stateName, ""));
            node.setDiffStatus(NodeDiffStatus.CLEAN);

            // Рецепты стейта
            List<String> recipes = scriptModel.getRecipesForState(stateName);
            if (recipes != null) {
                node.getRecipes().addAll(recipes);
            }

            // Переходы
            List<TransitionRule> rules = scriptModel.getTransitions().get(stateName);
            if (rules != null) {
                for (TransitionRule rule : rules) {
                    String condStr = ConditionDslPrinter.print(rule.condition());
                    EditorTransition transition = new EditorTransition(stateName, rule.targetState(), condStr);
                    transition.setDiffStatus(NodeDiffStatus.CLEAN);
                    node.addTransition(transition);
                }
            }

            graph.addNode(node);
        }

        // Применение метаданных координат или авто-раскладка
        if (editorJsonMeta != null && !editorJsonMeta.isBlank()) {
            try {
                graph.applyJsonMeta(JsonParser.parseString(editorJsonMeta).getAsJsonObject());
            } catch (Exception ignored) {
                graph.autoLayout();
            }
        } else {
            graph.autoLayout();
        }

        graph.markAllClean();
        return graph;
    }

    /**
     * Экспорт графа редактора в чистый читаемый текст DSL-файла .anom
     */
    public static String toDslText(EditorGraphModel graph) {
        StringBuilder sb = new StringBuilder();

        // 1. Блок привязок state --> "path.json"
        for (EditorStateNode node : graph.getNodes()) {
            if (!node.getBoundJsonPath().isEmpty()) {
                sb.append("state ").append(node.getStateName())
                  .append(" --> \"").append(node.getBoundJsonPath()).append("\";\n");
            }
        }

        sb.append("\n");

        // 2. Начальное состояние
        String initial = "idle";
        for (EditorStateNode node : graph.getNodes()) {
            if (node.isInitial()) {
                initial = node.getStateName();
                break;
            }
        }
        sb.append("initial = ").append(initial).append(";\n\n");

        // 3. Блоки стейтов с переходами и рецептами
        for (EditorStateNode node : graph.getNodes()) {
            sb.append("state ").append(node.getStateName()).append(" {\n");

            // Рецепты
            if (!node.getRecipes().isEmpty()) {
                sb.append("    recipe {\n");
                for (String r : node.getRecipes()) {
                    sb.append("        ").append(r).append(";\n");
                }
                sb.append("    }\n");
            }

            // Переходы
            for (EditorTransition t : node.getOutgoingTransitions()) {
                sb.append("    when ").append(t.getConditionExpression())
                  .append(" -> ").append(t.getTargetState()).append(";\n");
            }

            sb.append("}\n\n");
        }

        return sb.toString();
    }
}
