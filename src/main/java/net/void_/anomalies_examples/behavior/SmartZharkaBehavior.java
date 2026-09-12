package net.void_.anomalies_examples.behavior;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.void_.anomalies.api.behavior.IAnomalyStateBehavior;
import net.void_.anomalies.config.AnomalyIgnoreManager;
import net.void_.anomalies.core.AnomalyEntity;

import java.util.List;

public class SmartZharkaBehavior implements IAnomalyStateBehavior {

    @Override
    public String onTick(AnomalyEntity anomaly, int ticksInState) {
        String currentState = anomaly.getCurrentState();

        return switch (currentState) {
            case "idle" -> {
                // Ищем неигнорируемые цели в зоне обнаружения
                List<Entity> targets = anomaly.level().getEntitiesOfClass(
                        Entity.class,
                        anomaly.getBoundingBox().inflate(3.5),
                        entity -> !(entity instanceof AnomalyEntity) &&
                                !(entity instanceof Player player && AnomalyIgnoreManager.isIgnored(player))
                );

                // Замечена цель — начинаем разогрев
                yield !targets.isEmpty() ? "warmup" : null;
            }
            case "warmup" -> (ticksInState >= 20) ? "burst" : null;   // 1 секунда на бегство
            case "burst" -> (ticksInState >= 30) ? "cooldown" : null; // 1.5 секунды прожарки
            case "cooldown" -> (ticksInState >= 60) ? "idle" : null;   // 3 секунды передышки
            default -> "idle";
        };
    }
}