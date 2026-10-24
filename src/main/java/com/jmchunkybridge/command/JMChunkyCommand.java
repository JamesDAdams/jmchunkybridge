package com.jmchunkybridge.command;

import com.jmchunkybridge.config.BridgeConfig;
import com.jmchunkybridge.service.ChunkSyncService;
import com.jmchunkybridge.task.RegionScanTask;
import com.jmchunkybridge.task.TaskManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public class JMChunkyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("jmchunky")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("generate")
                        .executes(ctx -> executeGenerateCurrent(ctx.getSource(), -1))
                        .then(Commands.literal("all")
                                .executes(ctx -> executeGenerateAll(ctx.getSource(), -1))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(16))
                                        .executes(ctx -> executeGenerateAll(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16))
                                .executes(ctx -> executeGenerateCurrent(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(ctx -> executeGenerateDim(
                                                ctx.getSource(),
                                                DimensionArgument.getDimension(ctx, "dimension"),
                                                IntegerArgumentType.getInteger(ctx, "radius")
                                        )))))
                .then(Commands.literal("status")
                        .executes(ctx -> executeStatus(ctx.getSource())))
                .then(Commands.literal("pause")
                        .executes(ctx -> executePause(ctx.getSource())))
                .then(Commands.literal("resume")
                        .executes(ctx -> executeResume(ctx.getSource())))
                .then(Commands.literal("cancel")
                        .executes(ctx -> executeCancel(ctx.getSource())))
                .then(Commands.literal("sendall")
                        .executes(ctx -> executeSendAll(ctx.getSource())))
                .then(Commands.literal("sendme")
                        .executes(ctx -> executeSendMe(ctx.getSource())));

        dispatcher.register(root);

        // Alias /jmsync
        LiteralArgumentBuilder<CommandSourceStack> alias = Commands.literal("jmsync")
                .requires(source -> source.hasPermission(2))
                .redirect(dispatcher.getRoot().getChild("jmchunky"));
        dispatcher.register(alias);
    }

    private static int executeGenerateCurrent(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        ChunkPos center = null;
        if (source.getEntity() instanceof ServerPlayer player && radius > 0) {
            center = player.chunkPosition();
        }

        TaskManager tm = TaskManager.get();
        if (tm.isRunning()) {
            source.sendFailure(Component.literal("A JM-Bridge generation task is already running! Use /jmchunky cancel first.").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean skipCached = BridgeConfig.CONFIG.skipAlreadyCached.get();
        int count = tm.startSingleDimension(source.getServer(), level, radius, center, skipCached);
        if (count == 0) {
            source.sendSuccess(() -> Component.literal("[JM-Bridge] No unmapped chunks to process in " + level.dimension().location()).withStyle(ChatFormatting.YELLOW), true);
        } else {
            source.sendSuccess(() -> Component.literal("[JM-Bridge] Started mapping " + count + " chunks in " + level.dimension().location()).withStyle(ChatFormatting.GREEN), true);
        }
        return count;
    }

    private static int executeGenerateDim(CommandSourceStack source, ServerLevel level, int radius) {
        TaskManager tm = TaskManager.get();
        if (tm.isRunning()) {
            source.sendFailure(Component.literal("A JM-Bridge generation task is already running!").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean skipCached = BridgeConfig.CONFIG.skipAlreadyCached.get();
        int count = tm.startSingleDimension(source.getServer(), level, radius, null, skipCached);
        if (count == 0) {
            source.sendSuccess(() -> Component.literal("[JM-Bridge] No unmapped chunks in " + level.dimension().location()).withStyle(ChatFormatting.YELLOW), true);
        } else {
            source.sendSuccess(() -> Component.literal("[JM-Bridge] Started mapping " + count + " chunks in " + level.dimension().location()).withStyle(ChatFormatting.GREEN), true);
        }
        return count;
    }

    private static int executeGenerateAll(CommandSourceStack source, int radius) {
        TaskManager tm = TaskManager.get();
        if (tm.isRunning()) {
            source.sendFailure(Component.literal("A JM-Bridge generation task is already running!").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean skipCached = BridgeConfig.CONFIG.skipAlreadyCached.get();
        int count = tm.startAllDimensions(source.getServer(), radius, skipCached);
        source.sendSuccess(() -> Component.literal("[JM-Bridge] Started mapping all dimensions (" + count + " initial chunks queued)").withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int executeStatus(CommandSourceStack source) {
        TaskManager tm = TaskManager.get();
        if (!tm.isRunning()) {
            source.sendSuccess(() -> Component.literal("[JM-Bridge] No generation task currently active.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        RegionScanTask task = tm.getCurrentTask();
        if (task == null) {
            source.sendSuccess(() -> Component.literal("[JM-Bridge] Task idle.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        int processed = task.getProcessedChunks();
        int total = task.getTotalChunks();
        int mapped = task.getMappedChunks();
        float percent = total > 0 ? (processed * 100.0f) / total : 100.0f;
        String state = task.isPaused() ? "PAUSED" : "RUNNING";

        String info = String.format(
                "[JM-Bridge] Status: %s | Dim: %s | Progress: %d/%d (%.1f%%) | New Tiles: %d | Pending Dims: %d",
                state,
                task.getLevel().dimension().location(),
                processed,
                total,
                percent,
                mapped,
                tm.getPendingDimensionCount()
        );

        source.sendSuccess(() -> Component.literal(info).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int executePause(CommandSourceStack source) {
        TaskManager tm = TaskManager.get();
        if (!tm.isRunning()) {
            source.sendFailure(Component.literal("[JM-Bridge] No task is currently running.").withStyle(ChatFormatting.RED));
            return 0;
        }
        tm.pause();
        source.sendSuccess(() -> Component.literal("[JM-Bridge] Generation paused.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int executeResume(CommandSourceStack source) {
        TaskManager tm = TaskManager.get();
        if (!tm.isRunning()) {
            source.sendFailure(Component.literal("[JM-Bridge] No task to resume.").withStyle(ChatFormatting.RED));
            return 0;
        }
        tm.resume();
        source.sendSuccess(() -> Component.literal("[JM-Bridge] Generation resumed.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int executeCancel(CommandSourceStack source) {
        TaskManager tm = TaskManager.get();
        if (!tm.isRunning()) {
            source.sendFailure(Component.literal("[JM-Bridge] No task to cancel.").withStyle(ChatFormatting.RED));
            return 0;
        }
        tm.cancel();
        source.sendSuccess(() -> Component.literal("[JM-Bridge] Generation task cancelled.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int executeSendAll(CommandSourceStack source) {
        ChunkSyncService.startFullCatalogSync(source.getServer());
        source.sendSuccess(() -> Component.literal("[JM-Bridge] Triggered JourneyMap map catalog sync for all connected players.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int executeSendMe(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            ChunkSyncService.startFullCatalogSync(player);
            source.sendSuccess(() -> Component.literal("[JM-Bridge] Triggered JourneyMap map catalog sync for your client.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("This command must be run by a player. Use /jmchunky sendall from console."));
            return 0;
        }
    }
}
