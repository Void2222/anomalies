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

    // 🌟 Радиус отрисовки партиклов (в блоках). Если игрок дальше — аномалия "спит" на клиенте
    private static final double RENDER_DISTANCE = 70.0D;

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
            if (distanceSq > RENDER_DISTANCE * RENDER_DISTANCE) {
                return; // Игрок далеко — не тратим ресурсы на спавн партиклов
            }
        }

        clientTickCounter++;
        if (clientTickCounter >= nextTriggerTick) {
            clientTickCounter = 0;
            nextTriggerTick = intervalRange.getInt(); // Новый случайный интервал

            var pos = anomaly.position();
            var random = level.random;
            int currentCount = countRange.getInt(); // Случайное количество частиц в этот раз

            for (int i = 0; i < currentCount; i++) {
                double x = pos.x;
                double y = pos.y;
                double z = pos.z;
                double dx = 0.0D;
                double dy = 0.02D;
                double dz = 0.0D;

                switch (shape) {
                    case SPHERE -> {
                        double u = random.nextDouble();
                        double v = random.nextDouble();
                        double theta = u * 2.0 * Math.PI;
                        double phi = Math.acos(2.0 * v - 1.0);
                        double r = radius * Math.cbrt(random.nextDouble());
                        x += r * Math.sin(phi) * Math.cos(theta);
                        y += r * Math.sin(phi) * Math.sin(theta) + (height / 2);
                        z += r * Math.cos(phi);
                    }
                    case CYLINDER -> {
                        double angle = random.nextDouble() * 2.0 * Math.PI;
                        double r = random.nextDouble() * radius;
                        x += r * Math.cos(angle);
                        y += random.nextDouble() * height;
                        z += r * Math.sin(angle);
                        dy = 0.05D;
                    }
                    case DISC -> {
                        double angle = random.nextDouble() * 2.0 * Math.PI;
                        double r = Math.sqrt(random.nextDouble()) * radius;
                        x += r * Math.cos(angle);
                        y += (random.nextDouble() - 0.5) * 0.1D;
                        z += r * Math.sin(angle);
                        dy = 0.01D;
                    }
                }

                level.addParticle(particleType, x, y, z, dx, dy, dz);
            }
        }
    }
}