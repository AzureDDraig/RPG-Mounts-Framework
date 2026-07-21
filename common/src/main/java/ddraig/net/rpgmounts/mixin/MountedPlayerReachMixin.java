package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.config.ModConfig;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to adjust player combat attack reach when riding large mounts.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented attack range multiplier injectors for mounted players.
 * - 2026-06-19: [Fix Warnings] - Add remap = false and require = 0 to getAttackRange Inject to prevent crash on non-Forge loaders.
 */
@Mixin(Player.class)
public class MountedPlayerReachMixin {
    // Target signature matches double return values from attack range getters.
    // Set remap = false and require = 0 since this method is a Forge-added method not present in vanilla/Fabric.
    @Inject(method = "getAttackRange()D", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void adjustReachMounted(CallbackInfoReturnable<Double> info) {
        Player player = (Player) (Object) this;
        if (player.getVehicle() instanceof RPGMountEntity) {
            if (ModConfig.get().combatAndEnhancers.enable_rider_reach_mixin) {
                double bonus = ModConfig.get().combatAndEnhancers.rider_reach_offset;
                info.setReturnValue(info.getReturnValue() + bonus);
            }
        }
    }
}
