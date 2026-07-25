package net.void_.anomalies.item.processor;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.void_.anomalies.core.AnomalyEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModifyProcessor {

    public static void onInteract(Player player, ItemStack stack, AnomalyEntity anomaly, boolean isShiftDown) {
        // Shift + ПКМ: Сброс оверрайдов
        if (isShiftDown) {
            anomaly.setCustomOverrides(new CompoundTag());
            anomaly.rebuildComponents();
            player.sendSystemMessage(Component.literal("§b[Изменятор] §aВсе оверрайды аномалии сброшены!")
                    .withStyle(ChatFormatting.BOLD));

            CompoundTag tag = stack.getTag();
            if (tag != null) {
                clearSession(tag);
            }
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        UUID newUuid = anomaly.getUUID();

        // Если переключились с другой аномалии — уведомляем о закрытии прошлой сессии
        if (tag.getBoolean("WaitingForParams") && tag.hasUUID("SelectedAnomaly")) {
            UUID oldUuid = tag.getUUID("SelectedAnomaly");
            if (!oldUuid.equals(newUuid)) {
                String oldShortUuid = oldUuid.toString().substring(0, 8);
                player.sendSystemMessage(Component.literal("§6[Изменятор] §7Предыдущая сессия (" + oldShortUuid + "...) сохранена и закрыта."));
            }
        }

        // Открываем сессию
        tag.putUUID("SelectedAnomaly", newUuid);
        tag.putBoolean("WaitingForParams", true);
        tag.remove("WaitingForOffset");

        String shortUuid = newUuid.toString().substring(0, 8);

        // 📜 Полное меню подсказок возвращено на базу!
        player.sendSystemMessage(Component.literal("============== §b[ СЕССИЯ ИЗМЕНЕНИЯ ] §r==============").withStyle(ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("§aАномалия выбрана! (UUID: §f" + shortUuid + "...§a)"));
        player.sendSystemMessage(Component.literal(" §e• Размеры: §fwidth, height"));
        player.sendSystemMessage(Component.literal(" §e• Физика: §fouterRadius, innerRadius, pullForce, spinForce, impulseY, pullToCenter (true/false)"));
        player.sendSystemMessage(Component.literal(" §e• Бой: §fdamage, outerDamage, innerDamage, fireSeconds, expandRadius, triggerInterval"));
        player.sendSystemMessage(Component.literal(" §e• Звуки: §fsoundVolume, soundPitch, soundIntervalMin, soundIntervalMax"));
        player.sendSystemMessage(Component.literal(" §e• Частицы: §fparticleRadius, particleHeight, particleCountMin, particleCountMax"));
        player.sendSystemMessage(Component.literal("§7Можно менять сразу несколько через '§f;§7' (пример: §fwidth 3.0; damage 15§7)"));
        player.sendSystemMessage(Component.literal("§7Для выхода введите §fdone§7 или кликните по другой аномалии."));
        player.sendSystemMessage(Component.literal("==================================================").withStyle(ChatFormatting.BOLD));
    }

    public static boolean onChat(Player player, ItemStack stack, String message) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean("WaitingForParams") || !tag.hasUUID("SelectedAnomaly")) {
            return false;
        }

        String trimmed = message.trim();

        // Завершение сессии
        if (trimmed.equalsIgnoreCase("done") || trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("save")) {
            clearSession(tag);
            player.sendSystemMessage(Component.literal("§b[Изменятор] §aСессия редактирования успешно завершена!"));
            return true;
        }

        UUID anomalyUuid = tag.getUUID("SelectedAnomaly");
        Entity entity = ((ServerLevel) player.level()).getEntity(anomalyUuid);

        if (!(entity instanceof AnomalyEntity anomaly)) {
            player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка! Выбранная аномалия больше не существует."));
            clearSession(tag);
            return true;
        }

        // Разбор введенных параметров
        String[] instructions = trimmed.split(";");
        List<String> successApplied = new ArrayList<>();
        boolean hasErrors = false;

        for (String instruction : instructions) {
            String singleCmd = instruction.trim();
            if (singleCmd.isEmpty()) continue;

            String[] parts = singleCmd.split("\\s+");
            if (parts.length != 2) {
                player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка формата в: '§f" + singleCmd + "§c'. Формат: <параметр> <значение>"));
                hasErrors = true;
                continue;
            }

            String param = parts[0];
            String rawVal = parts[1];

            try {
                if (param.equals("pullToCenter")) {
                    boolean val = Boolean.parseBoolean(rawVal);
                    anomaly.getCustomOverrides().putBoolean(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                } else if (param.equals("fireSeconds")) {

                    int val = (int) Double.parseDouble(rawVal);
                    anomaly.getCustomOverrides().putInt(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                } else {
                    double val = Double.parseDouble(rawVal);
                    anomaly.getCustomOverrides().putDouble(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                }
            } catch (NumberFormatException e) {
                player.sendSystemMessage(Component.literal("§c[Изменятор] Неверное числовое значение в: '§f" + singleCmd + "§c'"));
                hasErrors = true;
            }
        }

        if (!successApplied.isEmpty()) {
            anomaly.rebuildComponents();
            String appliedString = String.join("§7, ", successApplied);
            player.sendSystemMessage(Component.literal("§b[Изменятор] §aПрименено (" + successApplied.size() + "): " + appliedString));
        }

        if (!hasErrors) {
            player.sendSystemMessage(Component.literal("§7(Сессия активна. Введите еще параметры или §fdone §7для выхода)"));
        }

        return true;
    }
    public static void clearSession(CompoundTag tag) {
        tag.remove("WaitingForParams");
        tag.remove("SelectedAnomaly");
    }
}