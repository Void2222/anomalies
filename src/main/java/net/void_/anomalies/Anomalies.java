package net.void_.anomalies;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.item.AnomalyMultitoolItem;
import net.void_.anomalies.setup.AnomalyCommands;
import net.void_.anomalies.setup.EntityInit;
import org.slf4j.Logger;

@Mod(Anomalies.MOD_ID)
public class Anomalies {
    public static final String MOD_ID = "anomalies";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Создаем реестр для предметов
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    // Регистрируем наш мультитул
    public static final RegistryObject<Item> ANOMALY_MULTITOOL = ITEMS.register("anomaly_multitool",
            () -> new AnomalyMultitoolItem(new Item.Properties().stacksTo(1)));

    public Anomalies() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрируем реестр предметов на шине мода
        ITEMS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);

        EntityInit.register(modEventBus);
    }

    @SubscribeEvent
    public void onAddReloadListeners(net.minecraftforge.event.AddReloadListenerEvent event) {
        event.addListener(new AnomalyReloadListener());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("INITIALIZING ANOMALIES MOD");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AnomalyCommands.register(event.getDispatcher());
    }
}