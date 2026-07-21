package ddraig.net.rpgmounts.evolution.fabric;

import ddraig.net.rpgmounts.evolution.RPGMountsEvolutionCommon;
import net.fabricmc.api.ModInitializer;

public class RPGMountsEvolutionFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RPGMountsEvolutionCommon.init();
    }
}
