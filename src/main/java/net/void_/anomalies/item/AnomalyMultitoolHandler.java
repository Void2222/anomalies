package net.void_.anomalies.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.void_.anomalies.Anomalies;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.item.processor.AnalyzeProcessor;
import net.void_.anomalies.item.processor.DeleteProcessor;
import net.void_.anomalies.item.processor.ModifyProcessor;
import net.void_.anomalies.item.processor.RelocateProcessor;

@Mod.EventBusSubscriber(modid = Anomalies.MOD_ID)
public class AnomalyMultitoolHandler {

    private static boolean isMultitool(ItemStack stack) {
        return stack.getItem() instanceof AnomalyMultitoolItem;
    }

    // 1. Взаимодействие с аномалией (ПКМ)
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();

        if (!isMultitool(mainHand)) return;
        if (player.level().isClientSide) return;

        if (event.getTarget() instanceof AnomalyEntity anomaly) {
            event.setCanceled(true);

            MultitoolMode mode = AnomalyMultitoolItem.getMode(mainHand);
            boolean isShift = player.isShiftKeyDown();

            switch (mode) {
                case ANALYZE -> AnalyzeProcessor.process(player, anomaly);
                case MODIFY -> ModifyProcessor.onInteract(player, mainHand, anomaly, isShift);
                case RELOCATE -> RelocateProcessor.onInteract(player, mainHand, anomaly, isShift);
                case DELETE -> DeleteProcessor.process(player, anomaly, isShift); // <-- Добавлен isShift
            }
        }
    }

    // 2. Удар по аномалии (ЛКМ) — для режима удаления
    @SubscribeEvent
    public static void onEntityAttacked(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();

        if (!isMultitool(mainHand)) return;
        if (player.level().isClientSide) return;

        if (event.getTarget() instanceof AnomalyEntity anomaly) {
            MultitoolMode mode = AnomalyMultitoolItem.getMode(mainHand);
            if (mode == MultitoolMode.DELETE) {
                // Вызываем с передачей зажатого Shift
                DeleteProcessor.process(player, anomaly, player.isShiftKeyDown());
                event.setCanceled(true);
            }
        }
    }

    // 3. Клик по блоку (ЛКМ) — для переноса аномалии
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();

        if (!isMultitool(mainHand)) return;
        if (player.level().isClientSide) return;

        MultitoolMode mode = AnomalyMultitoolItem.getMode(mainHand);
        if (mode == MultitoolMode.RELOCATE) {
            RelocateProcessor.onLeftClickBlock(player, mainHand, event.getPos());
            if (mainHand.hasTag() && mainHand.getTag().hasUUID("SelectedAnomaly")) {
                event.setCanceled(true);
            }
        }
    }

    // 4. Перехват чата — для ввода параметров в режимах MODIFY и RELOCATE
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getMainHandItem();

        if (!isMultitool(mainHand)) return;

        MultitoolMode mode = AnomalyMultitoolItem.getMode(mainHand);
        String message = event.getMessage().getString().trim();
        boolean handled = false;

        if (mode == MultitoolMode.MODIFY) {
            handled = ModifyProcessor.onChat(player, mainHand, message);
        } else if (mode == MultitoolMode.RELOCATE) {
            handled = RelocateProcessor.onChat(player, mainHand, message);
        }

        if (handled) {
            event.setCanceled(true);
        }
    }
}