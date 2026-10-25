package net.void_.anomalies.item.processor;

import net.minecraft.nbt.CompoundTag;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;

public class ModifyProcessor {

    /**
     * Полный сброс оверрайдов (очистка тега).
     */
    public static void resetAll(CompoundTag overrides) {
        for (String key : overrides.getAllKeys()) {
            overrides.remove(key);
        }
    }

    /**
     * Автоматически заполняет оверрайды зон значениями из дефолтного JSON-шаблона,
     * если в NBT сущности еще нет ключа zones_count. Это нужно для удобного старта редактирования в GUI.
     */
    public static void ensureZonesInitialized(CompoundTag overrides, AnomalyDefinition def) {
        if (!overrides.contains("zones_count") && def != null && def.zones() != null) {
            overrides.putInt("zones_count", def.zones().size());
            for (int i = 0; i < def.zones().size(); i++) {
                var z = def.zones().get(i);
                overrides.putDouble("zone_" + i + "_radius", z.radius());
                overrides.putDouble("zone_" + i + "_damage", z.damage() != null && z.damage().innerAmount() != null ? z.damage().innerAmount().getMax() : 0.0);
                overrides.putDouble("zone_" + i + "_pullForce", z.physics() != null ? z.physics().pullForce() : 0.05);
                overrides.putDouble("zone_" + i + "_spinForce", z.physics() != null ? z.physics().spinForce() : 0.0);
                overrides.putDouble("zone_" + i + "_impulseY", z.physics() != null ? z.physics().impulseY() : 0.0);
            }
        }
    }

    /**
     * Безопасное удаление оверрайдов конкретной зоны по ее индексу со сдвигом остальных слоев.
     */
    public static void removeZone(CompoundTag overrides, int indexToRemove) {
        int count = overrides.getInt("zones_count");

        if (indexToRemove >= 0 && indexToRemove < count) {
            // Сдвигаем зоны выше удаленной вниз
            for (int i = indexToRemove; i < count - 1; i++) {
                overrides.putDouble("zone_" + i + "_radius", overrides.getDouble("zone_" + (i + 1) + "_radius"));
                overrides.putDouble("zone_" + i + "_damage", overrides.getDouble("zone_" + (i + 1) + "_damage"));
                overrides.putDouble("zone_" + i + "_pullForce", overrides.getDouble("zone_" + (i + 1) + "_pullForce"));
                overrides.putDouble("zone_" + i + "_spinForce", overrides.getDouble("zone_" + (i + 1) + "_spinForce"));
                overrides.putDouble("zone_" + i + "_impulseY", overrides.getDouble("zone_" + (i + 1) + "_impulseY"));
            }
            // Удаляем хвост
            removeZoneOverrides(overrides, count - 1);
            overrides.putInt("zones_count", count - 1);
        }
    }

    /**
     * Точечная зачистка ключей конкретной зоны в NBT.
     */
    public static void removeZoneOverrides(CompoundTag overrides, int index) {
        overrides.remove("zone_" + index + "_radius");
        overrides.remove("zone_" + index + "_damage");
        overrides.remove("zone_" + index + "_pullForce");
        overrides.remove("zone_" + index + "_spinForce");
        overrides.remove("zone_" + index + "_impulseY");
    }

    /**
     * Добавление новой зоны в оверрайды с дефолтными значениями.
     */
    public static void addZone(CompoundTag overrides, double radius, double damage, double pullForce, double spinForce) {
        int count = overrides.contains("zones_count") ? overrides.getInt("zones_count") : 0;

        overrides.putDouble("zone_" + count + "_radius", radius);
        overrides.putDouble("zone_" + count + "_damage", damage);
        overrides.putDouble("zone_" + count + "_pullForce", pullForce);
        overrides.putDouble("zone_" + count + "_spinForce", spinForce);
        overrides.putDouble("zone_" + count + "_impulseY", 0.1);

        overrides.putInt("zones_count", count + 1);
    }
}