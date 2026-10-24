package com.jmchunkybridge.service;

import com.jmsharedmaps.server.CatchupSendQueue;
import com.jmsharedmaps.server.StoredTile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class ChunkSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkSyncService.class);

    public static void relayToAll(MinecraftServer server, StoredTile tile) {
        if (server == null || tile == null) return;
        List<StoredTile> list = Collections.singletonList(tile);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                CatchupSendQueue.enqueue(player, list);
            } catch (Throwable t) {
                LOGGER.debug("Failed to enqueue tile to player {}: {}", player.getName().getString(), t.getMessage());
            }
        }
    }

    public static void relayBatchToAll(MinecraftServer server, List<StoredTile> tiles) {
        if (server == null || tiles == null || tiles.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                CatchupSendQueue.enqueue(player, tiles);
            } catch (Throwable t) {
                LOGGER.debug("Failed to enqueue batch to player {}: {}", player.getName().getString(), t.getMessage());
            }
        }
    }

    public static void startFullCatalogSync(MinecraftServer server) {
        if (server == null) return;
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                CatchupSendQueue.startCatalogSync(player);
                count++;
            } catch (Throwable t) {
                LOGGER.warn("Failed to trigger catalog sync for {}: {}", player.getName().getString(), t.getMessage());
            }
        }
        LOGGER.info("Triggered JMSharedMaps catalog sync for {} online players", count);
    }

    public static void startFullCatalogSync(ServerPlayer player) {
        if (player == null) return;
        try {
            CatchupSendQueue.startCatalogSync(player);
        } catch (Throwable t) {
            LOGGER.warn("Failed to trigger catalog sync for {}: {}", player.getName().getString(), t.getMessage());
        }
    }
}
