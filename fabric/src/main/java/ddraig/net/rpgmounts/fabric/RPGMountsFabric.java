package ddraig.net.rpgmounts.fabric;

import ddraig.net.rpgmounts.RPGMounts;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric mod initializer entrypoint.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented standard Fabric ModInitializer wrapper calling common init.
 */
public class RPGMountsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RPGMounts.init();
    }
}
