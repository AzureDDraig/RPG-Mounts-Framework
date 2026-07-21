package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityVisibilityMixin {
    @Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
    private void redirectVisibilityPercent(Entity looker, CallbackInfoReturnable<Double> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        RPGMountEntity mount = null;
        if (self instanceof RPGMountEntity m) {
            mount = m;
        } else if (self.getVehicle() instanceof RPGMountEntity m) {
            mount = m;
        }

        if (mount != null && mount.hasShadowCamouflageActive) {
            if (self.level().getMaxLocalRawBrightness(self.blockPosition()) < 5) {
                cir.setReturnValue(cir.getReturnValue() * 0.5);
            }
        }
    }
}
