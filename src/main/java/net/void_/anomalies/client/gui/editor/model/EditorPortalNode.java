package net.void_.anomalies.client.gui.editor.model;

/**
 * Компактная нода-портал (Jump Node).
 * Позволяет замкнуть переход (например, cooldown -> idle) локально,
 * не протягивая длинную кривую через все ноды на холсте.
 */
public class EditorPortalNode {
    private final String id;
    private String targetState;
    private float x;
    private float y;
    private float width = 100.0f;
    private float height = 22.0f;

    public EditorPortalNode(String id, String targetState, float x, float y) {
        this.id = id;
        this.targetState = targetState;
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public String getTargetState() {
        return targetState;
    }

    public void setTargetState(String targetState) {
        this.targetState = targetState;
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

    public boolean contains(float canvasX, float canvasY) {
        return canvasX >= x && canvasX <= x + width && canvasY >= y && canvasY <= y + height;
    }

    public float getInputPortX() {
        return x;
    }

    public float getInputPortY() {
        return y + height / 2.0f;
    }
}
