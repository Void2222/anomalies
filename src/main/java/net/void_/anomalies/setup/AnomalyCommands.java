package net.void_.anomalies.setup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.void_.anomalies.anomaly.ZoneFactory;
import net.void_.anomalies.core.AnomalyEntity;

public class AnomalyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("anomaly")
                        .requires(source -> source.hasPermission(2)) // Только для операторов / с правами
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    // Подсказки в чате при вводе
                                    builder.suggest("zharka");
                                    builder.suggest("tramplin");
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getLevel();
                                    var player = source.getPlayerOrException();

                                    String type = StringArgumentType.getString(context, "type");
                                    AnomalyEntity anomaly = null;

                                    // Спавним на позиции игрока
                                    double x = player.getX();
                                    double y = player.getY();
                                    double z = player.getZ();

                                    if (type.equalsIgnoreCase("zharka")) {
                                        anomaly = ZoneFactory.createZharka(level, x, y, z);
                                    } else if (type.equalsIgnoreCase("tramplin")) {
                                        anomaly = ZoneFactory.createTramplin(level, x, y, z);
                                    }

                                    if (anomaly != null) {
                                        level.addFreshEntity(anomaly);
                                        source.sendSuccess(() -> Component.literal("§aАномалия " + type + " успешно создана!"), true);
                                    } else {
                                        source.sendFailure(Component.literal("§cНеизвестный тип аномалии!"));
                                    }

                                    return 1;
                                })
                        )
        );
    }
}