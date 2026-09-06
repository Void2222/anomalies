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
                player.sendSystemMessage(Component.literal("§6[Изменятор] §7Предыдущая сессия (" + oldShortUuid + "...) закрыта."));
            }
        }

        tag.putUUID("SelectedAnomaly", newUuid);
        tag.putBoolean("WaitingForParams", true);

        String shortUuid = newUuid.toString().substring(0, 8);

        // 📜 Заголовок и вызов Auto-Inspect
        player.sendSystemMessage(Component.literal("============== §b[ СЕССИЯ ИЗМЕНЕНИЯ ] §r==============").withStyle(ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("§aАномалия выбрана! (UUID: §f" + shortUuid + "...§a)"));

        // Автоматический осмотр состояния аномалии при входе
        printCurrentState(player, anomaly);

        player.sendSystemMessage(Component.literal("§eКоманды сессии: §fshow §7(состояние) | §fhelp §7(справка) | §ftoggle <param> §7| §freset <param> §7| §fdone §7(выход)"));
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

        // Команды состояния и помощи
        if (trimmed.equalsIgnoreCase("show") || trimmed.equalsIgnoreCase("list")) {
            printCurrentState(player, anomaly);
            return true;
        }

        if (trimmed.equalsIgnoreCase("help") || trimmed.equalsIgnoreCase("?")) {
            printHelp(player);
            return true;
        }

        // Инициализируем оверрайды зон из дефинишна, если их еще нет в теге
        ensureZonesInitialized(overrides, def);

        String[] instructions = trimmed.split(";");
        List<String> successApplied = new ArrayList<>();
        boolean hasErrors = false;

        for (String instruction : instructions) {
            String singleCmd = instruction.trim();
            if (singleCmd.isEmpty()) continue;

            String[] parts = singleCmd.split("\\s+");

            try {
                // 1. Команды точечного сброса (reset <param> / reset zone <index> / reset all)
                if (parts[0].equalsIgnoreCase("reset")) {
                    if (parts.length >= 2) {
                        if (parts[1].equalsIgnoreCase("all")) {
                            anomaly.setCustomOverrides(new CompoundTag());
                            anomaly.rebuildComponents();
                            player.sendSystemMessage(Component.literal("§b[Изменятор] §aВсе оверрайды сброшены до значений JSON!"));
                            return true;
                        }
                        if (parts[1].equalsIgnoreCase("zone") && parts.length >= 3) {
                            int idx = Integer.parseInt(parts[2]);
                            removeZoneOverrides(overrides, idx);
                            successApplied.add("§aСброшен оверрайд зоны #" + idx);
                            continue;
                        }

                        String targetParam = resolveAlias(parts[1]);
                        if (overrides.contains(targetParam)) {
                            overrides.remove(targetParam);
                            successApplied.add("§aСброшен параметр " + targetParam);
                        } else {
                            player.sendSystemMessage(Component.literal("§c[Изменятор] Параметр '§f" + targetParam + "§c' не имел оверрайда."));
                        }
                    }
                    continue;
                }

                // 2. Команды инверсии флагов (toggle <param>)
                if (parts[0].equalsIgnoreCase("toggle")) {
                    if (parts.length >= 2) {
                        String targetParam = resolveAlias(parts[1]);
                        if (targetParam.equalsIgnoreCase("pullToCenter")) {
                            boolean current = overrides.contains("pullToCenter")
                                    ? overrides.getBoolean("pullToCenter")
                                    : (def != null && def.physics() != null && def.physics().pullToCenter());
                            overrides.putBoolean("pullToCenter", !current);
                            successApplied.add("§epullToCenter§a=§f" + (!current));
                        } else {
                            player.sendSystemMessage(Component.literal("§c[Изменятор] 'toggle' применим только к boolean-параметрам (например: pullToCenter)"));
                            hasErrors = true;
                        }
                    }
                    continue;
                }

                // 3. Обработка команд зон: zone add / zone remove / zone <index> <param> <val>
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

                        if (r < 0.05 || r > 64.0 || d < 0 || pf < -10.0 || pf > 10.0 || sf < -10.0 || sf > 10.0) {
                            player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка! Недопустимые диапазоны параметров зоны."));
                            hasErrors = true;
                            continue;
                        }

                        int count = overrides.getInt("zones_count");
                        overrides.putDouble("zone_" + count + "_radius", r);
                        overrides.putDouble("zone_" + count + "_damage", d);
                        overrides.putDouble("zone_" + count + "_pullForce", pf);
                        overrides.putDouble("zone_" + count + "_spinForce", sf);
                        overrides.putDouble("zone_" + count + "_impulseY", 0.1);
                        overrides.putInt("zones_count", count + 1);

                        successApplied.add("§aДобавлена зона #" + count);
                        printZoneCard(player, overrides, count);
                        continue;
                    }

                    if (parts[1].equalsIgnoreCase("remove") && parts.length >= 3) {
                        int indexToRemove = Integer.parseInt(parts[2]);
                        int count = overrides.getInt("zones_count");

                        if (indexToRemove >= 0 && indexToRemove < count) {
                            for (int i = indexToRemove; i < count - 1; i++) {
                                overrides.putDouble("zone_" + i + "_radius", overrides.getDouble("zone_" + (i + 1) + "_radius"));
                                overrides.putDouble("zone_" + i + "_damage", overrides.getDouble("zone_" + (i + 1) + "_damage"));
                                overrides.putDouble("zone_" + i + "_pullForce", overrides.getDouble("zone_" + (i + 1) + "_pullForce"));
                                overrides.putDouble("zone_" + i + "_spinForce", overrides.getDouble("zone_" + (i + 1) + "_spinForce"));
                                overrides.putDouble("zone_" + i + "_impulseY", overrides.getDouble("zone_" + (i + 1) + "_impulseY"));
                            }
                            removeZoneOverrides(overrides, count - 1);
                            overrides.putInt("zones_count", count - 1);

                            successApplied.add("§cУдалена зона #" + indexToRemove);
                        } else {
                            player.sendSystemMessage(Component.literal("§c[Изменятор] Индекс зоны вне диапазона [0..." + (count - 1) + "]"));
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
                            if (param.equals("radius") && (val < 0.05 || val > 64.0)) {
                                player.sendSystemMessage(Component.literal("§c[Изменятор] Радиус зоны должен быть [0.05 ... 64.0]"));
                                hasErrors = true;
                                continue;
                            }
                            overrides.putDouble("zone_" + index + "_" + param, val);
                            successApplied.add("§eZone " + index + " " + param + "§a=§f" + val);
                            printZoneCard(player, overrides, index);
                        } else {
                            player.sendSystemMessage(Component.literal("§c[Изменятор] Некорректный индекс зоны: " + index));
                            hasErrors = true;
                        }
                        continue;
                    }
                }

                // 4. Обычные глобальные параметры с Алиасами и Валидацией
                if (parts.length != 2) {
                    hasErrors = true;
                    continue;
                }

                String param = resolveAlias(parts[0]);
                String rawVal = parts[1];

                if (!validateAndApply(param, rawVal, overrides, player, successApplied)) {
                    hasErrors = true;
                }

            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка в команде: '§f" + singleCmd + "§c'"));
                hasErrors = true;
            }
        }

        if (!successApplied.isEmpty()) {
            anomaly.setCustomOverrides(overrides);
            anomaly.rebuildComponents();
            String appliedString = String.join("§7, ", successApplied);
            player.sendSystemMessage(Component.literal("§b[Изменятор] §aПрименено: " + appliedString));
        }

        if (!hasErrors) {
            player.sendSystemMessage(Component.literal("§7(Сессия активна. Введите параметры, §fshow§7, §fhelp §7или §fdone§7)"));
        }

        return true;
    }

    public static void clearSession(CompoundTag tag) {
        tag.remove("WaitingForParams");
        tag.remove("SelectedAnomaly");
    }

    // --- Вспомогательные методы ---

    private static String resolveAlias(String param) {
        return switch (param.toLowerCase()) {
            case "w" -> "width";
            case "h" -> "height";
            case "r" -> "expandRadius";
            case "pull" -> "pullToCenter";
            case "fire" -> "fireSeconds";
            case "vol", "volume" -> "soundVolume";
            case "pitch" -> "soundPitch";
            case "prad" -> "particleRadius";
            case "pheight" -> "particleHeight";
            default -> param;
        };
    }

    private static boolean validateAndApply(String param, String rawVal, CompoundTag overrides, Player player, List<String> successApplied) {
        try {
            switch (param) {
                case "width", "height", "expandRadius", "particleRadius", "particleHeight" -> {
                    double val = Double.parseDouble(rawVal);
                    if (val < 0.05 || val > 64.0) {
                        player.sendSystemMessage(Component.literal("§c[Изменятор] '§f" + param + "§c' должен быть в диапазоне [0.05 ... 64.0]"));
                        return false;
                    }
                    overrides.putDouble(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                    return true;
                }
                case "soundVolume", "soundPitch" -> {
                    float val = Float.parseFloat(rawVal);
                    if (val < 0.0f || val > 10.0f) {
                        player.sendSystemMessage(Component.literal("§c[Изменятор] '§f" + param + "§c' должен быть в диапазоне [0.0 ... 10.0]"));
                        return false;
                    }
                    overrides.putDouble(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                    return true;
                }
                case "triggerInterval" -> {
                    double val = Double.parseDouble(rawVal);
                    if (val < 0.05 || val > 60.0) {
                        player.sendSystemMessage(Component.literal("§c[Изменятор] Интервал триггера должен быть [0.05 ... 60.0] сек"));
                        return false;
                    }
                    overrides.putDouble(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                    return true;
                }
                case "fireSeconds" -> {
                    int val = Integer.parseInt(rawVal);
                    if (val < 0 || val > 3600) {
                        player.sendSystemMessage(Component.literal("§c[Изменятор] Время горения должно быть [0 ... 3600] сек"));
                        return false;
                    }
                    overrides.putInt(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                    return true;
                }
                case "pullToCenter" -> {
                    boolean val = Boolean.parseBoolean(rawVal);
                    overrides.putBoolean(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                    return true;
                }
                default -> {
                    double val = Double.parseDouble(rawVal);
                    overrides.putDouble(param, val);
                    successApplied.add("§e" + param + "§a=§f" + val);
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("§c[Изменятор] Некорректное число для '§f" + param + "§c'"));
            return false;
        }
    }

    private static void ensureZonesInitialized(CompoundTag overrides, AnomalyDefinition def) {
        if (!overrides.contains("zones_count") && def != null && def.zones() != null) {
            overrides.putInt("zones_count", def.zones().size());
            for (int i = 0; i < def.zones().size(); i++) {
                var z = def.zones().get(i);
                overrides.putDouble("zone_" + i + "_radius", z.radius());
                overrides.putDouble("zone_" + i + "_damage", z.damage() != null && z.damage().innerAmount() != null ? z.damage().innerAmount().getMax() : 0.0);
                overrides.putDouble("zone_" + i + "_pullForce", z.physics() != null ? z.physics().pullForce() : 0.05);
                overrides.putDouble("zone_" + i + "_spinForce", z.physics() != null ? z.physics().spinForce() : 0.0);
                overrides.putDouble("zone_" + i + "_impulseY", z.physics() != null ? z.physics().impulseY() : 0.0);
            }
        }
    }

    private static void removeZoneOverrides(CompoundTag overrides, int index) {
        overrides.remove("zone_" + index + "_radius");
        overrides.remove("zone_" + index + "_damage");
        overrides.remove("zone_" + index + "_pullForce");
        overrides.remove("zone_" + index + "_spinForce");
        overrides.remove("zone_" + index + "_impulseY");
    }

    private static void printZoneCard(Player player, CompoundTag overrides, int index) {
        double r = overrides.getDouble("zone_" + index + "_radius");
        double d = overrides.getDouble("zone_" + index + "_damage");
        double pf = overrides.getDouble("zone_" + index + "_pullForce");
        double sf = overrides.getDouble("zone_" + index + "_spinForce");
        double iy = overrides.getDouble("zone_" + index + "_impulseY");
        player.sendSystemMessage(Component.literal(" §7↳ §aКарточка Зоны #" + index + ": §7Радиус=§f" + r + " §7| Урон=§f" + d + " §7| Тяга=§f" + pf + " §7| Вращение=§f" + sf + " §7| ИмпульсY=§f" + iy));
    }

    private static void printCurrentState(Player player, AnomalyEntity anomaly) {
        String type = anomaly.getAnomalyType();
        AnomalyDefinition def = AnomalyReloadListener.get(type);
        CompoundTag overrides = anomaly.getCustomOverrides();

        player.sendSystemMessage(Component.literal("§6📊 Текущие значения (Тип: §f" + type + "§6):"));

        float width = overrides.contains("width") ? (float) overrides.getDouble("width") : (def != null && def.size() != null ? def.size().width() : 1.0f);
        float height = overrides.contains("height") ? (float) overrides.getDouble("height") : (def != null && def.size() != null ? def.size().height() : 1.0f);
        player.sendSystemMessage(Component.literal(" §e• Размеры: §7w=§f" + formatVal("width", width, overrides) + " §7| h=§f" + formatVal("height", height, overrides)));

        double radius = overrides.contains("expandRadius") ? overrides.getDouble("expandRadius") : (def != null && def.trigger() != null ? def.trigger().expandRadius() : 1.0);
        player.sendSystemMessage(Component.literal(" §e• Триггер: §7r=§f" + formatVal("expandRadius", radius, overrides)));

        boolean pull = overrides.contains("pullToCenter") ? overrides.getBoolean("pullToCenter") : (def != null && def.physics() != null && def.physics().pullToCenter());
        player.sendSystemMessage(Component.literal(" §e• Втягивание: §f" + formatVal("pullToCenter", pull, overrides)));

        if (overrides.contains("zones_count")) {
            int count = overrides.getInt("zones_count");
            player.sendSystemMessage(Component.literal(" §e• Активные Зоны (" + count + "):"));
            for (int i = 0; i < count; i++) {
                printZoneCard(player, overrides, i);
            }
        }
    }

    private static String formatVal(String key, Object value, CompoundTag overrides) {
        if (overrides.contains(key)) {
            return "§e§l" + value + " §6[Override]";
        }
        return "§f" + value;
    }

    private static void printHelp(Player player) {
        player.sendSystemMessage(Component.literal("============== §b[ СПРАВОЧНИК ИЗМЕНЯТОРА ] §r==============").withStyle(ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("§e• Алиасы: §fw §7(width), §fh §7(height), §fr §7(expandRadius), §fpull §7(pullToCenter), §ffire §7(fireSeconds)"));
        player.sendSystemMessage(Component.literal("§e• Команды:"));
        player.sendSystemMessage(Component.literal("  - §fshow §7/ §flist §7— Показать текущие оверрайды"));
        player.sendSystemMessage(Component.literal("  - §ftoggle pull §7— Инвертировать втягивание в центр"));
        player.sendSystemMessage(Component.literal("  - §freset <param> §7— Сбросить один оверрайд до JSON"));
        player.sendSystemMessage(Component.literal("  - §freset zone <idx> §7— Сбросить оверрайд конкретной зоны"));
        player.sendSystemMessage(Component.literal("  - §fzone add <r> <d> <pf> <sf> §7— Добавить новую зону"));
        player.sendSystemMessage(Component.literal("  - §fzone remove <idx> §7— Удалить зону по индексу"));
        player.sendSystemMessage(Component.literal("  - §fzone <idx> <radius|damage|pullForce|spinForce> <val> §7— Изменить зону"));
        player.sendSystemMessage(Component.literal("==================================================").withStyle(ChatFormatting.BOLD));
    }
}