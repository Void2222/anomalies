package net.void_.anomalies.test;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.void_.anomalies.Anomalies;
import net.void_.lightentity.api.ILightComponent;
import net.void_.lightentity.api.ILightEntity;
import net.void_.lightentity.api.LightEntityType;
import net.void_.lightentity.core.LightEntityInstance;

import java.util.List;

/**
 * Quick demonstration unit for LightEntity module within Anomalies mod.
 * Spawns client flame particles and applies attraction + damage to nearby players/mobs.
 */
public class TestLightAnomaly {

    public static final ResourceLocation TYPE_ID = new ResourceLocation(Anomalies.MOD_ID, "test_light_anomaly");

    public static final LightEntityType<LightEntityInstance> TYPE = LightEntityType.register(
            TYPE_ID,
            (type, uuid, level, pos) -> {
                LightEntityInstance inst = new LightEntityInstance(type, uuid, level, pos);
                inst.setDimensions(2.0f, 2.0f);
                // Component 1: Visual flame aura (client)
                inst.addComponent(new TestVisualComponent());
                // Component 2: Gravitational pull & damage (server)
                inst.addComponent(new TestPhysicsAndDamageComponent(5.0));
                return inst;
            }
    );

    public static void init() {
        // Triggers static initialization of TYPE
    }

    /**
     * Client visual component: creates spherical fire particles around the entity.
     */
    public static class TestVisualComponent implements ILightComponent {
        private int tickCounter = 0;

        @Override
        public void clientTick(ILightEntity entity) {
            tickCounter++;
            if (tickCounter % 2 != 0) return;

            Level level = entity.getLevel();
            Vec3 pos = entity.getPosition();

            for (int i = 0; i < 4; i++) {
                double rx = (Math.random() - 0.5) * 2.0;
                double ry = (Math.random() - 0.5) * 2.0;
                double rz = (Math.random() - 0.5) * 2.0;
                level.addParticle(ParticleTypes.FLAME, pos.x + rx, pos.y + 1.0 + ry, pos.z + rz, 0, 0.02, 0);
            }
        }
    }

    /**
     * Server logic component: pulls entities closer and hurts them if too close.
     */
    public static class TestPhysicsAndDamageComponent implements ILightComponent {
        private final double radius;

        public TestPhysicsAndDamageComponent(double radius) {
            this.radius = radius;
        }

        @Override
        public void serverTick(ILightEntity entity) {
            Level level = entity.getLevel();
            Vec3 center = entity.getPosition().add(0, 1.0, 0);
            AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                    center.x + radius, center.y + radius, center.z + radius);

            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box);
            for (LivingEntity target : targets) {
                Vec3 tPos = target.position();
                Vec3 diff = center.subtract(tPos);
                double dist = diff.length();

                if (dist > 0.3 && dist <= radius) {
                    // Pull vector
                    Vec3 pull = diff.normalize().scale(0.06);
                    target.setDeltaMovement(target.getDeltaMovement().add(pull));
                    target.hurtMarked = true;
                }

                // Close contact damage
                if (dist <= 1.2 && target.tickCount % 20 == 0) {
                    target.hurt(level.damageSources().magic(), 4.0F);
                }
            }
        }
    }
}
