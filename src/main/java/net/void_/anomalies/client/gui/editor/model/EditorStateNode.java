package net.void_.anomalies.client.gui.editor.model;

import net.void_.anomalies.anomaly.data.*;

import java.util.ArrayList;
import java.util.List;

/**
 * UI-представление ноды состояния конечного автомата (FSM State)
 * с поддержкой локального черновика параметров (ZoneConfig, ParticleConfig, SoundConfig).
 */
public class EditorStateNode {
    private String stateName;
    private float x;
    private float y;
    private float width = 175.0f;
    private float height = 80.0f;

    private boolean isInitial = false;
    private boolean isCurrentlyActive = false; // Для live-инспектора мира
    private NodeDiffStatus diffStatus = NodeDiffStatus.CLEAN;

    private String boundJsonPath = "";
    private final List<String> recipes = new ArrayList<>();
    private final List<EditorTransition> outgoingTransitions = new ArrayList<>();

    // Инлайн-инспектор (раскрывающийся аккордеон)
    private boolean isExpanded = false;

    // Черновик параметров стейта
    private final List<ZoneConfig> draftZones = new ArrayList<>();
    private final List<ParticleConfig> draftParticles = new ArrayList<>();
    private SoundConfig draftSound = null;
    private AnomalyShape draftShape = AnomalyShape.CYLINDER;

    public EditorStateNode(String stateName, float x, float y) {
        this.stateName = stateName;
        this.x = x;
        this.y = y;
        initDefaultsIfEmpty();
        updateHeight();
    }

    public void initDefaultsIfEmpty() {
        if (draftZones.isEmpty()) {
            DamageConfig dConfig = new DamageConfig(
                    null,
                    new MinMaxRange(3.0, 5.0),
                    0,
                    "generic"
            );
            PhysicsConfig pConfig = new PhysicsConfig(0.0, 0.0, 0.0, true, 0.15, 0.0);
            draftZones.add(new ZoneConfig(3.5, dConfig, pConfig));
        }

        if (draftParticles.isEmpty()) {
            draftParticles.add(new ParticleConfig(
                    "minecraft:flame",
                    "CYLINDER",
                    new MinMaxRange(1.0, 2.0),
                    2.0,
                    1.5,
                    new MinMaxRange(4.0, 8.0)
            ));
        }

        if (draftSound == null) {
            draftSound = new SoundConfig(
                    "minecraft:block.fire.ambient",
                    new MinMaxRange(20.0, 40.0),
                    "BLOCKS",
                    1.0f,
                    1.0f
            );
        }
    }

    public void applyDefinition(AnomalyDefinition def) {
        if (def == null) return;
        this.draftZones.clear();
        if (def.zones() != null && !def.zones().isEmpty()) {
            this.draftZones.addAll(def.zones());
        }

        this.draftParticles.clear();
        if (def.particles() != null && !def.particles().isEmpty()) {
            this.draftParticles.addAll(def.particles());
        }

        if (def.sound() != null) {
            this.draftSound = def.sound();
        }

        if (def.shape() != null) {
            this.draftShape = def.shape();
        }

        initDefaultsIfEmpty();
        updateHeight();
    }

    public AnomalyDefinition toDefinition() {
        return new AnomalyDefinition(
                draftShape,
                new SizeConfig(2.0f, 2.0f),
                new ArrayList<>(draftParticles),
                draftSound,
                new TriggerConfig(5.0, new MinMaxRange(1, 1)),
                new ArrayList<>(draftZones),
                new PhysicsConfig(0, 0, 0, true, 0.1, 0),
                false
        );
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        this.isExpanded = expanded;
        updateHeight();
    }

    public void toggleExpanded() {
        this.isExpanded = !this.isExpanded;
        updateHeight();
    }

    public List<ZoneConfig> getDraftZones() {
        return draftZones;
    }

    public List<ParticleConfig> getDraftParticles() {
        return draftParticles;
    }

    public SoundConfig getDraftSound() {
        return draftSound;
    }

    public void setDraftSound(SoundConfig draftSound) {
        this.draftSound = draftSound;
        markModified();
    }

    public AnomalyShape getDraftShape() {
        return draftShape;
    }

    public void setDraftShape(AnomalyShape draftShape) {
        this.draftShape = draftShape;
        markModified();
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
        float baseH = 46.0f + outgoingTransitions.size() * 20.0f;
        if (!isExpanded) {
            this.width = 175.0f;
            this.height = Math.max(76.0f, baseH + 20.0f);
        } else {
            this.width = 215.0f;
            // Раскрытый инспектор параметров
            float paramsH = 16.0f; // Разделитель "ПАРАМЕТРЫ"
            paramsH += draftZones.size() * 66.0f + 20.0f; // Зоны + кнопка добавления
            paramsH += 36.0f; // Частицы
            paramsH += 36.0f; // Звук
            paramsH += 22.0f; // Нижняя плашка свернуть
            this.height = baseH + paramsH;
        }
    }

    public boolean contains(float canvasX, float canvasY) {
        return canvasX >= x && canvasX <= x + width && canvasY >= y && canvasY <= y + height;
    }

    public float getInputPortX() {
        return x;
    }

    public float getInputPortY() {
        return y + 25.0f;
    }

    public float getOutputPortX(int transitionIndex) {
        return x + width;
    }

    public float getOutputPortY(int transitionIndex) {
        return y + 42.0f + transitionIndex * 20.0f;
    }
}
