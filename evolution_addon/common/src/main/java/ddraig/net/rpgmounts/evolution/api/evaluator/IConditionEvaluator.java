package ddraig.net.rpgmounts.evolution.api.evaluator;

import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/**
 * Interface representing a condition evaluator.
 */
public interface IConditionEvaluator {
    boolean evaluate(EvolutionTreeManager.PrerequisiteConditions conds, RPGMountEntity mount, ServerPlayer player, DatabaseManager.UnlockedMountData uData, List<String> missing);
}
