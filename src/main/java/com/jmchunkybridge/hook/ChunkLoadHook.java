package com.jmchunkybridge.hook;

import com.jmchunkybridge.config.BridgeConfig;
import com.jmchunkybridge.integration.ChunkyIntegration;
import com.jmchunkybridge.service.ChunkSyncService;
import com.jmchunkybridge.service.TileConverter;
import com.jmsharedmaps.server.StoredTile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

public class ChunkLoadHook {

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!BridgeConfig.CONFIG.enabled.get() || !BridgeConfig.CONFIG.autoSyncOnChunkLoad.get()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        ChunkPos pos = chunk.getPos();
        ResourceLocation dim = level.dimension().location();

        boolean chunkyActive = ChunkyIntegration.isChunkyGenerating();
        boolean chunkyOnly = BridgeConfig.CONFIG.autoSyncChunkyOnly.get();

        if (chunkyOnly && !chunkyActive) {
            return;
        }

        if (TileConverter.isTileCached(dim, pos.x, pos.z)) {
            return;
        }

        StoredTile tile = TileConverter.convertAndStore(level, chunk);
        if (tile != null) {
            boolean shouldRelay = true;
            if (chunkyActive && !BridgeConfig.CONFIG.liveRelayDuringChunky.get()) {
                shouldRelay = false;
            }

            if (shouldRelay) {
                if (BridgeConfig.CONFIG.excludeNearbyPlayers.get()) {
                    int radius = BridgeConfig.CONFIG.playerExcludeRadiusChunks.get();
                    ChunkSyncService.relayExcludingNearby(level.getServer(), tile, level, pos.x, pos.z, radius);
                } else {
                    ChunkSyncService.relayToAll(level.getServer(), tile);
                }
            }
        }
    }
}
