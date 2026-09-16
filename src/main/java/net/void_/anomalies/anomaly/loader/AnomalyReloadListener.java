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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AnomalyReloadListener extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("Anomalies");

    private static final Gson GSON = new GsonBuilder()
            .setLenient()
            .registerTypeAdapter(MinMaxRange.class, new MinMaxRange.Deserializer())
            .create();

    private static final Map<String, Map<String, AnomalyDefinition>> REGISTRY = new HashMap<>();

    public AnomalyReloadListener() {
        super(GSON, "anomalies");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objectMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        REGISTRY.clear();

        objectMap.forEach((location, json) -> {
            try {
                AnomalyDefinition definition = GSON.fromJson(json, AnomalyDefinition.class);
                String path = location.getPath().toLowerCase();
                String[] parts = path.split("/");

                String type;
                String rawFileName;

                if (parts.length >= 3 && parts[1].equals("states")) {
                    // Новый стандарт: data/<mod>/anomalies/smart_zharka/states/idle.json
                    type = parts[0];
                    rawFileName = parts[2];
                } else if (parts.length == 2) {
                    // Папочный legacy: data/<mod>/anomalies/smart_zharka/idle.json
                    type = parts[0];
                    rawFileName = parts[1];
                } else {
                    // Однофайловый legacy: data/<mod>/anomalies/zharka.json
                    type = parts[0];
                    rawFileName = "idle.json";
                }

                // Извлекаем имя состояния без расширения .json
                String state = rawFileName.endsWith(".json")
                        ? rawFileName.substring(0, rawFileName.length() - 5)
                        : rawFileName;

                REGISTRY.computeIfAbsent(type, k -> new HashMap<>()).put(state, definition);
            } catch (Exception e) {
                LOGGER.error("Failed to parse anomaly json for {}", location, e);
            }
        });
    }

    public static AnomalyDefinition get(String type, String state) {
        if (type == null) return null;
        Map<String, AnomalyDefinition> states = REGISTRY.get(type.toLowerCase());
        if (states == null || states.isEmpty()) return null;

        if (state != null && states.containsKey(state.toLowerCase())) {
            return states.get(state.toLowerCase());
        }

        if (states.containsKey("idle")) {
            return states.get("idle");
        }
        return states.values().stream().findFirst().orElse(null);
    }

    public static AnomalyDefinition get(String type) {
        return get(type, "idle");
    }

    public static Set<String> getKeys() {
        return REGISTRY.keySet();
    }

    public static Set<String> getStates(String type) {
        if (type == null) return Collections.emptySet();
        Map<String, AnomalyDefinition> states = REGISTRY.get(type.toLowerCase());
        return states != null ? states.keySet() : Collections.emptySet();
    }

    public static boolean exists(String type) {
        return type != null && REGISTRY.containsKey(type.toLowerCase());
    }

    public static boolean hasState(String type, String state) {
        if (type == null || state == null) return false;
        Map<String, AnomalyDefinition> states = REGISTRY.get(type.toLowerCase());
        return states != null && states.containsKey(state.toLowerCase());
    }
}