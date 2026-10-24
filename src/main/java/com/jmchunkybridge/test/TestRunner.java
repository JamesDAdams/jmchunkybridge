package com.jmchunkybridge.test;

import com.jmchunkybridge.task.RegionScanner;
import com.jmsharedmaps.server.StoredTile;
import com.jmsharedmaps.util.SnapshotCompression;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("Running JM Chunky Bridge Test Suite...");
        testCompression();
        testStoredTileKey();
        testRegionScanner();
        System.out.println("ALL TESTS PASSED SUCCESSFULLY!");
    }

    private static void testCompression() {
        System.out.print("Testing SnapshotCompression... ");
        byte[] original = "Test JourneyMap chunk terrain data 1234567890".getBytes();
        byte[] compressed = SnapshotCompression.compress(original);
        if (compressed == null || compressed.length == 0) {
            throw new AssertionError("Compressed data is empty");
        }
        byte[] decompressed = SnapshotCompression.decompress(compressed);
        if (decompressed == null || decompressed.length != original.length) {
            throw new AssertionError("Decompressed length mismatch");
        }
        int fp1 = SnapshotCompression.fingerprint(compressed);
        int fp2 = SnapshotCompression.fingerprint(compressed);
        if (fp1 != fp2 || fp1 == 0) {
            throw new AssertionError("Fingerprint calculation failed");
        }
        System.out.println("OK");
    }

    private static void testStoredTileKey() {
        System.out.print("Testing StoredTile & ChunkKey... ");
        ResourceLocation dim = ResourceLocation.parse("minecraft:overworld");
        StoredTile.ChunkKey k1 = new StoredTile.ChunkKey(dim, 10, 20);
        StoredTile.ChunkKey k2 = new StoredTile.ChunkKey(dim, 10, 20);
        StoredTile.ChunkKey k3 = new StoredTile.ChunkKey(dim, 10, 21);

        if (!k1.equals(k2) || k1.hashCode() != k2.hashCode()) {
            throw new AssertionError("ChunkKey equals/hashCode failed for matching keys");
        }
        if (k1.equals(k3)) {
            throw new AssertionError("ChunkKey equals failed for different keys");
        }
        System.out.println("OK");
    }

    private static void testRegionScanner() throws Exception {
        System.out.print("Testing RegionScanner with mock .mca file... ");
        Path tempDir = Files.createTempDirectory("jmbridge_test_regions");
        try {
            Path mcaFile = tempDir.resolve("r.0.0.mca");
            byte[] header = new byte[4096];

            // Mark chunk (0, 0) as present: offset = sector 2 (0x00, 0x02, 0x00), length = 1 sector
            header[0] = 0x00;
            header[1] = 0x02;
            header[2] = 0x00;
            header[3] = 0x01;

            // Mark chunk (5, 5) -> index = 5 + 5 * 32 = 165 -> byte index = 165 * 4 = 660
            int idx = 165 * 4;
            header[idx] = 0x00;
            header[idx + 1] = 0x03;
            header[idx + 2] = 0x00;
            header[idx + 3] = 0x01;

            Files.write(mcaFile, header);

            ResourceLocation dim = ResourceLocation.parse("minecraft:overworld");
            List<ChunkPos> foundAll = RegionScanner.scanRegionDir(tempDir, dim, null, -1, false);
            if (foundAll.size() != 2) {
                throw new AssertionError("Expected 2 chunks, got " + foundAll.size());
            }

            // Radius test: center at 0,0, radius = 4 chunks (64 blocks) -> chunk (5, 5) is at distance sqrt(50)=7.07 > 4, so only (0,0) matches
            List<ChunkPos> foundRadius = RegionScanner.scanRegionDir(tempDir, dim, new ChunkPos(0, 0), 64, false);
            if (foundRadius.size() != 1 || foundRadius.get(0).x != 0 || foundRadius.get(0).z != 0) {
                throw new AssertionError("Radius filter failed: expected only (0,0), got " + foundRadius);
            }

            System.out.println("OK");
        } finally {
            // Cleanup
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        }
    }
}
