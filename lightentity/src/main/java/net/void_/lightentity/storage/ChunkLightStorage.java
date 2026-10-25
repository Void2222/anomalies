package net.void_.lightentity.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.api.LightEntityType;

import java.util.*;

/**
 * Encapsulates serialization and management of LightEntities residing within a single Chunk.
 */
public class ChunkLightStorage {

    public static final String NBT_TAG_ROOT = "LightEntities";

    private final ChunkPos chunkPos;
    private final Map<UUID, ILightEntity> entities = new HashMap<>();

    public ChunkLightStorage(ChunkPos chunkPos) {
        this.chunkPos = chunkPos;
    }

    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    public void add(ILightEntity entity) {
        entities.put(entity.getId(), entity);
    }

    public void remove(ILightEntity entity) {
        entities.remove(entity.getId());
    }

    public Collection<ILightEntity> getEntities() {
        return entities.values();
    }

    public boolean isEmpty() {
        return entities.isEmpty();
    }

    /**
     * Serializes all entities in this chunk to NBT.
     */
    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ILightEntity entity : entities.values()) {
            if (!entity.isDiscarded()) {
                list.add(entity.serializeNBT());
            }
        }
        tag.put("List", list);
        return tag;
    }

    /**
     * Reads and reconstructs all entities in this chunk from NBT.
     */
    public void readFromNBT(CompoundTag tag, Level level, SpatialGrid grid) {
        entities.clear();
        if (!tag.contains("List", Tag.TAG_LIST)) return;

        ListTag list = tag.getList("List", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entityTag = list.getCompound(i);
            try {
                UUID id = entityTag.getUUID("Id");
                ResourceLocation typeId = new ResourceLocation(entityTag.getString("Type"));
                double x = entityTag.getDouble("X");
                double y = entityTag.getDouble("Y");
                double z = entityTag.getDouble("Z");

                LightEntityType<?> type = LightEntityType.get(typeId);
                if (type != null) {
                    ILightEntity entity = type.create(id, level, new Vec3(x, y, z));
                    entity.deserializeNBT(entityTag);
                    entities.put(id, entity);
                    if (grid != null) {
                        grid.add(entity);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
