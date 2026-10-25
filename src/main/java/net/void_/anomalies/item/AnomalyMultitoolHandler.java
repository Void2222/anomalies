package net.void_.anomalies.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.void_.anomalies.Anomalies;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.item.processor.AnalyzeProcessor;
import net.void_.anomalies.item.processor.DeleteProcessor;
import net.void_.anomalies.item.processor.ModifyProcessor;
import net.void_.anomalies.item.processor.RelocateProcessor;
import net.void_.anomalies.network.AnomalyNetwork;
import net.void_.anomalies.network.OpenAnomalyEditPacket;

@Mod.EventBusSubscriber(modid = Anomalies.MOD_ID)
public class AnomalyMultitoolHandler {

    private static boolean isMultitool(ItemStack stack) {
        return stack.getItem() instanceof AnomalyMultitoolItem;
    }

    // 1. Взаимодействие с аномалией (ПКМ)
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();

        if (isMultitool(stack) && event.getTarget() instanceof AnomalyEntity anomaly) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);

            Player player = event.getEntity();
            if (player.level().isClientSide) return;

            MultitoolMode mode = AnomalyMultitoolItem.getMode(stack);

            switch (mode) {
                case ANALYZE -> AnalyzeProcessor.process(player, anomaly);
                case MODIFY -> {
                    // Обработка Shift + ПКМ: быстрый сброс оверрайдов
                    if (player.isShiftKeyDown()) {
                        anomaly.setCustomOverrides(new CompoundTag());
                        anomaly.rebuildComponents();
                        player.sendSystemMessage(Component.literal("§b[Изменятор] §aВсе оверрайды аномалии сброшены!"));
                    } else {
                        // Обычный ПКМ: отправляем пакет открытия GUI на клиент игроку
                        AnomalyNetwork.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                                new OpenAnomalyEditPacket(anomaly.getId(), anomaly.getCustomOverrides())
                        );
                    }
                }
                case RELOCATE -> RelocateProcessor.onInteract(player, stack, anomaly, player.isShiftKeyDown());
                case DELETE -> {
                    // Удаление происходит по ЛКМ
                }
            }
        }
    }

    // 1.1 Взаимодействие с LightEntity (ПКМ)
    @SubscribeEvent
    public static void onLightEntityInteract(net.void_.lightentity.api.event.LightEntityEvent.Interact event) {
        Player player = event.getPlayer();
        ItemStack stack = player.getItemInHand(event.getHand());

        if (isMultitool(stack) && event.getEntity() instanceof net.void_.anomalies.core.LightAnomalyInstance anomaly) {
            event.setCanceled(true);
            if (player.level().isClientSide) return;

            MultitoolMode mode = AnomalyMultitoolItem.getMode(stack);
            switch (mode) {
                case ANALYZE -> AnalyzeProcessor.process(player, anomaly);
                case MODIFY -> {
                    if (player.isShiftKeyDown()) {
                        anomaly.setCustomOverrides(new CompoundTag());
                        anomaly.rebuildComponents();
                        player.sendSystemMessage(Component.literal("§b[Изменятор] §aВсе оверрайды Light-аномалии сброшены!"));
                    } else {
                        player.sendSystemMessage(Component.literal("§b[Изменятор] §eLight-аномалия: тип §f" + anomaly.getAnomalyType() + "§e, оверрайдов: §f" + anomaly.getCustomOverrides().size()));
                    }
                }
                case RELOCATE -> player.sendSystemMessage(Component.literal("§b[Переместитель] §aLight-аномалия выбрана!"));
                case DELETE -> {}
            }
        }
    }

    // 2.1 Атака по LightEntity (ЛКМ) — удаление
    @SubscribeEvent
    public static void onLightEntityAttack(net.void_.lightentity.api.event.LightEntityEvent.Attack event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getMainHandItem();

        if (isMultitool(mainHand) && event.getEntity() instanceof net.void_.anomalies.core.LightAnomalyInstance anomaly) {
            if (player.level().isClientSide) return;

            MultitoolMode mode = AnomalyMultitoolItem.getMode(mainHand);
            if (mode == MultitoolMode.DELETE) {
                event.setCanceled(true);
                var mgr = net.void_.lightentity.storage.WorldLightManager.get(player.level());
                mgr.remove(anomaly);
                player.sendSystemMessage(Component.literal("§c[Удалитель] §aLight-аномалия удалена из мира!"));
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
                event.setCanceled(true);

                if (!player.level().isClientSide) {
                    RelocateProcessor.onLeftClickBlock(player, stack, event.getPos());
                }
            }
        }
    }
}