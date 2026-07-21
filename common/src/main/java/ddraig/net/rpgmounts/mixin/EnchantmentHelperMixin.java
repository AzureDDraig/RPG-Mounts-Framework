package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {
    @Inject(method = "getDepthStrider", at = @At("RETURN"), cancellable = true)
    private static void onGetDepthStrider(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof RPGMountEntity mount && mount.hasDeepDiverActive) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 3));
        }
    }
}
