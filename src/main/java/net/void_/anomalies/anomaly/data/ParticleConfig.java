package net.void_.anomalies.anomaly.data;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.void_.anomalies.components.ParticleComponent;

public record ParticleConfig(
        String type,
        String shape,
        MinMaxRange interval, // Теперь диапазон
        double radius,
        double height,
        MinMaxRange count     // Теперь диапазон
) {
    public ParticleComponent toComponent() {
        ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(type));
        ParticleOptions options = (particleType instanceof ParticleOptions opt) ? opt : ParticleTypes.FLAME;

        ParticleComponent.Shape parsedShape;
        try {
            parsedShape = ParticleComponent.Shape.valueOf(shape.toUpperCase());
        } catch (IllegalArgumentException e) {
            parsedShape = ParticleComponent.Shape.SPHERE;
        }

        return new ParticleComponent(options, interval, parsedShape, radius, height, count);
    }
}