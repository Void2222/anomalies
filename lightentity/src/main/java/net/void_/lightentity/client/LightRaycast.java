package net.void_.lightentity.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.storage.SpatialGrid;

import java.util.List;
import java.util.Optional;

/**
 * Utility for performing precise raycasts against LightEntities (e.g. for multitools, admin clicks, targeting).
 */
public class LightRaycast {

    public record RaycastHit(ILightEntity entity, Vec3 hitPos, double distance) {}

    /**
     * Raycasts from player's eyes in looking direction up to maxDistance blocks.
     */
    public static Optional<RaycastHit> raycast(Player player, SpatialGrid grid, double maxDistance) {
        if (player == null || grid == null) return Optional.empty();

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(maxDistance));

        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);
        List<ILightEntity> candidates = grid.queryAABB(searchBox, null);

        ILightEntity closestEntity = null;
        Vec3 closestHit = null;
        double closestDist = maxDistance * maxDistance;

        for (ILightEntity entity : candidates) {
            AABB bb = entity.getBoundingBox();
            Optional<Vec3> hitOpt = bb.clip(eyePos, endPos);
            if (hitOpt.isPresent()) {
                Vec3 hit = hitOpt.get();
                double distSq = eyePos.distanceToSqr(hit);
                if (distSq < closestDist) {
                    closestDist = distSq;
                    closestEntity = entity;
                    closestHit = hit;
                }
            }
        }

        if (closestEntity != null) {
            return Optional.of(new RaycastHit(closestEntity, closestHit, Math.sqrt(closestDist)));
        }
        return Optional.empty();
    }
}
