package net.void_.anomalies.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
public class AnomalyModifierHandler {

    private static final String STICK_NAME = "Изменятор аномалий";

    private static boolean isModifier(ItemStack stack) {
        return stack.is(Items.STICK) && stack.hasCustomHoverName() &&
                stack.getHoverName().getString().equals(STICK_NAME);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();

        if (!isModifier(mainHand)) return;
        if (player.level().isClientSide) return;

        if (event.getTarget() instanceof AnomalyEntity anomaly) {
            event.setCanceled(true);

            // Shift + ПКМ: Сброс оверрайдов
            if (player.isShiftKeyDown()) {
                anomaly.setCustomOverrides(new CompoundTag());
                player.sendSystemMessage(Component.literal("§b[Изменятор] §aВсе оверрайды аномалии сброшены!")
                        .withStyle(ChatFormatting.BOLD));
                return;
            }

            // ПКМ: Захват
            CompoundTag tag = mainHand.getOrCreateTag();
            tag.putUUID("SelectedAnomaly", anomaly.getUUID());
            tag.putBoolean("WaitingForParams", true);

            player.sendSystemMessage(Component.literal("§b[Изменятор] §eАномалия выбрана! Введите параметр и значение в чат."));
            player.sendSystemMessage(Component.literal("§7Доступные категории параметров:"));
            player.sendSystemMessage(Component.literal(" §e• Размеры: §fwidth, height"));
            player.sendSystemMessage(Component.literal(" §e• Физика: §fimpulseX, impulseY, impulseZ, pullToCenter (true/false)"));
            player.sendSystemMessage(Component.literal(" §e• Бой: §fdamage, fireSeconds, expandRadius, triggerInterval"));
            player.sendSystemMessage(Component.literal(" §e• Звуки: §fsoundVolume, soundPitch, soundIntervalMin, soundIntervalMax"));
            player.sendSystemMessage(Component.literal(" §e• Частицы: §fparticleRadius, particleHeight, particleCountMin, particleCountMax"));
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getMainHandItem();

        if (!isModifier(mainHand)) return;

        CompoundTag tag = mainHand.getTag();
        if (tag == null || !tag.getBoolean("WaitingForParams") || !tag.hasUUID("SelectedAnomaly")) return;

        event.setCanceled(true); // Перехватываем сообщение из чата

        String message = event.getMessage().getString().trim();
        String[] parts = message.split("\\s+");

        if (parts.length != 2) {
            player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка! Формат: <параметр> <значение> (пример: width 3.0)"));
            return;
        }

        String param = parts[0];
        String rawVal = parts[1];

        UUID anomalyUuid = tag.getUUID("SelectedAnomaly");
        ServerLevel level = (ServerLevel) player.level();
        Entity entity = level.getEntity(anomalyUuid);

        if (!(entity instanceof AnomalyEntity anomaly)) {
            player.sendSystemMessage(Component.literal("§c[Изменятор] Ошибка! Аномалия не найдена в мире."));
            tag.remove("WaitingForParams");
            return;
        }

        // Обработка Boolean параметров
        if (param.equals("pullToCenter")) {
            boolean val = Boolean.parseBoolean(rawVal);
            anomaly.getCustomOverrides().putBoolean(param, val);
            anomaly.rebuildComponents();
            player.sendSystemMessage(Component.literal("§b[Изменятор] §aПараметр §e" + param + "§a установлен в: §f" + val));
        } else {
            // Обработка числовых параметров
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
                return;
            }
        }

        tag.remove("WaitingForParams");
        tag.remove("SelectedAnomaly");
    }
}