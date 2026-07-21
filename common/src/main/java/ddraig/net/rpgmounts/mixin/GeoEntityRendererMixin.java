package ddraig.net.rpgmounts.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Mixin(value = GeoEntityRenderer.class, remap = false)
public class GeoEntityRendererMixin {
    @ModifyArg(
        method = "actuallyRender",
        at = @At(
            value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/renderer/GeoRenderer;actuallyRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIIFFFF)V"
        ),
        index = 10
    )
    private float modifyRedArg(float original) {
        Object anim = ((software.bernie.geckolib.renderer.GeoRenderer<?>)(Object)this).getAnimatable();
        if (anim instanceof Entity entity) {
            if (entity.getTags().contains("rpg_silhouette") || (entity instanceof ddraig.net.rpgmounts.entity.RPGMountEntity mount && mount.isSilhouette())) {
                return 0.0f;
            }
        }
        return original;
    }

    @ModifyArg(
        method = "actuallyRender",
        at = @At(
            value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/renderer/GeoRenderer;actuallyRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIIFFFF)V"
        ),
        index = 11
    )
    private float modifyGreenArg(float original) {
        Object anim = ((software.bernie.geckolib.renderer.GeoRenderer<?>)(Object)this).getAnimatable();
        if (anim instanceof Entity entity) {
            if (entity.getTags().contains("rpg_silhouette") || (entity instanceof ddraig.net.rpgmounts.entity.RPGMountEntity mount && mount.isSilhouette())) {
                return 0.0f;
            }
        }
        return original;
    }

    @ModifyArg(
        method = "actuallyRender",
        at = @At(
            value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/renderer/GeoRenderer;actuallyRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIIFFFF)V"
        ),
        index = 12
    )
    private float modifyBlueArg(float original) {
        Object anim = ((software.bernie.geckolib.renderer.GeoRenderer<?>)(Object)this).getAnimatable();
        if (anim instanceof Entity entity) {
            if (entity.getTags().contains("rpg_silhouette") || (entity instanceof ddraig.net.rpgmounts.entity.RPGMountEntity mount && mount.isSilhouette())) {
                return 0.0f;
            }
        }
        return original;
    }
}
