package net.void_.anomalies.setup;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.void_.anomalies.Anomalies;
import net.void_.anomalies.core.AnomalyEntity;

@Mod.EventBusSubscriber(modid = Anomalies.MOD_ID)
public class AdminToolsHandler {

    @SubscribeEvent
    public static void onEntityAttacked(AttackEntityEvent event) {
        Player player = event.getEntity();
        var mainHand = player.getMainHandItem();

        // Проверяем, что в руках палка и у нее есть наше имя "Удалятор аномалий"
        if (mainHand.is(Items.STICK) && mainHand.hasCustomHoverName() &&
                mainHand.getHoverName().getString().equals("Удалятор аномалий")) {

            if (!player.level().isClientSide) {
                if (event.getTarget() instanceof AnomalyEntity anomaly) {
                    anomaly.discard(); // Уничтожаем
                    player.sendSystemMessage(Component.literal("§c[Админ] §aАномалия стерта палкой!").withStyle(ChatFormatting.BOLD));
                    event.setCanceled(true);
                }
            }
        }
    }
}