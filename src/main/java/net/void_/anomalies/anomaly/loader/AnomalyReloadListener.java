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
                // GSON прекрасно принимает JsonElement для десериализации
                AnomalyDefinition definition = GSON.fromJson(json, AnomalyDefinition.class);
                REGISTRY.put(location.toString(), definition);
                REGISTRY.put(location.getPath(), definition); // Чтобы можно было искать просто по имени, например "zharka"
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("Anomalies").error("Failed to parse anomaly json for {}", location, e);
            }
        });
    }

    public static AnomalyDefinition get(String type) {
        if (type == null) return null;
        return REGISTRY.get(type.toLowerCase());
    }
}