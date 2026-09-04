package net.void_.anomalies.components;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.void_.anomalies.anomaly.data.MinMaxRange;
import net.void_.anomalies.config.AnomalyIgnoreManager;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

import java.util.List;
import java.util.function.BiConsumer;

public class TriggerComponent implements IAnomalyComponent {

    private final double expandRadius;
    private final MinMaxRange interval;
    private final BiConsumer<AnomalyEntity, Entity> onTrigger;
    private int timer = 0;
    private int currentIntervalTicks;

    // 🌟 Радиус активации (в блоках). Если ближе нет игрока — аномалия "спит"
    private static final double ACTIVATION_DISTANCE = 48.0D;

    public TriggerComponent(double expandRadius, MinMaxRange interval, BiConsumer<AnomalyEntity, Entity> onTrigger) {
        this.expandRadius = expandRadius;
        this.interval = interval;
        this.onTrigger = onTrigger;
        this.currentIntervalTicks = interval.getInt();
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        // 🛑 LOD-ОПТИМИЗАЦИЯ СЕРВЕРА: Проверяем, есть ли рядом игроки
        Player nearestPlayer = anomaly.level().getNearestPlayer(
                anomaly.getX(), anomaly.getY(), anomaly.getZ(),
                ACTIVATION_DISTANCE, false
        );

        if (nearestPlayer == null) {
            return; // Рядом никого нет — пропускаем тик, экономим процессор
        }

        timer++;
        if (timer >= currentIntervalTicks) {
            timer = 0;
            this.currentIntervalTicks = interval.getInt();

            AABB area = anomaly.getBoundingBox().inflate(expandRadius);

            List<Entity> targets = anomaly.level().getEntitiesOfClass(
                    Entity.class,
                    area,
                    entity -> {
                        // 🛑 Игнорируем игроков из конфигурации ignore-менеджера
                        if (entity instanceof Player player && AnomalyIgnoreManager.isIgnored(player)) {
                            return false;
                        }
                        return (entity instanceof LivingEntity || entity instanceof ItemEntity);
                    }
            );

            for (Entity target : targets) {
                onTrigger.accept(anomaly, target);
            }
        }
    }
}