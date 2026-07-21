package ddraig.net.rpgmounts.fabric.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client mod initializer entrypoint.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented standard ClientModInitializer wrapper.
 * - 2026-06-19: [Client Init] - Call RPGMountsClient.init() for common client setup.
 */
public class RPGMountsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ddraig.net.rpgmounts.client.renderer.GeckoLibRendererBridge.register(context ->
            new ddraig.net.rpgmounts.fabric.client.renderer.RPGMountGeoRenderer(context)
        );
        ddraig.net.rpgmounts.client.RPGMountsClient.init();
    }
}
