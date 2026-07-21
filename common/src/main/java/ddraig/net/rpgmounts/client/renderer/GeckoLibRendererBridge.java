package ddraig.net.rpgmounts.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class GeckoLibRendererBridge {
    public interface Factory {
        IGeckoLibRenderer create(EntityRendererProvider.Context context);
    }

    private static Factory factory;

    public static void register(Factory f) {
        factory = f;
    }

    public static IGeckoLibRenderer createRenderer(EntityRendererProvider.Context context) {
        return factory != null ? factory.create(context) : null;
    }
}
