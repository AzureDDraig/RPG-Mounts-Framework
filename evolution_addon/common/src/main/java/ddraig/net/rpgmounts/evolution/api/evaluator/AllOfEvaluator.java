package ddraig.net.rpgmounts.evolution.api.evaluator;

import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.evolution.api.AddonEvolutionProvider;
import ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/**
 * AND logical operator evaluator (short-circuiting).
 */
public class AllOfEvaluator implements IConditionEvaluator {
    @Override
    public boolean evaluate(EvolutionTreeManager.PrerequisiteConditions conds, RPGMountEntity mount, ServerPlayer player, DatabaseManager.UnlockedMountData uData, List<String> missing) {
        if (conds.all_of == null || conds.all_of.isEmpty()) return true;
        for (EvolutionTreeManager.PrerequisiteConditions subCond : conds.all_of) {
            if (!AddonEvolutionProvider.evaluateConditionsRecursive(subCond, mount, player, uData, missing)) {
                return false;
            }
        }
        return true;
    }
}
