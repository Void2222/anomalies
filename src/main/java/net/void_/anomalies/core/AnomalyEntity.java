package net.void_.anomalies.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.void_.anomalies.anomaly.ZoneFactory;
import net.void_.anomalies.components.StateMachineComponent;

import java.util.ArrayList;
import java.util.List;

public class AnomalyEntity extends Entity {

    private float anomalyWidth = 1.0F;
    private float anomalyHeight = 1.0F;

    private CompoundTag customOverrides = new CompoundTag();

    private static final EntityDataAccessor<String> ANOMALY_TYPE =
            SynchedEntityData.defineId(AnomalyEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<String> CURRENT_STATE =
            SynchedEntityData.defineId(AnomalyEntity.class, EntityDataSerializers.STRING);

    private final List<IAnomalyComponent> components = new ArrayList<>();
    private volatile IAnomalyComponent[] activeComponents = new IAnomalyComponent[0];

    public AnomalyEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ANOMALY_TYPE, "");
        this.entityData.define(CURRENT_STATE, "idle");
    }

    public AnomalyEntity addComponent(IAnomalyComponent component) {
        this.components.add(component);
        return this;
    }

    public void setAnomalyType(String type) {
        this.entityData.set(ANOMALY_TYPE, type);
        rebuildComponents();
    }

    public String getAnomalyType() {
        return this.entityData.get(ANOMALY_TYPE);
    }

    public void setCurrentState(String state) {
        String newState = (state != null && !state.isEmpty()) ? state.toLowerCase() : "idle";
        if (!getCurrentState().equals(newState)) {
            this.entityData.set(CURRENT_STATE, newState);
            rebuildComponents();
        }
    }

    public String getCurrentState() {
        return this.entityData.get(CURRENT_STATE);
    }

    public void setAnomalyDimensions(float width, float height) {
        this.anomalyWidth = width;
        this.anomalyHeight = height;
        this.refreshDimensions();
    }

    public CompoundTag getCustomOverrides() {
        return this.customOverrides;
    }

    public void setCustomOverrides(CompoundTag tag) {
        this.customOverrides = tag != null ? tag.copy() : new CompoundTag();
        rebuildComponents();
    }

    public void setOverrideDouble(String key, double value) {
        this.customOverrides.putDouble(key, value);
        rebuildComponents();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide) {
            if (ANOMALY_TYPE.equals(key) || CURRENT_STATE.equals(key)) {
                rebuildComponents();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        IAnomalyComponent[] safeComponents = this.activeComponents;

        if (this.level().isClientSide) {
            for (IAnomalyComponent component : safeComponents) {
                component.clientTick(this);
            }
        } else {
            // 1. Сначала отбатывают все обычные компоненты (включая RecipeProcessorComponent)
            for (IAnomalyComponent component : safeComponents) {
                if (!(component instanceof StateMachineComponent)) {
                    component.serverTick(this);
                }
            }

            // 2. Стейт-машина строго последней: дает рецептам доделать крафт на граничном тике
            for (IAnomalyComponent component : safeComponents) {
                if (component instanceof StateMachineComponent) {
                    component.serverTick(this);
                }
            }
        }
    }

    public void rebuildComponents() {
        this.components.clear();
        String type = getAnomalyType();
        if (!type.isEmpty()) {
            ZoneFactory.applyComponents(this, type, getCurrentState());
        }
        this.activeComponents = this.components.toArray(new IAnomalyComponent[0]);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("AnomalyType")) {
            this.entityData.set(ANOMALY_TYPE, tag.getString("AnomalyType"));
        }

        if (tag.contains("CurrentState")) {
            String state = tag.getString("CurrentState");
            this.entityData.set(CURRENT_STATE, state.isEmpty() ? "idle" : state.toLowerCase());
        }

        if (tag.contains("CustomOverrides")) {
            this.customOverrides = tag.getCompound("CustomOverrides");
        }

        rebuildComponents();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("AnomalyType", getAnomalyType());
        tag.putString("CurrentState", getCurrentState());
        if (!this.customOverrides.isEmpty()) {
            tag.put("CustomOverrides", this.customOverrides.copy());
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(this.anomalyWidth, this.anomalyHeight);
    }

    @SuppressWarnings("unchecked")
    public <T extends IAnomalyComponent> T getComponent(Class<T> type) {
        for (IAnomalyComponent component : this.components) {
            if (type.isInstance(component)) {
                return (T) component;
            }
        }
        return null;
    }
}