package com.dreadmod.command;

import com.dreadmod.config.DreadConfig;
import com.dreadmod.director.HorrorDirector;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /dread spawn            - призвать Сталкера рядом
 * /dread scare            - случайное страшное событие
 * /dread tension <0-100>  - установить уровень страха
 * /dread reload           - перечитать config/dread.json
 */
public final class DreadCommand {
    private DreadCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("dread")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("spawn").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    if (HorrorDirector.spawnStalker(p, 14, 22)) {
                        ctx.getSource().sendFeedback(() -> Text.literal("Он уже рядом..."), false);
                        return 1;
                    }
                    ctx.getSource().sendError(Text.literal("Не нашлось места для появления."));
                    return 0;
                }))
                .then(CommandManager.literal("scare").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    HorrorDirector.triggerAmbient(p);
                    return 1;
                }))
                .then(CommandManager.literal("tension")
                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            int value = IntegerArgumentType.getInteger(ctx, "value");
                            HorrorDirector.setTension(p, value);
                            ctx.getSource().sendFeedback(() -> Text.literal("Уровень страха: " + value), false);
                            return value;
                        })))
                .then(CommandManager.literal("reload").executes(ctx -> {
                    DreadConfig.load();
                    ctx.getSource().sendFeedback(() -> Text.literal("Конфиг Dread перезагружен."), true);
                    return 1;
                })));
    }
}
