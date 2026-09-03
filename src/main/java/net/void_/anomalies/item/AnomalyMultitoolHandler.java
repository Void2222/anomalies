package net.void_.anomalies.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
        ItemStack stack = event.getItemStack();

        if (isMultitool(stack)) {
            // Отменяем стандартную обработку клика ПКМ, чтобы игра не вызывала use() сразу после этого
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);

            Player player = event.getEntity();
            if (player.level().isClientSide) return; // Выполняем логику только на сервере!

            // Передаем управление в процессор
            RelocateProcessor.onInteract(player, stack, (AnomalyEntity) event.getTarget(), player.isShiftKeyDown());
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
                DeleteProcessor.process(player, anomaly, player.isShiftKeyDown());
                event.setCanceled(true);
            }
        }
    }

    // 3. Клик по блоку (ЛКМ) — для переноса аномалии
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();

        if (stack.getItem() instanceof AnomalyMultitoolItem) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.hasUUID("SelectedAnomaly") && !tag.getBoolean("WaitingForOffset")) {
                // Блокируем разрушение блока на клиенте и сервере
                event.setCanceled(true);

                // Логику переноса вызываем только на сервере
                if (!player.level().isClientSide) {
                    RelocateProcessor.onLeftClickBlock(player, stack, event.getPos());
                }
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