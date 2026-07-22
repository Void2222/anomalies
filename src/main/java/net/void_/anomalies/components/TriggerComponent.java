package net.void_.anomalies.components;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

import java.util.function.BiConsumer;

public class TriggerComponent implements IAnomalyComponent {

    private final double expandRadius; // На сколько расширяем стандартный бокс
    private final int triggerInterval; // Как часто проверяем попадание (в тиках)
    private final BiConsumer<AnomalyEntity, Entity> onEntityInside;

    private int tickCounter = 0;

    /**
     * @param expandRadius    Дополнительный радиус вокруг хитбокса аномалии
     * @param triggerInterval Задержка между срабатываниями (например, 10 тиков = 0.5 сек)
     * @param onEntityInside  Действие при попадании сущности в зону
     */
    public TriggerComponent(double expandRadius, int triggerInterval, BiConsumer<AnomalyEntity, Entity> onEntityInside) {
        this.expandRadius = expandRadius;
        this.triggerInterval = triggerInterval;
        this.onEntityInside = onEntityInside;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        tickCounter++;
        if (tickCounter >= triggerInterval) {
            tickCounter = 0; // Сбрасываем таймер

            AABB triggerBox = anomaly.getBoundingBox().inflate(expandRadius);

            var targets = anomaly.level().getEntitiesOfClass(
                    Entity.class,
                    triggerBox,
                    entity -> entity.isAlive() && entity != anomaly && !entity.isSpectator()
            );

            for (Entity target : targets) {
                onEntityInside.accept(anomaly, target);
            }
        }
    }
}