package net.void_.lightentity.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.void_.lightentity.client.ClientLightManager;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet for syncing dynamic component data or position updates to tracking clients.
 */
public class ClientboundSyncLightEntityPacket {

    private final UUID id;
    private final CompoundTag data;

    public ClientboundSyncLightEntityPacket(UUID id, CompoundTag data) {
        this.id = id;
        this.data = data;
    }

    public static void encode(ClientboundSyncLightEntityPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.id);
        buf.writeNbt(msg.data);
    }

    public static ClientboundSyncLightEntityPacket decode(FriendlyByteBuf buf) {
        return new ClientboundSyncLightEntityPacket(buf.readUUID(), buf.readNbt());
    }

    public static void handle(ClientboundSyncLightEntityPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ClientLightManager.handleSync(msg.id, msg.data);
        });
        ctx.setPacketHandled(true);
    }
}
