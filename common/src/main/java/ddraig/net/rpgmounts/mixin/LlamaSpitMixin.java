package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LlamaSpit.class)
public class LlamaSpitMixin {
    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void onHitEntityTail(EntityHitResult result, CallbackInfo ci) {
        LlamaSpit spit = (LlamaSpit) (Object) this;
        if (spit.getOwner() instanceof RPGMountEntity mount) {
            if (result.getEntity() instanceof LivingEntity target) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                target.hurt(target.damageSources().indirectMagic(spit, mount), 5.0F);
            }
        }
    }
}
