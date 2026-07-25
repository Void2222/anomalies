package net.void_.anomalies.item.processor;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.data.ZoneConfig;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.core.AnomalyEntity;

public class AnalyzeProcessor {

    public static void process(Player player, AnomalyEntity anomaly) {
        String type = anomaly.getAnomalyType();
        AnomalyDefinition def = AnomalyReloadListener.get(type);
        CompoundTag overrides = anomaly.getCustomOverrides();

        player.sendSystemMessage(Component.literal("============== §b[ АНАЛИЗ АНОМАЛИИ ] §r==============").withStyle(ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("§7Тип: §f" + type + " §7| UUID: §8" + anomaly.getUUID().toString().substring(0, 8) + "..."));

        // 1. Размеры
        float width = overrides.contains("width") ? (float) overrides.getDouble("width") : (def != null && def.size() != null ? def.size().width() : 1.0f);
        float height = overrides.contains("height") ? (float) overrides.getDouble("height") : (def != null && def.size() != null ? def.size().height() : 1.0f);
        player.sendSystemMessage(Component.literal("§e📐 Размеры: " + formatVal("width", width, overrides) + "§7x" + formatVal("height", height, overrides)));

        // 2. Зоны и Урон
        if (def != null && def.zones() != null && !def.zones().isEmpty()) {
            player.sendSystemMessage(Component.literal("§c⚔ Слои зон и Урон:"));
            int index = 1;
            for (ZoneConfig zone : def.zones()) {
                double dmg = (zone.damage() != null && zone.damage().innerAmount() != null) ? zone.damage().innerAmount().getMax() : 0.0;
                int fire = (zone.damage() != null) ? zone.damage().fireSeconds() : 0;
                player.sendSystemMessage(Component.literal(" §7- Зона " + index + ": §fРадиус=" + zone.radius() + " §7| Урон=" + dmg + (fire > 0 ? " §7| Огонь=" + fire + "s" : "")));
                index++;
            }
        }

        // 3. Триггер
        if (def != null && def.trigger() != null) {
            double radius = overrides.contains("expandRadius") ? overrides.getDouble("expandRadius") : def.trigger().expandRadius();
            player.sendSystemMessage(Component.literal("§a🎯 Зона триггера (Радиус): " + formatVal("expandRadius", radius, overrides)));
        }

        // 4. Физика
        if (def != null && def.physics() != null) {
            boolean pull = overrides.contains("pullToCenter") ? overrides.getBoolean("pullToCenter") : def.physics().pullToCenter();
            player.sendSystemMessage(Component.literal("§d🌀 Втягивание в центр: " + formatVal("pullToCenter", pull, overrides)));
        }

        // 5. Звуки
        if (def != null && def.sound() != null) {
            float vol = overrides.contains("soundVolume") ? (float) overrides.getDouble("soundVolume") : def.sound().volume();
            float pitch = overrides.contains("soundPitch") ? (float) overrides.getDouble("soundPitch") : def.sound().pitch();
            player.sendSystemMessage(Component.literal("§🔊 Звук: §7Громкость=" + formatVal("soundVolume", vol, overrides) + " §7| Высота=" + formatVal("soundPitch", pitch, overrides)));
        }

        // 6. Частицы
        if (def != null && def.particles() != null && !def.particles().isEmpty()) {
            double pRad = overrides.contains("particleRadius") ? overrides.getDouble("particleRadius") : def.particles().get(0).radius();
            double pHeight = overrides.contains("particleHeight") ? overrides.getDouble("particleHeight") : def.particles().get(0).height();
            player.sendSystemMessage(Component.literal("§6✨ Частицы: §7Радиус=" + formatVal("particleRadius", pRad, overrides) + " §7| Высота=" + formatVal("particleHeight", pHeight, overrides)));
        }

        player.sendSystemMessage(Component.literal("==================================================").withStyle(ChatFormatting.BOLD));
    }

    private static String formatVal(String key, Object value, CompoundTag overrides) {
        if (overrides.contains(key)) {
            return "§e§l" + value + " §6[Override]";
        }
        return "§f" + value;
    }
}