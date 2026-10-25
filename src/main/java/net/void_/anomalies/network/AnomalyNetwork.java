package net.void_.anomalies.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.void_.anomalies.Anomalies;

public class AnomalyNetwork {

    private static final String PROTOCOL_VERSION = "1.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Anomalies.MOD_ID, "main_channel"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(OpenAnomalyEditPacket.class, packetId++)
                .encoder(OpenAnomalyEditPacket::encode)
                .decoder(OpenAnomalyEditPacket::new) // Исправлено на ::new
                .consumerMainThread(OpenAnomalyEditPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncAnomalyOverridesPacket.class, packetId++)
                .encoder(SyncAnomalyOverridesPacket::encode)
                .decoder(SyncAnomalyOverridesPacket::new) // Исправлено на ::new
                .consumerMainThread(SyncAnomalyOverridesPacket::handle)
                .add();
    }
}