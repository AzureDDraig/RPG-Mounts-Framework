package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityHurtMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity victim = (LivingEntity) (Object) this;

        // Cancel fall damage for flying mounts and their riders
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
            boolean isFlyingType = false;
            if (victim instanceof RPGMountEntity mount) {
                ddraig.net.rpgmounts.data.MountData data = ddraig.net.rpgmounts.data.MountRegistry.getTemplate(mount.getTemplateId());
                if (data != null && data.category.equalsIgnoreCase("FLYING")) {
                    isFlyingType = true;
                }
            } else if (victim.getVehicle() instanceof RPGMountEntity mount) {
                ddraig.net.rpgmounts.data.MountData data = ddraig.net.rpgmounts.data.MountRegistry.getTemplate(mount.getTemplateId());
                if (data != null && data.category.equalsIgnoreCase("FLYING")) {
                    isFlyingType = true;
                }
            }
            if (isFlyingType) {
                cir.setReturnValue(false);
                return;
            }
        }

        // Check if the victim is riding an RPGMountEntity
        if (victim.getVehicle() instanceof RPGMountEntity mount) {
            if (!victim.level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != mount) {
                if (mount.hasThornGuardActive) {
                    attacker.hurt(victim.damageSources().thorns(mount), amount * 0.2F);
                }
                if (mount.hasToxicSecretionsActive && mount.getRandom().nextFloat() < 0.3F) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                    if (victim.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EFFECT, attacker.getX(), attacker.getY(0.5D), attacker.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.05D);
                    }
                }
            }
        }

        // Break stealth when the entity attacks or is hurt
        if (!victim.level().isClientSide) {
            // Case 1: The entity itself is hurt and has stealth active (or is riding/carrying a mount with stealth)
            if (victim instanceof RPGMountEntity mount) {
                breakStealth(mount, victim);
            } else if (victim.getVehicle() instanceof RPGMountEntity mount) {
                breakStealth(mount, victim);
            }

            // Case 2: The attacker has stealth active (handled when attacker hurts someone)
            if (source.getEntity() instanceof LivingEntity attacker) {
                if (attacker instanceof RPGMountEntity mount) {
                    breakStealth(mount, attacker);
                } else if (attacker.getVehicle() instanceof RPGMountEntity mount) {
                    breakStealth(mount, attacker);
                }
            }
        }
    }

    private void breakStealth(RPGMountEntity mount, LivingEntity entity) {
        if (mount.getStealthTicks() > 0) {
            mount.setStealthTicks(0);
            mount.setSilent(false);
            mount.removeEffect(MobEffects.INVISIBILITY);
            entity.setSilent(false);
            entity.removeEffect(MobEffects.INVISIBILITY);
            
            // Also ensure any other passengers get unsilenced/visible
            for (net.minecraft.world.entity.Entity passenger : mount.getPassengers()) {
                if (passenger instanceof LivingEntity living) {
                    living.setSilent(false);
                    living.removeEffect(MobEffects.INVISIBILITY);
                }
            }
        }
    }
}
