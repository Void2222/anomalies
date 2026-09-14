package net.void_.anomalies.components;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.void_.anomalies.anomaly.data.MinMaxRange;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class SoundComponent implements IAnomalyComponent {

    private final SoundEvent soundEvent;
    private final MinMaxRange intervalRange;
    private final SoundSource soundSource;
    private final float volume;
    private final float pitch;

    private int clientTickCounter = 0;
    private int nextTriggerTick;

    public SoundComponent(SoundEvent soundEvent, MinMaxRange intervalRange, SoundSource soundSource, float volume, float pitch) {
        this.soundEvent = soundEvent;
        this.intervalRange = intervalRange;
        this.soundSource = soundSource;
        this.volume = volume;
        this.pitch = pitch;
        // ⚡ Первое срабатывание запускаем сразу (0-й тик), чтобы звук фазы играл без задержки
        this.nextTriggerTick = 0;
    }

    @Override
    public void serverTick(AnomalyEntity anomaly) {}

    @Override
    public void clientTick(AnomalyEntity anomaly) {
        clientTickCounter++;
        if (clientTickCounter >= nextTriggerTick) {
            clientTickCounter = 0;
            // 🛡️ Защита от 0 тиков (микрофризы при некорректном конфиге)
            this.nextTriggerTick = Math.max(1, intervalRange.getInt());

            var pos = anomaly.position();

            anomaly.level().playLocalSound(
                    pos.x, pos.y, pos.z,
                    soundEvent,
                    soundSource,
                    volume,
                    pitch,
                    false
            );
        }
    }
}