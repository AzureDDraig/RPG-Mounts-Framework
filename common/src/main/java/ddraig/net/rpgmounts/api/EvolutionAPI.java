package ddraig.net.rpgmounts.api;

/**
 * Registry entry point for registering and querying evolution providers.
 */
public class EvolutionAPI {
    private static IEvolutionProvider activeProvider = new DefaultEvolutionProvider();

    public static void registerProvider(IEvolutionProvider provider) {
        activeProvider = provider;
    }

    public static IEvolutionProvider getProvider() {
        return activeProvider;
    }

    public static boolean hasCustomProvider() {
        return !(activeProvider instanceof DefaultEvolutionProvider);
    }
}
