package net.void_.anomalies.anomaly.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.void_.anomalies.components.SoundComponent;

public record SoundConfig(
        String event,
        MinMaxRange interval,
        String source,
        float volume,
        float pitch
) {
    public SoundComponent toComponent() {
        ResourceLocation loc = ResourceLocation.parse(event);

        // 🌟 Ищем в реестре (для ванилы и зарегистрированных звуков)
        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(loc);

        // Если звук не найден в реестре (значит, это кастомный звук твоего мода),
        // создаем его динамически!
        if (soundEvent == null) {
            soundEvent = SoundEvent.createVariableRangeEvent(loc);
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