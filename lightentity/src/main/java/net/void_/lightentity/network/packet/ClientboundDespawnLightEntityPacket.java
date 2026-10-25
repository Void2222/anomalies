package net.void_.lightentity.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.void_.lightentity.client.ClientLightManager;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet sent from server to client to remove a LightEntity leaving tracking view.
 */
public class ClientboundDespawnLightEntityPacket {

    private final UUID id;

    public ClientboundDespawnLightEntityPacket(UUID id) {
        this.id = id;
    }

    public static void encode(ClientboundDespawnLightEntityPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.id);
    }

    public static ClientboundDespawnLightEntityPacket decode(FriendlyByteBuf buf) {
        return new ClientboundDespawnLightEntityPacket(buf.readUUID());
    }

    public static void handle(ClientboundDespawnLightEntityPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ClientLightManager.handleDespawn(msg.id);
        });
        ctx.setPacketHandled(true);
    }
}
