package net.void_.anomalies.components;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.void_.anomalies.anomaly.data.MinMaxRange;
import net.void_.anomalies.core.IAnomalyComponent;
import net.void_.lightentity.api.ILightEntity;

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
        this.nextTriggerTick = 0;
    }

    @Override
    public void clientTick(ILightEntity entity) {
        clientTickCounter++;
        if (clientTickCounter >= nextTriggerTick) {
            clientTickCounter = 0;
            this.nextTriggerTick = Math.max(1, intervalRange.getInt());

            Vec3 pos = entity.getPosition();

            entity.getLevel().playLocalSound(
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