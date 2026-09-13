package net.void_.anomalies.dsl.event;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.void_.anomalies.api.event.AnomalyZoneTransitionEvent;
import net.void_.anomalies.dsl.cache.TransientZoneCache;

@Mod.EventBusSubscriber
public class AnomalyEventListener {

    @SubscribeEvent
    public static void onZoneTransition(AnomalyZoneTransitionEvent event) {
        TransientZoneCache.recordTransition(event);
    }
}