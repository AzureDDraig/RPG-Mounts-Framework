package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.network.ModPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LocalPlayerMixin
 * Intercepts vanilla player inventory keypresses (E) while riding a mount
 * to open the MountGearScreen instead.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Inject(method = "sendOpenInventory", at = @At("HEAD"), cancellable = true)
    private void interceptOpenInventoryWhileRiding(CallbackInfo info) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player.getVehicle() instanceof RPGMountEntity) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            NetworkManager.sendToServer(ModPackets.C2S_REQUEST_OPEN_GEAR, buf);
            info.cancel();
        }
    }


}
