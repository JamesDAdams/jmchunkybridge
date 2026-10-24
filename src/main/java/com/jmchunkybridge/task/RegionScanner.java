package com.jmchunkybridge.task;

import com.jmchunkybridge.service.TileConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RegionScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionScanner.class);
    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");

    public static List<ChunkPos> scanRegionDir(
            Path regionDir,
            ResourceLocation dimension,
            ChunkPos center,
            int radiusBlocks,
            boolean skipAlreadyCached
    ) {
        List<ChunkPos> foundChunks = new ArrayList<>();
        if (regionDir == null || !Files.isDirectory(regionDir)) {
            return foundChunks;
        }

        int maxChunkRadius = radiusBlocks > 0 ? (radiusBlocks + 15) / 16 : -1;
        long maxRadiusSq = maxChunkRadius > 0 ? (long) maxChunkRadius * maxChunkRadius : -1;
        int centerCx = center != null ? center.x : 0;
        int centerCz = center != null ? center.z : 0;

        try (Stream<Path> stream = Files.list(regionDir)) {
            List<Path> regionFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> REGION_FILE_PATTERN.matcher(p.getFileName().toString()).matches())
                    .toList();

            byte[] header = new byte[4096];

            for (Path mcaFile : regionFiles) {
                Matcher matcher = REGION_FILE_PATTERN.matcher(mcaFile.getFileName().toString());
                if (!matcher.matches()) continue;

                int rx = Integer.parseInt(matcher.group(1));
                int rz = Integer.parseInt(matcher.group(2));

                if (maxRadiusSq > 0) {
                    int minChunkX = rx * 32;
                    int maxChunkX = rx * 32 + 31;
                    int minChunkZ = rz * 32;
                    int maxChunkZ = rz * 32 + 31;

                    int closestX = Math.max(minChunkX, Math.min(centerCx, maxChunkX));
                    int closestZ = Math.max(minChunkZ, Math.min(centerCz, maxChunkZ));
                    long distSq = (long) (closestX - centerCx) * (closestX - centerCx)
                                + (long) (closestZ - centerCz) * (closestZ - centerCz);
                    if (distSq > maxRadiusSq) {
                        continue;
                    }
                }

                try (InputStream is = Files.newInputStream(mcaFile)) {
                    int bytesRead = is.readNBytes(header, 0, 4096);
                    if (bytesRead < 4096) continue;

                    for (int i = 0; i < 1024; i++) {
                        int idx = i * 4;
                        int offset = ((header[idx] & 0xFF) << 16)
                                   | ((header[idx + 1] & 0xFF) << 8)
                                   | (header[idx + 2] & 0xFF);

                        if (offset != 0) {
                            int cx = (i & 31);
                            int cz = (i >> 5);
                            int chunkX = rx * 32 + cx;
                            int chunkZ = rz * 32 + cz;

                            if (maxRadiusSq > 0) {
                                long dx = chunkX - centerCx;
                                long dz = chunkZ - centerCz;
                                if (dx * dx + dz * dz > maxRadiusSq) {
                                    continue;
                                }
                            }

                            if (skipAlreadyCached && TileConverter.isTileCached(dimension, chunkX, chunkZ)) {
                                continue;
                            }

                            foundChunks.add(new ChunkPos(chunkX, chunkZ));
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Failed to read header of region file {}: {}", mcaFile.getFileName(), t.getMessage());
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to list region directory {}: {}", regionDir, t.getMessage());
        }

        return foundChunks;
    }
}
