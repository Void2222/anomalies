package net.void_.lightentity.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.network.packet.ClientboundDespawnLightEntityPacket;
import net.void_.lightentity.network.packet.ClientboundSpawnLightEntityPacket;
import net.void_.lightentity.storage.SpatialGrid;
import net.void_.lightentity.storage.WorldLightManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks player proximity to LightEntities and dispatches spawn/despawn packets dynamically.
 */
public class LightEntityTracker {

    public static final double DEFAULT_TRACK_RANGE = 64.0;
    public static final double DEFAULT_TRACK_RANGE_SQ = DEFAULT_TRACK_RANGE * DEFAULT_TRACK_RANGE;

    // Player UUID -> Set of entity UUIDs currently tracked/visible to this player
    private static final Map<UUID, Set<UUID>> PLAYER_TRACKED_ENTITIES = new ConcurrentHashMap<>();

    public static void tickPlayer(ServerPlayer player) {
        if (player == null || player.isRemoved()) return;

        ServerLevel level = player.serverLevel();
        WorldLightManager mgr = WorldLightManager.get(level);
        SpatialGrid grid = mgr.getSpatialGrid();

        Set<UUID> currentlyTracked = PLAYER_TRACKED_ENTITIES.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());

        Vec3 pPos = player.position();
        List<ILightEntity> inRange = grid.queryRadius(pPos.x, pPos.y, pPos.z, DEFAULT_TRACK_RANGE, null);
        Set<UUID> visibleNow = new HashSet<>();

        // Send Spawn packets for newly in-range entities
        for (ILightEntity entity : inRange) {
            UUID id = entity.getId();
            visibleNow.add(id);
            if (!currentlyTracked.contains(id)) {
                currentlyTracked.add(id);
                ClientboundSpawnLightEntityPacket pkt = new ClientboundSpawnLightEntityPacket(
                        id,
                        entity.getType().getId(),
                        entity.getX(), entity.getY(), entity.getZ(),
                        entity.getWidth(), entity.getHeight(),
                        entity.serializeNBT()
                );
                LightNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
            }
        }

        // Send Despawn packets for entities that moved out of range
        Iterator<UUID> it = currentlyTracked.iterator();
        while (it.hasNext()) {
            UUID trackedId = it.next();
            if (!visibleNow.contains(trackedId)) {
                it.remove();
                LightNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new ClientboundDespawnLightEntityPacket(trackedId)
                );
            }
        }
    }

    public static void syncEntity(ILightEntity entity) {
        if (entity == null || entity.getLevel().isClientSide()) return;
        UUID id = entity.getId();
        net.void_.lightentity.network.packet.ClientboundSyncLightEntityPacket pkt =
                new net.void_.lightentity.network.packet.ClientboundSyncLightEntityPacket(id, entity.serializeNBT());

        if (entity.getLevel() instanceof ServerLevel serverLevel) {
            for (Map.Entry<UUID, Set<UUID>> entry : PLAYER_TRACKED_ENTITIES.entrySet()) {
                if (entry.getValue().contains(id)) {
                    ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
                    if (player != null) {
                        LightNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
                    }
                }
            }
        }
    }

    public static void removePlayer(ServerPlayer player) {
        if (player != null) {
            PLAYER_TRACKED_ENTITIES.remove(player.getUUID());
        }
    }
}
