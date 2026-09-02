package net.void_.anomalies.components;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.void_.anomalies.anomaly.data.MinMaxRange;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class ParticleComponent implements IAnomalyComponent {

    public enum Shape { SPHERE, CYLINDER, DISC }

    private final ParticleOptions particleType;
    private final MinMaxRange intervalRange;
    private final Shape shape;
    private final double radius;
    private final double height;
    private final MinMaxRange countRange;

    private int clientTickCounter = 0;
    private int nextTriggerTick;

    // 🌟 Предрассчитанный квадрат радиуса отрисовки (70.0^2 = 4900.0)
    private static final double RENDER_DISTANCE_SQ = 4900.0D;

    public ParticleComponent(ParticleOptions particleType, MinMaxRange intervalRange, Shape shape, double radius, double height, MinMaxRange countRange) {
        this.particleType = particleType;
        this.intervalRange = intervalRange;
        this.shape = shape;
        this.radius = radius;
        this.height = height;
        this.countRange = countRange;
        this.nextTriggerTick = intervalRange.getInt();
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {}

    @Override
    public void clientTick(AnomalyEntity anomaly) {
        var level = anomaly.level();

        // 🛑 LOD-ОПТИМИЗАЦИЯ КЛИЕНТА: Проверяем дистанцию до локального игрока
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            double distanceSq = anomaly.distanceToSqr(localPlayer);
            if (distanceSq > RENDER_DISTANCE_SQ) {
                return; // Игрок далеко — не тратим ресурсы на спавн партиклов
            }
        }

        clientTickCounter++;
        if (clientTickCounter >= nextTriggerTick) {
            clientTickCounter = 0;
            nextTriggerTick = intervalRange.getInt(); // Новый случайный интервал

            var pos = anomaly.position();
            var random = level.random;
            int currentCount = countRange.getInt(); // Случайное количество частиц

            double posX = pos.x;
            double posY = pos.y;
            double posZ = pos.z;

            // ⚡ LOOP UNSWITCHING: Выносим switch за пределы цикла
            // Проверяем форму ровно 1 раз за тик вместо N раз
            switch (shape) {
                case SPHERE -> {
                    // ⚡ REJECTION SAMPLING: Спавн в сфере без Math.sin, Math.cos, Math.acos и Math.cbrt
                    for (int i = 0; i < currentCount; i++) {
                        double rx, ry, rz;
                        do {
                            rx = random.nextDouble() * 2.0D - 1.0D;
                            ry = random.nextDouble() * 2.0D - 1.0D;
                            rz = random.nextDouble() * 2.0D - 1.0D;
                        } while (rx * rx + ry * ry + rz * rz > 1.0D);

                        double x = posX + rx * radius;
                        double y = posY + ry * radius + (height * 0.5D);
                        double z = posZ + rz * radius;

                        level.addParticle(particleType, x, y, z, 0.0D, 0.02D, 0.0D);
                    }
                }
                case CYLINDER -> {
                    // ⚡ REJECTION SAMPLING: Спавн в 2D-диске по осям XZ без тригонометрии
                    for (int i = 0; i < currentCount; i++) {
                        double rx, rz;
                        do {
                            rx = random.nextDouble() * 2.0D - 1.0D;
                            rz = random.nextDouble() * 2.0D - 1.0D;
                        } while (rx * rx + rz * rz > 1.0D);

                        double x = posX + rx * radius;
                        double y = posY + random.nextDouble() * height;
                        double z = posZ + rz * radius;

                        level.addParticle(particleType, x, y, z, 0.0D, 0.05D, 0.0D);
                    }
                }
                case DISC -> {
                    // ⚡ REJECTION SAMPLING: Равномерный диск без Math.sqrt, sin и cos
                    for (int i = 0; i < currentCount; i++) {
                        double rx, rz;
                        do {
                            rx = random.nextDouble() * 2.0D - 1.0D;
                            rz = random.nextDouble() * 2.0D - 1.0D;
                        } while (rx * rx + rz * rz > 1.0D);

                        double x = posX + rx * radius;
                        double y = posY + (random.nextDouble() - 0.5D) * 0.1D;
                        double z = posZ + rz * radius;

                        level.addParticle(particleType, x, y, z, 0.0D, 0.01D, 0.0D);
                    }
                }
            }
        }
    }
}