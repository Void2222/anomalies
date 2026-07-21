package net.void_.anomalies.component;

import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;
import net.minecraft.world.entity.Entity; // поправь опечатку в импорте ниже, если IDEA подсветит
import net.minecraft.world.phys.AABB;

import java.util.function.BiConsumer;

public class TriggerComponent implements IAnomalyComponent {

    private final double expandRadius; // На сколько расширяем стандартный бокс аномалии для зоны поражения
    private final BiConsumer<AnomalyEntity, Entity> onEntityInside; // Действие при попадании сущности в зону

    /**
     * @param expandRadius   Дополнительный радиус вокруг хитбокса аномалии (например, 0.5 блока)
     * @param onEntityInside Что делаем с сущностью, когда она внутри зоны
     */
    public TriggerComponent(double expandRadius, BiConsumer<AnomalyEntity, Entity> onEntityInside) {
        this.expandRadius = expandRadius;
        this.onEntityInside = onEntityInside;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        // Получаем бокс аномалии и расширяем его для зоны триггера
        AABB triggerBox = anomaly.getBoundingBox().inflate(expandRadius);

        // Ищем все живые сущности в этой зоне (игроки, мобы, выброшенные предметы)
        // Исключаем саму аномалию на всякий случай
        var targets = anomaly.level().getEntitiesOfClass(
                Entity.class,
                triggerBox,
                entity -> entity.isAlive() && entity != anomaly && !entity.isSpectator()
        );

        // Применяем логику для каждой найденной цели
        for (Entity target : targets) {
            onEntityInside.accept(anomaly, target);
        }
    }
}