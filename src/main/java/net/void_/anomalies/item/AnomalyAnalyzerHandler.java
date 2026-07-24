package net.void_.anomalies.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.void_.anomalies.Anomalies;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.core.AnomalyEntity;

@Mod.EventBusSubscriber(modid = Anomalies.MOD_ID)
public class AnomalyAnalyzerHandler {

    private static final String ANALYZER_NAME = "Анализатор аномалий";

    private static boolean isAnalyzer(ItemStack stack) {
        return stack.is(Items.STICK) && stack.hasCustomHoverName() &&
                stack.getHoverName().getString().equals(ANALYZER_NAME);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();

        if (!isAnalyzer(mainHand)) return;
        if (player.level().isClientSide) return;

        if (event.getTarget() instanceof AnomalyEntity anomaly) {
            event.setCanceled(true);

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
                double damage = overrides.contains("damage") ? overrides.getDouble("damage") : (def.damage() != null && def.damage().amount() != null ? def.damage().amount().getMax() : 0.0);
                int fire = overrides.contains("fireSeconds") ? overrides.getInt("fireSeconds") : (def.damage() != null ? def.damage().fireSeconds() : 0);

                player.sendSystemMessage(Component.literal("§c⚔ Урон: " + formatVal("damage", damage, overrides) + " §7| §cПоджог: " + formatVal("fireSeconds", fire + "s", overrides)));
                player.sendSystemMessage(Component.literal("§a🎯 Зона триггера (Радиус): " + formatVal("expandRadius", radius, overrides)));
            }

            // 3. Физика (Импульсы)
            if (def != null && def.physics() != null) {
                double impX = overrides.contains("impulseX") ? overrides.getDouble("impulseX") : def.physics().impulseX();
                double impY = overrides.contains("impulseY") ? overrides.getDouble("impulseY") : def.physics().impulseY();
                double impZ = overrides.contains("impulseZ") ? overrides.getDouble("impulseZ") : def.physics().impulseZ();
                boolean pull = overrides.contains("pullToCenter") ? overrides.getBoolean("pullToCenter") : def.physics().pullToCenter();

                player.sendSystemMessage(Component.literal("§d🌀 Физика (Импульс): §7X=" + formatVal("impulseX", impX, overrides) +
                        " §7Y=" + formatVal("impulseY", impY, overrides) +
                        " §7Z=" + formatVal("impulseZ", impZ, overrides)));
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
    }

    // Форматирование значений: жёлтый цвет и плашка, если параметр изменён в NBT
    private static String formatVal(String key, Object value, CompoundTag overrides) {
        if (overrides.contains(key)) {
            return "§e§l" + value + " §6[Override]";
        }
        return "§f" + value;
    }
}