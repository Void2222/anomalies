package net.void_.anomalies.client.gui.editor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.void_.anomalies.client.gui.editor.canvas.CanvasRenderUtils;
import net.void_.anomalies.client.gui.editor.canvas.NodeCardRenderer;
import net.void_.anomalies.client.gui.editor.model.EditorGraphModel;
import net.void_.anomalies.client.gui.editor.model.EditorStateNode;

import java.util.UUID;

/**
 * Главный экран холста нодового редактора аномалий (Node Graph Canvas).
 * Поддерживает бесконечный холст, матричный Pan/Zoom и выбор элементов.
 */
public class NodeGraphScreen extends Screen {

    private final UUID targetAnomalyId; // null если шаблон из файла
    private final EditorGraphModel graph;

    // Состояние холста и мыши
    private boolean isPanning = false;
    private double lastMouseX;
    private double lastMouseY;

    // Drag-and-drop ноды
    private EditorStateNode draggedNode = null;
    private float dragOffsetX;
    private float dragOffsetY;

    // Выделенный узел
    private EditorStateNode selectedNode = null;
    private EditorStateNode hoveredNode = null;

    // Интерактивное протягивание провода связи
    private EditorStateNode connectingSourceNode = null;
    private int connectingPortIndex = -1;

    // Кнопка сохранения
    private Button saveButton;

    private final CompoundTag initialOverrides;

    public NodeGraphScreen(Component title, UUID targetAnomalyId, EditorGraphModel graph, CompoundTag initialOverrides) {
        super(title);
        this.targetAnomalyId = targetAnomalyId;
        this.graph = graph != null ? graph : new EditorGraphModel();
        this.initialOverrides = initialOverrides != null ? initialOverrides.copy() : new CompoundTag();
    }

    public static void openLiveInspector(UUID anomalyId, String anomalyType, CompoundTag overrides) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        var scriptOpt = net.void_.anomalies.dsl.registry.AnomalyScriptRegistry.get(anomalyType);

        EditorGraphModel graph;
        if (scriptOpt.isPresent()) {
            graph = net.void_.anomalies.client.gui.editor.converter.GraphModelConverter.fromScriptModel(scriptOpt.get(), null);
        } else {
            graph = new EditorGraphModel();
            EditorStateNode defaultNode = new EditorStateNode("idle", 150, 150);
            defaultNode.setInitial(true);
            graph.addNode(defaultNode);
        }

        // Подсвечиваем активный стейт живой сущности в мире
        if (mc.level != null) {
            net.void_.lightentity.storage.WorldLightManager mgr = net.void_.lightentity.storage.WorldLightManager.get(mc.level);
            net.void_.lightentity.api.ILightEntity le = mgr.getEntity(anomalyId);
            if (le instanceof net.void_.anomalies.core.IAnomalyHolder anomaly) {
                EditorStateNode activeNode = graph.getNode(anomaly.getCurrentState());
                if (activeNode != null) {
                    activeNode.setCurrentlyActive(true);
                }
            }
        }

        mc.setScreen(new NodeGraphScreen(
                Component.literal("Редактор аномалии [" + anomalyType + "]"),
                anomalyId,
                graph,
                overrides
        ));
    }

    public static void openTemplateStudio() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        EditorGraphModel graph = new EditorGraphModel();

        // Базовый шаблон стейтов
        EditorStateNode idle = new EditorStateNode("idle", 100, 120);
        idle.setInitial(true);
        idle.addTransition(new net.void_.anomalies.client.gui.editor.model.EditorTransition("idle", "warmup", "player.in_zone(0)"));

        EditorStateNode warmup = new EditorStateNode("warmup", 360, 120);
        warmup.addTransition(new net.void_.anomalies.client.gui.editor.model.EditorTransition("warmup", "burst", "timer(20)"));

        EditorStateNode burst = new EditorStateNode("burst", 620, 120);
        burst.addTransition(new net.void_.anomalies.client.gui.editor.model.EditorTransition("burst", "cooldown", "timer(40)"));

        EditorStateNode cooldown = new EditorStateNode("cooldown", 880, 120);
        cooldown.addTransition(new net.void_.anomalies.client.gui.editor.model.EditorTransition("cooldown", "idle", "timer(60)"));

        graph.addNode(idle);
        graph.addNode(warmup);
        graph.addNode(burst);
        graph.addNode(cooldown);
        graph.markAllClean();

        mc.setScreen(new NodeGraphScreen(
                Component.literal("Студия шаблонов аномалий (Datapack Studio)"),
                null,
                graph,
                new CompoundTag()
        ));
    }

    // Кастомные плоские кнопки тулбара (в темном стиле студии)
    private static class ToolbarButton {
        final String text;
        final Runnable action;
        int x, y, width, height;
        boolean hovered;

        ToolbarButton(String text, Runnable action) {
            this.text = text;
            this.action = action;
            this.width = 75;
            this.height = 20;
        }

        void render(GuiGraphics gui, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
            this.hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
            int bg = hovered ? 0xFF2D333F : 0xFF1E222B;
            int border = hovered ? 0xFF58A6FF : 0xFF353B47;
            gui.fill(x, y, x + width, y + height, bg);
            gui.renderOutline(x, y, width, height, border);
            int textWidth = font.width(text);
            int textX = x + (width - textWidth) / 2;
            int textY = y + (height - 8) / 2;
            gui.drawString(font, text, textX, textY, hovered ? 0xFFFFFFFF : 0xC0C8D4, false);
        }

        boolean onClick(double mouseX, double mouseY) {
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                action.run();
                return true;
            }
            return false;
        }
    }

    private final java.util.List<ToolbarButton> toolbarButtons = new java.util.ArrayList<>();

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.toolbarButtons.clear();

        // Добавляем кастомные компактные кнопки
        this.toolbarButtons.add(new ToolbarButton("💾 Сохранить", this::onSaveRequested));
        this.toolbarButtons.add(new ToolbarButton("⟲ Сброс", () -> {
            graph.setPanX(100.0f);
            graph.setPanY(100.0f);
            graph.setZoom(1.0f);
        }));
        this.toolbarButtons.add(new ToolbarButton("✦ Авто", graph::autoLayout));

        layoutToolbar();
    }

    private void layoutToolbar() {
        int rightMargin = 8;
        int curX = this.width - rightMargin;
        for (ToolbarButton btn : toolbarButtons) {
            curX -= btn.width;
            btn.x = curX;
            btn.y = 8;
            curX -= 6; // отступ между кнопками
        }
    }

    public void onSaveRequested() {
        if (targetAnomalyId != null) {
            // Live Inspector: сохранение в инстанс в мире через единый пакет
            net.void_.anomalies.network.AnomalyNetwork.CHANNEL.sendToServer(
                    new net.void_.anomalies.network.SyncAnomalyOverridesPacket(targetAnomalyId, initialOverrides)
            );
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("§a[Студия] §fИзменения аномалии сохранены на сервер!"));
            }
        } else {
            // Template Studio: экспорт в файл .anom и метаданные .editor.json
            try {
                java.nio.file.Path dir = java.nio.file.Paths.get("anomalies_export");
                java.nio.file.Files.createDirectories(dir);
                String dsl = net.void_.anomalies.client.gui.editor.converter.GraphModelConverter.toDslText(graph);
                String meta = graph.toJsonMeta();

                java.nio.file.Files.writeString(dir.resolve("custom_anomaly.anom"), dsl);
                java.nio.file.Files.writeString(dir.resolve("custom_anomaly.editor.json"), meta);

                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.sendSystemMessage(Component.literal("§a[Студия] §fШаблон экспортирован в папку anomalies_export/!"));
                }
            } catch (Exception e) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.sendSystemMessage(Component.literal("§c[Студия] Ошибка сохранения: " + e.getMessage()));
                }
            }
        }
        graph.markAllClean();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Темный базовый фон студии (IDE background)
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF14161C);
        // Принудительно сбрасываем буфер GuiGraphics, чтобы фоновый прямоугольник отрисовался ДО сырого Tesselator!
        guiGraphics.flush();

        // 2. Отрисовка фоновой процедурной сетки
        PoseStack poseStack = guiGraphics.pose();
        CanvasRenderUtils.renderGrid(poseStack, this.width, this.height, graph.getPanX(), graph.getPanY(), graph.getZoom());

        // 3. Вычисление координат мыши в пространстве Canvas (с учетом Zoom и Pan)
        float canvasMouseX = (mouseX - graph.getPanX()) / graph.getZoom();
        float canvasMouseY = (mouseY - graph.getPanY()) / graph.getZoom();

        // Поиск наведенной ноды
        this.hoveredNode = null;
        for (EditorStateNode node : graph.getNodes()) {
            if (node.contains(canvasMouseX, canvasMouseY)) {
                this.hoveredNode = node;
                break;
            }
        }

        // 4. Рендеринг графа нод с матричной трансформацией Canvas
        poseStack.pushPose();
        poseStack.translate(graph.getPanX(), graph.getPanY(), 0.0f);
        poseStack.scale(graph.getZoom(), graph.getZoom(), 1.0f);

        // Сначала отрисовываем связи Безье (под карточками)
        NodeCardRenderer.renderTransitions(guiGraphics, graph, hoveredNode);

        // Тянущийся провод при соединении портов
        renderConnectingWire(guiGraphics, canvasMouseX, canvasMouseY);

        // Сбрасываем сырой рендер перед рендером карточек через GuiGraphics
        guiGraphics.flush();

        // Затем отрисовываем сами карточки состояний
        for (EditorStateNode node : graph.getNodes()) {
            boolean isSelected = (node == selectedNode);
            boolean isHovered = (node == hoveredNode);
            NodeCardRenderer.renderNode(guiGraphics, this.font, node, isSelected, isHovered);
        }

        poseStack.popPose();
        guiGraphics.flush();

        // 5. Отрисовка адаптивного статус-бара и плоских кнопок поверх холста
        renderOverlayHeader(guiGraphics, mouseX, mouseY);
    }

    private void renderOverlayHeader(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Плашка верхней панели
        guiGraphics.fill(0, 0, this.width, 36, 0xD0181A22);
        guiGraphics.renderOutline(0, 36, this.width, 1, 0xFF2A2D37);

        // Вычисляем свободное место для текста до кнопок
        int leftmostButtonX = toolbarButtons.isEmpty() ? this.width : toolbarButtons.get(toolbarButtons.size() - 1).x;
        int maxTextRight = leftmostButtonX - 10;

        // Заголовок (с обрезкой если не влезает)
        String titleStr = this.font.plainSubstrByWidth(this.title.getString(), Math.max(60, maxTextRight - 150));
        guiGraphics.drawString(this.font, titleStr, 12, 14, 0xFFFFFF, false);

        int titleWidth = this.font.width(titleStr);
        int statusX = 18 + titleWidth;

        // Индикатор изменений только если для него есть место
        if (statusX + 110 < maxTextRight) {
            if (graph.isDirty()) {
                guiGraphics.drawString(this.font, "● Не сохранено", statusX, 14, 0xFF58A6FF, false);
            } else {
                guiGraphics.drawString(this.font, "✓ Синхронизировано", statusX, 14, 0xFF3FB950, false);
            }
        }

        // Отрисовка кастомных кнопок
        for (ToolbarButton btn : toolbarButtons) {
            btn.render(guiGraphics, this.font, mouseX, mouseY);
        }
    }

    private void renderConnectingWire(GuiGraphics guiGraphics, float mouseCanvasX, float mouseCanvasY) {
        if (connectingSourceNode != null && connectingPortIndex >= 0) {
            float x1 = connectingSourceNode.getOutputPortX(connectingPortIndex);
            float y1 = connectingSourceNode.getOutputPortY(connectingPortIndex);
            CanvasRenderUtils.renderBezierCurve(guiGraphics.pose(), x1, y1, mouseCanvasX, mouseCanvasY, 0xFFFFAA00, 0xFF58A6FF, 3.0f);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY <= 36) {
            if (button == 0) {
                for (ToolbarButton btn : toolbarButtons) {
                    if (btn.onClick(mouseX, mouseY)) {
                        return true;
                    }
                }
            }
            return true;
        }

        float canvasMouseX = (float) ((mouseX - graph.getPanX()) / graph.getZoom());
        float canvasMouseY = (float) ((mouseY - graph.getPanY()) / graph.getZoom());

        if (button == 0) { // ЛКМ
            // 1. Проверяем клик по выходному порту (оранжевый кружок справа) для протягивания провода
            for (EditorStateNode node : graph.getNodes()) {
                var transitions = node.getOutgoingTransitions();
                for (int i = 0; i < transitions.size(); i++) {
                    float px = node.getOutputPortX(i);
                    float py = node.getOutputPortY(i);
                    if (Math.abs(canvasMouseX - px) <= 8 && Math.abs(canvasMouseY - py) <= 8) {
                        this.connectingSourceNode = node;
                        this.connectingPortIndex = i;
                        return true;
                    }
                }
            }

            // 2. Клик по самой ноде для выделения и перетаскивания
            for (EditorStateNode node : graph.getNodes()) {
                if (node.contains(canvasMouseX, canvasMouseY)) {
                    this.selectedNode = node;
                    this.draggedNode = node;
                    this.dragOffsetX = canvasMouseX - node.getX();
                    this.dragOffsetY = canvasMouseY - node.getY();
                    return true;
                }
            }
            // Клик в пустоту
            this.selectedNode = null;
        } else if (button == 2 || (button == 0 && hasShiftDown())) { // СКМ или Shift+ЛКМ: панорамирование
            this.isPanning = true;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggedNode = null;

            // Если тянули провод связи и отпустили
            if (connectingSourceNode != null) {
                float canvasMouseX = (float) ((mouseX - graph.getPanX()) / graph.getZoom());
                float canvasMouseY = (float) ((mouseY - graph.getPanY()) / graph.getZoom());

                // Ищем целевую ноду, над которой отпустили мышь (над входным портом или телом ноды)
                for (EditorStateNode targetNode : graph.getNodes()) {
                    if (targetNode != connectingSourceNode) {
                        float inX = targetNode.getInputPortX();
                        float inY = targetNode.getInputPortY();
                        boolean hitPort = Math.abs(canvasMouseX - inX) <= 12 && Math.abs(canvasMouseY - inY) <= 12;
                        boolean hitNode = targetNode.contains(canvasMouseX, canvasMouseY);

                        if (hitPort || hitNode) {
                            // Перенаправляем или соединяем переход
                            var transitions = connectingSourceNode.getOutgoingTransitions();
                            if (connectingPortIndex >= 0 && connectingPortIndex < transitions.size()) {
                                transitions.get(connectingPortIndex).setTargetState(targetNode.getStateName());
                            }
                            graph.setDirty(true);
                            break;
                        }
                    }
                }
                this.connectingSourceNode = null;
                this.connectingPortIndex = -1;
            }
        }
        if (button == 2 || button == 0) {
            this.isPanning = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isPanning) {
            graph.setPanX(graph.getPanX() + (float) dragX);
            graph.setPanY(graph.getPanY() + (float) dragY);
            return true;
        }

        if (draggedNode != null) {
            float canvasMouseX = (float) ((mouseX - graph.getPanX()) / graph.getZoom());
            float canvasMouseY = (float) ((mouseY - graph.getPanY()) / graph.getZoom());
            draggedNode.setX(canvasMouseX - dragOffsetX);
            draggedNode.setY(canvasMouseY - dragOffsetY);
            graph.setDirty(true);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float oldZoom = graph.getZoom();
        float zoomFactor = delta > 0 ? 1.1f : 0.9f;
        float newZoom = Math.max(0.3f, Math.min(2.0f, oldZoom * zoomFactor));

        if (newZoom != oldZoom) {
            // Зуммирование относительно положения курсора мыши
            float mouseCanvasX = (float) ((mouseX - graph.getPanX()) / oldZoom);
            float mouseCanvasY = (float) ((mouseY - graph.getPanY()) / oldZoom);

            graph.setZoom(newZoom);
            graph.setPanX((float) (mouseX - mouseCanvasX * newZoom));
            graph.setPanY((float) (mouseY - mouseCanvasY * newZoom));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
