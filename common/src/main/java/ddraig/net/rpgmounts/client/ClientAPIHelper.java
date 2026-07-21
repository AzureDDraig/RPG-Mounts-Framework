package ddraig.net.rpgmounts.client;

import ddraig.net.rpgmounts.client.gui.MountEvolutionPanel;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.client.Minecraft;

/**
 * Lazy helper for executing Client-only UI calls in common packages safely.
 */
public class ClientAPIHelper {
    public static void openDefaultEvolutionScreen(RPGMountEntity mount) {
        Minecraft.getInstance().setScreen(new MountEvolutionPanel(mount));
    }
}
