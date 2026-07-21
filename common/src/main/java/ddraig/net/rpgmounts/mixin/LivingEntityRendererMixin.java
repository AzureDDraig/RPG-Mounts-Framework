package ddraig.net.rpgmounts.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @SuppressWarnings("unchecked")
    @Redirect(
        method = "getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;"
        )
    )
    private ResourceLocation redirectTextureLocation(LivingEntityRenderer<?, ?> instance, Entity entity) {
        if (entity != null) {
            for (String tag : entity.getTags()) {
                if (tag.startsWith("rpg_texture:")) {
                    return new ResourceLocation(tag.substring(12));
                }
            }
        }
        return ((LivingEntityRenderer<LivingEntity, ?>) instance).getTextureLocation((LivingEntity) entity);
    }

    @Redirect(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"
        ),
        require = 0
    )
    private void redirectRenderToBuffer(
        net.minecraft.client.model.EntityModel<?> model,
        com.mojang.blaze3d.vertex.PoseStack poseStack,
        com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer,
        int packedLight,
        int packedOverlay,
        float red,
        float green,
        float blue,
        float alpha,
        LivingEntity entity
    ) {
        boolean isSilhouette = false;
        if (entity != null) {
            if (entity instanceof ddraig.net.rpgmounts.entity.RPGMountEntity mount && mount.isSilhouette()) {
                isSilhouette = true;
            } else if (entity.getTags().contains("rpg_silhouette")) {
                isSilhouette = true;
            }
        }

        if (isSilhouette) {
            model.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, 0.0F, 0.0F, 0.0F, alpha);
        } else {
            model.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            shift = At.Shift.AFTER
        )
    )
    private void injectPassengerRotations(
        LivingEntity entity,
        float entityYaw,
        float partialTicks,
        com.mojang.blaze3d.vertex.PoseStack poseStack,
        net.minecraft.client.renderer.MultiBufferSource bufferSource,
        int packedLight,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
    ) {
        if (entity != null && entity.getTags().contains("rpg_mount_preview_rider")) {
            return;
        }
        if (entity.getVehicle() instanceof ddraig.net.rpgmounts.entity.RPGMountEntity mount) {
            int seatIndex = mount.getPassengers().indexOf(entity);
            if (seatIndex >= 0) {
                float mountRoll = mount.getRoll();
                float mountPitch = mount.getXRot();
                
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(mountRoll));
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-mountPitch));
                
                String templateId = mount.getTemplateId();
                ddraig.net.rpgmounts.data.MountData data = ddraig.net.rpgmounts.data.MountRegistry.getTemplate(templateId);
                if (data != null && seatIndex < data.seats.size()) {
                    String boneName = "programmatic_seat_" + templateId + "_" + seatIndex;
                    
                    org.joml.Quaternionf boneRot = getClientBoneRotation(mount, boneName);
                    if (boneRot != null) {
                        poseStack.mulPose(boneRot);
                    }
                }
            }
        }
    }

    private static org.joml.Quaternionf getClientBoneRotation(ddraig.net.rpgmounts.entity.RPGMountEntity mount, String boneName) {
        try {
            Object dispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
            Object renderer = ((net.minecraft.client.renderer.entity.EntityRenderDispatcher) dispatcher).getRenderer(mount);
            Object geoRenderer = null;
            if (renderer instanceof ddraig.net.rpgmounts.client.renderer.RPGMountRenderer rpgRenderer) {
                geoRenderer = rpgRenderer.getGeckoLibRenderer();
            }
            if (geoRenderer != null) {
                java.lang.reflect.Method getGeoModelMethod = geoRenderer.getClass().getMethod("getGeoModel");
                Object model = getGeoModelMethod.invoke(geoRenderer);
                if (model != null) {
                    java.lang.reflect.Method getModelResourceMethod = model.getClass().getMethod("getModelResource", ddraig.net.rpgmounts.entity.RPGMountEntity.class);
                    net.minecraft.resources.ResourceLocation modelLoc = (net.minecraft.resources.ResourceLocation) getModelResourceMethod.invoke(model, mount);
                    
                    java.lang.reflect.Method getBakedModelMethod = model.getClass().getMethod("getBakedModel", net.minecraft.resources.ResourceLocation.class);
                    Object bakedModel = getBakedModelMethod.invoke(model, modelLoc);
                    
                    if (bakedModel != null) {
                        java.lang.reflect.Method getBonesMethod = bakedModel.getClass().getMethod("getBones");
                        Object rootBones = getBonesMethod.invoke(bakedModel);
                        Object bone = null;
                        if (rootBones instanceof java.util.Collection) {
                            for (Object rootObj : (java.util.Collection<?>) rootBones) {
                                bone = findBoneRecursive(rootObj, boneName);
                                if (bone != null) {
                                    break;
                                }
                            }
                        }
                        if (bone != null) {
                            Class<?> boneClass = bone.getClass();
                            java.lang.reflect.Method getModelSpaceMatrix = boneClass.getMethod("getModelSpaceMatrix");
                            Object matrixObj = getModelSpaceMatrix.invoke(bone);
                            if (matrixObj instanceof org.joml.Matrix4f) {
                                org.joml.Matrix4f matrix = (org.joml.Matrix4f) matrixObj;
                                return matrix.getUnnormalizedRotation(new org.joml.Quaternionf());
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Object findBoneRecursive(Object boneObj, String name) {
        if (boneObj == null) return null;
        try {
            Class<?> boneClass = boneObj.getClass();
            java.lang.reflect.Method getNameMethod = boneClass.getMethod("getName");
            String boneName = (String) getNameMethod.invoke(boneObj);
            if (name.equals(boneName)) {
                return boneObj;
            }
            java.lang.reflect.Method getChildBonesMethod = boneClass.getMethod("getChildBones");
            Object children = getChildBonesMethod.invoke(boneObj);
            if (children instanceof java.util.Collection) {
                for (Object child : (java.util.Collection<?>) children) {
                    Object found = findBoneRecursive(child, name);
                    if (found != null) {
                        return found;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
