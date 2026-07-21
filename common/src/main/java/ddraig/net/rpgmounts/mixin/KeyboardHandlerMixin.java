package ddraig.net.rpgmounts.mixin;

import ddraig.net.rpgmounts.client.integration.RPGWaypointsIntegration;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (RPGWaypointsIntegration.activeSearchField != null && RPGWaypointsIntegration.activeSearchField.isFocused()) {
            if (RPGWaypointsIntegration.activeSearchField.charTyped((char) codePoint, modifiers)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (RPGWaypointsIntegration.activeSearchField != null && RPGWaypointsIntegration.activeSearchField.isFocused()) {
            // EditBox.keyPressed should only be called for press or repeat (action != 0)
            if (action != 0) {
                if (RPGWaypointsIntegration.activeSearchField.keyPressed(key, scancode, modifiers)) {
                    ci.cancel();
                }
            }
        }
    }
}
