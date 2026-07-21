package ddraig.net.rpgmounts.api;

import ddraig.net.rpgmounts.config.ModConfig;
import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.network.ModPackets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultEvolutionProvider implements IEvolutionProvider {

    @Override
    public boolean hasEvolutionPath(RPGMountEntity mount) {
        MountData current = MountRegistry.getTemplate(mount.getTemplateId());
        return current != null && current.evolution != null && !current.evolution.targetId.isEmpty();
    }

    @Override
    public void openEvolutionScreen(RPGMountEntity mount) {
        dev.architectury.utils.EnvExecutor.runInEnv(dev.architectury.utils.Env.CLIENT, () -> () -> {
            ddraig.net.rpgmounts.client.ClientAPIHelper.openDefaultEvolutionScreen(mount);
        });
    }

    @Override
    public boolean processServerEvolution(ServerPlayer player, RPGMountEntity mount, String targetTemplateId) {
        String mountId = mount.getTemplateId();
        MountData current = MountRegistry.getTemplate(mountId);
        MountData target = MountRegistry.getTemplate(targetTemplateId);

        if (current == null || target == null) {
            return false;
        }

        UUID playerUuid = player.getUUID();
        Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(playerUuid);
        String instanceId = mount.getInstanceId();
        if (owned == null || !owned.containsKey(instanceId)) {
            return false;
        }

        DatabaseManager.UnlockedMountData uData = owned.get(instanceId);
        int bonding = uData.bondingScore;
        if (bonding < current.evolution.requiredBonding) {
            return false;
        }

        // Check required item catalysts
        boolean hasItems = true;
        for (Map.Entry<String, Integer> req : current.evolution.requiredItems.entrySet()) {
            ResourceLocation itemId = new ResourceLocation(req.getKey());
            Item item = BuiltInRegistries.ITEM.get(itemId);
            int requiredCount = req.getValue();
            int playerHas = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() == item) {
                    playerHas += stack.getCount();
                }
            }
            if (playerHas < requiredCount) {
                hasItems = false;
                break;
            }
        }

        if (!hasItems) {
            player.sendSystemMessage(Component.literal("You do not have the required items to evolve this mount."));
            return false;
        }

        // Deduct items
        for (Map.Entry<String, Integer> req : current.evolution.requiredItems.entrySet()) {
            ResourceLocation itemId = new ResourceLocation(req.getKey());
            Item item = BuiltInRegistries.ITEM.get(itemId);
            int remainingToDeduct = req.getValue();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() == item) {
                    int count = stack.getCount();
                    if (count >= remainingToDeduct) {
                        stack.shrink(remainingToDeduct);
                        break;
                    } else {
                        remainingToDeduct -= count;
                        stack.setCount(0);
                    }
                }
            }
        }

        // Calculate inherited level
        int targetLevel = 1;
        double targetXp = 0.0;
        double dmgDealt = 0.0;
        double dmgTaken = 0.0;
        int hpZero = 0;
        double distTravelled = 0.0;

        if (ModConfig.get().evolution.enable_evolution_heritage) {
            String policy = ModConfig.get().evolution.evolution_level_policy.toUpperCase();
            if (policy.equals("RETAIN")) {
                targetLevel = uData.level;
                targetXp = uData.xp;
            } else if (policy.equals("DEGRADE")) {
                targetLevel = Math.max(1, (int) (uData.level * (1.0 - ModConfig.get().evolution.evolution_degrade_percentage)));
                targetXp = 0.0;
            }
            dmgDealt = uData.damageDealt;
            dmgTaken = uData.damageTaken;
            hpZero = uData.hpZeroCount;
            distTravelled = uData.distanceTravelled;
        }

        // Keep same instanceId (preserving gear/inventory automatically since it is keyed by instance ID in sqlite)
        String ancestryLog = uData.ancestryLog;
        try {
            com.google.gson.JsonArray arr;
            if (ancestryLog == null || ancestryLog.isEmpty() || ancestryLog.equals("[]")) {
                arr = new com.google.gson.JsonArray();
            } else {
                arr = new com.google.gson.JsonParser().parse(ancestryLog).getAsJsonArray();
            }
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("action", "EVOLVED");
            obj.addProperty("fromId", mountId);
            obj.addProperty("toId", targetTemplateId);
            obj.addProperty("level", uData.level);
            arr.add(obj);
            ancestryLog = new com.google.gson.Gson().toJson(arr);
        } catch (Exception e) {
            // Ignore
        }

        DatabaseManager.saveUnlockedMountDataAsync(
                playerUuid,
                uData.instanceId,
                targetTemplateId,
                0, // Evolved starts at 0 bonding, taming gets increase
                targetLevel,
                targetXp,
                dmgDealt,
                dmgTaken,
                hpZero,
                distTravelled,
                uData.isChroma,
                ancestryLog,
                uData.customName
        );

        // Save Bestiary discoveries permanently for both parent and child
        DatabaseManager.saveBestiaryDiscoveryAsync(playerUuid, mountId);
        DatabaseManager.saveBestiaryDiscoveryAsync(playerUuid, targetTemplateId);

        // Discard active mount entity
        mount.discard();

        player.sendSystemMessage(Component.literal("Your mount evolved into " + target.name + "!"));
        ModPackets.syncUnlockedMounts(player);
        ModPackets.syncBestiaryDiscoveries(player);

        return true;
    }

    @Override
    public List<EvolutionPathInfo> getEvolutionPaths(RPGMountEntity mount) {
        List<EvolutionPathInfo> paths = new ArrayList<>();
        MountData current = MountRegistry.getTemplate(mount.getTemplateId());
        if (current != null && current.evolution != null && !current.evolution.targetId.isEmpty()) {
            MountData target = MountRegistry.getTemplate(current.evolution.targetId);
            String name = (target != null) ? target.name : current.evolution.targetId;

            // Check if current satisfies linear requirement
            boolean isUnlocked = true;
            List<String> missing = new ArrayList<>();

            UUID playerUuid = mount.getOwnerUuid();
            if (playerUuid != null) {
                Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(playerUuid);
                if (owned != null && owned.containsKey(mount.getInstanceId())) {
                    DatabaseManager.UnlockedMountData uData = owned.get(mount.getInstanceId());
                    if (uData.bondingScore < current.evolution.requiredBonding) {
                        isUnlocked = false;
                        missing.add("bonding:" + current.evolution.requiredBonding);
                    }
                }
            }

            paths.add(new EvolutionPathInfo(current.evolution.targetId, name, isUnlocked, missing));
        }
        return paths;
    }
}
