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

import java.util.ArrayList;
import java.util.List;

public class AnomalyEntity extends Entity {

    private float anomalyWidth = 1.0F;
    private float anomalyHeight = 1.0F;

    // Хранилище кастомных переопределений (оверрайдов) для конкретной сущности
    private CompoundTag customOverrides = new CompoundTag();

    private static final EntityDataAccessor<String> ANOMALY_TYPE =
            SynchedEntityData.defineId(AnomalyEntity.class, EntityDataSerializers.STRING);

    private final List<IAnomalyComponent> components = new ArrayList<>();

    public AnomalyEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ANOMALY_TYPE, "");
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

    public void setAnomalyDimensions(float width, float height) {
        this.anomalyWidth = width;
        this.anomalyHeight = height;
        this.refreshDimensions();
    }

    // 🌟 МЕТОДЫ ДЛЯ РАБОТЫ С ОВЕРРАЙДАМИ (NBT)

    public CompoundTag getCustomOverrides() {
        return this.customOverrides;
    }

    public void setCustomOverrides(CompoundTag tag) {
        this.customOverrides = tag != null ? tag.copy() : new CompoundTag();
        rebuildComponents(); // Пересобираем компоненты при изменении оверрайдов
    }

    /**
     * Записать/обновить конкретное числовое значение оверрайда (например, "impulseY", 2.0)
     */
    public void setOverrideDouble(String key, double value) {
        this.customOverrides.putDouble(key, value);
        rebuildComponents();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ANOMALY_TYPE.equals(key)) {
            rebuildComponents();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            for (IAnomalyComponent component : components) {
                component.clientTick(this);
            }
        } else {
            for (IAnomalyComponent component : components) {
                component.serverTick(this);
            }
        }
    }

    public void rebuildComponents() {
        this.components.clear();
        String type = getAnomalyType();
        if (!type.isEmpty()) {
            ZoneFactory.applyComponents(this, type);
        }
    }

    // 🌟 СХРАНЕНИЕ И ЗАГРУЗКА NBT В МИР

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("AnomalyType")) {
            // Загружаем тип, но не пересобираем сразу, чтобы сначала прочитать оверрайды
            this.entityData.set(ANOMALY_TYPE, tag.getString("AnomalyType"));
        }

        if (tag.contains("CustomOverrides")) {
            this.customOverrides = tag.getCompound("CustomOverrides");
        }

        rebuildComponents();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("AnomalyType", getAnomalyType());
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
}