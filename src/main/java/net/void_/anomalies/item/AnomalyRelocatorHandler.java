package net.void_.anomalies.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.void_.anomalies.Anomalies;
import net.void_.anomalies.core.AnomalyEntity;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Anomalies.MOD_ID)
public class AnomalyRelocatorHandler {

    private static final String STICK_NAME = "Переноситель аномалий";

    private static boolean isRelocator(ItemStack stack) {
        return stack.is(Items.STICK) && stack.hasCustomHoverName() &&
                stack.getHoverName().getString().equals(STICK_NAME);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {

        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();

        if (!isRelocator(mainHand)) return;
        if (player.level().isClientSide) return;

        if (event.getTarget() instanceof AnomalyEntity anomaly) {
            event.setCanceled(true);

            CompoundTag tag = mainHand.getOrCreateTag();
            tag.putUUID("SelectedAnomaly", anomaly.getUUID());

            if (player.isShiftKeyDown()) {
                tag.putBoolean("WaitingForOffset", true);
                player.sendSystemMessage(Component.literal("§e[Переноситель] §bАномалия выбрана! Введите в чат смещение (например: §f0.5 0 -0.5§b):"));
            } else {
                tag.putBoolean("WaitingForOffset", false);
                player.sendSystemMessage(Component.literal("§e[Переноситель] §aАномалия захвачена! Левый клик по любому блоку перенесет её туда."));
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();

        if (!isRelocator(mainHand)) return;
        if (player.level().isClientSide) return;

        CompoundTag tag = mainHand.getTag();
        if (tag == null || !tag.hasUUID("SelectedAnomaly")) return;

        if (tag.getBoolean("WaitingForOffset")) return;

        event.setCanceled(true);

        UUID anomalyUuid = tag.getUUID("SelectedAnomaly");
        ServerLevel level = (ServerLevel) player.level();
        Entity entity = level.getEntity(anomalyUuid);

        if (entity instanceof AnomalyEntity anomaly) {
            var blockPos = event.getPos();
            anomaly.setPos(blockPos.getX() + 0.5D, blockPos.getY() + 1.0D, blockPos.getZ() + 0.5D);
            player.sendSystemMessage(Component.literal("§a[Переноситель] Аномалия успешно перемещена в точку: " +
                    blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ()));

            tag.remove("SelectedAnomaly");
        } else {
            player.sendSystemMessage(Component.literal("§c[Переноситель] Выбранная аномалия больше не существует!"));
            tag.remove("SelectedAnomaly");
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getMainHandItem();

        if (!isRelocator(mainHand)) return;

        CompoundTag tag = mainHand.getTag();
        if (tag == null || !tag.getBoolean("WaitingForOffset") || !tag.hasUUID("SelectedAnomaly")) return;

        event.setCanceled(true); // Отменяем отправку сообщения в общий чат

        // Получаем текст из Component с помощью .getString()
        String message = event.getMessage().getString().trim();
        try {
            String[] parts = message.split("\\s+");
            if (parts.length == 3) {
                double dx = Double.parseDouble(parts[0]);
                double dy = Double.parseDouble(parts[1]);
                double dz = Double.parseDouble(parts[2]);

                UUID anomalyUuid = tag.getUUID("SelectedAnomaly");
                ServerLevel level = (ServerLevel) player.level();
                Entity entity = level.getEntity(anomalyUuid);

                if (entity instanceof AnomalyEntity anomaly) {
                    var pos = anomaly.position();
                    anomaly.setPos(pos.x + dx, pos.y + dy, pos.z + dz);
                    player.sendSystemMessage(Component.literal("§a[Переноситель] Аномалия сдвинута на: dX=" + dx + ", dY=" + dy + ", dZ=" + dz));
                } else {
                    player.sendSystemMessage(Component.literal("§c[Переноситель] Аномалия не найдена в мире!"));
                }
            } else {
                player.sendSystemMessage(Component.literal("§c[Переноситель] Ошибка! Введите ровно 3 числа через пробел (например: §f0.5 0 -0.5§c):"));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("§c[Переноситель] Ошибка! Используйте только числа, разделенные пробелом."));
            return;
        }

        tag.remove("WaitingForOffset");
        tag.remove("SelectedAnomaly");
    }
}