package net.void_.anomalies.core;

import net.minecraft.nbt.CompoundTag;
import net.void_.lightentity.api.ILightComponent;
import net.void_.lightentity.api.ILightEntity;

public interface IAnomalyComponent extends ILightComponent {

    default void serverTick(AnomalyEntity anomaly) {}
    default void clientTick(AnomalyEntity anomaly) {}

    default void save(CompoundTag tag) {}
    default void load(CompoundTag tag) {}

    @Override
    default void serverTick(ILightEntity entity) {
        if (entity instanceof AnomalyEntity ae) {
            serverTick(ae);
        }
    }

    @Override
    default void clientTick(ILightEntity entity) {
        if (entity instanceof AnomalyEntity ae) {
            clientTick(ae);
        }
    }

    @Override
    default void writeToNbt(CompoundTag tag) {
        save(tag);
    }

    @Override
    default void readFromNbt(CompoundTag tag) {
        load(tag);
    }
}