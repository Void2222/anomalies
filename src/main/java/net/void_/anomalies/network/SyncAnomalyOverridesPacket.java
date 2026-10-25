package net.void_.anomalies.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.void_.anomalies.core.IAnomalyHolder;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.network.LightEntityTracker;
import net.void_.lightentity.storage.WorldLightManager;

import java.util.UUID;
import java.util.function.Supplier;

public class SyncAnomalyOverridesPacket {
    private final UUID anomalyId;
    private final CompoundTag overrides;

    public SyncAnomalyOverridesPacket(UUID anomalyId, CompoundTag overrides) {
        this.anomalyId = anomalyId;
        this.overrides = overrides;
    }

    public SyncAnomalyOverridesPacket(FriendlyByteBuf buf) {
        this.anomalyId = buf.readUUID();
        this.overrides = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.anomalyId);
        buf.writeNbt(this.overrides);
    }

    public static void handle(SyncAnomalyOverridesPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.serverLevel() != null) {
                WorldLightManager mgr = WorldLightManager.get(player.serverLevel());
                ILightEntity entity = mgr.getEntity(packet.anomalyId);
                if (entity instanceof IAnomalyHolder anomaly) {
                    anomaly.setCustomOverrides(packet.overrides);
                    anomaly.rebuildComponents();
                    LightEntityTracker.syncEntity(entity);
                    player.sendSystemMessage(Component.literal("§b[Изменятор] §aОверрайды аномалии успешно применены!"));
                }
            }
        });
        context.setPacketHandled(true);
    }
}