package net.void_.anomalies;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.setup.AnomalyCommands;
import net.void_.anomalies.setup.EntityInit;
import org.slf4j.Logger;

@Mod(Anomalies.MOD_ID)
public class Anomalies {
    public static final String MOD_ID = "anomalies";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Anomalies() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрация метода общей инициализации
        modEventBus.addListener(this::commonSetup);

        // Регистрация шины событий Forge для игровых событий (например, запуск сервера и т.д.)
        MinecraftForge.EVENT_BUS.register(this);

        EntityInit.register(modEventBus);

    }

    @SubscribeEvent
    public void onAddReloadListeners(net.minecraftforge.event.AddReloadListenerEvent event) {
        event.addListener(new AnomalyReloadListener());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Код общей инициализации мода (выполняется при запуске игры)
        LOGGER.info("INITIALIZING ANOMALIES MOD");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AnomalyCommands.register(event.getDispatcher());
    }
}

