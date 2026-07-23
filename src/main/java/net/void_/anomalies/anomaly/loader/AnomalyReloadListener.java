package net.void_.anomalies.anomaly.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.data.MinMaxRange;

import java.util.HashMap;
import java.util.Map;

public class AnomalyReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder()
            .setLenient()
            .registerTypeAdapter(MinMaxRange.class, new MinMaxRange.Deserializer())
            .create();

    // Хранилище всех загруженных аномалий по их ID
    private static final Map<String, AnomalyDefinition> REGISTRY = new HashMap<>();

    public AnomalyReloadListener() {
        super(GSON, "anomalies"); // Указываем папку в датапаках: data/<mod>/anomalies/
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objectMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        REGISTRY.clear();

        objectMap.forEach((location, json) -> {
            try {
                AnomalyDefinition definition = GSON.fromJson(json, AnomalyDefinition.class);
                // Регистрируем ТОЛЬКО чистый path (например, "zharka" вместо "anomalies:zharka")
                REGISTRY.put(location.getPath().toLowerCase(), definition);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("Anomalies").error("Failed to parse anomaly json for {}", location, e);
            }
        });
    }

    public static AnomalyDefinition get(String type) {
        if (type == null) return null;
        return REGISTRY.get(type.toLowerCase());
    }

    public static java.util.Set<String> getKeys() {
        return REGISTRY.keySet();
    }

    public static boolean exists(String type) {
        return type != null && REGISTRY.containsKey(type.toLowerCase());
    }
}