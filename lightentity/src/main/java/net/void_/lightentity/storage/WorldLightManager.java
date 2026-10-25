package net.void_.lightentity.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.void_.lightentity.api.ILightEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World-level manager coordinating Chunk lifecycle, SpatialGrid, and ticking.
 */
public class WorldLightManager {

    private static final Map<Level, WorldLightManager> MANAGERS = new ConcurrentHashMap<>();

    private final Level level;
    private final SpatialGrid spatialGrid = new SpatialGrid();
    private final Map<Long, ChunkLightStorage> chunkStorages = new ConcurrentHashMap<>();

    public WorldLightManager(Level level) {
        this.level = level;
    }

    public static WorldLightManager get(Level level) {
        return MANAGERS.computeIfAbsent(level, WorldLightManager::new);
    }

    public static void unload(Level level) {
        WorldLightManager mgr = MANAGERS.remove(level);
        if (mgr != null) {
            mgr.clear();
        }
    }

    public SpatialGrid getSpatialGrid() {
        return spatialGrid;
    }

    public ILightEntity getEntity(UUID id) {
        return spatialGrid.getById(id);
    }

    public void spawn(ILightEntity entity) {
        if (entity == null) return;
        ChunkPos cp = entity.getChunkPos();
        ChunkLightStorage storage = chunkStorages.computeIfAbsent(cp.toLong(), k -> new ChunkLightStorage(cp));
        storage.add(entity);
        spatialGrid.add(entity);
    }

    public void remove(ILightEntity entity) {
        if (entity == null) return;
        ChunkPos cp = entity.getChunkPos();
        ChunkLightStorage storage = chunkStorages.get(cp.toLong());
        if (storage != null) {
            storage.remove(entity);
        }
        spatialGrid.remove(entity);
        entity.discard();
    }

    public void onChunkLoad(LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        ChunkLightStorage storage = chunkStorages.computeIfAbsent(cp.toLong(), k -> new ChunkLightStorage(cp));
        for (ILightEntity entity : storage.getEntities()) {
            if (!entity.isDiscarded()) {
                spatialGrid.add(entity);
            }
        }
    }

    public void onChunkUnload(LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        ChunkLightStorage storage = chunkStorages.get(cp.toLong());
        if (storage != null) {
            // Удаляем сущности только из активной пространственной сетки,
            // но сохраняем storage в памяти до вызова ChunkDataEvent.Save!
            for (ILightEntity entity : storage.getEntities()) {
                spatialGrid.remove(entity);
            }
        }
    }

    public void onChunkSave(LevelChunk chunk, CompoundTag chunkTag) {
        ChunkPos cp = chunk.getPos();
        ChunkLightStorage storage = chunkStorages.get(cp.toLong());
        if (storage != null) {
            if (!storage.isEmpty()) {
                chunkTag.put(ChunkLightStorage.NBT_TAG_ROOT, storage.writeToNBT());
            }
            // Если чанк уже выгружен из мира, теперь можно безопасно очистить storage
            if (!level.hasChunk(cp.x, cp.z)) {
                chunkStorages.remove(cp.toLong());
            }
        }
    }

    public void onChunkLoadData(LevelChunk chunk, CompoundTag chunkTag) {
        if (chunkTag.contains(ChunkLightStorage.NBT_TAG_ROOT)) {
            ChunkPos cp = chunk.getPos();
            ChunkLightStorage storage = chunkStorages.computeIfAbsent(cp.toLong(), k -> new ChunkLightStorage(cp));
            storage.readFromNBT(chunkTag.getCompound(ChunkLightStorage.NBT_TAG_ROOT), level, spatialGrid);
        }
    }

    public void tick() {
        for (ILightEntity entity : spatialGrid.getAll()) {
            if (!entity.isDiscarded()) {
                entity.tick();
            }
        }
    }

    public void clear() {
        spatialGrid.clear();
        chunkStorages.clear();
    }
}
