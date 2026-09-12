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

    // 🌟 Двухуровневое хранилище: type -> (state -> AnomalyDefinition)
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
                String state;

                if (parts.length == 1) {
                    // Старый формат: data/<mod>/anomalies/zharka.json -> type="zharka", state="idle"
                    type = parts[0];
                    state = "idle";
                } else {
                    // Новый формат: data/<mod>/anomalies/zharka/idle.json -> type="zharka", state="idle"
                    type = parts[0];
                    state = parts[1];
                }

                REGISTRY.computeIfAbsent(type, k -> new HashMap<>()).put(state, definition);
            } catch (Exception e) {
                LOGGER.error("Failed to parse anomaly json for {}", location, e);
            }
        });
    }

    /**
     * Получить определение для конкретного состояния аномалии
     */
    public static AnomalyDefinition get(String type, String state) {
        if (type == null) return null;
        Map<String, AnomalyDefinition> states = REGISTRY.get(type.toLowerCase());
        if (states == null || states.isEmpty()) return null;

        if (state != null && states.containsKey(state.toLowerCase())) {
            return states.get(state.toLowerCase());
        }

        // Фоллбэк: если запрошенного состояния нет, пытаемся отдать "idle", либо первичное попавшееся
        if (states.containsKey("idle")) {
            return states.get("idle");
        }
        return states.values().stream().findFirst().orElse(null);
    }

    /**
     * Фоллбэк-метод для получения начального ("idle") состояния
     */
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