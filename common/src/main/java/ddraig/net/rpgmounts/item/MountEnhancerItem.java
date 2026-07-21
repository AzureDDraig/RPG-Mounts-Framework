package ddraig.net.rpgmounts.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mount Enhancer Item
 * Custom item class that reads NBT tags for enhancers and displays tooltips dynamically.
 * 
 * Change Log:
 * - 2026-06-19: [Initial Creation] - Implemented dynamic tooltip descriptions for enhancers.
 */
public class MountEnhancerItem extends Item {
    public MountEnhancerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        
        if (stack.hasTag() && stack.getTag() != null) {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag.contains("EnhancerCategory")) {
                String category = tag.getString("EnhancerCategory").toUpperCase();
                String type = tag.getString("EnhancerType");
                double value = tag.getDouble("EnhancerValue");
                
                tooltip.add(Component.translatable("tooltip.rpg_mounts.enhancer.category", category));
                
                if ("grant_ability".equals(type) && tag.contains("EnhancerAbility")) {
                    String abName = tag.getString("EnhancerAbility");
                    String baseKey = "ability.rpg_mounts." + abName.toLowerCase().replace(" ", "_");
                    Component abNameComp = Component.translatable(baseKey);
                    String abNameStr = abNameComp.getString();
                    if (abNameStr.equals(baseKey)) {
                        abNameStr = abName;
                    }
                    tooltip.add(Component.translatable("tooltip.rpg_mounts.enhancer.grant_ability", abNameStr));
                } else {
                    tooltip.add(Component.translatable("tooltip.rpg_mounts.enhancer.stat", type));
                    String sign = value >= 0 ? "+" : "";
                    tooltip.add(Component.translatable("tooltip.rpg_mounts.enhancer.modifier", sign + value));
                }
            } else {
                tooltip.add(Component.translatable("tooltip.rpg_mounts.enhancer.configure"));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.rpg_mounts.enhancer.no_modifiers"));
        }
    }
}
