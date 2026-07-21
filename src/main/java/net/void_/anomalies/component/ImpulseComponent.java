package net.void_.anomalies.component;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class ImpulseComponent implements IAnomalyComponent {

    private final double xMultiplier;
    private final double yMultiplier;
    private final double zMultiplier;
    private final boolean pullToCenter; // Если true, то затягивает к центру аномалии, иначе просто толкает по векторам

    /**
     * @param xMultiplier Множитель/сила по оси X
     * @param yMultiplier Множитель/сила по оси Y (например, подброс вверх: 0.8D)
     * @param zMultiplier Множитель/сила по оси Z
     * @param pullToCenter Использовать ли физику притяжения к центру
     */
    public ImpulseComponent(double xMultiplier, double yMultiplier, double zMultiplier, boolean pullToCenter) {
        this.xMultiplier = xMultiplier;
        this.yMultiplier = yMultiplier;
        this.zMultiplier = zMultiplier;
        this.pullToCenter = pullToCenter;
    }

    /**
     * Метод для вызова из TriggerComponent при попадании в зону
     */
    public void applyImpulse(AnomalyEntity anomaly, Entity target) {
        if (target == null || !target.isAlive()) return;

        if (pullToCenter) {
            // Вычисляем вектор от цели к центру аномалии
            Vec3 anomalyPos = anomaly.position().add(0, anomaly.getBbHeight() / 2, 0); // Центр аномалии
            Vec3 targetPos = target.position();

            Vec3 direction = anomalyPos.subtract(targetPos).normalize();

            // Применяем притяжение с заданными коэффициентами
            target.setDeltaMovement(
                    target.getDeltaMovement().add(
                            direction.x * xMultiplier,
                            yMultiplier, // часто вверх или тоже к центру
                            direction.z * zMultiplier
                    )
            );
        } else {
            // Просто пихаем с заданным импульсом (например, резкий скачок вверх для Трамплина)
            target.setDeltaMovement(
                    target.getDeltaMovement().add(xMultiplier, yMultiplier, zMultiplier)
            );
        }

        // Обязательно помечаем на сервере, что скорость сущности изменилась, чтобы пакет улетел клиенту
        target.hurtMarked = true;
    }
}