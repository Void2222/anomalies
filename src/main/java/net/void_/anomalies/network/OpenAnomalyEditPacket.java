package net.void_.anomalies.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.void_.anomalies.client.gui.AnomalyEditScreen;

import java.util.UUID;
import java.util.function.Supplier;

public class OpenAnomalyEditPacket {
    private final UUID anomalyId;
    private final CompoundTag overrides;

    public OpenAnomalyEditPacket(UUID anomalyId, CompoundTag overrides) {
        this.anomalyId = anomalyId;
        this.overrides = overrides;
    }

    public OpenAnomalyEditPacket(FriendlyByteBuf buf) {
        this.anomalyId = buf.readUUID();
        this.overrides = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.anomalyId);
        buf.writeNbt(this.overrides);
    }

    public static void handle(OpenAnomalyEditPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                mc.setScreen(new AnomalyEditScreen(packet.anomalyId, packet.overrides));
            }
        });
        context.setPacketHandled(true);
    }
}