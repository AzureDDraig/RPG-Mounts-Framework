package ddraig.net.rpgmounts;

import ddraig.net.rpgmounts.command.MountCommands;
import ddraig.net.rpgmounts.config.ModConfig;
import ddraig.net.rpgmounts.config.AnimationMappingConfig;
import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.network.ModPackets;
import ddraig.net.rpgmounts.registry.ModEntities;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPG Mounts Framework Mod
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented main mod initializer RPGMounts.
 * - 2026-06-18: [ModEntities Init] - Added registration call to ModEntities.
 * - 2026-06-18: [Data Init & Lifecycle] - Added Config/Registry loading and registered Server Started/Stopping lifecycle events for SQLite.
 * - 2026-06-18: [Attributes Registry] - Added EntityAttributeRegistry registration for RPGMountEntity.
 * - 2026-06-18: [Commands Init] - Added registration call to MountCommands.
 * - 2026-06-18: [Packets Init] - Added registration call to ModPackets.
 * - 2026-06-19: [Items Init] - Added ModItems registry initialization.
 */
public class RPGMounts {
    public static final String MOD_ID = "rpg_mounts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Initializing RPG Mounts Framework...");
        ModConfig.load();
        AnimationMappingConfig.load();
        MountRegistry.init();
        ModEntities.init();
        ddraig.net.rpgmounts.registry.ModItems.init();
        
        if (dev.architectury.platform.Platform.isModLoaded("rpg_mounts_evolution_framework")) {
            LOGGER.info("RPG Mounts Evolution Framework Addon detected: initializing API compatibility layer.");
        }
        
        EntityAttributeRegistry.register(ModEntities.RPG_MOUNT, RPGMountEntity::createAttributes);
        MountCommands.init();
        ModPackets.init();
        ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.init();

        // Lifecycle database listeners
        LifecycleEvent.SERVER_STARTED.register(server -> {
            DatabaseManager.init(server.getWorldPath(LevelResource.ROOT));
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            DatabaseManager.close();
        });

        // Player join sync listener
        dev.architectury.event.events.common.PlayerEvent.PLAYER_JOIN.register(player -> {
            try {
                ModPackets.syncUnlockedMounts(player);
                ModPackets.syncTemplates(player);
                ModPackets.syncAbilities(player);
                ModPackets.syncConfig(player);
            } catch (Exception e) {
                LOGGER.error("Failed to sync data for player on join", e);
            }
        });
    }
}


