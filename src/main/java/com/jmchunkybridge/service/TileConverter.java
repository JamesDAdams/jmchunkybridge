package com.jmchunkybridge.service;

import com.jmsharedmaps.server.ServerTileCache;
import com.jmsharedmaps.server.StoredTile;
import com.jmsharedmaps.util.SnapshotCompression;
import io.netty.buffer.Unpooled;
import journeymap.common.chunk.JMChunkSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TileConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TileConverter.class);

    public static boolean isTileCached(ResourceLocation dimension, int chunkX, int chunkZ) {
        ServerTileCache cache = ServerTileCache.get();
        if (cache == null) return false;
        var bucket = cache.bucketTiles("global");
        if (bucket == null) return false;
        return bucket.containsKey(new StoredTile.ChunkKey(dimension, chunkX, chunkZ));
    }

    public static StoredTile convertAndStore(ServerLevel level, LevelChunk chunk) {
        if (level == null || chunk == null) return null;
        ChunkPos pos = chunk.getPos();
        ResourceLocation dim = level.dimension().location();

        try {
            JMChunkSnapshot snapshot = JMChunkSnapshot.fromLevelChunk(chunk, null);
            if (snapshot == null) {
                return null;
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(4096));
            byte[] compressed;
            try {
                snapshot.encode(buf);
                byte[] raw = new byte[buf.readableBytes()];
                buf.readBytes(raw);
                compressed = SnapshotCompression.compress(raw);
            } finally {
                buf.release();
            }

            if (compressed == null || compressed.length == 0) {
                return null;
            }

            StoredTile tile = new StoredTile(dim, pos.x, pos.z, System.currentTimeMillis(), compressed);
            ServerTileCache cache = ServerTileCache.get();
            if (cache != null) {
                boolean accepted = cache.putIfNewer("global", tile);
                if (accepted) {
                    return tile;
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Failed to convert chunk at [{}, {}] in {}: {}", pos.x, pos.z, dim, t.getMessage());
        }
        return null;
    }
}
