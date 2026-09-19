package net.void_.anomalies.anomaly.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CraftingSession {
    private final String recipeId;
    private int progress;
    private final Map<UUID, Integer> reservedItems; // UUID предмета -> Сколько единиц из стака забронировано

    public CraftingSession(String recipeId, int progress, Map<UUID, Integer> reservedItems) {
        this.recipeId = recipeId;
        this.progress = progress;
        this.reservedItems = reservedItems != null ? reservedItems : new HashMap<>();
    }

    public String getRecipeId() { return recipeId; }
    public int getProgress() { return progress; }
    public void incrementProgress() { this.progress++; }
    public Map<UUID, Integer> getReservedItems() { return reservedItems; }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("RecipeId", recipeId);
        tag.putInt("Progress", progress);

        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> entry : reservedItems.entrySet()) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("UUID", entry.getKey().toString());
            itemTag.putInt("Count", entry.getValue());
            list.add(itemTag);
        }
        tag.put("ReservedItems", list);
        return tag;
    }

    public static CraftingSession deserializeNBT(CompoundTag tag) {
        String recipeId = tag.getString("RecipeId");
        int progress = tag.getInt("Progress");

        Map<UUID, Integer> reservedMap = new HashMap<>();
        if (tag.contains("ReservedItems", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ReservedItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                try {
                    UUID uuid = UUID.fromString(itemTag.getString("UUID"));
                    int count = itemTag.getInt("Count");
                    reservedMap.put(uuid, count);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return new CraftingSession(recipeId, progress, reservedMap);
    }
}