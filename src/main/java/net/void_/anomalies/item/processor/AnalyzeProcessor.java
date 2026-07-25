package net.void_.anomalies.item.processor;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
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

        // 2. Урон и Триггер
        if (def != null && def.trigger() != null) {
            double radius = overrides.contains("expandRadius") ? overrides.getDouble("expandRadius") : def.trigger().expandRadius();

            // 🌟 Учитываем раздельный урон (outer / inner)
            double outerDmg = overrides.contains("outerDamage") ? overrides.getDouble("outerDamage") : (def.damage() != null && def.damage().outerAmount() != null ? def.damage().outerAmount().getMax() : 0.0);
            double innerDmg = overrides.contains("innerDamage") ? overrides.getDouble("innerDamage") : (def.damage() != null && def.damage().innerAmount() != null ? def.damage().innerAmount().getMax() : 0.0);
            int fire = overrides.contains("fireSeconds") ? overrides.getInt("fireSeconds") : (def.damage() != null ? def.damage().fireSeconds() : 0);

            player.sendSystemMessage(Component.literal("§c⚔ Урон (Внеш/Внутр): " + formatVal("outerDamage", outerDmg, overrides) + " §7/ " + formatVal("innerDamage", innerDmg, overrides) + " §7| §cПоджог: " + formatVal("fireSeconds", fire + "s", overrides)));
            player.sendSystemMessage(Component.literal("§a🎯 Зона триггера (Радиус): " + formatVal("expandRadius", radius, overrides)));
        }

        // 3. Физика (Импульсы и Зоны)
        if (def != null && def.physics() != null) {
            double outRad = overrides.contains("outerRadius") ? overrides.getDouble("outerRadius") : (def.physics().outerRadius() != null ? def.physics().outerRadius() : 0.0);
            double inRad = overrides.contains("innerRadius") ? overrides.getDouble("innerRadius") : (def.physics().innerRadius() != null ? def.physics().innerRadius() : 0.0);
            double pForce = overrides.contains("pullForce") ? overrides.getDouble("pullForce") : (def.physics().pullForce() != null ? def.physics().pullForce() : 0.0);
            double sForce = overrides.contains("spinForce") ? overrides.getDouble("spinForce") : (def.physics().spinForce() != null ? def.physics().spinForce() : 0.0);
            boolean pull = overrides.contains("pullToCenter") ? overrides.getBoolean("pullToCenter") : def.physics().pullToCenter();

            player.sendSystemMessage(Component.literal("§d🌀 Гравитация: §7ВнешнийR=" + formatVal("outerRadius", outRad, overrides) + " §7| ВнутреннийR=" + formatVal("innerRadius", inRad, overrides)));
            player.sendSystemMessage(Component.literal("§d🌀 Силы: §7Тяга=" + formatVal("pullForce", pForce, overrides) + " §7| Вращение=" + formatVal("spinForce", sForce, overrides)));
            player.sendSystemMessage(Component.literal("§d🌀 Втягивание в центр: " + formatVal("pullToCenter", pull, overrides)));
        }

        // 4. Звуки
        if (def != null && def.sound() != null) {
            float vol = overrides.contains("soundVolume") ? (float) overrides.getDouble("soundVolume") : def.sound().volume();
            float pitch = overrides.contains("soundPitch") ? (float) overrides.getDouble("soundPitch") : def.sound().pitch();
            player.sendSystemMessage(Component.literal("§🔊 Звук: §7Громкость=" + formatVal("soundVolume", vol, overrides) + " §7| Высота=" + formatVal("soundPitch", pitch, overrides)));
        }

        // 5. Частицы
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