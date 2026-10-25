package net.void_.anomalies.client.gui.editor.canvas;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.void_.anomalies.client.gui.editor.model.EditorGraphModel;
import net.void_.anomalies.client.gui.editor.model.EditorStateNode;
import net.void_.anomalies.client.gui.editor.model.EditorTransition;
import net.void_.anomalies.client.gui.editor.model.NodeDiffStatus;

/**
 * Рендерер карточек нод состояний и портов переходов.
 */
public final class NodeCardRenderer {

    private NodeCardRenderer() {}

    /**
     * Отрисовка всех связей (кривых Безье) в графе, включая порталы.
     */
    public static void renderTransitions(GuiGraphics guiGraphics, EditorGraphModel graph, EditorStateNode hoveredNode) {
        PoseStack poseStack = guiGraphics.pose();

        for (EditorStateNode sourceNode : graph.getNodes()) {
            var transitions = sourceNode.getOutgoingTransitions();
            for (int i = 0; i < transitions.size(); i++) {
                EditorTransition transition = transitions.get(i);
                
                // Проверяем, есть ли выделенный портал для этого перехода
                net.void_.anomalies.client.gui.editor.model.EditorPortalNode portal = null;
                for (var p : graph.getPortals()) {
                    if (p.getId().equals(sourceNode.getStateName() + "_" + i)) {
                        portal = p;
                        break;
                    }
                }

                float x1 = sourceNode.getOutputPortX(i);
                float y1 = sourceNode.getOutputPortY(i);

                float x2, y2;
                if (portal != null) {
                    x2 = portal.getInputPortX();
                    y2 = portal.getInputPortY();
                } else {
                    EditorStateNode targetNode = graph.getNode(transition.getTargetState());
                    if (targetNode == null) continue;
                    x2 = targetNode.getInputPortX();
                    y2 = targetNode.getInputPortY();
                }

                int colorFrom = getColorForStatus(sourceNode.getDiffStatus());
                int colorTo = (portal != null) ? 0xFF00FFCC : 0xFF58A6FF;

                if (sourceNode.isCurrentlyActive()) {
                    colorFrom = 0xFF00FFCC;
                    colorTo = 0xFF00AAFF;
                }

                float thickness = (sourceNode == hoveredNode) ? 3.5f : 2.0f;
                CanvasRenderUtils.renderBezierCurve(poseStack, x1, y1, x2, y2, colorFrom, colorTo, thickness);
            }
        }
    }

    /**
     * Отрисовка ноды-портала (Jump Node)
     */
    public static void renderPortal(GuiGraphics guiGraphics, Font font, net.void_.anomalies.client.gui.editor.model.EditorPortalNode portal, boolean isHovered) {
        int x = (int) portal.getX();
        int y = (int) portal.getY();
        int w = (int) portal.getWidth();
        int h = (int) portal.getHeight();

        // Скругленная капсула портала
        int bg = isHovered ? 0xFF2D333F : 0xEE1E222B;
        int border = isHovered ? 0xFF00FFCC : 0xFF353B47;

        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.renderOutline(x, y, w, h, border);

        // Входной порт слева
        int inX = (int) portal.getInputPortX();
        int inY = (int) portal.getInputPortY();
        guiGraphics.fill(inX - 3, inY - 3, inX + 3, inY + 3, 0xFF00FFCC);
        guiGraphics.renderOutline(inX - 3, inY - 3, 6, 6, 0xFFFFFFFF);

        // Текст "➔ target"
        String label = "➔ " + truncate(portal.getTargetState(), 10);
        guiGraphics.drawString(font, label, x + 8, y + (h - 8) / 2, 0xFF00FFCC, false);
    }

    /**
     * Отрисовка карточки отдельной ноды.
     */
    public static void renderNode(GuiGraphics guiGraphics, Font font, EditorStateNode node, boolean isSelected, boolean isHovered) {
        int x = (int) node.getX();
        int y = (int) node.getY();
        int w = (int) node.getWidth();
        int h = (int) node.getHeight();

        // Фон карточки ноды (стильный темно-серый с полупрозрачностью)
        int bgColor = isSelected ? 0xF0242834 : 0xEE1E2028;
        guiGraphics.fill(x, y, x + w, y + h, bgColor);

        // Цвет рамки по диффу или активности
        int borderColor = getColorForStatus(node.getDiffStatus());
        if (node.isCurrentlyActive()) {
            borderColor = 0xFF00FFCC; // Неоновый циан живого стейта
        } else if (isSelected) {
            borderColor = 0xFF58A6FF; // Выделение синим
        }

        guiGraphics.renderOutline(x, y, w, h, borderColor);

        // Шапка ноды
        int headerBg = node.isInitial() ? 0xFF2B3A4A : 0xFF2A2D37;
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + 18, headerBg);

        // Имя стейта
        String title = node.getStateName() + (node.isInitial() ? " [START]" : "");
        int titleColor = node.isCurrentlyActive() ? 0xFF00FFCC : 0xFFFFFFFF;
        guiGraphics.drawString(font, title, x + 8, y + 5, titleColor, false);

        // Входной порт (кружок слева)
        int inX = (int) node.getInputPortX();
        int inY = (int) node.getInputPortY();
        guiGraphics.fill(inX - 4, inY - 4, inX + 4, inY + 4, 0xFF58A6FF);
        guiGraphics.renderOutline(inX - 4, inY - 4, 8, 8, 0xFFFFFFFF);

        // JSON Binds
        if (!node.getBoundJsonPath().isEmpty()) {
            String bindShort = "JSON: " + truncate(node.getBoundJsonPath(), 18);
            guiGraphics.drawString(font, bindShort, x + 8, y + 22, 0x8B949E, false);
        }

        // Выходные порты и условия переходов
        var transitions = node.getOutgoingTransitions();
        for (int i = 0; i < transitions.size(); i++) {
            EditorTransition t = transitions.get(i);
            int portY = (int) node.getOutputPortY(i);
            int outX = (int) node.getOutputPortX(i);

            // Метка условия
            String condText = truncate(t.getConditionExpression(), 16) + " -> " + t.getTargetState();
            int textColor = getColorForStatus(t.getDiffStatus());
            guiGraphics.drawString(font, condText, x + 8, portY - 4, textColor, false);

            // Кружок порта справа
            guiGraphics.fill(outX - 4, portY - 4, outX + 4, portY + 4, 0xFFFFAA00);
            guiGraphics.renderOutline(outX - 4, portY - 4, 8, 8, 0xFFFFFFFF);
        }
    }

    private static int getColorForStatus(NodeDiffStatus status) {
        return switch (status) {
            case CLEAN -> 0xFF8B949E;    // Серый (как на диске)
            case MODIFIED -> 0xFF58A6FF; // Синий (изменен)
            case ADDED -> 0xFF3FB950;    // Зеленый (добавлен)
            case DELETED -> 0xFFF85149;  // Красный (удален)
        };
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }
}
