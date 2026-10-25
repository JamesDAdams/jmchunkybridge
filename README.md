# JM Chunky Bridge

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A **NeoForge 1.21.1** mod that bridges **[Chunky](https://modrinth.com/mod/chunky)** chunk pre-generation with **[JourneyMap](https://modrinth.com/mod/journeymap)** and **[JourneyMap Shared Maps (JMSharedMaps)](https://modrinth.com/mod/journeymap-shared-maps)**.

Chunks generated in the background but never visited by a player still get rendered as JourneyMap tiles and shared with every player on the server.

---

## The Problem

When running a dedicated Minecraft server, administrators often pre-generate world chunks using **Chunky** to eliminate chunk generation lag during player exploration. Additionally, server owners frequently use **JMSharedMaps** so that all players share explored JourneyMap map data.

However, JourneyMap tiles are normally only generated when a player actively visits and renders chunks on the client side. When Chunky generates chunks in the background without players present:
- JourneyMap does **not** generate map tiles for those pre-generated chunks.
- As a result, those chunks are **never synced** to players through JMSharedMaps until a player physically travels there.
- The shared map remains blank despite chunks already existing on the server.

---

## The Solution

**JM Chunky Bridge** hooks into chunk generation and loading on the server:
1. Intercepts server-side chunk loads and generation events (`ChunkEvent.Load`).
2. Converts chunk terrain and blocks into JourneyMap snapshots (`JMChunkSnapshot`) directly on the server without requiring player presence.
3. Compresses and injects the resulting tiles directly into JMSharedMaps' server-side tile cache (`ServerTileCache`).
4. Persists tiles into `.jmregion` files on disk, fully compatible with normal JMSharedMaps catalog sync.
5. Intelligently manages network traffic with configurable live relay modes and proximity filters to avoid client micro-stutters.
6. Provides scanning commands (`/jmchunky generate`) to scan existing `.mca` region files and backfill/sync worlds that were already pre-generated before installing the mod.

---

## Requirements

| Component | Required Version | Notes |
|---|---|---|
| **Minecraft** | `1.21.1` | NeoForge |
| **NeoForge** | `21.1.x` | Mod loader |
| **JourneyMap** | `6.0.x` | Required (`journeymap-neoforge-1.21.1-6.0.9+`) |
| **JMSharedMaps** | `1.7.x` | Required (`jmsharedmaps-1.7.1-1.21.1-neoforge+`) |
| **Chunky** | `1.4.x` | **Optional**; detected via reflection, mod works standalone as well |

---

## Commands

All commands require **permission level 2** (server operator).

| Command | Description |
|---|---|
| `/jmchunky generate [radius] [dimension]` | Scan existing `.mca` region files and generate/sync JourneyMap tiles for cached chunks within a radius (in blocks) around the player or world spawn. |
| `/jmchunky generate all [radius]` | Run chunk scanning and mapping sequentially across all registered dimensions. |
| `/jmchunky status` | Display the progress, current chunk processing rate, and ETA of the active scan task. |
| `/jmchunky pause` | Temporarily pause the active generation task. |
| `/jmchunky resume` | Resume a paused generation task. |
| `/jmchunky cancel` | Cancel the running generation task and clear pending dimensions. |
| `/jmchunky sendall` | Trigger a full JMSharedMaps tile catalog synchronization to all online players. |
| `/jmchunky sendme` | Trigger a full JMSharedMaps tile catalog synchronization to the executing player. |
| `/jmsync` | Alias for `/jmchunky`. |

---

## Configuration

Configuration is located in `config/jmchunkybridge-common.toml`:

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Master switch to enable or disable the bridge. |
| `autoSyncOnChunkLoad` | `true` | Automatically generate JourneyMap data when chunks are loaded by Chunky. |
| `autoSyncChunkyOnly` | `true` | Only auto-generate when Chunky is actively running (prevents lag while flying). |
| `liveRelayDuringChunky` | `false` | Save to server cache without spamming live network packets during Chunky generation. |
| `liveRelayDuringScan` | `false` | Save to server cache and perform a full catalog sync at the end of `/jmchunky generate`. |
| `excludeNearbyPlayers` | `true` | Never relay tiles to players already within render distance of the chunk (since their client already maps it). |
| `playerExcludeRadiusChunks` | `12` | Chunk radius around a player considered nearby to skip relaying duplicate tiles. |
| `chunksPerTick` | `10` | Maximum number of chunks to process per server tick during `/jmchunky generate` (1 - 100). |
| `notifyIntervalSeconds` | `5` | Interval in seconds between progress broadcasts in chat/console (1 - 60). |
| `skipAlreadyCached` | `true` | Skip chunks already present in the server tile cache during region scans. |

---

## Recommended JMSharedMaps Configuration

To avoid client frame drops and micro-stutters when receiving or processing map tiles, adjust these settings in JMSharedMaps:

### Client: `config/jmsharedmaps-common.toml`

```toml
# Max shared map chunks applied to JourneyMap per client tick.
# Default is 4 (up to 80 cartography renders/s on the client thread, causing FPS hitching).
# Setting to 1 or 2 completely eliminates micro-stutters.
maxAppliesPerTick = 2
```

### Server: `config/jmsharedmaps-server.toml`

```toml
# Max map chunks sent to each player per server tick during catch-up and bulk transfers.
# Default is 8 (up to 160 tiles/s). Lowering to 2 or 4 smooths out network packet spikes.
joinSyncTilesPerTick = 4
```

---

## How It Works

```
                                  +-------------------+
                                  | Chunky Generation |
                                  +---------+---------+
                                            |
                                            v (Chunk Load / Generate)
+------------------------+        +---------+---------+
| Existing Region Files  | -----> |    ChunkLoadHook  |
| (.mca Header Scanner)  |        +---------+---------+
+------------------------+                  |
                                            v
                                  +-------------------+
                                  |   TileConverter   |
                                  | (LevelChunk ->    |
                                  |  JMChunkSnapshot) |
                                  +---------+---------+
                                            |
                         +------------------+------------------+
                         |                                     |
                         v                                     v
             +-----------------------+             +-----------------------+
             |    ServerTileCache    |             |   ChunkSyncService    |
             | (Persists to .jmregion|             | (CatchupSendQueue     |
             |  files on disk)       |             |  with proximity filter|
             +-----------------------+             +-----------------------+
```

1. **ChunkLoadHook**: Listens for NeoForge's `ChunkEvent.Load`. When a server-side `LevelChunk` is loaded, it checks whether Chunky is active (if `autoSyncChunkyOnly` is enabled) and whether the chunk is already cached.
2. **TileConverter**: Calls JourneyMap's internal `JMChunkSnapshot.fromLevelChunk(chunk, null)`. The snapshot is serialized into byte data, compressed with JMSharedMaps' `SnapshotCompression`, and wrapped into a `StoredTile`.
3. **Storage & Sync**: The `StoredTile` is saved to `ServerTileCache.get().putIfNewer("global", tile)` (the same method JMSharedMaps uses for client uploads). This ensures tiles persist into `.jmregion` files on disk.
4. **Relay & Performance**:
   - `liveRelayDuringChunky` and `liveRelayDuringScan` default to `false` to avoid flooding player network buffers during massive generation operations.
   - `excludeNearbyPlayers` prevents sending tiles to players within `playerExcludeRadiusChunks` of the chunk, eliminating redundant updates.
5. **RegionScanner**: Directly parses `.mca` region file headers without fully deserializing NBT data, allowing rapid batch backfilling of pre-existing chunks.

---

## Building from Source

The repository includes lightweight PowerShell build scripts using the Eclipse Compiler for Java (ECJ 3.38.0), eliminating the need for a full Gradle setup:

```powershell
# Build the mod jar (outputs to build/jmchunkybridge-1.0.0-neoforge-1.21.1.jar)
./build.ps1

# Run the test suite
./test.ps1
```

Custom paths can be specified via script parameters:
```powershell
./build.ps1 -ModsDir "path/to/mods" -LibrariesDir "path/to/libraries" -JavaExe "path/to/java.exe"
```

---

## Project Structure

```
src/main/java/com/jmchunkybridge/
  JMChunkyBridge.java                 # Mod initialization & event registration
  command/JMChunkyCommand.java        # /jmchunky command dispatcher and subcommands
  config/BridgeConfig.java            # NeoForge common configuration specification
  hook/ChunkLoadHook.java             # ChunkEvent.Load listener
  integration/ChunkyIntegration.java   # Reflection-based Chunky detection and status check
  service/ChunkSyncService.java       # Player packet relay via CatchupSendQueue & proximity filter
  service/TileConverter.java          # LevelChunk -> JMChunkSnapshot -> StoredTile conversion
  task/RegionScanner.java             # Fast .mca region file header parser
  task/RegionScanTask.java            # Tick-paced background chunk generation task
  task/TaskManager.java               # Task lifecycle and server tick driver
  test/TestRunner.java                # Standalone test suite
src/main/resources/
  META-INF/neoforge.mods.toml         # NeoForge mod metadata
  pack.mcmeta                         # Resource pack metadata
```

---

## License

This project is licensed under the [MIT License](LICENSE).
