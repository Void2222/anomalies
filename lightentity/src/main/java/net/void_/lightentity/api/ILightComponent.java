package net.void_.lightentity.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Common contract for modular components attached to a {@link ILightEntity}.
 * Follows the ECS paradigm: components encapsulate behavior, physics, and state.
 */
public interface ILightComponent {

    /**
     * Called when the component is attached to an entity.
     */
    default void onAttach(ILightEntity entity) {}

    /**
     * Called when the component is detached from an entity or entity is discarded.
     */
    default void onDetach(ILightEntity entity) {}

    /**
     * Ticked every server tick on the entity's thread / tick phase.
     */
    default void serverTick(ILightEntity entity) {}

    /**
     * Ticked every client frame/tick if the entity is within client view distance.
     */
    default void clientTick(ILightEntity entity) {}

    /**
     * Serializes component-specific state to NBT (saved in chunk).
     */
    default void writeToNbt(CompoundTag tag) {}

    /**
     * Restores component-specific state from NBT (loaded from chunk).
     */
    default void readFromNbt(CompoundTag tag) {}

    /**
     * Writes dynamic sync data into network buffer for clients entering tracking range.
     */
    default void writeSyncData(FriendlyByteBuf buf) {}

    /**
     * Reads dynamic sync data on client from network buffer.
     */
    default void readSyncData(FriendlyByteBuf buf) {}
}
