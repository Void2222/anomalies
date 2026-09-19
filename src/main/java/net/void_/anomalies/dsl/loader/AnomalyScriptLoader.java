package net.void_.anomalies.dsl.loader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.dsl.model.AnomalyScriptModel;
import net.void_.anomalies.dsl.registry.AnomalyScriptRegistry;
import net.void_.anomalies.dsl.visitor.AnomalyAstBuilder;
import net.void_.anomalies.grammar.AnomalyDSLLexer;
import net.void_.anomalies.grammar.AnomalyDSLParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class AnomalyScriptLoader implements PreparableReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("Anomalies-DSL");

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor, Executor gameExecutor) {

        return CompletableFuture.supplyAsync(() -> loadScripts(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(this::apply, gameExecutor);
    }

    private Map<String, AnomalyScriptModel> loadScripts(ResourceManager resourceManager) {
        Map<String, AnomalyScriptModel> parsedScripts = new HashMap<>();

        Map<ResourceLocation, Resource> resources =
                resourceManager.listResources("anomalies", location -> location.getPath().endsWith(".anom"));

        Map<String, Map<String, Resource>> folderToScripts = new HashMap<>();
        resources.forEach((location, resource) -> {
            String path = location.getPath();
            String[] parts = path.split("/");

            int folderIndex = (parts.length > 0 && parts[0].equalsIgnoreCase("anomalies")) ? 1 : 0;

            if (parts.length <= folderIndex + 1) {
                LOGGER.error("CRITICAL DSL ERROR: Root file '{}' is invalid! All .anom scripts must be inside a folder (e.g. anomalies/<folder>/<folder>.anom)", path);
                return;
            }

            String folderName = parts[folderIndex].toLowerCase();
            String fileName = parts[parts.length - 1].toLowerCase();

            folderToScripts.computeIfAbsent(folderName, k -> new HashMap<>()).put(fileName, resource);
        });

        folderToScripts.forEach((folderName, files) -> {
            String expectedFileName = folderName + ".anom";

            if (!files.containsKey(expectedFileName)) {
                LOGGER.error("CRITICAL DSL ERROR: Folder 'anomalies/{}' is missing required script '{}.anom'! Found unmatched files: {}",
                        folderName, folderName, files.keySet());
                return;
            }

            if (files.size() > 1) {
                LOGGER.warn("DSL WARNING: Folder 'anomalies/{}' contains multiple .anom files: {}. Executing expected '{}' and ignoring others.",
                        folderName, files.keySet(), expectedFileName);
            }

            Resource scriptResource = files.get(expectedFileName);
            try (InputStream stream = scriptResource.open()) {
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

                AnomalyDSLLexer lexer = new AnomalyDSLLexer(CharStreams.fromString(content));
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                AnomalyDSLParser parser = new AnomalyDSLParser(tokens);

                AnomalyAstBuilder astBuilder = new AnomalyAstBuilder();
                AnomalyScriptModel scriptModel = astBuilder.visitScript(parser.script());

                parsedScripts.put(folderName, scriptModel);
            } catch (Exception e) {
                LOGGER.error("CRITICAL DSL ERROR: Failed to parse '{}' in folder '{}'", expectedFileName, folderName, e);
            }
        });

        return parsedScripts;
    }

    private void apply(Map<String, AnomalyScriptModel> scripts) {
        AnomalyScriptRegistry.clear();

        scripts.forEach((type, script) -> {
            validateScriptBinds(type, script);
            AnomalyScriptRegistry.register(type, script);
            LOGGER.info("Successfully loaded anomaly DSL engine for '{}'", type);
        });
    }

    private void validateScriptBinds(String anomalyType, AnomalyScriptModel script) {
        // Валидация состояний
        script.getBinds().forEach((state, jsonPath) -> {
            if (!jsonPath.toLowerCase().endsWith(".json")) {
                LOGGER.error("CRITICAL DSL ERROR for '{}': State '{}' binds to '{}'. Path MUST explicitly specify '.json' extension!",
                        anomalyType, state, jsonPath);
                return;
            }

            if (!AnomalyReloadListener.hasState(anomalyType, state)) {
                LOGGER.warn("DSL validation warning for '{}': State '{}' binds to '{}', but target JSON definition was not found in 'states/{}'!",
                        anomalyType, state, jsonPath, jsonPath);
            }
        });

        // Валидация рецептов через валидный метод AnomalyReloadListener.hasRecipe
        script.getRecipeBinds().forEach((recipeName, jsonPath) -> {
            if (!jsonPath.toLowerCase().endsWith(".json")) {
                LOGGER.error("CRITICAL DSL ERROR for '{}': Recipe '{}' binds to '{}'. Path MUST explicitly specify '.json' extension!",
                        anomalyType, recipeName, jsonPath);
                return;
            }

            if (!AnomalyReloadListener.hasRecipe(anomalyType, recipeName)) {
                LOGGER.warn("DSL validation warning for '{}': Recipe '{}' binds to '{}', but target recipe JSON was not found in RECIPE_REGISTRY!",
                        anomalyType, recipeName, jsonPath);
            }
        });
    }
}