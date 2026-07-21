package ddraig.net.rpgmounts.forge.client;

import ddraig.net.rpgmounts.client.RPGMountsClient;
import ddraig.net.rpgmounts.client.renderer.GeckoLibRendererBridge;
import ddraig.net.rpgmounts.forge.client.renderer.RPGMountGeoRenderer;

public class RPGMountsForgeClient {
    public static void init() {
        GeckoLibRendererBridge.register(RPGMountGeoRenderer::new);
        RPGMountsClient.init();
    }
}
