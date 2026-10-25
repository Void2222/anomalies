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

                                            var anomaly = ZoneFactory.createLight(
                                                    level,
                                                    player.getX(),
                                                    player.getY(),
                                                    player.getZ(),
                                                    type
                                            );

                                            if (anomaly != null) {
                                                net.void_.lightentity.storage.WorldLightManager.get(level).spawn(anomaly);
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
                                                        var mgr = net.void_.lightentity.storage.WorldLightManager.get(player.serverLevel());
                                                        var nearby = mgr.getSpatialGrid().queryRadius(player.getX(), player.getY(), player.getZ(), 10.0, null);
                                                        for (var e : nearby) {
                                                            if (e instanceof net.void_.anomalies.core.IAnomalyHolder ah) {
                                                                AnomalyReloadListener.getStates(ah.getAnomalyType())
                                                                        .forEach(builder::suggest);
                                                                break;
                                                            }
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    ServerPlayer player = source.getPlayerOrException();
                                                    String newState = StringArgumentType.getString(context, "state");

                                                    var mgr = net.void_.lightentity.storage.WorldLightManager.get(player.serverLevel());
                                                    var nearby = mgr.getSpatialGrid().queryRadius(player.getX(), player.getY(), player.getZ(), 10.0, null);

                                                    net.void_.anomalies.core.IAnomalyHolder target = null;
                                                    for (var e : nearby) {
                                                        if (e instanceof net.void_.anomalies.core.IAnomalyHolder ah) {
                                                            target = ah;
                                                            break;
                                                        }
                                                    }

                                                    if (target == null) {
                                                        source.sendFailure(Component.literal("§cПоблизости (10 блоков) не найдено ни одной аномалии!"));
                                                        return 0;
                                                    }

                                                    if (!AnomalyReloadListener.hasState(target.getAnomalyType(), newState)) {
                                                        source.sendFailure(Component.literal("§cУ аномалии '" + target.getAnomalyType() + "' нет состояния '" + newState + "' в JSON!"));
                                                        return 0;
                                                    }

                                                    target.setCurrentState(newState);
                                                    if (target instanceof net.void_.lightentity.api.ILightEntity le) {
                                                        net.void_.lightentity.network.LightEntityTracker.syncEntity(le);
                                                    }
                                                    String aType = target.getAnomalyType();
                                                    source.sendSuccess(() -> Component.literal("§aСостояние аномалии (" + aType + ") изменено на: §e" + newState.toUpperCase()), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }
}