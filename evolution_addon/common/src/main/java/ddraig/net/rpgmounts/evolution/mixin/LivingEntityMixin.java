package ddraig.net.rpgmounts.evolution.mixin;

import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.evolution.api.AddonEvolutionProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void onDie(DamageSource source, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity) (Object) this;
        if (source.getEntity() instanceof RPGMountEntity mount) {
            AddonEvolutionProvider.recordKill(mount, victim);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(net.minecraft.world.entity.Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof RPGMountEntity mount) {
            UUID ownerUuid = mount.getOwnerUuid();
            if (ownerUuid != null) {
                DatabaseManager.flushPlayerCachesSynchronously(ownerUuid);
            }
        }
    }
}
