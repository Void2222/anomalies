package net.void_.lightentity.storage;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.void_.lightentity.api.ILightEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * High-performance spatial indexing structure using dynamic chunk/section buckets.
 * Enables O(1) entity lookups by radius or bounding box, bypassing linear O(N) iterations.
 */
public class SpatialGrid {

    // Maps ChunkPos.toLong() to the set of entities residing in that chunk
    private final ConcurrentHashMap<Long, Set<ILightEntity>> chunkBuckets = new ConcurrentHashMap<>();
    // Fast O(1) lookup by UUID
    private final ConcurrentHashMap<UUID, ILightEntity> entityMap = new ConcurrentHashMap<>();

    public void add(ILightEntity entity) {
        if (entity == null || entity.isDiscarded()) return;
        entityMap.put(entity.getId(), entity);
        long chunkKey = entity.getChunkPos().toLong();
        chunkBuckets.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(entity);
    }

    public void remove(ILightEntity entity) {
        if (entity == null) return;
        entityMap.remove(entity.getId());
        long chunkKey = entity.getChunkPos().toLong();
        Set<ILightEntity> bucket = chunkBuckets.get(chunkKey);
        if (bucket != null) {
            bucket.remove(entity);
            if (bucket.isEmpty()) {
                chunkBuckets.remove(chunkKey);
            }
        }
    }

    public void updatePosition(ILightEntity entity, ChunkPos oldChunk, ChunkPos newChunk) {
        if (oldChunk.equals(newChunk)) return;
        long oldKey = oldChunk.toLong();
        Set<ILightEntity> oldBucket = chunkBuckets.get(oldKey);
        if (oldBucket != null) {
            oldBucket.remove(entity);
            if (oldBucket.isEmpty()) {
                chunkBuckets.remove(oldKey);
            }
        }
        long newKey = newChunk.toLong();
        chunkBuckets.computeIfAbsent(newKey, k -> ConcurrentHashMap.newKeySet()).add(entity);
    }

    public ILightEntity getById(UUID id) {
        return entityMap.get(id);
    }

    public Collection<ILightEntity> getInChunk(ChunkPos chunkPos) {
        Set<ILightEntity> bucket = chunkBuckets.get(chunkPos.toLong());
        return bucket != null ? Collections.unmodifiableCollection(bucket) : Collections.emptyList();
    }

    /**
     * Queries entities whose bounding boxes intersect the target AABB.
     * Iterates ONLY over the overlapping chunks, not the entire world.
     */
    public List<ILightEntity> queryAABB(AABB aabb, Predicate<ILightEntity> filter) {
        int minChunkX = ((int) Math.floor(aabb.minX)) >> 4;
        int maxChunkX = ((int) Math.floor(aabb.maxX)) >> 4;
        int minChunkZ = ((int) Math.floor(aabb.minZ)) >> 4;
        int maxChunkZ = ((int) Math.floor(aabb.maxZ)) >> 4;

        List<ILightEntity> results = new ArrayList<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                Set<ILightEntity> bucket = chunkBuckets.get(key);
                if (bucket != null) {
                    for (ILightEntity entity : bucket) {
                        if (!entity.isDiscarded() && entity.getBoundingBox().intersects(aabb)) {
                            if (filter == null || filter.test(entity)) {
                                results.add(entity);
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    /**
     * Queries entities within a spherical radius from a point.
     */
    public List<ILightEntity> queryRadius(double x, double y, double z, double radius, Predicate<ILightEntity> filter) {
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        double radiusSq = radius * radius;

        return queryAABB(box, entity -> {
            double dx = entity.getX() - x;
            double dy = entity.getY() - y;
            double dz = entity.getZ() - z;
            return (dx * dx + dy * dy + dz * dz) <= radiusSq && (filter == null || filter.test(entity));
        });
    }

    public Collection<ILightEntity> getAll() {
        return Collections.unmodifiableCollection(entityMap.values());
    }

    public void clear() {
        chunkBuckets.clear();
        entityMap.clear();
    }
}
