package net.void_.anomalies.anomaly.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.void_.anomalies.anomaly.data.AnomalyDefinition;
import net.void_.anomalies.anomaly.data.AnomalyRecipeDefinition;
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
    private static final Map<String, Map<String, AnomalyRecipeDefinition>> RECIPE_REGISTRY = new HashMap<>();

    public AnomalyReloadListener() {
        super(GSON, "anomalies");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objectMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        REGISTRY.clear();
        RECIPE_REGISTRY.clear();

        objectMap.forEach((location, json) -> {
            try {
                String path = location.getPath().toLowerCase();
                String[] parts = path.split("/");

                // Динамический поиск сегмента "recipes" вне зависимости от стартовой глубины пути
                int recipesIndex = -1;
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("recipes")) {
                        recipesIndex = i;
                        break;
                    }
                }

                if (recipesIndex > 0) {
                    // Структура: .../<type>/recipes/<recipe_name>.json
                    String type = parts[recipesIndex - 1];
                    String rawFileName = parts[parts.length - 1];
                    String recipeName = rawFileName.endsWith(".json")
                            ? rawFileName.substring(0, rawFileName.length() - 5)
                            : rawFileName;

                    AnomalyRecipeDefinition recipeDef = GSON.fromJson(json, AnomalyRecipeDefinition.class);
                    RECIPE_REGISTRY.computeIfAbsent(type, k -> new HashMap<>()).put(recipeName, recipeDef);
                } else {
                    // Обработка состояния аномалии
                    AnomalyDefinition definition = GSON.fromJson(json, AnomalyDefinition.class);
                    String type;
                    String rawFileName;

                    if (parts.length >= 3 && parts[1].equals("states")) {
                        type = parts[0];
                        rawFileName = parts[2];
                    } else if (parts.length == 2) {
                        type = parts[0];
                        rawFileName = parts[1];
                    } else {
                        String fileName = parts[0];
                        type = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
                        rawFileName = "idle.json";
                    }

                    String state = rawFileName.endsWith(".json")
                            ? rawFileName.substring(0, rawFileName.length() - 5)
                            : rawFileName;

                    REGISTRY.computeIfAbsent(type, k -> new HashMap<>()).put(state, definition);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse anomaly resource for {}", location, e);
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

    public static AnomalyRecipeDefinition getRecipe(String type, String recipeName) {
        if (type == null || recipeName == null) return null;
        Map<String, AnomalyRecipeDefinition> recipes = RECIPE_REGISTRY.get(type.toLowerCase());
        return recipes != null ? recipes.get(recipeName.toLowerCase()) : null;
    }

    public static boolean hasRecipe(String type, String recipeName) {
        return getRecipe(type, recipeName) != null;
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