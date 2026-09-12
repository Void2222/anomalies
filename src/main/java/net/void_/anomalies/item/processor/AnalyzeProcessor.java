package net.void_.anomalies.item.processor;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.core.AnomalyEntity;

import java.util.Set;

public class AnalyzeProcessor {

    public static void process(Player player, AnomalyEntity anomaly) {
        String type = anomaly.getAnomalyType();
        String currentState = anomaly.getCurrentState();
        CompoundTag overrides = anomaly.getCustomOverrides();
        Set<String> states = AnomalyReloadListener.getStates(type);

        player.sendSystemMessage(Component.literal("============== §b[ ПОЛНЫЙ РЕНТГЕН АНОМАЛИИ ] §r==============").withStyle(ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("§7Тип: §f" + type + " §7| UUID: §8" + anomaly.getUUID().toString().substring(0, 8) + "..."));
        player.sendSystemMessage(Component.literal("§7Активная фаза прямо сейчас: §a§l" + currentState.toUpperCase()));

        if (!overrides.isEmpty()) {
            player.sendSystemMessage(Component.literal("§6⚠ Внимание: На аномалию навешены жесткие NBT-оверрайды (они игнорируют фазы JSON)."));
        }

        if (states.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cСостояния в датапаках не обнаружены!"));
            return;
        }

        // Обход абсолютно всех зарегистрированных состояний для этого типа аномалии
        for (String stateName : states) {
            boolean isActive = stateName.equalsIgnoreCase(currentState);
            AnomalyDefinition def = AnomalyReloadListener.get(type, stateName);

            String statusTag = isActive ? " §a▶ [ТЕКУЩАЯ ФАЗА]" : " §7[ИНАКТИВНА]";
            player.sendSystemMessage(Component.literal("\n§e=== Состояние: §6" + stateName.toUpperCase() + statusTag + " §e==="));

            if (def == null) {
                player.sendSystemMessage(Component.literal("  §c[Ошибка: Конфиг не загружен]"));
                continue;
            }

            // 1. Размеры
            float width = overrides.contains("width") ? (float) overrides.getDouble("width") : (def.size() != null ? def.size().width() : 1.0f);
            float height = overrides.contains("height") ? (float) overrides.getDouble("height") : (def.size() != null ? def.size().height() : 1.0f);
            player.sendSystemMessage(Component.literal("  §7📐 Размеры: " + formatVal("width", width, overrides) + "§7x" + formatVal("height", height, overrides)));

            // 2. Зоны урона и физики
            int zoneCount = overrides.contains("zones_count")
                    ? overrides.getInt("zones_count")
                    : (def.zones() != null ? def.zones().size() : 0);

            if (zoneCount > 0) {
                player.sendSystemMessage(Component.literal("  §c⚔ Зоны (" + zoneCount + " шт.):"));
                for (int i = 0; i < zoneCount; i++) {
                    ZoneConfig defaultZone = (def.zones() != null && i < def.zones().size()) ? def.zones().get(i) : null;

                    double radius = overrides.contains("zone_" + i + "_radius")
                            ? overrides.getDouble("zone_" + i + "_radius")
                            : (defaultZone != null ? defaultZone.radius() : 1.0);

                    double damage = overrides.contains("zone_" + i + "_damage")
                            ? overrides.getDouble("zone_" + i + "_damage")
                            : (defaultZone != null && defaultZone.damage() != null && defaultZone.damage().innerAmount() != null ? defaultZone.damage().innerAmount().getMax() : 0.0);

                    double pullForce = overrides.contains("zone_" + i + "_pullForce")
                            ? overrides.getDouble("zone_" + i + "_pullForce")
                            : (defaultZone != null && defaultZone.physics() != null ? defaultZone.physics().pullForce() : 0.0);

                    double spinForce = overrides.contains("zone_" + i + "_spinForce")
                            ? overrides.getDouble("zone_" + i + "_spinForce")
                            : (defaultZone != null && defaultZone.physics() != null ? defaultZone.physics().spinForce() : 0.0);

                    double impulseY = overrides.contains("zone_" + i + "_impulseY")
                            ? overrides.getDouble("zone_" + i + "_impulseY")
                            : (defaultZone != null && defaultZone.physics() != null ? defaultZone.physics().impulseY() : 0.0);

                    player.sendSystemMessage(Component.literal(
                            "   §7- Зона #" + i + ": R=" + formatVal("zone_" + i + "_radius", radius, overrides) +
                                    " §7| Dmg=" + formatVal("zone_" + i + "_damage", damage, overrides) +
                                    " §7| Тяга=" + formatVal("zone_" + i + "_pullForce", pullForce, overrides) +
                                    " §7| Вращение=" + formatVal("zone_" + i + "_spinForce", spinForce, overrides) +
                                    " §7| ВыбросY=" + formatVal("zone_" + i + "_impulseY", impulseY, overrides)
                    ));
                }
            }

            // 3. Зона триггера
            if (def.trigger() != null) {
                double radius = overrides.contains("expandRadius") ? overrides.getDouble("expandRadius") : def.trigger().expandRadius();
                player.sendSystemMessage(Component.literal("  §a🎯 Зона активации (Радиус): " + formatVal("expandRadius", radius, overrides)));
            }

            // 4. Звуковое сопровождение
            if (def.sound() != null) {
                float vol = overrides.contains("soundVolume") ? (float) overrides.getDouble("soundVolume") : def.sound().volume();
                float pitch = overrides.contains("soundPitch") ? (float) overrides.getDouble("soundPitch") : def.sound().pitch();
                player.sendSystemMessage(Component.literal("  §f🔊 Звук: §7" + def.sound().event() + " §7(Vol=" + formatVal("soundVolume", vol, overrides) + ", Pitch=" + formatVal("soundPitch", pitch, overrides) + ")"));
            }

            // 5. Визуальные эффекты (частицы)
            if (def.particles() != null && !def.particles().isEmpty()) {
                var p = def.particles().get(0);
                double pRad = overrides.contains("particleRadius") ? overrides.getDouble("particleRadius") : p.radius();
                double pHeight = overrides.contains("particleHeight") ? overrides.getDouble("particleHeight") : p.height();
                player.sendSystemMessage(Component.literal("  §6✨ Частицы: §7" + p.type() + " §7(R=" + formatVal("particleRadius", pRad, overrides) + ", H=" + formatVal("particleHeight", pHeight, overrides) + ")"));
            }
        }

        player.sendSystemMessage(Component.literal("==================================================").withStyle(ChatFormatting.BOLD));
    }

    private static String formatVal(String key, Object value, CompoundTag overrides) {
        if (overrides.contains(key)) {
            return "§e§l" + value + " §6[NBT]";
        }
        return "§f" + value;
    }
}