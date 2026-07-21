package ddraig.net.rpgmounts.item;

import ddraig.net.rpgmounts.network.ModPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bestiary Item
 * Opens the Mounts Bestiary screen when right-clicked.
 * Has an enchanted glint rendering.
 */
public class BestiaryItem extends Item {
    public BestiaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player instanceof ServerPlayer serverPlayer) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                NetworkManager.sendToPlayer(serverPlayer, ModPackets.S2C_OPEN_BESTIARY, buf);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
