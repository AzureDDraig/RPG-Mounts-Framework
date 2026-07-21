package ddraig.net.rpgmounts.forge;

import ddraig.net.rpgmounts.RPGMounts;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge mod initializer entrypoint.
 */
@Mod(RPGMounts.MOD_ID)
public class RPGMountsForge {
    public RPGMountsForge() {
        EventBuses.registerModEventBus(RPGMounts.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        RPGMounts.init();
        
        dev.architectury.utils.EnvExecutor.runInEnv(dev.architectury.utils.Env.CLIENT, () -> () -> {
            ddraig.net.rpgmounts.forge.client.RPGMountsForgeClient.init();
        });
    }
}
