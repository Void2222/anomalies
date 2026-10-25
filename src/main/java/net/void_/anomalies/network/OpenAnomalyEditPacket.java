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
    private final String anomalyType;
    private final boolean isGraphMode;

    public OpenAnomalyEditPacket(UUID anomalyId, CompoundTag overrides, String anomalyType, boolean isGraphMode) {
        this.anomalyId = anomalyId;
        this.overrides = overrides;
        this.anomalyType = anomalyType != null ? anomalyType : "";
        this.isGraphMode = isGraphMode;
    }

    public OpenAnomalyEditPacket(UUID anomalyId, CompoundTag overrides) {
        this(anomalyId, overrides, "", false);
    }

    public OpenAnomalyEditPacket(FriendlyByteBuf buf) {
        this.anomalyId = buf.readUUID();
        this.overrides = buf.readNbt();
        this.anomalyType = buf.readUtf();
        this.isGraphMode = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.anomalyId);
        buf.writeNbt(this.overrides);
        buf.writeUtf(this.anomalyType);
        buf.writeBoolean(this.isGraphMode);
    }

    public static void handle(OpenAnomalyEditPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                if (packet.isGraphMode) {
                    net.void_.anomalies.client.gui.editor.NodeGraphScreen.openLiveInspector(
                            packet.anomalyId,
                            packet.anomalyType,
                            packet.overrides
                    );
                } else {
                    mc.setScreen(new AnomalyEditScreen(packet.anomalyId, packet.overrides));
                }
            }
        });
        context.setPacketHandled(true);
    }
}