package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.config.ModConfig;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to implement smart adjacent block positioning and mid-air fall protection on dismount.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented safe block calculations and slow falling grace indicators.
 * - 2026-06-19: [Manual Dismissal Check] - Auto-despawn mount on dismount if require_manual_dismissal is false.
 */
@Mixin(Entity.class)
public class SmartDismountMixin {
    @Inject(method = "removePassenger", at = @At("HEAD"))
    private void applySmartDismountSafeLanding(Entity passenger, CallbackInfo info) {
        Entity vehicle = (Entity) (Object) this;
        if (vehicle instanceof RPGMountEntity mount && passenger instanceof LivingEntity player) {
            if (!vehicle.level().isClientSide) {
                BlockPos pos = vehicle.blockPosition();

                // Simple collision check for safe adjacent blocks
                BlockPos[] adjacentPos = {
                        pos.east(), pos.west(), pos.north(), pos.south(),
                        pos.east().north(), pos.west().south()
                };

                for (BlockPos check : adjacentPos) {
                    // Safe floor and empty ceiling check
                    if (vehicle.level().getBlockState(check).isAir() && 
                        vehicle.level().getBlockState(check.above()).isAir() && 
                        vehicle.level().getBlockState(check.below()).isSolid()) {
                        
                        player.teleportTo(check.getX() + 0.5, check.getY(), check.getZ() + 0.5);
                        break;
                    }
                }

                // Reset player vertical velocity on dismount
                player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
                player.hurtMarked = true;

                // Mid-air fall protection checks
                if (vehicle.level().getBlockState(pos.below(2)).isAir() && ModConfig.get().mortalityAndSafety.enable_fall_protection) {
                    int durationTicks = ModConfig.get().mortalityAndSafety.fall_protection_seconds * 20;
                    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationTicks, 0, false, false, true));
                }

                // Auto despawn/dismiss if auto_dismiss_on_dismount is true
                if (ModConfig.get().general.auto_dismiss_on_dismount) {
                    if (player.getUUID().equals(mount.getOwnerUuid())) {
                        mount.discard();
                    }
                }
            }
        }
    }
}
