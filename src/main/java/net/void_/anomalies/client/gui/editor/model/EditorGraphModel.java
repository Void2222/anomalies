package net.void_.anomalies.client.gui.editor.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * Модель данных всего графа редактора со списком состояний, связей и позициями на холсте.
 */
public class EditorGraphModel {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, EditorStateNode> nodes = new LinkedHashMap<>();
    private final List<EditorPortalNode> portals = new ArrayList<>();
    private float panX = 100.0f;
    private float panY = 100.0f;
    private float zoom = 1.0f;
    private boolean isDirty = false;

    public void addNode(EditorStateNode node) {
        nodes.put(node.getStateName(), node);
        node.updateHeight();
        isDirty = true;
    }

    public void addPortal(EditorPortalNode portal) {
        portals.add(portal);
        isDirty = true;
    }

    public void removePortal(EditorPortalNode portal) {
        portals.remove(portal);
        isDirty = true;
    }

    public List<EditorPortalNode> getPortals() {
        return portals;
    }

    public void removeNode(String stateName) {
        nodes.remove(stateName);
        portals.removeIf(p -> p.getTargetState().equals(stateName));
        // Удаляем все переходы, ведущие в эту ноду
        for (EditorStateNode node : nodes.values()) {
            node.getOutgoingTransitions().removeIf(t -> t.getTargetState().equals(stateName));
            node.updateHeight();
        }
        isDirty = true;
    }

    public EditorStateNode getNode(String stateName) {
        return nodes.get(stateName);
    }

    public Collection<EditorStateNode> getNodes() {
        return nodes.values();
    }

    public float getPanX() {
        return panX;
    }

    public void setPanX(float panX) {
        this.panX = panX;
    }

    public float getPanY() {
        return panY;
    }

    public void setPanY(float panY) {
        this.panY = panY;
    }

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = Math.max(0.3f, Math.min(2.0f, zoom));
    }

    public boolean isDirty() {
        if (isDirty) return true;
        for (EditorStateNode node : nodes.values()) {
            if (node.getDiffStatus() != NodeDiffStatus.CLEAN) return true;
            for (EditorTransition t : node.getOutgoingTransitions()) {
                if (t.getDiffStatus() != NodeDiffStatus.CLEAN) return true;
            }
        }
        return false;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public void markAllClean() {
        this.isDirty = false;
        for (EditorStateNode node : nodes.values()) {
            node.setDiffStatus(NodeDiffStatus.CLEAN);
            for (EditorTransition t : node.getOutgoingTransitions()) {
                t.setDiffStatus(NodeDiffStatus.CLEAN);
            }
        }
    }

    /**
     * Сериализация координат и масштаба в JSON для файла .editor.json
     */
    public String toJsonMeta() {
        JsonObject root = new JsonObject();
        JsonObject canvas = new JsonObject();
        canvas.addProperty("panX", panX);
        canvas.addProperty("panY", panY);
        canvas.addProperty("zoom", zoom);
        root.add("canvas", canvas);

        JsonObject nodesObj = new JsonObject();
        for (EditorStateNode node : nodes.values()) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", node.getX());
            pos.addProperty("y", node.getY());
            nodesObj.add(node.getStateName(), pos);
        }
        root.add("nodes", nodesObj);

        JsonObject portalsObj = new JsonObject();
        for (EditorPortalNode portal : portals) {
            JsonObject p = new JsonObject();
            p.addProperty("id", portal.getId());
            p.addProperty("target", portal.getTargetState());
            p.addProperty("x", portal.getX());
            p.addProperty("y", portal.getY());
            portalsObj.add(portal.getId(), p);
        }
        root.add("portals", portalsObj);

        return GSON.toJson(root);
    }

    /**
     * Загрузка координат из JSON файла .editor.json
     */
    public void applyJsonMeta(JsonObject root) {
        if (root == null) return;
        if (root.has("canvas")) {
            JsonObject canvas = root.getAsJsonObject("canvas");
            if (canvas.has("panX")) this.panX = canvas.get("panX").getAsFloat();
            if (canvas.has("panY")) this.panY = canvas.get("panY").getAsFloat();
            if (canvas.has("zoom")) this.zoom = canvas.get("zoom").getAsFloat();
        }

        if (root.has("nodes")) {
            JsonObject nodesObj = root.getAsJsonObject("nodes");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : nodesObj.entrySet()) {
                EditorStateNode node = nodes.get(entry.getKey());
                if (node != null && entry.getValue().isJsonObject()) {
                    JsonObject pos = entry.getValue().getAsJsonObject();
                    if (pos.has("x")) node.setX(pos.get("x").getAsFloat());
                    if (pos.has("y")) node.setY(pos.get("y").getAsFloat());
                }
            }
        }

        if (root.has("portals")) {
            JsonObject portalsObj = root.getAsJsonObject("portals");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : portalsObj.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    JsonObject p = entry.getValue().getAsJsonObject();
                    String portalId = entry.getKey();
                    float px = p.has("x") ? p.get("x").getAsFloat() : 0.0f;
                    float py = p.has("y") ? p.get("y").getAsFloat() : 0.0f;
                    String target = p.has("target") ? p.get("target").getAsString() : "";

                    EditorPortalNode existing = null;
                    for (EditorPortalNode portal : portals) {
                        if (portal.getId().equals(portalId)) {
                            existing = portal;
                            break;
                        }
                    }
                    if (existing != null) {
                        existing.setX(px);
                        existing.setY(py);
                        if (!target.isEmpty()) existing.setTargetState(target);
                    } else if (!target.isEmpty()) {
                        portals.add(new EditorPortalNode(portalId, target, px, py));
                    }
                }
            }
        }
    }

    /**
     * Авто-расстановка нод слева направо (Sugiyama-style layout) если нет .editor.json
     */
    public void autoLayout() {
        float startX = 60.0f;
        float currentX = startX;
        float currentY = 80.0f;
        float columnWidth = 240.0f;
        float rowHeight = 120.0f;

        int index = 0;
        for (EditorStateNode node : nodes.values()) {
            node.setX(currentX);
            node.setY(currentY);

            currentY += rowHeight;
            index++;
            if (index % 3 == 0) {
                currentX += columnWidth;
                currentY = 80.0f;
            }
        }
    }
}
