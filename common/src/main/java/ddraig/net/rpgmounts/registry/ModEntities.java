package ddraig.net.rpgmounts.registry;

import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Mod Entities Registry
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented entity type registration registry class.
 */
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = 
            DeferredRegister.create(RPGMounts.MOD_ID, Registries.ENTITY_TYPE);

    public static final RegistrySupplier<EntityType<RPGMountEntity>> RPG_MOUNT = ENTITIES.register("rpg_mount",
            () -> EntityType.Builder.of(RPGMountEntity::new, MobCategory.CREATURE)
                    .sized(1.2f, 1.5f)
                    .build("rpg_mount")
    );

    public static void init() {
        ENTITIES.register();
    }
}
