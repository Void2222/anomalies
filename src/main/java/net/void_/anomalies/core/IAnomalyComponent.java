package net.void_.anomalies.core;

import net.minecraft.nbt.CompoundTag;

public interface IAnomalyComponent {

    /**
     * Вызывается на сервере каждые 10 тиков (настраиваемый интервал из AnomalyEntity).
     * Здесь пишем логику триггеров, урона и физики.
     */
    default void serverTick(AnomalyEntity anomaly) {}

    /**
     * Вызывается на клиенте каждый тик.
     * Здесь крутим партиклы и эмбиент-звуки.
     */
    default void clientTick(AnomalyEntity anomaly) {}

    // Новые методы для NBT
    default void save(CompoundTag tag) {}
    default void load(CompoundTag tag) {}

}