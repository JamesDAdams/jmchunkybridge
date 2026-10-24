package com.jmchunkybridge.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class BridgeConfig {
    public static final BridgeConfig CONFIG;
    public static final ModConfigSpec SPEC;

    public final ModConfigSpec.BooleanValue enabled;
    public final ModConfigSpec.BooleanValue autoSyncOnChunkLoad;
    public final ModConfigSpec.BooleanValue autoSyncChunkyOnly;
    public final ModConfigSpec.IntValue chunksPerTick;
    public final ModConfigSpec.IntValue notifyIntervalSeconds;
    public final ModConfigSpec.BooleanValue skipAlreadyCached;

    static {
        Pair<BridgeConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(BridgeConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }

    private BridgeConfig(ModConfigSpec.Builder builder) {
        builder.comment("JM Chunky Bridge Configuration");
        builder.push("general");

        enabled = builder
                .comment("Enable or disable JM Chunky Bridge")
                .define("enabled", true);

        autoSyncOnChunkLoad = builder
                .comment("Automatically generate and share JourneyMap data when chunks are generated/loaded by Chunky")
                .define("autoSyncOnChunkLoad", true);

        autoSyncChunkyOnly = builder
                .comment("If true, only auto-generate for chunks when Chunky is actively running or chunk is newly generated. If false, covers any unmapped chunk loaded.")
                .define("autoSyncChunkyOnly", false);

        chunksPerTick = builder
                .comment("Maximum number of chunks to process per server tick during /jmchunky generate")
                .defineInRange("chunksPerTick", 10, 1, 100);

        notifyIntervalSeconds = builder
                .comment("Interval in seconds between generation progress broadcasts in chat/console")
                .defineInRange("notifyIntervalSeconds", 5, 1, 60);

        skipAlreadyCached = builder
                .comment("Skip chunks that are already present in JMSharedMaps server cache during scanning")
                .define("skipAlreadyCached", true);

        builder.pop();
    }
}
