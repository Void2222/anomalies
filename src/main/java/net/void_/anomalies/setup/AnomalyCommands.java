package net.void_.anomalies.setup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.void_.anomalies.anomaly.ZoneFactory;
import net.void_.anomalies.anomaly.loader.AnomalyReloadListener;
import net.void_.anomalies.core.AnomalyEntity;

public class AnomalyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("anomaly")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    // 🌟 Автоматические подсказки в чате из загруженных JSON!
                                    AnomalyReloadListener.getKeys().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getLevel();
                                    var player = source.getPlayerOrException();

                                    String type = StringArgumentType.getString(context, "type");

                                    // 🌟 Создаем аномалию универсально
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
        );
    }
}