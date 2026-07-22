package net.void_.anomalies.components;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class SoundComponent implements IAnomalyComponent {

    private final SoundEvent soundEvent;
    private final int intervalTicks;
    private final SoundSource soundSource;
    private final float volume;
    private final float pitch;

    private int clientTickCounter = 0;

    /**
     * @param soundEvent  Звуковое событие
     * @param intervalTicks Интервал между воспроизведениями (в тиках)
     * @param soundSource Источник звука (например, BLOCKS или AMBIENT)
     * @param volume Громкость
     * @param pitch Высота тона (1.0F по умолчанию)
     */
    public SoundComponent(SoundEvent soundEvent, int intervalTicks, SoundSource soundSource, float volume, float pitch) {
        this.soundEvent = soundEvent;
        this.intervalTicks = intervalTicks;
        this.soundSource = soundSource;
        this.volume = volume;
        this.pitch = pitch;
    }

    // Упрощенный конструктор с громкостью 1.0 и питчем 1.0 по умолчанию
    public SoundComponent(SoundEvent soundEvent, int intervalTicks, SoundSource soundSource) {
        this(soundEvent, intervalTicks, soundSource, 1.0F, 1.0F);
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {
        // Фоновый звук спавнится на клиенте
    }

    @Override
    public void clientTick(AnomalyEntity anomaly) {
        clientTickCounter++;
        if (clientTickCounter >= intervalTicks) {
            clientTickCounter = 0;

            var level = anomaly.level();
            var pos = anomaly.position();

            // playLocalSound воспроизводит звук только на клиенте
            level.playLocalSound(
                    pos.x, pos.y, pos.z,
                    soundEvent,
                    soundSource,
                    volume,
                    pitch,
                    false // distanceDelay
            );
        }
    }
}