package ddraig.net.rpgmounts.evolution.api.evaluator;

import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.evolution.api.AddonEvolutionProvider;
import ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

/**
 * OR logical operator evaluator (short-circuiting).
 */
public class AnyOfEvaluator implements IConditionEvaluator {
    @Override
    public boolean evaluate(EvolutionTreeManager.PrerequisiteConditions conds, RPGMountEntity mount, ServerPlayer player, DatabaseManager.UnlockedMountData uData, List<String> missing) {
        if (conds.any_of == null || conds.any_of.isEmpty()) return true;
        List<String> tempMissing = new ArrayList<>();
        for (EvolutionTreeManager.PrerequisiteConditions subCond : conds.any_of) {
            List<String> subMissing = new ArrayList<>();
            if (AddonEvolutionProvider.evaluateConditionsRecursive(subCond, mount, player, uData, subMissing)) {
                return true;
            } else {
                tempMissing.addAll(subMissing);
            }
        }
        missing.add("any_of_failed");
        missing.addAll(tempMissing);
        return false;
    }
}
