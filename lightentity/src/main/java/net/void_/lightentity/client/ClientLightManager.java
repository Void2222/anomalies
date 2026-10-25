package net.void_.lightentity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.api.LightEntityType;
import net.void_.lightentity.storage.SpatialGrid;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of active LightEntities currently within player's render/tracking distance.
 */
public class ClientLightManager {

    private static final SpatialGrid CLIENT_GRID = new SpatialGrid();
    private static final Map<UUID, ILightEntity> CLIENT_ENTITIES = new ConcurrentHashMap<>();

    public static SpatialGrid getGrid() {
        return CLIENT_GRID;
    }

    public static Collection<ILightEntity> getEntities() {
        return CLIENT_ENTITIES.values();
    }

    public static ILightEntity getById(UUID id) {
        return CLIENT_ENTITIES.get(id);
    }

    public static void handleSpawn(UUID id, ResourceLocation typeId, Vec3 pos, float width, float height, CompoundTag initialData) {
        Level clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) return;

        LightEntityType<?> type = LightEntityType.get(typeId);
        if (type == null) return;

        // If exists, remove first
        handleDespawn(id);

        ILightEntity entity = type.create(id, clientLevel, pos);
        entity.setDimensions(width, height);
        if (initialData != null && !initialData.isEmpty()) {
            entity.deserializeNBT(initialData);
        }
        CLIENT_ENTITIES.put(id, entity);
        CLIENT_GRID.add(entity);
    }

    public static void handleDespawn(UUID id) {
        ILightEntity entity = CLIENT_ENTITIES.remove(id);
        if (entity != null) {
            CLIENT_GRID.remove(entity);
            entity.discard();
        }
    }

    public static void handleSync(UUID id, CompoundTag data) {
        ILightEntity entity = CLIENT_ENTITIES.get(id);
        if (entity != null && data != null) {
            entity.deserializeNBT(data);
        }
    }

    public static void clientTick() {
        for (ILightEntity entity : CLIENT_ENTITIES.values()) {
            if (!entity.isDiscarded()) {
                entity.tick();
            }
        }
    }

    public static void clear() {
        for (ILightEntity entity : CLIENT_ENTITIES.values()) {
            entity.discard();
        }
        CLIENT_ENTITIES.clear();
        CLIENT_GRID.clear();
    }
}
