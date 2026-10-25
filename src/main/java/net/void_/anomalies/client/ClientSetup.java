package net.void_.anomalies.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientSetup {

    private static volatile Frustum latestFrustum;

    public static Frustum getLatestFrustum() {
        return latestFrustum;
    }

    // 2️⃣ Игровая шина FORGE: перехват актуального Frustum каждый кадр
    @Mod.EventBusSubscriber(modid = "anomalies", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusEvents {
        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            // Кэшируем Frustum сразу после отрисовки частиц
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                latestFrustum = event.getFrustum();
            }
        }
    }
}