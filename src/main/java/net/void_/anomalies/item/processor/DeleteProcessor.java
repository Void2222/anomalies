package net.void_.anomalies.item.processor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.void_.anomalies.core.AnomalyEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeleteProcessor {

    // Храним UUID игрока -> (UUID аномалии, время первого клика)
    private static final Map<UUID, ConfirmData> CONFIRM_MAP = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 5000; // 5 секунд на подтверждение

    private record ConfirmData(UUID anomalyUuid, long timestamp) {}

    public static void process(Player player, AnomalyEntity anomaly, boolean isShiftDown) {
        // Если зажат Shift — мгновенное удаление без вопросов
        if (isShiftDown) {
            deleteAnomaly(player, anomaly);
            CONFIRM_MAP.remove(player.getUUID());
            return;
        }

        long now = System.currentTimeMillis();
        ConfirmData data = CONFIRM_MAP.get(player.getUUID());

        // Проверяем, кликал ли игрок уже по ЭТОЙ ЖЕ аномалии не так давно
        if (data != null
                && data.anomalyUuid().equals(anomaly.getUUID())
                && (now - data.timestamp()) <= CONFIRM_TIMEOUT_MS) {

            deleteAnomaly(player, anomaly);
            CONFIRM_MAP.remove(player.getUUID());
        } else {
            // Первый клик — запрашиваем подтверждение
            CONFIRM_MAP.put(player.getUUID(), new ConfirmData(anomaly.getUUID(), now));
            player.sendSystemMessage(Component.literal("§c[Админ] §eВы уверены, что хотите удалить аномалию?")
                    .withStyle(ChatFormatting.BOLD));
            player.sendSystemMessage(Component.literal("§7Кликните еще раз в течение 5 сек для подтверждения или используйте §fShift + ЛКМ §7для мгновенного удаления."));
        }
    }

    private static void deleteAnomaly(Player player, AnomalyEntity anomaly) {
        anomaly.discard();
        player.sendSystemMessage(Component.literal("§c[Админ] §aАномалия стёрта!").withStyle(ChatFormatting.BOLD));
    }
}