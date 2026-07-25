package net.void_.anomalies.components;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class ImpulseComponent implements IAnomalyComponent {

    private final double xMultiplier;
    private final double yMultiplier;
    private final double zMultiplier;
    private final boolean pullToCenter;

    // 🌟 Параметры двухзонности
    private final double outerRadius;
    private final double innerRadius;
    private final double pullForce;
    private final double spinForce;

    public ImpulseComponent(double xMultiplier, double yMultiplier, double zMultiplier, boolean pullToCenter,
                            double outerRadius, double innerRadius, double pullForce, double spinForce) {
        this.xMultiplier = xMultiplier;
        this.yMultiplier = yMultiplier;
        this.zMultiplier = zMultiplier;
        this.pullToCenter = pullToCenter;

        this.outerRadius = outerRadius;
        this.innerRadius = innerRadius;
        this.pullForce = pullForce;
        this.spinForce = spinForce;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {}
    @Override
    public void clientTick(AnomalyEntity anomaly) {}

    /**
     * Применяет импульс.
     * 🌟 Возвращает TRUE, если игрок находится в ЗОНЕ СМЕРТИ (innerRadius) или если это обычная аномалия.
     * Возвращает FALSE, если игрок лишь во внешней зоне притяжения.
     */
    public boolean applyImpulse(AnomalyEntity anomaly, Entity target) {
        if (target == null || !target.isAlive()) return false;

        boolean inDangerZone = true; // По умолчанию для старых аномалий считаем, что мы в опасности

        if (pullToCenter) {
            Vec3 anomalyPos = anomaly.position().add(0, anomaly.getBbHeight() / 2.0, 0);
            Vec3 targetPos = target.position();
            double distance = anomalyPos.distanceTo(targetPos);
            Vec3 direction = anomalyPos.subtract(targetPos).normalize();

            // 🌟 ДВУХЗОННАЯ ЛОГИКА (Если заданы радиусы)
            if (outerRadius > 0 && innerRadius > 0) {
                if (distance <= innerRadius) {
                    // ЗОНА СМЕРТИ: Бешеное затягивание + Ураганное вращение
                    double closenessFactor = 1.0 - (distance / innerRadius);
                    double aggressiveSpin = spinForce * (1.5 + (closenessFactor * 3.0));

                    Vec3 spinVector = new Vec3(-direction.z, 0, direction.x).normalize().scale(aggressiveSpin);

                    target.setDeltaMovement(target.getDeltaMovement()
                            .add(direction.scale(pullForce * 2.5)) // Мощнейший рывок к центру
                            .add(spinVector)                       // Ураганное кручение по касательной
                            .add(0, yMultiplier * 2.0, 0));        // Пулл вверх

                    inDangerZone = true;
                } else if (distance <= outerRadius) {
                    // ВНЕШНЯЯ ЗОНА: Легкое притяжение
                    target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(pullForce)));
                    inDangerZone = false;
                } else {
                    // Игрок вообще вне зоны (на всякий случай)
                    return false;
                }
            }
            // СТАРАЯ ЛОГИКА (обычное притяжение)
            else {
                target.setDeltaMovement(
                        target.getDeltaMovement().add(direction.x * xMultiplier, yMultiplier, direction.z * zMultiplier)
                );
            }
        } else {
            // Обычный толчок (Трамплин и т.д.)
            target.setDeltaMovement(target.getDeltaMovement().add(xMultiplier, yMultiplier, zMultiplier));
        }

        target.hurtMarked = true;
        return inDangerZone;
    }
}