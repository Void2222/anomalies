package net.void_.anomalies.setup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.void_.anomalies.anomaly.ZoneFactory;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.config.AnomalyIgnoreManager;
import net.void_.anomalies.core.AnomalyEntity;

import java.util.List;

public class AnomalyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("anomaly")
                        .requires(source -> source.hasPermission(2))
                        // 1. Команда создания: /anomaly create <type>
                        .then(Commands.literal("create")
                                .then(Commands.argument("type", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            AnomalyReloadListener.getKeys().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            ServerLevel level = source.getLevel();
                                            var player = source.getPlayerOrException();

                                            String type = StringArgumentType.getString(context, "type");

                                            AnomalyEntity anomaly = ZoneFactory.create(
                                                    level,
                                                    player.getX(),
                                                    player.getY(),
                                                    player.getZ(),
                                                    type
                                            );

                                            if (anomaly != null) {
                                                level.addFreshEntity(anomaly);
                                                source.sendSuccess(() -> Component.literal("§aАномалия " + type + " успешно создана!"), true);
                                            } else {
                                                source.sendFailure(Component.literal("§cАномалия типа '" + type + "' не найдена в JSON!"));
                                            }

                                            return 1;
                                        })
                                )
                        )
                        // 2. Команда игнорирования: /anomaly ignore <player> <state>
                        .then(Commands.literal("ignore")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("state", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                    boolean state = BoolArgumentType.getBool(context, "state");

                                                    AnomalyIgnoreManager.setIgnored(target.getUUID(), state);

                                                    if (state) {
                                                        source.sendSuccess(() -> Component.literal(
                                                                "§a[Аномалии] Игрок " + target.getScoreboardName() + " добавлен в список игнорирования."
                                                        ), true);
                                                    } else {
                                                        source.sendSuccess(() -> Component.literal(
                                                                "§c[Аномалии] Игрок " + target.getScoreboardName() + " удален из списка игнорирования."
                                                        ), true);
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                        )
                        // 🌟 3. Команда смены состояния: /anomaly state set <state>
                        .then(Commands.literal("state")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("state", StringArgumentType.string())
                                                .suggests((context, builder) -> {
                                                    var source = context.getSource();
                                                    var player = source.getPlayer();
                                                    if (player != null) {
                                                        List<AnomalyEntity> anomalies = player.level().getEntitiesOfClass(
                                                                AnomalyEntity.class,
                                                                player.getBoundingBox().inflate(10.0)
                                                        );
                                                        if (!anomalies.isEmpty()) {
                                                            AnomalyEntity nearest = anomalies.get(0);
                                                            AnomalyReloadListener.getStates(nearest.getAnomalyType()).forEach(builder::suggest);
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String newState = StringArgumentType.getString(context, "state");

                                                    List<AnomalyEntity> anomalies = player.level().getEntitiesOfClass(
                                                            AnomalyEntity.class,
                                                            player.getBoundingBox().inflate(10.0)
                                                    );

                                                    if (anomalies.isEmpty()) {
                                                        source.sendFailure(Component.literal("§cПоблизости (10 блоков) не найдено ни одной аномалии!"));
                                                        return 0;
                                                    }

                                                    AnomalyEntity anomaly = anomalies.get(0);
                                                    if (!AnomalyReloadListener.hasState(anomaly.getAnomalyType(), newState)) {
                                                        source.sendFailure(Component.literal("§cУ аномалии '" + anomaly.getAnomalyType() + "' нет состояния '" + newState + "' в JSON!"));
                                                        return 0;
                                                    }

                                                    anomaly.setCurrentState(newState);
                                                    source.sendSuccess(() -> Component.literal("§aСостояние аномалии (" + anomaly.getAnomalyType() + ") изменено на: §e" + newState.toUpperCase()), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }
}