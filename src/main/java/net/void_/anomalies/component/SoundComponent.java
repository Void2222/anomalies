package net.void_.anomalies.component;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.void_.anomalies.core.AnomalyEntity;
import net.void_.anomalies.core.IAnomalyComponent;

public class SoundComponent implements IAnomalyComponent {

    private final SoundEvent ambientSound;
    private final int ambientInterval; // Как часто проигрывать эмбиент (в тиках)
    private final SoundSource source;

    private int soundTickCounter = 0;

    /**
     * @param ambientSound    Звук эмбиента (например, гул или потрескивание)
     * @param ambientInterval Интервал в тиках между повторами эмбиента
     * @param source          Источник звука (например, SoundSource.BLOCKS или HOSTILE)
     */
    public SoundComponent(SoundEvent ambientSound, int ambientInterval, SoundSource source) {
        this.ambientSound = ambientSound;
        this.ambientInterval = ambientInterval;
        this.source = source;
    }

    @Override
    public void clientTick(AnomalyEntity anomaly) {
        if (ambientSound == null) return;

        soundTickCounter++;
        if (soundTickCounter >= ambientInterval) {
            soundTickCounter = 0;

            var level = anomaly.level();
            var pos = anomaly.position();

            // Проигрываем эмбиент-звук в позиции аномалии для всех клиентов рядом
            level.playLocalSound(
                    pos.x, pos.y, pos.z,
                    ambientSound,
                    source,
                    1.0F, // Громкость
                    1.0F, // Пич (высота звука)
                    false
            );
        }
    }

    /**
     * Метод для ручного вызова звука активации (например, резкий бабах или разряд молнии),
     * когда сущность наступает на аномалию.
     */
    public static void playActivationSound(AnomalyEntity anomaly, SoundEvent sound, SoundSource source, float volume, float pitch) {
        var level = anomaly.level();
        if (!level.isClientSide) {
            var pos = anomaly.position();
            level.playSound(
                    null, // Исключение (null — слышат все вокруг)
                    pos.x, pos.y, pos.z,
                    sound,
                    source,
                    volume,
                    pitch
            );
        }
    }
}