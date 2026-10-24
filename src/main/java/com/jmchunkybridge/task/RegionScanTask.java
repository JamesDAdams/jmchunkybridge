package com.jmchunkybridge.task;

import com.jmchunkybridge.config.BridgeConfig;
import com.jmchunkybridge.service.ChunkSyncService;
import com.jmchunkybridge.service.TileConverter;
import com.jmsharedmaps.server.StoredTile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class RegionScanTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionScanTask.class);
    private static final TicketType<Unit> TICKET = TicketType.create("jmchunkybridge", (a, b) -> 0);

    private final MinecraftServer server;
    private final ServerLevel level;
    private final Queue<ChunkPos> queue;
    private final int totalChunks;

    private int processedChunks = 0;
    private int mappedChunks = 0;
    private final long startTime;
    private long lastNotifyTime;
    private boolean paused = false;
    private boolean cancelled = false;
    private boolean completed = false;

    public RegionScanTask(MinecraftServer server, ServerLevel level, List<ChunkPos> chunks) {
        this.server = server;
        this.level = level;
        this.queue = new ArrayDeque<>(chunks);
        this.totalChunks = chunks.size();
        this.startTime = System.currentTimeMillis();
        this.lastNotifyTime = this.startTime;
    }

    public void tick() {
        if (paused || cancelled || completed) {
            return;
        }

        if (queue.isEmpty()) {
            finish();
            return;
        }

        int budget = BridgeConfig.CONFIG.chunksPerTick.get();
        ServerChunkCache chunkSource = level.getChunkSource();
        List<StoredTile> batch = new ArrayList<>();

        for (int i = 0; i < budget && !queue.isEmpty(); i++) {
            ChunkPos pos = queue.poll();
            if (pos == null) break;

            processedChunks++;

            try {
                // If chunk is already in memory
                LevelChunk chunk = chunkSource.getChunkNow(pos.x, pos.z);
                boolean tempLoaded = false;

                if (chunk == null) {
                    chunkSource.addRegionTicket(TICKET, pos, 0, Unit.INSTANCE);
                    tempLoaded = true;
                    net.minecraft.world.level.chunk.ChunkAccess ca = chunkSource.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
                    if (ca instanceof LevelChunk lc) {
                        chunk = lc;
                    }
                }

                if (chunk != null) {
                    StoredTile tile = TileConverter.convertAndStore(level, chunk);
                    if (tile != null) {
                        mappedChunks++;
                        batch.add(tile);
                    }
                }

                if (tempLoaded) {
                    chunkSource.removeRegionTicket(TICKET, pos, 0, Unit.INSTANCE);
                }
            } catch (Throwable t) {
                LOGGER.debug("Error processing chunk pos {}: {}", pos, t.getMessage());
            }
        }

        if (!batch.isEmpty()) {
            ChunkSyncService.relayBatchToAll(server, batch);
        }

        long now = System.currentTimeMillis();
        long notifyIntervalMs = BridgeConfig.CONFIG.notifyIntervalSeconds.get() * 1000L;
        if (now - lastNotifyTime >= notifyIntervalMs) {
            broadcastProgress();
            lastNotifyTime = now;
        }

        if (queue.isEmpty()) {
            finish();
        }
    }

    private void broadcastProgress() {
        long elapsedMs = Math.max(1, System.currentTimeMillis() - startTime);
        double rate = (processedChunks * 1000.0) / elapsedMs;
        int remaining = totalChunks - processedChunks;
        long etaSec = rate > 0 ? (long) (remaining / rate) : 0;
        float percent = totalChunks > 0 ? (processedChunks * 100.0f) / totalChunks : 100.0f;

        String msg = String.format(
                "[JM-Bridge] %s: %d/%d (%.1f%%) | Rate: %.1f chunks/s | New: %d | ETA: %02dm%02ds",
                level.dimension().location(),
                processedChunks,
                totalChunks,
                percent,
                rate,
                mappedChunks,
                etaSec / 60,
                etaSec % 60
        );

        LOGGER.info(msg);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(msg).withStyle(ChatFormatting.AQUA),
                false
        );
    }

    private void finish() {
        completed = true;
        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000L;

        String msg = String.format(
                "[JM-Bridge] Finished for %s! Total: %d | New JourneyMap Tiles: %d | Time: %02dm%02ds",
                level.dimension().location(),
                processedChunks,
                mappedChunks,
                elapsedSec / 60,
                elapsedSec % 60
        );

        LOGGER.info(msg);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(msg).withStyle(ChatFormatting.GREEN),
                false
        );

        ChunkSyncService.startFullCatalogSync(server);
    }

    public void pause() {
        this.paused = true;
    }

    public void resume() {
        this.paused = false;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isCompleted() {
        return completed;
    }

    public int getProcessedChunks() {
        return processedChunks;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getMappedChunks() {
        return mappedChunks;
    }

    public ServerLevel getLevel() {
        return level;
    }
}
