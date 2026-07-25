package net.void_.anomalies.item.processor;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.void_.anomalies.core.AnomalyEntity;

import java.util.UUID;

public class ModifyProcessor {

    public static void onInteract(Player player, ItemStack stack, AnomalyEntity anomaly, boolean isShiftDown) {
        if (isShiftDown) {
            anomaly.setCustomOverrides(new CompoundTag());
            player.sendSystemMessage(Component.literal("§b[Изменятор] §aВсе оверрайды аномалии сброшены!")
                    .withStyle(ChatFormatting.BOLD));
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID("SelectedAnomaly", anomaly.getUUID());
        tag.putBoolean("WaitingForParams", true);
        tag.remove("WaitingForOffset");

        // Тот самый красивый вывод меню
        player.sendSystemMessage(Component.literal("§b[Изменятор] §eАномалия выбрана! Введите параметр и значение в чат."));
        player.sendSystemMessage(Component.literal("§7Доступные категории параметров:"));
        player.sendSystemMessage(Component.literal(" §e• Размеры: §fwidth, height"));
        player.sendSystemMessage(Component.literal(" §e• Физика: §fimpulseX, impulseY, impulseZ, pullToCenter (true/false)"));
        player.sendSystemMessage(Component.literal(" §e• Бой: §fdamage, fireSeconds, expandRadius, triggerInterval"));
        player.sendSystemMessage(Component.literal(" §e• Звуки: §fsoundVolume, soundPitch, soundIntervalMin, soundIntervalMax"));
        player.sendSystemMessage(Component.literal(" §e• Частицы: §fparticleRadius, particleHeight, particleCountMin, particleCountMax"));
    }

    public static boolean onChat(Player player, ItemStack stack, String message) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean("WaitingForParams") || !tag.hasUUID("SelectedAnomaly")) return false;

        String[] parts = message.split("\\s+");
        if (parts.length != 2) {
            player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка! Формат: <параметр> <значение> (пример: width 3.0)"));
            return true;
        }

        String param = parts[0];
        String rawVal = parts[1];

        UUID anomalyUuid = tag.getUUID("SelectedAnomaly");
        Entity entity = ((ServerLevel) player.level()).getEntity(anomalyUuid);

        if (!(entity instanceof AnomalyEntity anomaly)) {
            player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка! Аномалия не найдена в мире."));
            tag.remove("WaitingForParams");
            tag.remove("SelectedAnomaly");
            return true;
        }

        if (param.equals("pullToCenter")) {
            boolean val = Boolean.parseBoolean(rawVal);
            anomaly.getCustomOverrides().putBoolean(param, val);
            anomaly.rebuildComponents();
            player.sendSystemMessage(Component.literal("§b[Изменятор] §aПараметр §e" + param + "§a установлен в: §f" + val));
        } else {
            try {
                double val = Double.parseDouble(rawVal);
                if (param.equals("fireSeconds")) {
                    anomaly.getCustomOverrides().putInt(param, (int) val);
                } else {
                    anomaly.getCustomOverrides().putDouble(param, val);
                }
                anomaly.rebuildComponents();
                player.sendSystemMessage(Component.literal("§b[Изменятор] §aПараметр §e" + param + "§a установлен в: §f" + val));
            } catch (NumberFormatException e) {
                player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка! Значение должно быть числом (или true/false)."));
                return true;
            }
        }

        tag.remove("WaitingForParams");
        tag.remove("SelectedAnomaly");
        return true;
    }
}