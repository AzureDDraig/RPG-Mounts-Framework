package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntitySpeedFactorMixin {
    @Inject(method = "getBlockSpeedFactor", at = @At("HEAD"), cancellable = true)
    private void onGetBlockSpeedFactor(CallbackInfoReturnable<Float> cir) {
        Entity entity = (Entity) (Object) this;
        if (entity.getVehicle() instanceof RPGMountEntity mount && mount.hasTractionTreadActive) {
            cir.setReturnValue(1.0F);
        } else if (entity instanceof RPGMountEntity mount && mount.hasTractionTreadActive) {
            cir.setReturnValue(1.0F);
        }
    }
}
