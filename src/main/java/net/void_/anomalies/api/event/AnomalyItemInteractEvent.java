package net.void_.anomalies.api.event;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.void_.anomalies.core.AnomalyEntity;

@Cancelable // Позволяет отменять ванильное поведение (например, если другой мод перехватил предмет)
public class AnomalyItemInteractEvent extends Event {

    private final AnomalyEntity anomaly;
    private final ItemEntity itemEntity;
    private final ItemStack itemStack;
    private final String anomalyType;

    public AnomalyItemInteractEvent(AnomalyEntity anomaly, ItemEntity itemEntity, String anomalyType) {
        this.anomaly = anomaly;
        this.itemEntity = itemEntity;
        this.itemStack = itemEntity.getItem(); // Удобный доступ к самому ItemStack
        this.anomalyType = anomalyType;
    }

    public AnomalyEntity getAnomaly() {
        return anomaly;
    }

    public ItemEntity getItemEntity() {
        return itemEntity;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public String getAnomalyType() {
        return anomalyType;
    }
}