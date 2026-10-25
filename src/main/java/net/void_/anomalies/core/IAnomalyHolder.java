package net.void_.anomalies.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.void_.lightentity.api.ILightComponent;
import net.void_.lightentity.api.ILightEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Common contract for both legacy AnomalyEntity and new LightAnomalyInstance.
 * Allows components, processors, and zone logic to work seamlessly with either implementation.
 */
public interface IAnomalyHolder {

    UUID getUUID();
    Level getLevel();

    double getX();
    double getY();
    double getZ();
    Vec3 getPosition();
    BlockPos getBlockPos();
    AABB getBoundingBox();

    float getWidth();
    float getHeight();
    void setDimensions(float width, float height);

    String getAnomalyType();
    void setAnomalyType(String type);

    String getCurrentState();
    void setCurrentState(String state);

    CompoundTag getCustomOverrides();
    void setCustomOverrides(CompoundTag tag);

    void rebuildComponents();
    void discard();
    boolean isDiscarded();

    <C extends ILightComponent> void addComponent(C component);
    <C extends ILightComponent> Optional<C> getLightComponent(Class<C> componentClass);
}
