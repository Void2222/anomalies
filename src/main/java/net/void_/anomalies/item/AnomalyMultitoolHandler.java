package net.void_.anomalies.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.void_.anomalies.Anomalies;
import net.void_.anomalies.core.IAnomalyHolder;
import net.void_.anomalies.item.processor.AnalyzeProcessor;
import net.void_.anomalies.item.processor.DeleteProcessor;
import net.void_.anomalies.item.processor.RelocateProcessor;
import net.void_.anomalies.network.AnomalyNetwork;
import net.void_.anomalies.network.OpenAnomalyEditPacket;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.api.event.LightEntityEvent;
import net.void_.lightentity.network.LightEntityTracker;

@Mod.EventBusSubscriber(modid = Anomalies.MOD_ID)
public class AnomalyMultitoolHandler {

    private static boolean isMultitool(ItemStack stack) {
        return stack.getItem() instanceof AnomalyMultitoolItem;
    }

    // 1. Взаимодействие с аномалией (ПКМ) через LightEntityEvent.Interact
    @SubscribeEvent
    public static void onLightEntityInteract(LightEntityEvent.Interact event) {
        Player player = event.getPlayer();
        ItemStack stack = player.getItemInHand(event.getHand());

        if (isMultitool(stack) && event.getEntity() instanceof IAnomalyHolder anomaly) {
            event.setCanceled(true);
            if (player.level().isClientSide) return;

            MultitoolMode mode = AnomalyMultitoolItem.getMode(stack);
            switch (mode) {
                case ANALYZE -> AnalyzeProcessor.process(player, anomaly);
                case MODIFY -> {
                    if (player.isShiftKeyDown()) {
                        anomaly.setCustomOverrides(new CompoundTag());
                        anomaly.rebuildComponents();
                        if (anomaly instanceof ILightEntity le) {
                            LightEntityTracker.syncEntity(le);
                        }
                        player.sendSystemMessage(Component.literal("§b[Изменятор] §aВсе оверрайды аномалии сброшены!"));
                    } else {
                        AnomalyNetwork.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                                new OpenAnomalyEditPacket(anomaly.getUUID(), anomaly.getCustomOverrides(), anomaly.getAnomalyType(), false)
                        );
                    }
                }
                case INSPECTOR -> {
                    AnomalyNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                            new OpenAnomalyEditPacket(anomaly.getUUID(), anomaly.getCustomOverrides(), anomaly.getAnomalyType(), true)
                    );
                }
                case RELOCATE -> RelocateProcessor.onInteract(player, stack, anomaly, player.isShiftKeyDown());
                case DELETE -> {
                    // Удаление происходит по ЛКМ
                }
            }
        }
    }

    // 2. Атака по аномалии (ЛКМ) через LightEntityEvent.Attack
    @SubscribeEvent
    public static void onLightEntityAttack(LightEntityEvent.Attack event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getMainHandItem();

        if (isMultitool(mainHand) && event.getEntity() instanceof IAnomalyHolder anomaly) {
            if (player.level().isClientSide) return;

            MultitoolMode mode = AnomalyMultitoolItem.getMode(mainHand);
            if (mode == MultitoolMode.DELETE) {
                event.setCanceled(true);
                DeleteProcessor.process(player, anomaly, player.isShiftKeyDown());
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