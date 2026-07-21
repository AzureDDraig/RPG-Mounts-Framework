package ddraig.net.rpgmounts.evolution.forge;

import ddraig.net.rpgmounts.evolution.RPGMountsEvolutionCommon;
import ddraig.net.rpgmounts.evolution.client.EvolutionPacketsClient;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("rpg_mounts_evolution_framework")
public class RPGMountsEvolutionForge {
    public RPGMountsEvolutionForge() {
        EventBuses.registerModEventBus("rpg_mounts_evolution_framework", FMLJavaModLoadingContext.get().getModEventBus());
        RPGMountsEvolutionCommon.init();
        
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            EvolutionPacketsClient.initClient();
        });
    }
}
