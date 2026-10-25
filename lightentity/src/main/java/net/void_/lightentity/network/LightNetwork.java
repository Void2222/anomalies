package net.void_.lightentity.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.void_.lightentity.network.packet.ClientboundDespawnLightEntityPacket;
import net.void_.lightentity.network.packet.ClientboundSpawnLightEntityPacket;
import net.void_.lightentity.network.packet.ClientboundSyncLightEntityPacket;

/**
 * Handles network packets registration and communication for LightEntity.
 */
public class LightNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("lightentity", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void init() {
        CHANNEL.registerMessage(
                packetId++,
                ClientboundSpawnLightEntityPacket.class,
                ClientboundSpawnLightEntityPacket::encode,
                ClientboundSpawnLightEntityPacket::decode,
                ClientboundSpawnLightEntityPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                ClientboundDespawnLightEntityPacket.class,
                ClientboundDespawnLightEntityPacket::encode,
                ClientboundDespawnLightEntityPacket::decode,
                ClientboundDespawnLightEntityPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                ClientboundSyncLightEntityPacket.class,
                ClientboundSyncLightEntityPacket::encode,
                ClientboundSyncLightEntityPacket::decode,
                ClientboundSyncLightEntityPacket::handle
        );
    }
}
