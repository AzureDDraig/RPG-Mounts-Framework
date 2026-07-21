package ddraig.net.rpgmounts.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

public interface IGeckoLibRenderer {
    void render(RPGMountEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight);
    ResourceLocation getTextureLocation(RPGMountEntity entity);
}
