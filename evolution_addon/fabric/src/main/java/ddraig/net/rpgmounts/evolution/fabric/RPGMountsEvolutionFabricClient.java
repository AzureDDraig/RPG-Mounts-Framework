package ddraig.net.rpgmounts.evolution.fabric;

import ddraig.net.rpgmounts.evolution.client.EvolutionPacketsClient;
import net.fabricmc.api.ClientModInitializer;

public class RPGMountsEvolutionFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EvolutionPacketsClient.initClient();
    }
}
