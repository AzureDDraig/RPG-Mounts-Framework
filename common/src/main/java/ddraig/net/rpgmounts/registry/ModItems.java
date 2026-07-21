package ddraig.net.rpgmounts.registry;

import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.item.MountEnhancerItem;
import ddraig.net.rpgmounts.item.WhistleItem;
import ddraig.net.rpgmounts.item.BestiaryItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

/**
 * Mod Items Registry
 * Registers custom items for the mod.
 * 
 * Change Log:
 * - 2026-06-19: [Initial Creation] - Implemented item registration registry class for mount enhancers.
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = 
            DeferredRegister.create(RPGMounts.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> DEFENSE_ENHANCER = ITEMS.register("defense_enhancer",
            () -> new MountEnhancerItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistrySupplier<Item> MOVEMENT_ENHANCER = ITEMS.register("movement_enhancer",
            () -> new MountEnhancerItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistrySupplier<Item> DAMAGE_ENHANCER = ITEMS.register("damage_enhancer",
            () -> new MountEnhancerItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistrySupplier<Item> ABILITY_ENHANCER = ITEMS.register("ability_enhancer",
            () -> new MountEnhancerItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistrySupplier<Item> WHISTLE = ITEMS.register("whistle",
            () -> new WhistleItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistrySupplier<Item> BESTIARY = ITEMS.register("bestiary",
            () -> new BestiaryItem(new Item.Properties().stacksTo(1))
    );

    public static void init() {
        ITEMS.register();
    }
}
