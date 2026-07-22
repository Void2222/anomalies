package net.void_.anomalies.components;

import net.minecraft.core.particles.ParticleOptions;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class ParticleComponent implements IAnomalyComponent {

    public enum Shape {
        SPHERE,   // Сфера (вокруг центра)
        CYLINDER, // Столб (высокий цилиндр, идеально для Жарки)
        DISC      // Диск/лужа на земле (плоский круг, идеально для Трамплина)
    }

    private final ParticleOptions particleType;
    private final int spawnInterval;
    private final Shape shape;
    private final double radius;     // Радиус зоны
    private final double height;     // Высота зоны (для цилиндра/столба)
    private final int count;         // Сколько частиц за раз

    private int clientTickCounter = 0;

    /**
     * @param particleType  Тип частицы
     * @param spawnInterval Как часто спавнить (в тиках)
     * @param shape         Форма (SPHERE, CYLINDER, DISC)
     * @param radius        Радиус зоны
     * @param height        Высота зоны
     * @param count         Количество частиц за один спавн
     */
    public ParticleComponent(ParticleOptions particleType, int spawnInterval, Shape shape, double radius, double height, int count) {
        this.particleType = particleType;
        this.spawnInterval = spawnInterval;
        this.shape = shape;
        this.radius = radius;
        this.height = height;
        this.count = count;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        // На сервере ничего не делаем — партиклы полностью клиентские!
    }

    @Override
    public void clientTick(AnomalyEntity anomaly) {
        var level = anomaly.level();

        clientTickCounter++;
        if (clientTickCounter >= spawnInterval) {
            clientTickCounter = 0;

            var pos = anomaly.position();
            var random = level.random;

            for (int i = 0; i < count; i++) {
                double x = pos.x;
                double y = pos.y;
                double z = pos.z;
                double dx = 0.0D;
                double dy = 0.02D; // Небольшое движение вверх по умолчанию
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
                        // Столб огня (равномерно по кругу + снизу доверху)
                        double angle = random.nextDouble() * 2.0 * Math.PI;
                        double r = random.nextDouble() * radius;
                        x += r * Math.cos(angle);
                        y += random.nextDouble() * height; // Заполняет всю высоту столба
                        z += r * Math.sin(angle);
                        dy = 0.05D; // Столб чуть быстрее тянет вверх
                    }
                    case DISC -> {
                        // Лужа на земле (круг с минимальной высотой)
                        double angle = random.nextDouble() * 2.0 * Math.PI;
                        double r = Math.sqrt(random.nextDouble()) * radius; // Плотнее к центру
                        x += r * Math.cos(angle);
                        y += (random.nextDouble() - 0.5) * 0.1D; // Почти вровень с полом
                        z += r * Math.sin(angle);
                        dy = 0.01D;
                    }
                }

                // Спавним частицу напрямую на клиенте с векторной скоростью
                level.addParticle(
                        particleType,
                        x, y, z,
                        dx, dy, dz
                );
            }
        }
    }
}