package com.jmchunkybridge.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkyIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkyIntegration.class);
    private static Boolean chunkyAvailable = null;

    public static boolean isAvailable() {
        if (chunkyAvailable == null) {
            try {
                Class.forName("org.popcraft.chunky.ChunkyProvider");
                chunkyAvailable = true;
                LOGGER.info("Chunky mod detected and integrated successfully!");
            } catch (Throwable ignored) {
                chunkyAvailable = false;
                LOGGER.info("Chunky mod not detected; bridge will run in standalone mode.");
            }
        }
        return chunkyAvailable;
    }

    public static boolean isChunkyGenerating() {
        if (!isAvailable()) return false;
        try {
            org.popcraft.chunky.Chunky chunky = org.popcraft.chunky.ChunkyProvider.get();
            if (chunky != null && chunky.getGenerationTasks() != null) {
                return !chunky.getGenerationTasks().isEmpty();
            }
        } catch (Throwable t) {
            LOGGER.debug("Error checking Chunky tasks: {}", t.getMessage());
        }
        return false;
    }

    public static int getActiveChunkyTaskCount() {
        if (!isAvailable()) return 0;
        try {
            org.popcraft.chunky.Chunky chunky = org.popcraft.chunky.ChunkyProvider.get();
            if (chunky != null && chunky.getGenerationTasks() != null) {
                return chunky.getGenerationTasks().size();
            }
        } catch (Throwable ignored) {}
        return 0;
    }
}
