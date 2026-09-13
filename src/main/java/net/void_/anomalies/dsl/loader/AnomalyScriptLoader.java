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

        // Сканируем датапаки на наличие файлов .anom в папке data/<mod>/anomalies/
        Map<ResourceLocation, Resource> resources =
                resourceManager.listResources("anomalies", location -> location.getPath().endsWith(".anom"));

        resources.forEach((location, resource) -> {
            try (InputStream stream = resource.open()) {
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

                // Лексический и синтаксический анализ ANTLR
                AnomalyDSLLexer lexer = new AnomalyDSLLexer(CharStreams.fromString(content));
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                AnomalyDSLParser parser = new AnomalyDSLParser(tokens);

                // Запускаем наш AnomalyAstBuilder для обхода ParseTree и генерации AST
                AnomalyAstBuilder astBuilder = new AnomalyAstBuilder();
                AnomalyScriptModel scriptModel = astBuilder.visitScript(parser.script());

                // Извлекаем имя аномалии из пути: data/void/anomalies/zharka.anom -> "zharka"
                String path = location.getPath();
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                String anomalyType = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase();

                parsedScripts.put(anomalyType, scriptModel);
            } catch (Exception e) {
                LOGGER.error("CRITICAL: Failed to parse anomaly DSL script at {}", location, e);
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
        script.getBinds().forEach((state, jsonPath) -> {
            if (!AnomalyReloadListener.hasState(anomalyType, state)) {
                LOGGER.warn("DSL validation warning for '{}': State '{}' binds to '{}', but target JSON definition was not found!",
                        anomalyType, state, jsonPath);
            }
        });
    }
}