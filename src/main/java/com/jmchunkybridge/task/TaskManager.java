package com.jmchunkybridge.task;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class TaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManager.class);
    private static final TaskManager INSTANCE = new TaskManager();

    private RegionScanTask currentTask = null;
    private final Queue<PendingDimensionTask> pendingDimensionTasks = new ArrayDeque<>();

    public record PendingDimensionTask(
            ServerLevel level,
            int radiusBlocks,
            ChunkPos center,
            boolean skipAlreadyCached
    ) {}

    public static TaskManager get() {
        return INSTANCE;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.tick(event.getServer());
    }

    public synchronized void tick(MinecraftServer server) {
        if (currentTask != null) {
            currentTask.tick();
            if (currentTask.isCompleted() || currentTask.isCancelled()) {
                currentTask = null;
                // Check if more dimensions are in the queue
                if (!pendingDimensionTasks.isEmpty()) {
                    PendingDimensionTask next = pendingDimensionTasks.poll();
                    startSingleDimension(server, next.level(), next.radiusBlocks(), next.center(), next.skipAlreadyCached());
                }
            }
        }
    }

    public synchronized boolean isRunning() {
        return currentTask != null && !currentTask.isCompleted() && !currentTask.isCancelled();
    }

    public synchronized boolean isPaused() {
        return currentTask != null && currentTask.isPaused();
    }

    public synchronized void pause() {
        if (currentTask != null) {
            currentTask.pause();
        }
    }

    public synchronized void resume() {
        if (currentTask != null) {
            currentTask.resume();
        }
    }

    public synchronized void cancel() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        pendingDimensionTasks.clear();
    }

    public synchronized RegionScanTask getCurrentTask() {
        return currentTask;
    }

    public synchronized int getPendingDimensionCount() {
        return pendingDimensionTasks.size();
    }

    public synchronized int startSingleDimension(
            MinecraftServer server,
            ServerLevel level,
            int radiusBlocks,
            ChunkPos center,
            boolean skipAlreadyCached
    ) {
        Path rootPath = server.getWorldPath(LevelResource.ROOT);
        ResourceKey<Level> dimKey = level.dimension();
        Path storageDir = DimensionType.getStorageFolder(dimKey, rootPath);
        Path regionDir = storageDir.resolve("region");

        LOGGER.info("Scanning region files for {} at: {}", dimKey.location(), regionDir);
        List<ChunkPos> chunks = RegionScanner.scanRegionDir(
                regionDir,
                dimKey.location(),
                center,
                radiusBlocks,
                skipAlreadyCached
        );

        if (chunks.isEmpty()) {
            LOGGER.info("No unmapped chunks found to process in {}", dimKey.location());
            return 0;
        }

        currentTask = new RegionScanTask(server, level, chunks);
        LOGGER.info("Started JM Chunky Bridge scan for {}: {} chunks queued", dimKey.location(), chunks.size());
        return chunks.size();
    }

    public synchronized int startAllDimensions(
            MinecraftServer server,
            int radiusBlocks,
            boolean skipAlreadyCached
    ) {
        cancel();
        List<ServerLevel> allLevels = new java.util.ArrayList<>();
        for (ServerLevel lvl : server.getAllLevels()) {
            allLevels.add(lvl);
        }
        int totalQueued = 0;

        for (int i = 0; i < allLevels.size(); i++) {
            ServerLevel lvl = allLevels.get(i);
            if (i == 0) {
                totalQueued += startSingleDimension(server, lvl, radiusBlocks, null, skipAlreadyCached);
            } else {
                pendingDimensionTasks.add(new PendingDimensionTask(lvl, radiusBlocks, null, skipAlreadyCached));
            }
        }

        return totalQueued;
    }
}
