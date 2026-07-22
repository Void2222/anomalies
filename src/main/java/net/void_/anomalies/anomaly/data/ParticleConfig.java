package net.void_.anomalies.anomaly.data;

import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.void_.anomalies.components.ParticleComponent;

public record ParticleConfig(
        String type,
        String shape,
        int interval,
        double radius,
        double height,
        int count
) {
    public ParticleComponent toComponent() {
        // Парсим тип частицы из строкового ID (например, "minecraft:flame")
        ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(type));
        // На случай, если в JSON передали что-то не то, подстрахуемся дефолтной частицей
        ParticleOptions options = (particleType instanceof ParticleOptions opt) ? opt : net.minecraft.core.particles.ParticleTypes.FLAME;

        ParticleComponent.Shape parsedShape;
        try {
            parsedShape = ParticleComponent.Shape.valueOf(shape.toUpperCase());
        } catch (IllegalArgumentException e) {
            parsedShape = ParticleComponent.Shape.SPHERE;
        }

        return new ParticleComponent(options, interval, parsedShape, radius, height, count);
    }
}