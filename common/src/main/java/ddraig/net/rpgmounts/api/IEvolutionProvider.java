package ddraig.net.rpgmounts.api;

import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/**
 * Interface representing a pluggable evolution provider.
 */
public interface IEvolutionProvider {
    /**
     * Checks if this provider handles custom evolution mechanics for the given mount.
     */
    boolean hasEvolutionPath(RPGMountEntity mount);

    /**
     * Triggers the UI screen presentation for the mount's evolution path.
     * Called client-side when the player clicks the "Evolve" button in the Mount HUD.
     */
    void openEvolutionScreen(RPGMountEntity mount);

    /**
     * Validates and executes the server-side evolution command.
     * Called server-side when receiving a request to evolve a mount.
     *
     * @return true if evolution succeeds
     */
    boolean processServerEvolution(ServerPlayer player, RPGMountEntity mount, String targetTemplateId);

    /**
     * Queries active evolution paths to render in the base mod Mount HUD drawer.
     */
    List<EvolutionPathInfo> getEvolutionPaths(RPGMountEntity mount);

    /**
     * Holds details of an evolution branch pathway.
     */
    public static class EvolutionPathInfo {
        public final String targetTemplateId;
        public final String displayName;
        public final boolean isUnlocked;
        public final List<String> missingRequirements;

        public EvolutionPathInfo(String targetTemplateId, String displayName, boolean isUnlocked, List<String> missingRequirements) {
            this.targetTemplateId = targetTemplateId;
            this.displayName = displayName;
            this.isUnlocked = isUnlocked;
            this.missingRequirements = missingRequirements;
        }
    }
}
