package net.void_.anomalies.anomaly.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.void_.anomalies.components.SoundComponent;

public record SoundConfig(
        String event,
        MinMaxRange interval, // Теперь диапазон
        String source,
        float volume,
        float pitch
) {
    public SoundComponent toComponent() {
        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(event));
        if (soundEvent == null) {
            soundEvent = net.minecraft.sounds.SoundEvents.FIRE_AMBIENT;
        }

        SoundSource soundSource;
        try {
            soundSource = SoundSource.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            soundSource = SoundSource.BLOCKS;
        }

        return new SoundComponent(soundEvent, interval, soundSource, volume, pitch);
    }
}