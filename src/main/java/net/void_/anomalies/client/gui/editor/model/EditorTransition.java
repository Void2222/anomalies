package net.void_.anomalies.client.gui.editor.model;

/**
 * Переход между состояниями (связь в графе).
 */
public class EditorTransition {
    private String sourceState;
    private String targetState;
    private String conditionExpression; // Например, "timer >= 100", "player_in_zone(0)"
    private NodeDiffStatus diffStatus = NodeDiffStatus.CLEAN;

    public EditorTransition(String sourceState, String targetState, String conditionExpression) {
        this.sourceState = sourceState;
        this.targetState = targetState;
        this.conditionExpression = conditionExpression != null ? conditionExpression : "always";
    }

    public String getSourceState() {
        return sourceState;
    }

    public void setSourceState(String sourceState) {
        this.sourceState = sourceState;
        markModified();
    }

    public String getTargetState() {
        return targetState;
    }

    public void setTargetState(String targetState) {
        this.targetState = targetState;
        markModified();
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
        markModified();
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
}
