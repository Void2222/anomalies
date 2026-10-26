package net.void_.anomalies.client.gui.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.client.gui.editor.canvas.CanvasRenderUtils;
import net.void_.anomalies.client.gui.editor.canvas.NodeCardRenderer;
import net.void_.anomalies.client.gui.editor.converter.GraphModelConverter;
import net.void_.anomalies.client.gui.editor.model.EditorGraphModel;
import net.void_.anomalies.client.gui.editor.model.EditorPortalNode;
import net.void_.anomalies.client.gui.editor.model.EditorStateNode;
import net.void_.anomalies.client.gui.editor.model.NodeDiffStatus;
import net.void_.anomalies.network.AnomalyNetwork;
import net.void_.anomalies.network.SyncAnomalyOverridesPacket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Главный экран холста нодового редактора аномалий (Node Graph Studio).
 * Поддерживает бесконечный холст, Pan/Zoom, инлайн-аккордеон параметров,
 * свободное перемещение Jump-порталов и нижнюю панель действий (Bottom Dock).
 */
public class NodeGraphScreen extends Screen {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final UUID targetAnomalyId; // null если шаблон из файла
    private final String anomalyType;
    private final EditorGraphModel graph;
    private final CompoundTag initialOverrides;

    // Состояние холста и мыши
    private boolean isPanning = false;
    private double lastMouseX;
    private double lastMouseY;

    // Drag-and-drop ноды
    private EditorStateNode draggedNode = null;
    private float dragOffsetX;
    private float dragOffsetY;

    // Drag-and-drop портала
    private EditorPortalNode draggedPortal = null;
    private float dragPortalOffsetX;
    private float dragPortalOffsetY;
    private float portalMouseDownX;
    private float portalMouseDownY;
    private boolean portalMoved = false;

    // Выделенный узел
    private EditorStateNode selectedNode = null;
    private EditorStateNode hoveredNode = null;

    // Интерактивное протягивание провода связи
    private EditorStateNode connectingSourceNode = null;
    private int connectingPortIndex = -1;

    // Кастомные плоские кнопки тулбара и нижней панели
    public static class FlatStudioButton {
        final String text;
        final Runnable action;
        int x, y, width, height;
        boolean hovered;
        int customColor = 0;

        public FlatStudioButton(String text, int width, Runnable action) {
            this.text = text;
            this.width = width;
            this.height = 20;
            this.action = action;
        }

        public FlatStudioButton(String text, int width, int customColor, Runnable action) {
            this(text, width, action);
            this.customColor = customColor;
        }

        void render(GuiGraphics gui, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
            this.hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
            int bg = hovered ? 0xFF2D333F : 0xFF1E222B;
            int border = hovered ? (customColor != 0 ? customColor : 0xFF58A6FF) : 0xFF353B47;
            gui.fill(x, y, x + width, y + height, bg);
            gui.renderOutline(x, y, width, height, border);
            int textWidth = font.width(text);
            int textX = x + (width - textWidth) / 2;
            int textY = y + (height - 8) / 2;
            int textColor = customColor != 0 ? customColor : (hovered ? 0xFFFFFFFF : 0xC0C8D4);
            gui.drawString(font, text, textX, textY, textColor, false);
        }

        boolean onClick(double mouseX, double mouseY) {
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                action.run();
                return true;
            }
            return false;
        }
    }

    private final List<FlatStudioButton> toolbarButtons = new ArrayList<>();
    private final List<FlatStudioButton> bottomDockButtons = new ArrayList<>();

    public NodeGraphScreen(Component title, UUID targetAnomalyId, String anomalyType, EditorGraphModel graph, CompoundTag initialOverrides) {
        super(title);
        this.targetAnomalyId = targetAnomalyId;
        this.anomalyType = anomalyType != null ? anomalyType : "custom";
        this.graph = graph != null ? graph : new EditorGraphModel();
        this.initialOverrides = initialOverrides != null ? initialOverrides.copy() : new CompoundTag();
    }

    public static void openLiveInspector(UUID anomalyId, String anomalyType, CompoundTag overrides) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        var scriptOpt = net.void_.anomalies.dsl.registry.AnomalyScriptRegistry.get(anomalyType);

        EditorGraphModel graph;
        if (scriptOpt.isPresent()) {
            graph = GraphModelConverter.fromScriptModel(scriptOpt.get(), null);
        } else {
            graph = new EditorGraphModel();
            EditorStateNode defaultNode = new EditorStateNode("idle", 150, 150);
            defaultNode.setInitial(true);
            graph.addNode(defaultNode);
        }

        // Загружаем определения состояний из реестра
        for (EditorStateNode node : graph.getNodes()) {
            AnomalyDefinition def = AnomalyReloadListener.get(anomalyType, node.getStateName());
            if (def != null) {
                node.applyDefinition(def);
            }
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
                anomalyType,
                graph,
                overrides
        ));
    }

    public static void openTemplateStudio() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        EditorGraphModel graph = new EditorGraphModel();

        // Базовый шаблон стейтов
        EditorStateNode idle = new EditorStateNode("idle", 80, 120);
        idle.setInitial(true);
        idle.addTransition(new net.void_.anomalies.client.gui.editor.model.EditorTransition("idle", "warmup", "player.in_zone(0)"));

        EditorStateNode warmup = new EditorStateNode("warmup", 340, 120);
        warmup.addTransition(new net.void_.anomalies.client.gui.editor.model.EditorTransition("warmup", "burst", "timer(20)"));

        EditorStateNode burst = new EditorStateNode("burst", 600, 120);
        burst.addTransition(new net.void_.anomalies.client.gui.editor.model.EditorTransition("burst", "cooldown", "timer(40)"));

        EditorStateNode cooldown = new EditorStateNode("cooldown", 860, 120);
        cooldown.addTransition(new net.void_.anomalies.client.gui.editor.model.EditorTransition("cooldown", "idle", "timer(60)"));

        // Авто-портал для замыкания cooldown -> idle
        graph.addPortal(new EditorPortalNode("cooldown_0", "idle", 1080, 150));

        graph.addNode(idle);
        graph.addNode(warmup);
        graph.addNode(burst);
        graph.addNode(cooldown);
        graph.markAllClean();

        mc.setScreen(new NodeGraphScreen(
                Component.literal("Студия шаблонов аномалий (Datapack Studio)"),
                null,
                "custom_anomaly",
                graph,
                new CompoundTag()
        ));
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.toolbarButtons.clear();
        this.bottomDockButtons.clear();

        // Верхний тулбар
        this.toolbarButtons.add(new FlatStudioButton("💾 Сохранить", 80, 0xFF3FB950, this::onSaveRequested));
        this.toolbarButtons.add(new FlatStudioButton("⟲ Сброс", 65, () -> {
            graph.setPanX(80.0f);
            graph.setPanY(80.0f);
            graph.setZoom(1.0f);
        }));
        this.toolbarButtons.add(new FlatStudioButton("✦ Авто", 65, graph::autoLayout));

        // Нижняя плавающая док-панель (Bottom Action Bar)
        this.bottomDockButtons.add(new FlatStudioButton("+ Состояние", 85, 0xFF58A6FF, this::addNewState));
        this.bottomDockButtons.add(new FlatStudioButton("+ Jump-портал", 95, 0xFF00FFCC, this::addJumpPortal));
        this.bottomDockButtons.add(new FlatStudioButton("🎯 Фокус [START]", 100, this::focusInitialState));

        layoutUI();
    }

    private void layoutUI() {
        // Верхний тулбар (справа)
        int curX = this.width - 8;
        for (FlatStudioButton btn : toolbarButtons) {
            curX -= btn.width;
            btn.x = curX;
            btn.y = 8;
            curX -= 6;
        }

        // Нижняя док-панель (по центру)
        int totalDockW = 0;
        for (FlatStudioButton btn : bottomDockButtons) {
            totalDockW += btn.width + 6;
        }
        totalDockW -= 6;

        int dockStartX = (this.width - totalDockW) / 2;
        int dockY = this.height - 30;
        int bx = dockStartX;
        for (FlatStudioButton btn : bottomDockButtons) {
            btn.x = bx;
            btn.y = dockY;
            bx += btn.width + 6;
        }
    }

    private void addNewState() {
        float cx = (this.width / 2.0f - graph.getPanX()) / graph.getZoom();
        float cy = (this.height / 2.0f - graph.getPanY()) / graph.getZoom();

        int counter = 1;
        String newName = "state_" + counter;
        while (graph.getNode(newName) != null) {
            counter++;
            newName = "state_" + counter;
        }

        EditorStateNode node = new EditorStateNode(newName, cx - 80.0f, cy - 40.0f);
        node.setDiffStatus(NodeDiffStatus.ADDED);
        graph.addNode(node);
        this.selectedNode = node;
        graph.setDirty(true);
    }

    private void addJumpPortal() {
        float cx = (this.width / 2.0f - graph.getPanX()) / graph.getZoom();
        float cy = (this.height / 2.0f - graph.getPanY()) / graph.getZoom();

        String target = "idle";
        if (selectedNode != null) {
            target = selectedNode.getStateName();
        }

        String portalId = "portal_" + (System.currentTimeMillis() % 100000);
        EditorPortalNode portal = new EditorPortalNode(portalId, target, cx - 50.0f, cy - 11.0f);
        graph.addPortal(portal);
        graph.setDirty(true);
    }

    private void focusInitialState() {
        EditorStateNode initNode = null;
        for (EditorStateNode n : graph.getNodes()) {
            if (n.isInitial()) {
                initNode = n;
                break;
            }
        }
        if (initNode == null && !graph.getNodes().isEmpty()) {
            initNode = graph.getNodes().iterator().next();
        }
        if (initNode != null) {
            graph.setPanX(this.width / 2.0f - (initNode.getX() + initNode.getWidth() / 2.0f) * graph.getZoom());
            graph.setPanY(this.height / 2.0f - (initNode.getY() + initNode.getHeight() / 2.0f) * graph.getZoom());
        }
    }

    public void onSaveRequested() {
        if (targetAnomalyId != null) {
            // Live Inspector: сохранение параметров зон в сущность мира
            EditorStateNode activeOrSelected = (selectedNode != null) ? selectedNode : null;
            if (activeOrSelected == null) {
                for (EditorStateNode n : graph.getNodes()) {
                    if (n.isCurrentlyActive()) {
                        activeOrSelected = n;
                        break;
                    }
                }
            }

            CompoundTag overrides = this.initialOverrides.copy();
            if (activeOrSelected != null) {
                var zones = activeOrSelected.getDraftZones();
                overrides.putInt("zones_count", zones.size());
                for (int i = 0; i < zones.size(); i++) {
                    var z = zones.get(i);
                    overrides.putDouble("zone_" + i + "_radius", z.radius());
                    double dmg = (z.damage() != null && z.damage().innerAmount() != null) ? z.damage().innerAmount().getDouble() : 0.0;
                    overrides.putDouble("zone_" + i + "_damage", dmg);
                    double pull = (z.physics() != null) ? z.physics().pullForce() : 0.0;
                    overrides.putDouble("zone_" + i + "_pullForce", pull);
                    overrides.putDouble("zone_" + i + "_spinForce", z.physics() != null ? z.physics().spinForce() : 0.0);
                    overrides.putDouble("zone_" + i + "_impulseY", z.physics() != null ? z.physics().impulseY() : 0.0);
                }
            }

            AnomalyNetwork.CHANNEL.sendToServer(new SyncAnomalyOverridesPacket(targetAnomalyId, overrides));
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("§a[Студия] §fПараметры зон аномалии сохранены на сервер!"));
            }
        } else {
            // Template Studio: сохранение .anom, .editor.json и states/<state>.json
            try {
                Path dir = Paths.get("anomalies_export");
                Path statesDir = dir.resolve("states");
                Files.createDirectories(statesDir);

                String dsl = GraphModelConverter.toDslText(graph);
                String meta = graph.toJsonMeta();

                Files.writeString(dir.resolve("custom_anomaly.anom"), dsl);
                Files.writeString(dir.resolve("custom_anomaly.editor.json"), meta);

                // Экспорт каждого состояния в states/<state>.json
                for (EditorStateNode node : graph.getNodes()) {
                    AnomalyDefinition def = node.toDefinition();
                    String json = GSON.toJson(def);
                    Files.writeString(statesDir.resolve(node.getStateName() + ".json"), json);
                }

                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.sendSystemMessage(Component.literal("§a[Студия] §fШаблон и JSON-стейты экспортированы в папку anomalies_export/!"));
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
        // 1. Базовый фон
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF12141A);
        guiGraphics.flush();

        // 2. Фоновая процедурная сетка
        PoseStack poseStack = guiGraphics.pose();
        CanvasRenderUtils.renderGrid(poseStack, this.width, this.height, graph.getPanX(), graph.getPanY(), graph.getZoom());

        // 3. Вычисление координат мыши в Canvas-пространстве
        float canvasMouseX = (mouseX - graph.getPanX()) / graph.getZoom();
        float canvasMouseY = (mouseY - graph.getPanY()) / graph.getZoom();

        this.hoveredNode = null;
        for (EditorStateNode node : graph.getNodes()) {
            if (node.contains(canvasMouseX, canvasMouseY)) {
                this.hoveredNode = node;
                break;
            }
        }

        // 4. Рендеринг графа нод
        poseStack.pushPose();
        poseStack.translate(graph.getPanX(), graph.getPanY(), 0.0f);
        poseStack.scale(graph.getZoom(), graph.getZoom(), 1.0f);

        // Связи Безье
        NodeCardRenderer.renderTransitions(guiGraphics, graph, hoveredNode);

        // Тянущийся провод
        renderConnectingWire(guiGraphics, canvasMouseX, canvasMouseY);
        guiGraphics.flush();

        // Порталы (Jump Nodes)
        for (EditorPortalNode portal : graph.getPortals()) {
            boolean isHovered = portal.contains(canvasMouseX, canvasMouseY);
            NodeCardRenderer.renderPortal(guiGraphics, this.font, portal, isHovered);
        }

        // Карточки состояний
        for (EditorStateNode node : graph.getNodes()) {
            boolean isSelected = (node == selectedNode);
            boolean isHovered = (node == hoveredNode);
            NodeCardRenderer.renderNode(guiGraphics, this.font, node, isSelected, isHovered);
        }

        poseStack.popPose();
        guiGraphics.flush();

        // 5. Нижняя док-панель (Bottom Action Bar)
        renderBottomDock(guiGraphics, mouseX, mouseY);

        // 6. Верхний статус-бар и тулбар
        renderOverlayHeader(guiGraphics, mouseX, mouseY);
    }

    private void renderBottomDock(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (bottomDockButtons.isEmpty()) return;

        int totalDockW = 0;
        for (FlatStudioButton btn : bottomDockButtons) {
            totalDockW += btn.width + 6;
        }
        totalDockW -= 6;

        int dockStartX = (this.width - totalDockW) / 2;
        int dockY = this.height - 30;

        // Фон плавающей док-панели
        guiGraphics.fill(dockStartX - 8, dockY - 4, dockStartX + totalDockW + 8, dockY + 24, 0xD0181A24);
        guiGraphics.renderOutline(dockStartX - 8, dockY - 4, totalDockW + 16, 28, 0xFF2A2D37);

        for (FlatStudioButton btn : bottomDockButtons) {
            btn.render(guiGraphics, this.font, mouseX, mouseY);
        }
    }

    private void renderOverlayHeader(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, this.width, 36, 0xD0181A22);
        guiGraphics.renderOutline(0, 36, this.width, 1, 0xFF2A2D37);

        int leftmostButtonX = toolbarButtons.isEmpty() ? this.width : toolbarButtons.get(toolbarButtons.size() - 1).x;
        int maxTextRight = leftmostButtonX - 10;

        String titleStr = this.font.plainSubstrByWidth(this.title.getString(), Math.max(60, maxTextRight - 150));
        guiGraphics.drawString(this.font, titleStr, 12, 14, 0xFFFFFF, false);

        int titleWidth = this.font.width(titleStr);
        int statusX = 18 + titleWidth;

        if (statusX + 110 < maxTextRight) {
            if (graph.isDirty()) {
                guiGraphics.drawString(this.font, "● Черновик (не сохранено)", statusX, 14, 0xFF58A6FF, false);
            } else {
                guiGraphics.drawString(this.font, "✓ Синхронизировано", statusX, 14, 0xFF3FB950, false);
            }
        }

        for (FlatStudioButton btn : toolbarButtons) {
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
        // Клик по верхнему тулбару
        if (mouseY <= 36) {
            if (button == 0) {
                for (FlatStudioButton btn : toolbarButtons) {
                    if (btn.onClick(mouseX, mouseY)) return true;
                }
            }
            return true;
        }

        // Клик по нижней док-панели
        if (mouseY >= this.height - 34) {
            if (button == 0) {
                for (FlatStudioButton btn : bottomDockButtons) {
                    if (btn.onClick(mouseX, mouseY)) return true;
                }
            }
            return true;
        }

        float canvasMouseX = (float) ((mouseX - graph.getPanX()) / graph.getZoom());
        float canvasMouseY = (float) ((mouseY - graph.getPanY()) / graph.getZoom());

        if (button == 0) { // ЛКМ
            // 1. Проверяем клик по выходному порту для протягивания провода
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

            // 2. Клик / Drag по ноде-порталу (Jump-портал)
            for (EditorPortalNode portal : graph.getPortals()) {
                if (portal.contains(canvasMouseX, canvasMouseY)) {
                    this.draggedPortal = portal;
                    this.dragPortalOffsetX = canvasMouseX - portal.getX();
                    this.dragPortalOffsetY = canvasMouseY - portal.getY();
                    this.portalMouseDownX = canvasMouseX;
                    this.portalMouseDownY = canvasMouseY;
                    this.portalMoved = false;
                    return true;
                }
            }

            // 3. Клик по самой ноде (контролы аккордеона ИЛИ drag ноды)
            for (EditorStateNode node : graph.getNodes()) {
                if (node.contains(canvasMouseX, canvasMouseY)) {
                    this.selectedNode = node;

                    // Проверяем интерактивные контролы инспектора ноды
                    if (NodeCardRenderer.handleNodeClick(node, canvasMouseX, canvasMouseY)) {
                        graph.setDirty(true);
                        return true;
                    }

                    // Если клик не поглощен контролом — начинаем перетаскивание ноды
                    this.draggedNode = node;
                    this.dragOffsetX = canvasMouseX - node.getX();
                    this.dragOffsetY = canvasMouseY - node.getY();
                    return true;
                }
            }

            this.selectedNode = null;
        } else if (button == 2 || (button == 0 && hasShiftDown())) {
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
            // Обработка клика/перемещения портала
            if (draggedPortal != null) {
                if (!portalMoved) {
                    // Одиночный клик: мгновенный прыжок камеры к целевой ноде!
                    EditorStateNode target = graph.getNode(draggedPortal.getTargetState());
                    if (target != null) {
                        this.selectedNode = target;
                        graph.setPanX(this.width / 2.0f - (target.getX() + target.getWidth() / 2.0f) * graph.getZoom());
                        graph.setPanY(this.height / 2.0f - (target.getY() + target.getHeight() / 2.0f) * graph.getZoom());
                    }
                }
                this.draggedPortal = null;
                this.portalMoved = false;
            }

            this.draggedNode = null;

            // Если соединяли провод связи
            if (connectingSourceNode != null) {
                float canvasMouseX = (float) ((mouseX - graph.getPanX()) / graph.getZoom());
                float canvasMouseY = (float) ((mouseY - graph.getPanY()) / graph.getZoom());

                for (EditorStateNode targetNode : graph.getNodes()) {
                    if (targetNode != connectingSourceNode) {
                        float inX = targetNode.getInputPortX();
                        float inY = targetNode.getInputPortY();
                        boolean hitPort = Math.abs(canvasMouseX - inX) <= 12 && Math.abs(canvasMouseY - inY) <= 12;
                        boolean hitNode = targetNode.contains(canvasMouseX, canvasMouseY);

                        if (hitPort || hitNode) {
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

        float canvasMouseX = (float) ((mouseX - graph.getPanX()) / graph.getZoom());
        float canvasMouseY = (float) ((mouseY - graph.getPanY()) / graph.getZoom());

        // Перетаскивание портала
        if (draggedPortal != null) {
            float dx = canvasMouseX - portalMouseDownX;
            float dy = canvasMouseY - portalMouseDownY;
            if (Math.hypot(dx, dy) > 3.0) {
                portalMoved = true;
            }
            draggedPortal.setX(canvasMouseX - dragPortalOffsetX);
            draggedPortal.setY(canvasMouseY - dragPortalOffsetY);
            graph.setDirty(true);
            return true;
        }

        // Перетаскивание ноды
        if (draggedNode != null) {
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Клавиша 'P' — превратить последний переход выбранной ноды в Jump-портал
        if (keyCode == 80 && selectedNode != null) {
            var transitions = selectedNode.getOutgoingTransitions();
            if (!transitions.isEmpty()) {
                int lastIdx = transitions.size() - 1;
                var t = transitions.get(lastIdx);
                String portalId = selectedNode.getStateName() + "_" + lastIdx;

                float px = selectedNode.getX() + selectedNode.getWidth() + 45.0f;
                float py = selectedNode.getOutputPortY(lastIdx) - 10.0f;

                graph.getPortals().removeIf(p -> p.getId().equals(portalId));
                graph.addPortal(new EditorPortalNode(portalId, t.getTargetState(), px, py));
                graph.setDirty(true);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
