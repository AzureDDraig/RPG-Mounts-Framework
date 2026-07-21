package ddraig.net.rpgmounts.forge.client.renderer;

import ddraig.net.rpgmounts.client.renderer.IGeckoLibRenderer;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RPGMountGeoRenderer extends GeoEntityRenderer<RPGMountEntity> implements IGeckoLibRenderer {
    public RPGMountGeoRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new RPGMountGeoModel());
    }
}
