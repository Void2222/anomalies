package net.void_.anomalies.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.void_.anomalies.anomaly.data.MinMaxRange;
import net.void_.anomalies.client.ClientSetup;
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

    // 🚀 Настройки оптимизации (Culling)
    private static final double RENDER_DISTANCE_SQ = 4900.0D; // 70 блоков
    private static final int OCCLUSION_CHECK_INTERVAL = 8;    // Проверка стен раз в 8 тиков (~0.4 сек)

    private int occlusionCheckTimer;
    private boolean isOccluded = false;

    public ParticleComponent(ParticleOptions particleType, MinMaxRange intervalRange, Shape shape, double radius, double height, MinMaxRange countRange) {
        this.particleType = particleType;
        this.intervalRange = intervalRange;
        this.shape = shape;
        this.radius = radius;
        this.height = height;
        this.countRange = countRange;

        // ⚡ Спавним визуал сразу на первом тике новой фазы
        this.nextTriggerTick = 0;

        // 🎲 Разносим графики рейкаста разным аномалиям, чтобы избежать пиковых микрофризов
        this.occlusionCheckTimer = (int) (Math.random() * OCCLUSION_CHECK_INTERVAL);
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {}

    @Override
    public void clientTick(AnomalyEntity anomaly) {
        Minecraft mc = Minecraft.getInstance();
        Player localPlayer = mc.player;
        if (localPlayer == null) return;

        // 1️⃣ LOD: Предельная дистанция
        double distanceSq = anomaly.distanceToSqr(localPlayer);
        if (distanceSq > RENDER_DISTANCE_SQ) {
            return;
        }

        // 2️⃣ СЛОЙ 1: Frustum Culling (Используем кэш из ClientSetup)
        Frustum frustum = ClientSetup.getLatestFrustum();
        if (frustum != null) {
            AABB particleBounds = anomaly.getBoundingBox().inflate(radius, height * 0.5D, radius);
            if (!frustum.isVisible(particleBounds)) {
                return; // Аномалия вне экрана или за спиной — отсекаем сразу
            }
        }

        // 3️⃣ СЛОЙ 2: Occlusion Culling (Интервальный рейкаст за стенами)
        occlusionCheckTimer++;
        if (occlusionCheckTimer >= OCCLUSION_CHECK_INTERVAL) {
            occlusionCheckTimer = 0;

            // Если игрок вплотную или внутри аномалии — рейкаст не нужен
            double effectRadiusSq = Math.max(radius * radius, 1.0D);
            if (distanceSq <= effectRadiusSq) {
                isOccluded = false;
            } else if (mc.gameRenderer.getMainCamera().isInitialized()) {
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
                Vec3 anomalyCenter = anomaly.position().add(0.0D, height * 0.5D, 0.0D);

                ClipContext context = new ClipContext(
                        cameraPos,
                        anomalyCenter,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        localPlayer
                );

                HitResult hitResult = anomaly.level().clip(context);
                isOccluded = hitResult.getType() != HitResult.Type.MISS;
            }
        }

        if (isOccluded) {
            return; // За глухой стеной — частицы не спавним
        }

        // 4️⃣ Логика спавна частиц
        clientTickCounter++;
        if (clientTickCounter >= nextTriggerTick) {
            clientTickCounter = 0;
            nextTriggerTick = Math.max(1, intervalRange.getInt());

            var level = anomaly.level();
            var pos = anomaly.position();
            var random = level.random;
            int currentCount = countRange.getInt();

            double posX = pos.x;
            double posY = pos.y;
            double posZ = pos.z;

            switch (shape) {
                case SPHERE -> {
                    for (int i = 0; i < currentCount; i++) {
                        double rx, ry, rz;
                        int attempts = 0;
                        do {
                            rx = random.nextDouble() * 2.0D - 1.0D;
                            ry = random.nextDouble() * 2.0D - 1.0D;
                            rz = random.nextDouble() * 2.0D - 1.0D;
                            attempts++;
                        } while (rx * rx + ry * ry + rz * rz > 1.0D && attempts < 10);

                        double x = posX + rx * radius;
                        double y = posY + ry * radius + (height * 0.5D);
                        double z = posZ + rz * radius;

                        level.addParticle(particleType, x, y, z, 0.0D, 0.02D, 0.0D);
                    }
                }
                case CYLINDER -> {
                    for (int i = 0; i < currentCount; i++) {
                        double rx, rz;
                        int attempts = 0;
                        do {
                            rx = random.nextDouble() * 2.0D - 1.0D;
                            rz = random.nextDouble() * 2.0D - 1.0D;
                            attempts++;
                        } while (rx * rx + rz * rz > 1.0D && attempts < 10);

                        double x = posX + rx * radius;
                        double y = posY + random.nextDouble() * height;
                        double z = posZ + rz * radius;

                        level.addParticle(particleType, x, y, z, 0.0D, 0.05D, 0.0D);
                    }
                }
                case DISC -> {
                    for (int i = 0; i < currentCount; i++) {
                        double rx, rz;
                        int attempts = 0;
                        do {
                            rx = random.nextDouble() * 2.0D - 1.0D;
                            rz = random.nextDouble() * 2.0D - 1.0D;
                            attempts++;
                        } while (rx * rx + rz * rz > 1.0D && attempts < 10);

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