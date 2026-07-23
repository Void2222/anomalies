package net.void_.anomalies.components;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.void_.anomalies.anomaly.data.MinMaxRange;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

import java.util.function.BiConsumer;

public class TriggerComponent implements IAnomalyComponent {

    private final double expandRadius;
    private final MinMaxRange intervalRange;
    private final BiConsumer<AnomalyEntity, Entity> onEntityInside;

    private int tickCounter = 0;
    private int nextTriggerTick;

    public TriggerComponent(double expandRadius, MinMaxRange intervalRange, BiConsumer<AnomalyEntity, Entity> onEntityInside) {
        this.expandRadius = expandRadius;
        this.intervalRange = intervalRange;
        this.onEntityInside = onEntityInside;
        this.nextTriggerTick = intervalRange.getInt();
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        tickCounter++;
        if (tickCounter >= nextTriggerTick) {
            tickCounter = 0;
            nextTriggerTick = intervalRange.getInt();

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