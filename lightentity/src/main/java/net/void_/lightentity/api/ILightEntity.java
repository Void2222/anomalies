package net.void_.lightentity.api;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Lightweight, high-performance spatial object.
 * Replaces vanilla Entity overhead (saves ~90% RAM, zero unnecessary vanilla fields).
 */
public interface ILightEntity {

    /**
     * Unique identifier of this entity.
     */
    UUID getId();

    /**
     * Entity type defining factory and serialization.
     */
    LightEntityType<?> getType();

    /**
     * Exact double position in the world.
     */
    Vec3 getPosition();

    double getX();
    double getY();
    double getZ();

    /**
     * Move or update position. Automatically updates chunk and spatial grid bounds.
     */
    void setPosition(double x, double y, double z);

    default void setPosition(Vec3 pos) {
        setPosition(pos.x, pos.y, pos.z);
    }

    /**
     * Current block coordinates.
     */
    BlockPos getBlockPos();

    /**
     * Current chunk coordinates.
     */
    ChunkPos getChunkPos();

    /**
     * Level (dimension) this entity resides in.
     */
    Level getLevel();

    /**
     * Local or world-space bounding box for raycasting and spatial detection.
     */
    AABB getBoundingBox();

    /**
     * Dimensions of the entity.
     */
    float getWidth();
    float getHeight();
    void setDimensions(float width, float height);

    /**
     * True if entity has been removed from world / discarded.
     */
    boolean isDiscarded();

    /**
     * Discards the entity, detaches all components and cleans up from storage.
     */
    void discard();

    /**
     * Component management.
     */
    <C extends ILightComponent> void addComponent(C component);

    <C extends ILightComponent> void removeComponent(Class<C> componentClass);

    <C extends ILightComponent> Optional<C> getComponent(Class<C> componentClass);

    <C extends ILightComponent> boolean hasComponent(Class<C> componentClass);

    Collection<ILightComponent> getComponents();

    /**
     * Ticks logic. Server tick processes behavior, client tick processes visual/audio.
     */
    void tick();

    /**
     * Full NBT serialization.
     */
    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);
}
