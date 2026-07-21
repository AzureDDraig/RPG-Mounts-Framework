package ddraig.net.rpgmounts.evolution.mixin;

import com.mojang.brigadier.CommandDispatcher;
import ddraig.net.rpgmounts.command.MountCommands;
import ddraig.net.rpgmounts.evolution.RPGMountsEvolutionCommon;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MountCommands.class, remap = false)
public class MountCommandsMixin {

    @Inject(method = "register", at = @At("TAIL"))
    private static void onRegisterTail(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        RPGMountsEvolutionCommon.registerEvolutionSubcommands(dispatcher);
    }
}
