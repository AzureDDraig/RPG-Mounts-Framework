package ddraig.net.rpgmounts.evolution.api.evaluator;

import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.evolution.api.AddonEvolutionProvider;
import ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

/**
 * NOT logical operator evaluator (strict).
 */
public class NoneOfEvaluator implements IConditionEvaluator {
    @Override
    public boolean evaluate(EvolutionTreeManager.PrerequisiteConditions conds, RPGMountEntity mount, ServerPlayer player, DatabaseManager.UnlockedMountData uData, List<String> missing) {
        if (conds.none_of == null || conds.none_of.isEmpty()) return true;
        for (EvolutionTreeManager.PrerequisiteConditions subCond : conds.none_of) {
            List<String> subMissing = new ArrayList<>();
            if (AddonEvolutionProvider.evaluateConditionsRecursive(subCond, mount, player, uData, subMissing)) {
                missing.add("none_of_failed");
                return false;
            }
        }
        return true;
    }
}
