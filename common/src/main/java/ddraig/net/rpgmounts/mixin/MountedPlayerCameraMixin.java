package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to adjust client camera zoom offsets when riding custom mounts.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented setup Camera height and third-person zoom modifiers.
 */
@Mixin(Camera.class)
public abstract class MountedPlayerCameraMixin {
    @Shadow protected abstract void move(double distance, double height, double side);
    @Shadow private org.joml.Quaternionf rotation;

    @Inject(method = "setup", at = @At("TAIL"))
    private void adjustCameraHeight(BlockGetter level, Entity entity, boolean thirdPerson, boolean inverseView, float partialTicks, CallbackInfo info) {
        if (entity.getVehicle() instanceof RPGMountEntity mount) {
            float roll = net.minecraft.util.Mth.lerp(partialTicks, mount.rollO, mount.getRoll());
            if (roll != 0.0F) {
                this.rotation.rotateLocalZ(-roll * net.minecraft.util.Mth.DEG_TO_RAD);
            }
            if (thirdPerson) {
                // Read model scale factor to calculate offset ratios
                float scale = mount.getScale();
                double zoomOffset = (scale > 1.0f) ? (scale - 1.0f) * 2.0D : 0.0D;
                double verticalOffset = (scale > 1.0f) ? (scale - 1.0f) * 0.7D : 0.0D;

                // Adjust third-person distance and height offsets
                this.move(-zoomOffset, verticalOffset, 0.0D);
            }
        }
    }
}
