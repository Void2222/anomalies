package net.void_.anomalies.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.void_.anomalies.Anomalies;
import net.void_.anomalies.anomaly.ZoneFactory;
import net.void_.lightentity.api.ILightComponent;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.api.LightEntityType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High-performance Anomaly Entity powered by LightEntity engine.
 * Fully decoupled from net.minecraft.world.entity.Entity.
 */
public class LightAnomalyInstance implements ILightEntity, IAnomalyHolder {

    public static final ResourceLocation TYPE_ID = new ResourceLocation(Anomalies.MOD_ID, "light_anomaly");

    public static final LightEntityType<LightAnomalyInstance> TYPE = LightEntityType.register(
            TYPE_ID,
            LightAnomalyInstance::new
    );

    private final UUID id;
    private final LightEntityType<?> type;
    private Level level;

    private double x, y, z;
    private float width = 1.0f;
    private float height = 1.0f;
    private AABB boundingBox;

    private BlockPos blockPos;
    private ChunkPos chunkPos;
    private volatile boolean discarded = false;

    // Anomaly specific state
    private String anomalyType = "idle";
    private String currentState = "idle";
    private CompoundTag customOverrides = new CompoundTag();

    // Components state (Snapshot array swap pattern for 0 allocations in ticks)
    private final List<ILightComponent> components = new CopyOnWriteArrayList<>();
    private volatile ILightComponent[] activeComponents = new ILightComponent[0];

    public LightAnomalyInstance(LightEntityType<?> type, UUID id, Level level, Vec3 pos) {
        this.type = type != null ? type : TYPE;
        this.id = id != null ? id : UUID.randomUUID();
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.blockPos = BlockPos.containing(x, y, z);
        this.chunkPos = new ChunkPos(blockPos);
        recalculateBoundingBox();
    }

    private void recalculateBoundingBox() {
        double halfW = width / 2.0;
        this.boundingBox = new AABB(x - halfW, y, z - halfW, x + halfW, y + height, z + halfW);
    }

    public void rebuildComponents() {
        // Detach old components
        for (ILightComponent comp : activeComponents) {
            try {
                comp.onDetach(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        components.clear();

        // Apply new components from ZoneFactory
        if (anomalyType != null && !anomalyType.isEmpty()) {
            ZoneFactory.applyComponents(this, anomalyType, currentState);
        }

        updateActiveSnapshot();
    }

    public String getAnomalyType() {
        return anomalyType;
    }

    public void setAnomalyType(String anomalyType) {
        this.anomalyType = anomalyType != null ? anomalyType.toLowerCase() : "idle";
        rebuildComponents();
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String state) {
        String newState = (state != null && !state.isEmpty()) ? state.toLowerCase() : "idle";
        if (!this.currentState.equals(newState)) {
            this.currentState = newState;
            rebuildComponents();
        }
    }

    public CompoundTag getCustomOverrides() {
        return customOverrides;
    }

    public void setCustomOverrides(CompoundTag overrides) {
        this.customOverrides = overrides != null ? overrides.copy() : new CompoundTag();
        rebuildComponents();
    }

    public void setOverrideDouble(String key, double value) {
        this.customOverrides.putDouble(key, value);
        rebuildComponents();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getUUID() {
        return id;
    }

    @Override
    public LightEntityType<?> getType() {
        return type;
    }

    @Override
    public Vec3 getPosition() {
        return new Vec3(x, y, z);
    }

    @Override
    public double getX() { return x; }

    @Override
    public double getY() { return y; }

    @Override
    public double getZ() { return z; }

    @Override
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockPos = BlockPos.containing(x, y, z);
        this.chunkPos = new ChunkPos(blockPos);
        recalculateBoundingBox();
    }

    @Override
    public BlockPos getBlockPos() { return blockPos; }

    @Override
    public ChunkPos getChunkPos() { return chunkPos; }

    @Override
    public Level getLevel() { return level; }

    @Override
    public AABB getBoundingBox() { return boundingBox; }

    @Override
    public float getWidth() { return width; }

    @Override
    public float getHeight() { return height; }

    @Override
    public void setDimensions(float width, float height) {
        this.width = width;
        this.height = height;
        recalculateBoundingBox();
    }

    @Override
    public boolean isDiscarded() { return discarded; }

    @Override
    public void discard() {
        if (!discarded) {
            discarded = true;
            for (ILightComponent comp : activeComponents) {
                try {
                    comp.onDetach(this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            components.clear();
            activeComponents = new ILightComponent[0];
        }
    }

    @Override
    public <C extends ILightComponent> void addComponent(C component) {
        Objects.requireNonNull(component, "Component cannot be null");
        components.add(component);
        updateActiveSnapshot();
        component.onAttach(this);
    }

    @Override
    public <C extends ILightComponent> void removeComponent(Class<C> componentClass) {
        boolean removed = false;
        for (ILightComponent comp : components) {
            if (componentClass.isInstance(comp)) {
                comp.onDetach(this);
                components.remove(comp);
                removed = true;
            }
        }
        if (removed) {
            updateActiveSnapshot();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ILightComponent> Optional<C> getComponent(Class<C> componentClass) {
        for (ILightComponent comp : activeComponents) {
            if (componentClass.isInstance(comp)) {
                return Optional.of((C) comp);
            }
        }
        return Optional.empty();
    }

    @Override
    public <C extends ILightComponent> Optional<C> getLightComponent(Class<C> componentClass) {
        return getComponent(componentClass);
    }

    @Override
    public <C extends ILightComponent> boolean hasComponent(Class<C> componentClass) {
        for (ILightComponent comp : activeComponents) {
            if (componentClass.isInstance(comp)) return true;
        }
        return false;
    }

    @Override
    public Collection<ILightComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }

    private synchronized void updateActiveSnapshot() {
        this.activeComponents = components.toArray(new ILightComponent[0]);
    }

    @Override
    public void tick() {
        if (discarded) return;

        ILightComponent[] snapshot = activeComponents;
        boolean isClient = level.isClientSide();

        for (ILightComponent comp : snapshot) {
            try {
                if (isClient) {
                    comp.clientTick(this);
                } else {
                    comp.serverTick(this);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Type", type.getId().toString());
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Width", width);
        tag.putFloat("Height", height);

        tag.putString("AnomalyType", anomalyType);
        tag.putString("CurrentState", currentState);
        tag.put("CustomOverrides", customOverrides.copy());

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("X") && tag.contains("Y") && tag.contains("Z")) {
            setPosition(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        }
        if (tag.contains("Width") && tag.contains("Height")) {
            setDimensions(tag.getFloat("Width"), tag.getFloat("Height"));
        }
        if (tag.contains("AnomalyType")) {
            this.anomalyType = tag.getString("AnomalyType");
        }
        if (tag.contains("CurrentState")) {
            this.currentState = tag.getString("CurrentState");
        }
        if (tag.contains("CustomOverrides")) {
            this.customOverrides = tag.getCompound("CustomOverrides");
        }
        rebuildComponents();
    }
}
