package net.void_.anomalies.client.gui.editor.canvas;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.void_.anomalies.anomaly.data.*;
import net.void_.anomalies.client.gui.editor.model.EditorGraphModel;
import net.void_.anomalies.client.gui.editor.model.EditorPortalNode;
import net.void_.anomalies.client.gui.editor.model.EditorStateNode;
import net.void_.anomalies.client.gui.editor.model.EditorTransition;
import net.void_.anomalies.client.gui.editor.model.NodeDiffStatus;

import java.util.List;
import java.util.Locale;

/**
 * Рендерер карточек нод состояний, портов переходов, Jump-порталов
 * и инлайн-инспектора зон, урона, частиц и звука.
 */
public final class NodeCardRenderer {

    private static final String[] PARTICLE_PRESETS = {
            "minecraft:flame",
            "minecraft:smoke",
            "minecraft:portal",
            "minecraft:soul_fire_flame",
            "minecraft:witch"
    };

    private static final String[] SOUND_PRESETS = {
            "minecraft:block.fire.ambient",
            "minecraft:entity.generic.explode",
            "minecraft:ambient.cave",
            "minecraft:block.beacon.ambient",
            "none"
    };

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

                // Ищем портал для этого перехода
                EditorPortalNode portal = null;
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
    public static void renderPortal(GuiGraphics guiGraphics, Font font, EditorPortalNode portal, boolean isHovered) {
        int x = (int) portal.getX();
        int y = (int) portal.getY();
        int w = (int) portal.getWidth();
        int h = (int) portal.getHeight();

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
     * Отрисовка карточки отдельной ноды (включая инлайн-инспектор параметров).
     */
    public static void renderNode(GuiGraphics guiGraphics, Font font, EditorStateNode node, boolean isSelected, boolean isHovered) {
        int x = (int) node.getX();
        int y = (int) node.getY();
        int w = (int) node.getWidth();
        int h = (int) node.getHeight();

        // 1. Фон карточки ноды
        int bgColor = isSelected ? 0xF0222632 : 0xEE1A1C24;
        guiGraphics.fill(x, y, x + w, y + h, bgColor);

        // 2. Цвет рамки по диффу или активности
        int borderColor = getColorForStatus(node.getDiffStatus());
        if (node.isCurrentlyActive()) {
            borderColor = 0xFF00FFCC;
        } else if (isSelected) {
            borderColor = 0xFF58A6FF;
        }

        guiGraphics.renderOutline(x, y, w, h, borderColor);

        // 3. Шапка ноды
        int headerBg = node.isInitial() ? 0xFF2B3A4A : 0xFF282B35;
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + 18, headerBg);

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
            String bindShort = "JSON: " + truncate(node.getBoundJsonPath(), 20);
            guiGraphics.drawString(font, bindShort, x + 8, y + 22, 0x8B949E, false);
        }

        // Выходные порты и условия переходов
        var transitions = node.getOutgoingTransitions();
        for (int i = 0; i < transitions.size(); i++) {
            EditorTransition t = transitions.get(i);
            int portY = (int) node.getOutputPortY(i);
            int outX = (int) node.getOutputPortX(i);

            String condText = truncate(t.getConditionExpression(), 14) + " -> " + t.getTargetState();
            int textColor = getColorForStatus(t.getDiffStatus());
            guiGraphics.drawString(font, condText, x + 8, portY - 4, textColor, false);

            // Кружок порта справа
            guiGraphics.fill(outX - 4, portY - 4, outX + 4, portY + 4, 0xFFFFAA00);
            guiGraphics.renderOutline(outX - 4, portY - 4, 8, 8, 0xFFFFFFFF);
        }

        float curY = y + 46.0f + transitions.size() * 20.0f;

        // 4. Инлайн-инспектор (аккордеон параметров)
        if (!node.isExpanded()) {
            // Свернутое состояние: кнопка развернуть
            int btnY = y + h - 17;
            guiGraphics.fill(x + 4, btnY, x + w - 4, btnY + 13, 0xFF232733);
            guiGraphics.renderOutline(x + 4, btnY, w - 8, 13, 0xFF353B47);
            guiGraphics.drawString(font, "▼ Параметры зон", x + (w - font.width("▼ Параметры зон")) / 2, btnY + 3, 0x58A6FF, false);
        } else {
            // Развернутое состояние: заголовок секции Зон
            guiGraphics.fill(x + 4, (int) curY, x + w - 4, (int) curY + 13, 0xFF252A36);
            guiGraphics.drawString(font, "✦ ЗОНЫ ПОРАЖЕНИЯ", x + 8, (int) curY + 3, 0x58A6FF, false);
            curY += 16.0f;

            // Список зон
            List<ZoneConfig> zones = node.getDraftZones();
            for (int zi = 0; zi < zones.size(); zi++) {
                ZoneConfig z = zones.get(zi);
                int zBoxY = (int) curY;
                int zBoxH = 62;

                guiGraphics.fill(x + 5, zBoxY, x + w - 5, zBoxY + zBoxH, 0xFF171922);
                guiGraphics.renderOutline(x + 5, zBoxY, w - 10, zBoxH, 0xFF2E3340);

                // Заголовок зоны и кнопка удаления
                guiGraphics.drawString(font, "Зона #" + zi, x + 8, zBoxY + 3, 0xFFFFAA00, false);
                if (zones.size() > 1) {
                    guiGraphics.fill(x + w - 18, zBoxY + 2, x + w - 7, zBoxY + 12, 0xFF3A2024);
                    guiGraphics.renderOutline(x + w - 18, zBoxY + 2, 11, 10, 0xFFF85149);
                    guiGraphics.drawString(font, "×", x + w - 15, zBoxY + 2, 0xFFF85149, false);
                }

                // Строка 1: Радиус
                int row1Y = zBoxY + 14;
                guiGraphics.drawString(font, "Рад: " + formatDouble(z.radius()) + "м", x + 8, row1Y + 2, 0xC0C8D4, false);
                renderMiniButton(guiGraphics, font, "-", x + w - 38, row1Y, 13, 10);
                renderMiniButton(guiGraphics, font, "+", x + w - 21, row1Y, 13, 10);

                // Строка 2: Урон
                int row2Y = zBoxY + 28;
                double dmg = (z.damage() != null && z.damage().innerAmount() != null) ? z.damage().innerAmount().getDouble() : 0.0;
                guiGraphics.drawString(font, "Урон: " + formatDouble(dmg), x + 8, row2Y + 2, 0xC0C8D4, false);
                renderMiniButton(guiGraphics, font, "-", x + w - 38, row2Y, 13, 10);
                renderMiniButton(guiGraphics, font, "+", x + w - 21, row2Y, 13, 10);

                // Строка 3: Тяга к центру
                int row3Y = zBoxY + 42;
                double pull = (z.physics() != null) ? z.physics().pullForce() : 0.0;
                guiGraphics.drawString(font, "Тяга: " + formatDouble(pull), x + 8, row3Y + 2, 0xC0C8D4, false);
                renderMiniButton(guiGraphics, font, "-", x + w - 38, row3Y, 13, 10);
                renderMiniButton(guiGraphics, font, "+", x + w - 21, row3Y, 13, 10);

                curY += 66.0f;
            }

            // Кнопка [+ Добавить зону]
            int addZoneY = (int) curY;
            guiGraphics.fill(x + 5, addZoneY, x + w - 5, addZoneY + 13, 0xFF1E2836);
            guiGraphics.renderOutline(x + 5, addZoneY, w - 10, 13, 0xFF3FB950);
            guiGraphics.drawString(font, "+ Добавить зону", x + (w - font.width("+ Добавить зону")) / 2, addZoneY + 3, 0xFF3FB950, false);
            curY += 18.0f;

            // Секция частиц
            guiGraphics.fill(x + 4, (int) curY, x + w - 4, (int) curY + 13, 0xFF252A36);
            guiGraphics.drawString(font, "✦ ЧАСТИЦЫ", x + 8, (int) curY + 3, 0x3FB950, false);
            curY += 15.0f;

            String partType = !node.getDraftParticles().isEmpty() ? node.getDraftParticles().get(0).type() : "minecraft:flame";
            String partShort = partType.replace("minecraft:", "");
            int partBtnY = (int) curY;
            guiGraphics.fill(x + 5, partBtnY, x + w - 5, partBtnY + 13, 0xFF1B202B);
            guiGraphics.renderOutline(x + 5, partBtnY, w - 10, 13, 0xFF353B47);
            guiGraphics.drawString(font, "Тип: " + truncate(partShort, 14), x + 8, partBtnY + 3, 0xC0C8D4, false);
            curY += 18.0f;

            // Секция звука
            guiGraphics.fill(x + 4, (int) curY, x + w - 4, (int) curY + 13, 0xFF252A36);
            guiGraphics.drawString(font, "✦ ЗВУК", x + 8, (int) curY + 3, 0xE3B341, false);
            curY += 15.0f;

            String sndEvent = node.getDraftSound() != null ? node.getDraftSound().event() : "none";
            String sndShort = sndEvent.replace("minecraft:", "");
            int sndBtnY = (int) curY;
            guiGraphics.fill(x + 5, sndBtnY, x + w - 5, sndBtnY + 13, 0xFF1B202B);
            guiGraphics.renderOutline(x + 5, sndBtnY, w - 10, 13, 0xFF353B47);
            guiGraphics.drawString(font, "Звук: " + truncate(sndShort, 14), x + 8, sndBtnY + 3, 0xC0C8D4, false);
            curY += 18.0f;

            // Кнопка свернуть в самом низу
            int collapseY = y + h - 17;
            guiGraphics.fill(x + 4, collapseY, x + w - 4, collapseY + 13, 0xFF232733);
            guiGraphics.renderOutline(x + 4, collapseY, w - 8, 13, 0xFF353B47);
            guiGraphics.drawString(font, "▲ Свернуть", x + (w - font.width("▲ Свернуть")) / 2, collapseY + 3, 0x8B949E, false);
        }
    }

    private static void renderMiniButton(GuiGraphics gui, Font font, String text, int bx, int by, int bw, int bh) {
        gui.fill(bx, by, bx + bw, by + bh, 0xFF2A2E3B);
        gui.renderOutline(bx, by, bw, bh, 0xFF3E4457);
        int tw = font.width(text);
        gui.drawString(font, text, bx + (bw - tw) / 2, by + 1, 0xFFFFFF, false);
    }

    /**
     * Обработка клика мыши по интерактивным контролам ноды.
     * Возвращает true, если клик поглощен контролом (кнопкой раскрытия, слайдером, кнопкой удаления).
     */
    public static boolean handleNodeClick(EditorStateNode node, float mx, float my) {
        float x = node.getX();
        float y = node.getY();
        float w = node.getWidth();
        float h = node.getHeight();

        // 1. Свернутое состояние: клик по кнопке развернуть
        if (!node.isExpanded()) {
            float btnY = y + h - 17.0f;
            if (mx >= x + 4 && mx <= x + w - 4 && my >= btnY && my <= btnY + 14.0f) {
                node.toggleExpanded();
                return true;
            }
            return false;
        }

        // 2. Развернутое состояние: проверяем контролы сверху вниз
        float curY = y + 46.0f + node.getOutgoingTransitions().size() * 20.0f;
        curY += 16.0f; // заголовок ЗОНЫ

        List<ZoneConfig> zones = node.getDraftZones();
        for (int zi = 0; zi < zones.size(); zi++) {
            ZoneConfig z = zones.get(zi);
            float zBoxY = curY;

            // Кнопка удаления зоны [×]
            if (zones.size() > 1) {
                float delX = x + w - 18.0f;
                float delY = zBoxY + 2.0f;
                if (mx >= delX && mx <= delX + 11.0f && my >= delY && my <= delY + 11.0f) {
                    zones.remove(zi);
                    node.markModified();
                    node.updateHeight();
                    return true;
                }
            }

            // Радиус [-] [+]
            float row1Y = zBoxY + 14.0f;
            if (my >= row1Y && my <= row1Y + 11.0f) {
                if (mx >= x + w - 38.0f && mx <= x + w - 25.0f) {
                    double newR = Math.max(0.5, z.radius() - 0.5);
                    zones.set(zi, new ZoneConfig(newR, z.damage(), z.physics()));
                    node.markModified();
                    return true;
                }
                if (mx >= x + w - 21.0f && mx <= x + w - 8.0f) {
                    double newR = Math.min(25.0, z.radius() + 0.5);
                    zones.set(zi, new ZoneConfig(newR, z.damage(), z.physics()));
                    node.markModified();
                    return true;
                }
            }

            // Урон [-] [+]
            float row2Y = zBoxY + 28.0f;
            if (my >= row2Y && my <= row2Y + 11.0f) {
                double curDmg = (z.damage() != null && z.damage().innerAmount() != null) ? z.damage().innerAmount().getDouble() : 0.0;
                if (mx >= x + w - 38.0f && mx <= x + w - 25.0f) {
                    double newDmg = Math.max(0.0, curDmg - 1.0);
                    DamageConfig dc = new DamageConfig(null, new MinMaxRange(newDmg, newDmg), 0, "generic");
                    zones.set(zi, new ZoneConfig(z.radius(), dc, z.physics()));
                    node.markModified();
                    return true;
                }
                if (mx >= x + w - 21.0f && mx <= x + w - 8.0f) {
                    double newDmg = Math.min(100.0, curDmg + 1.0);
                    DamageConfig dc = new DamageConfig(null, new MinMaxRange(newDmg, newDmg), 0, "generic");
                    zones.set(zi, new ZoneConfig(z.radius(), dc, z.physics()));
                    node.markModified();
                    return true;
                }
            }

            // Тяга [-] [+]
            float row3Y = zBoxY + 42.0f;
            if (my >= row3Y && my <= row3Y + 11.0f) {
                double curPull = (z.physics() != null) ? z.physics().pullForce() : 0.0;
                if (mx >= x + w - 38.0f && mx <= x + w - 25.0f) {
                    double newPull = Math.max(-1.0, curPull - 0.05);
                    PhysicsConfig op = z.physics();
                    PhysicsConfig np = new PhysicsConfig(op != null ? op.impulseX() : 0, op != null ? op.impulseY() : 0, op != null ? op.impulseZ() : 0, true, newPull, op != null ? op.spinForce() : 0);
                    zones.set(zi, new ZoneConfig(z.radius(), z.damage(), np));
                    node.markModified();
                    return true;
                }
                if (mx >= x + w - 21.0f && mx <= x + w - 8.0f) {
                    double newPull = Math.min(1.0, curPull + 0.05);
                    PhysicsConfig op = z.physics();
                    PhysicsConfig np = new PhysicsConfig(op != null ? op.impulseX() : 0, op != null ? op.impulseY() : 0, op != null ? op.impulseZ() : 0, true, newPull, op != null ? op.spinForce() : 0);
                    zones.set(zi, new ZoneConfig(z.radius(), z.damage(), np));
                    node.markModified();
                    return true;
                }
            }

            curY += 66.0f;
        }

        // Кнопка [+ Добавить зону]
        float addZoneY = curY;
        if (mx >= x + 5.0f && mx <= x + w - 5.0f && my >= addZoneY && my <= addZoneY + 14.0f) {
            double nextR = zones.isEmpty() ? 3.0 : zones.get(zones.size() - 1).radius() + 2.0;
            zones.add(new ZoneConfig(
                    nextR,
                    new DamageConfig(null, new MinMaxRange(2.0, 4.0), 0, "generic"),
                    new PhysicsConfig(0, 0, 0, true, 0.1, 0)
            ));
            node.markModified();
            node.updateHeight();
            return true;
        }
        curY += 18.0f;

        // Пресет частиц
        curY += 15.0f;
        float partBtnY = curY;
        if (mx >= x + 5.0f && mx <= x + w - 5.0f && my >= partBtnY && my <= partBtnY + 14.0f) {
            cycleParticle(node);
            node.markModified();
            return true;
        }
        curY += 18.0f;

        // Пресет звука
        curY += 15.0f;
        float sndBtnY = curY;
        if (mx >= x + 5.0f && mx <= x + w - 5.0f && my >= sndBtnY && my <= sndBtnY + 14.0f) {
            cycleSound(node);
            node.markModified();
            return true;
        }

        // Кнопка свернуть в самом низу
        float collapseY = y + h - 17.0f;
        if (mx >= x + 4.0f && mx <= x + w - 4.0f && my >= collapseY && my <= collapseY + 14.0f) {
            node.toggleExpanded();
            return true;
        }

        return false;
    }

    private static void cycleParticle(EditorStateNode node) {
        String current = !node.getDraftParticles().isEmpty() ? node.getDraftParticles().get(0).type() : PARTICLE_PRESETS[0];
        int nextIdx = 0;
        for (int i = 0; i < PARTICLE_PRESETS.length; i++) {
            if (PARTICLE_PRESETS[i].equalsIgnoreCase(current)) {
                nextIdx = (i + 1) % PARTICLE_PRESETS.length;
                break;
            }
        }
        node.getDraftParticles().clear();
        node.getDraftParticles().add(new ParticleConfig(
                PARTICLE_PRESETS[nextIdx],
                "CYLINDER",
                new MinMaxRange(1.0, 2.0),
                2.0,
                1.5,
                new MinMaxRange(4.0, 8.0)
        ));
    }

    private static void cycleSound(EditorStateNode node) {
        String current = node.getDraftSound() != null ? node.getDraftSound().event() : SOUND_PRESETS[0];
        int nextIdx = 0;
        for (int i = 0; i < SOUND_PRESETS.length; i++) {
            if (SOUND_PRESETS[i].equalsIgnoreCase(current)) {
                nextIdx = (i + 1) % SOUND_PRESETS.length;
                break;
            }
        }
        node.setDraftSound(new SoundConfig(
                SOUND_PRESETS[nextIdx],
                new MinMaxRange(20.0, 40.0),
                "BLOCKS",
                1.0f,
                1.0f
        ));
    }

    private static String formatDouble(double val) {
        return String.format(Locale.US, "%.1f", val);
    }

    private static int getColorForStatus(NodeDiffStatus status) {
        return switch (status) {
            case CLEAN -> 0xFF8B949E;
            case MODIFIED -> 0xFF58A6FF;
            case ADDED -> 0xFF3FB950;
            case DELETED -> 0xFFF85149;
        };
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }
}
