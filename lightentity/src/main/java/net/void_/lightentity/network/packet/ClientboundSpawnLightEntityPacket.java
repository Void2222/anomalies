package net.void_.lightentity.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.void_.lightentity.client.ClientLightManager;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet sent from server to client to spawn a LightEntity within tracking view.
 * Includes initial NBT data for seamless client component reconstruction.
 */
public class ClientboundSpawnLightEntityPacket {

    private final UUID id;
    private final ResourceLocation typeId;
    private final double x, y, z;
    private final float width, height;
    private final CompoundTag initialData;

    public ClientboundSpawnLightEntityPacket(UUID id, ResourceLocation typeId, double x, double y, double z, float width, float height, CompoundTag initialData) {
        this.id = id;
        this.typeId = typeId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.width = width;
        this.height = height;
        this.initialData = initialData != null ? initialData : new CompoundTag();
    }

    public static void encode(ClientboundSpawnLightEntityPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.id);
        buf.writeResourceLocation(msg.typeId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.width);
        buf.writeFloat(msg.height);
        buf.writeNbt(msg.initialData);
    }

    public static ClientboundSpawnLightEntityPacket decode(FriendlyByteBuf buf) {
        return new ClientboundSpawnLightEntityPacket(
                buf.readUUID(),
                buf.readResourceLocation(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readNbt()
        );
    }

    public static void handle(ClientboundSpawnLightEntityPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ClientLightManager.handleSpawn(msg.id, msg.typeId, new Vec3(msg.x, msg.y, msg.z), msg.width, msg.height, msg.initialData);
        });
        ctx.setPacketHandled(true);
    }
}
