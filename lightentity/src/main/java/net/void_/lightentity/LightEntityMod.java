package net.void_.lightentity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.void_.lightentity.client.ClientLightManager;
import net.void_.lightentity.network.LightEntityTracker;
import net.void_.lightentity.network.LightNetwork;
import net.void_.lightentity.storage.WorldLightManager;

@Mod(LightEntityMod.MODID)
public class LightEntityMod {

    public static final String MODID = "lightentity";

    public LightEntityMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(LightNetwork::init);
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
            WorldLightManager.get(event.level).tick();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer sp) {
            LightEntityTracker.tickPlayer(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            LightEntityTracker.removePlayer(sp);
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk && !chunk.getLevel().isClientSide()) {
            WorldLightManager.get((ServerLevel) chunk.getLevel()).onChunkLoad(chunk);
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk && !chunk.getLevel().isClientSide()) {
            WorldLightManager.get((ServerLevel) chunk.getLevel()).onChunkUnload(chunk);
        }
    }

    @SubscribeEvent
    public void onChunkDataSave(ChunkDataEvent.Save event) {
        if (event.getChunk() instanceof LevelChunk chunk && !chunk.getLevel().isClientSide()) {
            WorldLightManager.get((ServerLevel) chunk.getLevel()).onChunkSave(chunk, event.getData());
        }
    }

    @SubscribeEvent
    public void onChunkDataLoad(ChunkDataEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk && !chunk.getLevel().isClientSide()) {
            WorldLightManager.get((ServerLevel) chunk.getLevel()).onChunkLoadData(chunk, event.getData());
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            WorldLightManager.unload(sl);
        } else if (event.getLevel().isClientSide()) {
            ClientLightManager.clear();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientLightManager.clientTick();
        }
    }
}
