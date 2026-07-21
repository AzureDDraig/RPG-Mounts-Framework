package ddraig.net.rpgmounts.item;

import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Whistle Item
 * Calls the player's active mount when right-clicked.
 */
public class WhistleItem extends Item {
    public WhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        // Play whistle sound on client and server
        level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.5F, 1.8F);
        
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            boolean found = false;
            if (serverLevel.getServer() != null) {
                for (ServerLevel otherLevel : serverLevel.getServer().getAllLevels()) {
                    for (Entity entity : otherLevel.getAllEntities()) {
                        if (entity instanceof RPGMountEntity mount && player.getUUID().equals(mount.getOwnerUuid())) {
                            found = true;
                            
                            // Check if the mount is aquatic
                            MountData data = MountRegistry.getTemplate(mount.getTemplateId());
                            if (data != null && data.category.equalsIgnoreCase("AQUATIC")) {
                                BlockPos playerPos = player.blockPosition();
                                net.minecraft.world.level.material.FluidState playerFluid = level.getFluidState(playerPos);
                                boolean playerInWater = playerFluid.is(net.minecraft.tags.FluidTags.WATER) || player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
                                boolean playerInLava = playerFluid.is(net.minecraft.tags.FluidTags.LAVA);
                                if (!playerInWater && !playerInLava) {
                                    player.sendSystemMessage(Component.literal("§cCannot summon aquatic mounts out of water or lava."));
                                    return InteractionResultHolder.fail(stack);
                                }
                            }

                            if (mount.level() != serverLevel) {
                                mount.changeDimension(serverLevel);
                            }
                            if (mount.distanceToSqr(player) > 1024) {
                                mount.teleportTo(player.getX(), player.getY(), player.getZ());
                            } else {
                                mount.getNavigation().moveTo(player, 1.25);
                            }
                            break;
                        }
                    }
                    if (found) break;
                }
            }
            if (found) {
                player.displayClientMessage(Component.translatable("message.rpg_mounts.whistle.called"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.rpg_mounts.whistle.not_found"), true);
            }
        }
        
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
