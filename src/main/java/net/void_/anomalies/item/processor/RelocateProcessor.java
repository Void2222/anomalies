package net.void_.anomalies.item.processor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.void_.anomalies.core.AnomalyEntity;

import java.util.UUID;

public class RelocateProcessor {

    public static void onInteract(Player player, ItemStack stack, AnomalyEntity anomaly, boolean isShiftDown) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID("SelectedAnomaly", anomaly.getUUID());
        tag.remove("WaitingForParams");

        if (isShiftDown) {
            tag.putBoolean("WaitingForOffset", true);
            player.sendSystemMessage(Component.literal("§e[Переноситель] §bАномалия выбрана! Введите в чат смещение (например: §f0.5 0 -0.5§b):"));
        } else {
            tag.putBoolean("WaitingForOffset", false);
            player.sendSystemMessage(Component.literal("§e[Переноситель] §aАномалия захвачена! Левый клик по любому блоку перенесет её туда."));
        }
    }

    public static void onLeftClickBlock(Player player, ItemStack stack, BlockPos pos) {
        // Гарантируем, что метод не выполнится на клиенте
        if (player.level().isClientSide) return;

        CompoundTag tag = stack.getTag();
        if (tag == null || tag.getBoolean("WaitingForOffset") || !tag.hasUUID("SelectedAnomaly")) return;

        UUID anomalyUuid = tag.getUUID("SelectedAnomaly");
        Entity entity = ((ServerLevel) player.level()).getEntity(anomalyUuid);

        if (entity instanceof AnomalyEntity anomaly) {
            anomaly.setPos(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
            player.sendSystemMessage(Component.literal("§a[Переноситель] Аномалия успешно перемещена в точку: " +
                    pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        } else {
            player.sendSystemMessage(Component.literal("§c[Переноситель] Выбранная аномалия больше не существует!"));
        }
        tag.remove("SelectedAnomaly");
    }

    public static boolean onChat(Player player, ItemStack stack, String message) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean("WaitingForOffset") || !tag.hasUUID("SelectedAnomaly")) return false;

        String[] parts = message.split("\\s+");
        if (parts.length == 3) {
            try {
                double dx = Double.parseDouble(parts[0]);
                double dy = Double.parseDouble(parts[1]);
                double dz = Double.parseDouble(parts[2]);

                UUID anomalyUuid = tag.getUUID("SelectedAnomaly");
                Entity entity = ((ServerLevel) player.level()).getEntity(anomalyUuid);

                if (entity instanceof AnomalyEntity anomaly) {
                    var pos = anomaly.position();
                    anomaly.setPos(pos.x + dx, pos.y + dy, pos.z + dz);
                    player.sendSystemMessage(Component.literal("§a[Переноситель] Аномалия сдвинута на: dX=" + dx + ", dY=" + dy + ", dZ=" + dz));
                } else {
                    player.sendSystemMessage(Component.literal("§c[Переноситель] Аномалия не найдена в мире!"));
                }
            } catch (NumberFormatException e) {
                player.sendSystemMessage(Component.literal("§c[Переноситель] Ошибка! Используйте только числа, разделенные пробелом."));
                return true;
            }
        } else {
            player.sendSystemMessage(Component.literal("§c[Переноситель] Ошибка! Введите ровно 3 числа через пробел (например: §f0.5 0 -0.5§c):"));
            return true;
        }

        tag.remove("WaitingForOffset");
        tag.remove("SelectedAnomaly");
        return true;
    }
}