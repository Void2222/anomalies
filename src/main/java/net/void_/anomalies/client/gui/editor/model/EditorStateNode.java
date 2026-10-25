package net.void_.anomalies.client.gui.editor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * UI-представление ноды состояния конечного автомата (FSM State).
 */
public class EditorStateNode {
    private String stateName;
    private float x;
    private float y;
    private float width = 160.0f;
    private float height = 80.0f;

    private boolean isInitial = false;
    private boolean isCurrentlyActive = false; // Для live-инспектора мира
    private NodeDiffStatus diffStatus = NodeDiffStatus.CLEAN;

    private String boundJsonPath = "";
    private final List<String> recipes = new ArrayList<>();
    private final List<EditorTransition> outgoingTransitions = new ArrayList<>();

    public EditorStateNode(String stateName, float x, float y) {
        this.stateName = stateName;
        this.x = x;
        this.y = y;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
        markModified();
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean isInitial() {
        return isInitial;
    }

    public void setInitial(boolean initial) {
        isInitial = initial;
        markModified();
    }

    public boolean isCurrentlyActive() {
        return isCurrentlyActive;
    }

    public void setCurrentlyActive(boolean currentlyActive) {
        isCurrentlyActive = currentlyActive;
    }

    public NodeDiffStatus getDiffStatus() {
        return diffStatus;
    }

    public void setDiffStatus(NodeDiffStatus diffStatus) {
        this.diffStatus = diffStatus;
    }

    public void markModified() {
        if (this.diffStatus == NodeDiffStatus.CLEAN) {
            this.diffStatus = NodeDiffStatus.MODIFIED;
        }
    }

    public String getBoundJsonPath() {
        return boundJsonPath;
    }

    public void setBoundJsonPath(String boundJsonPath) {
        this.boundJsonPath = boundJsonPath;
        markModified();
    }

    public List<String> getRecipes() {
        return recipes;
    }

    public List<EditorTransition> getOutgoingTransitions() {
        return outgoingTransitions;
    }

    public void addTransition(EditorTransition transition) {
        this.outgoingTransitions.add(transition);
        updateHeight();
        markModified();
    }

    public void removeTransition(EditorTransition transition) {
        this.outgoingTransitions.remove(transition);
        updateHeight();
        markModified();
    }

    public void updateHeight() {
        // Базовая высота шапки и баундинга + высота портов переходов
        this.height = Math.max(70.0f, 46.0f + outgoingTransitions.size() * 20.0f);
    }

    public boolean contains(float canvasX, float canvasY) {
        return canvasX >= x && canvasX <= x + width && canvasY >= y && canvasY <= y + height;
    }

    /**
     * Координаты входного порта (слева по центру)
     */
    public float getInputPortX() {
        return x;
    }

    public float getInputPortY() {
        return y + 25.0f;
    }

    /**
     * Координаты выходного порта для конкретного перехода
     */
    public float getOutputPortX(int transitionIndex) {
        return x + width;
    }

    public float getOutputPortY(int transitionIndex) {
        return y + 42.0f + transitionIndex * 20.0f;
    }
}
