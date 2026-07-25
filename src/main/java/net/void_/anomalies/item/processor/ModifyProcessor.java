package net.void_.anomalies.item.processor;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.core.AnomalyEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModifyProcessor {

    public static void onInteract(Player player, ItemStack stack, AnomalyEntity anomaly, boolean isShiftDown) {
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

        if (tag.getBoolean("WaitingForParams") && tag.hasUUID("SelectedAnomaly")) {
            UUID oldUuid = tag.getUUID("SelectedAnomaly");
            if (!oldUuid.equals(newUuid)) {
                String oldShortUuid = oldUuid.toString().substring(0, 8);
                player.sendSystemMessage(Component.literal("§6[Изменятор] §7Предыдущая сессия (" + oldShortUuid + "...) сохранена и закрыта."));
            }
        }

        tag.putUUID("SelectedAnomaly", newUuid);
        tag.putBoolean("WaitingForParams", true);

        String shortUuid = newUuid.toString().substring(0, 8);

        // 📜 Красивое интерактивное меню с подсказками типов данных
        player.sendSystemMessage(Component.literal("============== §b[ СЕССИЯ ИЗМЕНЕНИЯ ] §r==============").withStyle(ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("§aАномалия выбрана! (UUID: §f" + shortUuid + "...§a)"));
        player.sendSystemMessage(Component.literal(" §e• Размеры: §fwidth §7[float], §fheight §7[float]"));
        player.sendSystemMessage(Component.literal(" §e• Триггер: §fexpandRadius §7[double], §ftriggerInterval §7[double]"));
        player.sendSystemMessage(Component.literal(" §e• Звуки/Частицы: §fsoundVolume §7[float], §fparticleRadius §7[double]"));
        player.sendSystemMessage(Component.literal(" §e• Управление зонами:"));
        player.sendSystemMessage(Component.literal("   - Изменить: §fzone <индекс> <параметр> <значение> §7(radius, damage, pullForce, spinForce, impulseY)"));
        player.sendSystemMessage(Component.literal("   - Добавить: §fzone add <radius> <damage> <pullForce> <spinForce>"));
        player.sendSystemMessage(Component.literal("   - Удалить: §fzone remove <индекс>"));
        player.sendSystemMessage(Component.literal("§7Пример: §fwidth 2.0; zone 0 radius 3.0; zone add 5.0 0 0.02 0.05"));
        player.sendSystemMessage(Component.literal("§7Для выхода введите §fdone§7."));
        player.sendSystemMessage(Component.literal("==================================================").withStyle(ChatFormatting.BOLD));
    }

    public static boolean onChat(Player player, ItemStack stack, String message) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean("WaitingForParams") || !tag.hasUUID("SelectedAnomaly")) {
            return false;
        }

        String trimmed = message.trim();

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

        CompoundTag overrides = anomaly.getCustomOverrides();
        String type = anomaly.getAnomalyType();
        AnomalyDefinition def = AnomalyReloadListener.get(type);

        // Инициализируем оверрайды зон из дефинишна, если их еще нет в теге
        if (!overrides.contains("zones_count") && def != null && def.zones() != null) {
            overrides.putInt("zones_count", def.zones().size());
            for (int i = 0; i < def.zones().size(); i++) {
                var z = def.zones().get(i);
                overrides.putDouble("zone_" + i + "_radius", z.radius());
                overrides.putDouble("zone_" + i + "_damage", z.damage() != null && z.damage().innerAmount() != null ? z.damage().innerAmount().getDouble() : 0.0);
                overrides.putDouble("zone_" + i + "_pullForce", z.physics() != null ? z.physics().pullForce() : 0.05);
                overrides.putDouble("zone_" + i + "_spinForce", z.physics() != null ? z.physics().spinForce() : 0.0);
                overrides.putDouble("zone_" + i + "_impulseY", z.physics() != null ? z.physics().impulseY() : 0.0);
            }
        }

        String[] instructions = trimmed.split(";");
        List<String> successApplied = new ArrayList<>();
        boolean hasErrors = false;

        for (String instruction : instructions) {
            String singleCmd = instruction.trim();
            if (singleCmd.isEmpty()) continue;

            String[] parts = singleCmd.split("\\s+");

            try {
                // 🌟 Обработка команд зон: zone add / zone remove / zone <index> <param> <val>
                if (parts[0].equalsIgnoreCase("zone")) {
                    if (parts.length < 2) {
                        hasErrors = true;
                        continue;
                    }

                    if (parts[1].equalsIgnoreCase("add") && parts.length >= 6) {
                        double r = Double.parseDouble(parts[2]);
                        double d = Double.parseDouble(parts[3]);
                        double pf = Double.parseDouble(parts[4]);
                        double sf = Double.parseDouble(parts[5]);

                        int count = overrides.getInt("zones_count");
                        overrides.putDouble("zone_" + count + "_radius", r);
                        overrides.putDouble("zone_" + count + "_damage", d);
                        overrides.putDouble("zone_" + count + "_pullForce", pf);
                        overrides.putDouble("zone_" + count + "_spinForce", sf);
                        overrides.putDouble("zone_" + count + "_impulseY", 0.1);
                        overrides.putInt("zones_count", count + 1);

                        successApplied.add("§aДобавлена зона #" + count);
                        continue;
                    }

                    if (parts[1].equalsIgnoreCase("remove") && parts.length >= 3) {
                        int indexToRemove = Integer.parseInt(parts[2]);
                        int count = overrides.getInt("zones_count");

                        if (indexToRemove >= 0 && indexToRemove < count) {
                            // Сдвигаем зоны
                            for (int i = indexToRemove; i < count - 1; i++) {
                                overrides.putDouble("zone_" + i + "_radius", overrides.getDouble("zone_" + (i + 1) + "_radius"));
                                overrides.putDouble("zone_" + i + "_damage", overrides.getDouble("zone_" + (i + 1) + "_damage"));
                                overrides.putDouble("zone_" + i + "_pullForce", overrides.getDouble("zone_" + (i + 1) + "_pullForce"));
                                overrides.putDouble("zone_" + i + "_spinForce", overrides.getDouble("zone_" + (i + 1) + "_spinForce"));
                                overrides.putDouble("zone_" + i + "_impulseY", overrides.getDouble("zone_" + (i + 1) + "_impulseY"));
                            }
                            overrides.remove("zone_" + (count - 1) + "_radius");
                            overrides.remove("zone_" + (count - 1) + "_damage");
                            overrides.remove("zone_" + (count - 1) + "_pullForce");
                            overrides.remove("zone_" + (count - 1) + "_spinForce");
                            overrides.remove("zone_" + (count - 1) + "_impulseY");
                            overrides.putInt("zones_count", count - 1);

                            successApplied.add("§cУдалена зона #" + indexToRemove);
                        } else {
                            hasErrors = true;
                        }
                        continue;
                    }

                    if (parts.length >= 4) {
                        int index = Integer.parseInt(parts[1]);
                        String param = parts[2];
                        double val = Double.parseDouble(parts[3]);

                        int count = overrides.getInt("zones_count");
                        if (index >= 0 && index < count) {
                            overrides.putDouble("zone_" + index + "_" + param, val);
                            successApplied.add("§eZone " + index + " " + param + "§a=§f" + val);
                        } else {
                            hasErrors = true;
                        }
                        continue;
                    }
                }

                // 🌟 Обычные глобальные параметры
                if (parts.length != 2) {
                    hasErrors = true;
                    continue;
                }

                String param = parts[0];
                String rawVal = parts[1];

                if (param.equals("pullToCenter")) {
                    boolean val = Boolean.parseBoolean(rawVal);
                    overrides.putBoolean(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                } else if (param.equals("fireSeconds")) {
                    int val = (int) Double.parseDouble(rawVal);
                    overrides.putInt(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                } else {
                    double val = Double.parseDouble(rawVal);
                    overrides.putDouble(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                }

            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка в команде: '§f" + singleCmd + "§c'"));
                hasErrors = true;
            }
        }

        if (!successApplied.isEmpty()) {
            anomaly.setCustomOverrides(overrides);
            String appliedString = String.join("§7, ", successApplied);
            player.sendSystemMessage(Component.literal("§b[Изменятор] §aПрименено: " + appliedString));
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