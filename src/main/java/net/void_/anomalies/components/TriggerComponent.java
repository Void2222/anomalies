package net.void_.anomalies.components;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.void_.anomalies.anomaly.data.MinMaxRange;
import net.void_.anomalies.config.AnomalyIgnoreManager;
import net.void_.anomalies.core.IAnomalyComponent;
import net.void_.anomalies.core.IAnomalyHolder;
import net.void_.lightentity.api.ILightEntity;

import java.util.List;
import java.util.function.BiConsumer;

public class TriggerComponent implements IAnomalyComponent {

    private final double expandRadius;
    private final MinMaxRange interval;
    private final BiConsumer<IAnomalyHolder, Entity> onTrigger;
    private int timer = 0;
    private int currentIntervalTicks;

    private static final double ACTIVATION_DISTANCE = 48.0D;

    public TriggerComponent(double expandRadius, MinMaxRange interval, BiConsumer<IAnomalyHolder, Entity> onTrigger) {
        this.expandRadius = expandRadius;
        this.interval = interval;
        this.onTrigger = onTrigger;
        this.currentIntervalTicks = interval.getInt();
    }

    @Override
    public void serverTick(ILightEntity entity) {
        if (!(entity instanceof IAnomalyHolder anomaly)) return;

        Player nearestPlayer = anomaly.getLevel().getNearestPlayer(
                anomaly.getX(), anomaly.getY(), anomaly.getZ(),
                ACTIVATION_DISTANCE, false
        );

        if (nearestPlayer == null) {
            return;
        }

        timer++;
        if (timer >= currentIntervalTicks) {
            timer = 0;
            this.currentIntervalTicks = interval.getInt();

            AABB area = anomaly.getBoundingBox().inflate(expandRadius);

            List<Entity> targets = anomaly.getLevel().getEntitiesOfClass(
                    Entity.class,
                    area,
                    e -> {
                        if (e instanceof Player player && AnomalyIgnoreManager.isIgnored(player)) {
                            return false;
                        }
                        return e instanceof LivingEntity || e instanceof ItemEntity;
                    }
            );

            for (Entity target : targets) {
                onTrigger.accept(anomaly, target);
            }
        }
    }
}