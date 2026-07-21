package ddraig.net.rpgmounts.mixin;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import ddraig.net.rpgmounts.client.renderer.DynamicMountPackResources;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Inject(method = "discoverAvailable", at = @At("RETURN"), cancellable = true)
    private void injectDynamicPack(CallbackInfoReturnable<Map<String, Pack>> cir) {
        Map<String, Pack> original = cir.getReturnValue();
        if (original.containsKey("rpg_mounts_dynamic")) {
            return;
        }

        Pack pack = Pack.readMetaAndCreate(
            "rpg_mounts_dynamic",
            net.minecraft.network.chat.Component.literal("RPG Mounts Dynamic Resources"),
            true,
            id -> new DynamicMountPackResources(),
            net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
            Pack.Position.TOP,
            net.minecraft.server.packs.repository.PackSource.BUILT_IN
        );

        if (pack != null) {
            Map<String, Pack> modified = new HashMap<>(original);
            modified.put("rpg_mounts_dynamic", pack);
            cir.setReturnValue(Map.copyOf(modified));
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "setSelected",
        at = @At("HEAD"),
        argsOnly = true
    )
    private java.util.Collection<String> modifySelected(java.util.Collection<String> selected) {
        if (selected != null && !selected.contains("rpg_mounts_dynamic")) {
            java.util.List<String> list = new java.util.ArrayList<>(selected);
            list.add("rpg_mounts_dynamic");
            return list;
        }
        return selected;
    }
}
