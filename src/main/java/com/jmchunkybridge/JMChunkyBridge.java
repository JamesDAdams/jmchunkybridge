package com.jmchunkybridge;

import com.jmchunkybridge.command.JMChunkyCommand;
import com.jmchunkybridge.config.BridgeConfig;
import com.jmchunkybridge.hook.ChunkLoadHook;
import com.jmchunkybridge.integration.ChunkyIntegration;
import com.jmchunkybridge.task.TaskManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(JMChunkyBridge.MODID)
public class JMChunkyBridge {
    public static final String MODID = "jmchunkybridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JMChunkyBridge(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing JourneyMap Chunky Bridge...");

        // Register configuration
        modContainer.registerConfig(ModConfig.Type.COMMON, BridgeConfig.SPEC, "jmchunkybridge-common.toml");

        // Register game event listeners
        NeoForge.EVENT_BUS.register(ChunkLoadHook.class);
        NeoForge.EVENT_BUS.register(TaskManager.class);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        LOGGER.info("JourneyMap Chunky Bridge initialized!");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        JMChunkyCommand.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        boolean chunkyAvailable = ChunkyIntegration.isAvailable();
        LOGGER.info("JM Chunky Bridge server starting (Chunky available: {})", chunkyAvailable);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("JM Chunky Bridge server stopping, cleaning up active tasks...");
        TaskManager.get().cancel();
    }
}
