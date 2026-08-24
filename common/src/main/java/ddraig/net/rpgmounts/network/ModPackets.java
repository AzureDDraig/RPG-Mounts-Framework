package ddraig.net.rpgmounts.network;

import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.config.ModConfig;
import ddraig.net.rpgmounts.config.AnimationMappingConfig;
import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.registry.ModEntities;
import ddraig.net.rpgmounts.item.MountEnhancerItem;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.UUID;

/**
 * RPG Mounts Network Packets class
 * Registers client-to-server and server-to-client packet streams and serializations.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented summon, dismiss, action, sync, whistle, and evolve packets using Architectury NetworkManager.
 * - 2026-06-19: [Fix Compile Errors] - Cast player level to ServerLevel for accessing getAllEntities.
 * - 2026-06-19: [Admin & GUI packets] - Added S2C packets for opening creator/config screens and syncing admin mode.
 * - 2026-06-19: [Enhancer Creator Packet] - Added C2S packet to request enhancer creation.
 */
public class ModPackets {
    public static final ResourceLocation C2S_SUMMON = new ResourceLocation(RPGMounts.MOD_ID, "summon");
    public static final ResourceLocation C2S_DISMISS = new ResourceLocation(RPGMounts.MOD_ID, "dismiss");
    public static final ResourceLocation C2S_ACTION = new ResourceLocation(RPGMounts.MOD_ID, "action");
    public static final ResourceLocation C2S_WHISTLE = new ResourceLocation(RPGMounts.MOD_ID, "whistle");
    public static final ResourceLocation C2S_EVOLVE = new ResourceLocation(RPGMounts.MOD_ID, "evolve");
    public static final ResourceLocation C2S_TOGGLE_PASSIVE = new ResourceLocation(RPGMounts.MOD_ID, "toggle_passive");
    
    public static final ResourceLocation S2C_SYNC_UNLOCKED = new ResourceLocation(RPGMounts.MOD_ID, "sync_unlocked");
    public static final ResourceLocation S2C_SYNC_TEMPLATES = new ResourceLocation(RPGMounts.MOD_ID, "sync_templates");
    public static final ResourceLocation S2C_OPEN_CREATOR = new ResourceLocation(RPGMounts.MOD_ID, "open_creator");
    public static final ResourceLocation S2C_OPEN_CONFIG = new ResourceLocation(RPGMounts.MOD_ID, "open_config");
    public static final ResourceLocation S2C_OPEN_ENHANCER_CREATOR = new ResourceLocation(RPGMounts.MOD_ID, "open_enhancer_creator");
    public static final ResourceLocation S2C_OPEN_ABILITY_CREATOR = new ResourceLocation(RPGMounts.MOD_ID, "open_ability_creator");
    public static final ResourceLocation S2C_SYNC_ADMIN_MODE = new ResourceLocation(RPGMounts.MOD_ID, "sync_admin_mode");
    public static final ResourceLocation S2C_OPEN_BESTIARY = new ResourceLocation(RPGMounts.MOD_ID, "open_bestiary");
    public static final ResourceLocation S2C_SYNC_ABILITIES = new ResourceLocation(RPGMounts.MOD_ID, "sync_abilities");
    public static final ResourceLocation S2C_SYNC_BESTIARY = new ResourceLocation(RPGMounts.MOD_ID, "sync_bestiary");
    public static final ResourceLocation S2C_PLAY_SOUND = new ResourceLocation(RPGMounts.MOD_ID, "play_sound");

    public static final ResourceLocation C2S_SAVE_TEMPLATE = new ResourceLocation(RPGMounts.MOD_ID, "save_template");
    public static final ResourceLocation C2S_DELETE_TEMPLATE = new ResourceLocation(RPGMounts.MOD_ID, "delete_template");
    public static final ResourceLocation C2S_SAVE_ANIMATION_MAPPINGS = new ResourceLocation(RPGMounts.MOD_ID, "save_animation_mappings");

    public static final ResourceLocation C2S_GEAR_CLICK = new ResourceLocation(RPGMounts.MOD_ID, "gear_click");
    public static final ResourceLocation S2C_SYNC_GEAR = new ResourceLocation(RPGMounts.MOD_ID, "sync_gear");
    public static final ResourceLocation S2C_OPEN_GEAR = new ResourceLocation(RPGMounts.MOD_ID, "open_gear");
    public static final ResourceLocation C2S_SWITCH_ABILITY = new ResourceLocation(RPGMounts.MOD_ID, "switch_ability");
    public static final ResourceLocation C2S_REQUEST_OPEN_GEAR = new ResourceLocation(RPGMounts.MOD_ID, "request_open_gear");
    public static final ResourceLocation C2S_CREATE_ENHANCER = new ResourceLocation(RPGMounts.MOD_ID, "create_enhancer");
    public static final ResourceLocation C2S_CREATE_ABILITY = new ResourceLocation(RPGMounts.MOD_ID, "create_ability");
    public static final ResourceLocation C2S_REQUEST_AUDITS = new ResourceLocation(RPGMounts.MOD_ID, "request_audits");
    public static final ResourceLocation S2C_SYNC_AUDITS = new ResourceLocation(RPGMounts.MOD_ID, "sync_audits");
    public static final ResourceLocation C2S_SAVE_CONFIG = new ResourceLocation(RPGMounts.MOD_ID, "save_config");
    public static final ResourceLocation S2C_SYNC_CONFIG = new ResourceLocation(RPGMounts.MOD_ID, "sync_config");

    public static void init() {
        // C2S Summon Packet
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SUMMON, (buf, context) -> {
            String instanceId = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(player.getUUID());
                DatabaseManager.UnlockedMountData uData = null;
                if (owned != null) {
                    uData = owned.get(instanceId);
                }

                boolean isOp = player.hasPermissions(2);
                if (uData == null && !isOp) {
                    player.sendSystemMessage(Component.literal("§cYou do not own this mount instance."));
                    return;
                }

                String templateId = uData != null ? uData.mountId : instanceId;
                MountData data = MountRegistry.getTemplate(templateId);
                if (data != null) {
                    if (!canSummonAt(player, data)) return;
                    dismissExistingMounts(player);
                    RPGMountEntity mount = new RPGMountEntity(ModEntities.RPG_MOUNT.get(), player.level());
                    mount.setTemplateId(templateId);
                    mount.setOwnerUuid(player.getUUID());
                    if (uData != null) {
                        mount.setInstanceId(instanceId);
                        mount.setBonding(uData.bondingScore);
                        mount.setLevel(uData.level);
                        mount.setXp((float) uData.xp);
                        if (uData.customName != null && !uData.customName.isEmpty()) {
                            mount.setCustomName(Component.literal(uData.customName));
                        }
                    } else {
                        // Admin summoning by template ID directly
                        mount.setInstanceId(instanceId);
                    }
                    mount.setPos(player.getX(), player.getY(), player.getZ());
                    player.level().addFreshEntity(mount);
                    player.startRiding(mount);

                    if (player.level() instanceof ServerLevel serverLevel) {
                        // Play spawn sound
                        String soundId = (data.spawnEffects != null && data.spawnEffects.sound != null && !data.spawnEffects.sound.isEmpty())
                                ? data.spawnEffects.sound : (data.sounds != null && data.sounds.ambient != null ? data.sounds.ambient : "");
                        if (!soundId.isEmpty()) {
                            try {
                                net.minecraft.resources.ResourceLocation sLoc = new net.minecraft.resources.ResourceLocation(soundId);
                                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                                        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(sLoc),
                                        net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                            } catch (Exception ignored) {}
                        }

                        // Play spawn particles
                        String partId = (data.spawnEffects != null && data.spawnEffects.particle != null && !data.spawnEffects.particle.isEmpty())
                                ? data.spawnEffects.particle : (data.groundParticle != null && !data.groundParticle.isEmpty() ? data.groundParticle : "minecraft:poof");
                        try {
                            net.minecraft.core.particles.ParticleOptions part = (net.minecraft.core.particles.ParticleOptions) 
                                    net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(new net.minecraft.resources.ResourceLocation(partId));
                            if (part != null) {
                                serverLevel.sendParticles(part, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });
        });

        // C2S Dismiss Packet
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_DISMISS, (buf, context) -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> dismissExistingMounts(player));
        });

        // C2S Whistle Packet
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_WHISTLE, (buf, context) -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                boolean found = false;
                if (player.server != null) {
                    for (ServerLevel level : player.server.getAllLevels()) {
                        for (Entity entity : level.getAllEntities()) {
                            if (entity instanceof RPGMountEntity mount && player.getUUID().equals(mount.getOwnerUuid())) {
                                found = true;
                                if (mount.level() != player.level()) {
                                    mount.changeDimension(player.serverLevel());
                                }
                                if (mount.distanceToSqr(player) > 1024) {
                                    mount.teleportTo(player.getX(), player.getY(), player.getZ());
                                } else {
                                    mount.getNavigation().moveTo(player, 1.25);
                                }
                            }
                        }
                    }
                }
                if (found) {
                    player.sendSystemMessage(Component.translatable("message.rpg_mounts.whistle.called"), true);
                } else {
                    player.sendSystemMessage(Component.translatable("message.rpg_mounts.whistle.not_found"), true);
                }
            });
        });

        // C2S Action Packet
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_ACTION, (buf, context) -> {
            int actionId = buf.readInt();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.getVehicle() instanceof RPGMountEntity mount) {
                    switch (actionId) {
                        case 3: // ABILITY 1
                            mount.triggerAbility(1);
                            break;
                        case 4: // ABILITY 2
                            mount.triggerAbility(2);
                            break;
                        case 5: // FLY UP ON
                            mount.inputFlyUp = true;
                            break;
                        case 6: // FLY UP OFF
                            mount.inputFlyUp = false;
                            break;
                        case 7: // FLY DOWN ON
                            mount.inputFlyDown = true;
                            break;
                        case 8: // FLY DOWN OFF
                            mount.inputFlyDown = false;
                            break;
                        case 9: // SPRINT ON
                            mount.inputSprint = true;
                            break;
                        case 10: // SPRINT OFF
                            mount.inputSprint = false;
                            break;
                    }
                }
            });
        });

        // C2S Evolve Packet
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_EVOLVE, (buf, context) -> {
            String instanceId = buf.readUtf();
            String targetId = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(player.getUUID());
                if (owned != null && owned.containsKey(instanceId)) {
                    DatabaseManager.UnlockedMountData uData = owned.get(instanceId);
                    MountData current = MountRegistry.getTemplate(uData.mountId);
                    MountData target = MountRegistry.getTemplate(targetId);
                    if (current != null && target != null) {
                        int bonding = uData.bondingScore;
                        if (bonding >= current.evolution.requiredBonding) {
                            // Check items
                            boolean hasItems = true;
                            for (Map.Entry<String, Integer> req : current.evolution.requiredItems.entrySet()) {
                                ResourceLocation itemId = new ResourceLocation(req.getKey());
                                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
                                int requiredCount = req.getValue();
                                int playerHas = 0;
                                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                                    net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
                                    if (stack.getItem() == item) {
                                        playerHas += stack.getCount();
                                    }
                                }
                                if (playerHas < requiredCount) {
                                    hasItems = false;
                                    break;
                                }
                            }
                            if (hasItems) {
                                // Deduct items
                                for (Map.Entry<String, Integer> req : current.evolution.requiredItems.entrySet()) {
                                    ResourceLocation itemId = new ResourceLocation(req.getKey());
                                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
                                    int remainingToDeduct = req.getValue();
                                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                                        net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
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
                                // Deduct items and Evolve (update existing instanceId)
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
                                    obj.addProperty("fromId", uData.mountId);
                                    obj.addProperty("toId", targetId);
                                    obj.addProperty("level", uData.level);
                                    arr.add(obj);
                                    ancestryLog = new com.google.gson.Gson().toJson(arr);
                                } catch (Exception e) {
                                    // Ignore
                                }
                                
                                DatabaseManager.saveUnlockedMountDataAsync(
                                        player.getUUID(),
                                        instanceId,
                                        targetId,
                                        0, // Evolved starts at 0 bonding
                                        uData.level, // Inherited level
                                        uData.xp,
                                        uData.damageDealt,
                                        uData.damageTaken,
                                        uData.hpZeroCount,
                                        uData.distanceTravelled,
                                        uData.isChroma,
                                        ancestryLog,
                                        uData.customName
                                );
                                player.sendSystemMessage(Component.literal("Your mount evolved into " + target.name + "!"));
                                syncUnlockedMounts(player);
                            } else {
                                player.sendSystemMessage(Component.literal("You do not have the required items to evolve this mount."));
                            }
                        }
                    }
                }
            });
        });

        // C2S Save Template Packet
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SAVE_TEMPLATE, (buf, context) -> {
            String json = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    MountData data = gson.fromJson(json, MountData.class);
                    if (data != null && !data.id.isEmpty()) {
                        java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
                        
                        // If custom model, copy the files from suggestion folder to template folder only if needed,
                        // but DO NOT overwrite modelId to data.id, to allow using shared model sub-folders.
                        if (data.modelType != null && !data.modelType.equalsIgnoreCase("vanilla") && data.modelId != null && !data.modelId.isEmpty() && !data.modelId.equals(data.id)) {
                            java.io.File srcFolder = new java.io.File(baseDir, "Mounts/Unpacked/" + data.modelId.toLowerCase(java.util.Locale.ROOT));
                            java.io.File destFolder = new java.io.File(baseDir, "Mounts/Unpacked/" + data.id.toLowerCase(java.util.Locale.ROOT));
                            if (srcFolder.exists() && srcFolder.isDirectory()) {
                                if (!destFolder.exists() || destFolder.list() == null || destFolder.list().length == 0) {
                                    copyModelFiles(srcFolder, destFolder);
                                }
                            }
                        }

                        // Save to config directory
                        java.io.File folder = new java.io.File(baseDir, "Mounts/Unpacked/" + data.id.toLowerCase(java.util.Locale.ROOT));
                        if (!folder.exists()) {
                            folder.mkdirs();
                        }
                        java.io.File file = new java.io.File(folder, "mount.json");
                        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
                            String adminName = player.getName().getString();
                            ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "SAVE_MOUNT_TEMPLATE " + data.id, data.name);
                            player.sendSystemMessage(Component.literal("Saved and hot-reloaded mount template: " + data.name));
                        } catch (java.io.IOException e) {
                            RPGMounts.LOGGER.error("Failed to save template " + data.id, e);
                        }

                        // Add to loaded mounts config
                        if (!ddraig.net.rpgmounts.config.ModConfig.get().general.loaded_mounts.contains(data.id)) {
                            ddraig.net.rpgmounts.config.ModConfig.get().general.loaded_mounts.add(data.id);
                            ddraig.net.rpgmounts.config.ModConfig.get().save();
                        }
                        
                        // Reload templates
                        MountRegistry.reloadTemplates();
                        syncTemplatesToAll(player.server);
                        
                        // Update active spawned mount entities in-world
                        if (player.server != null) {
                            for (ServerLevel level : player.server.getAllLevels()) {
                                for (Entity entity : level.getAllEntities()) {
                                    if (entity instanceof RPGMountEntity mount && mount.getTemplateId().equals(data.id)) {
                                        mount.recalculateStats();
                                        mount.setHealth((float) mount.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH));
                                    }
                                }
                            }
                        }
                    }
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SAVE_ANIMATION_MAPPINGS, (buf, context) -> {
            String templateId = buf.readUtf();
            String json = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    try {
                        ddraig.net.rpgmounts.config.AnimationMappingConfig.AnimationNames mapping = 
                            new com.google.gson.Gson().fromJson(json, ddraig.net.rpgmounts.config.AnimationMappingConfig.AnimationNames.class);
                        if (mapping != null && templateId != null && !templateId.isEmpty()) {
                            ddraig.net.rpgmounts.config.AnimationMappingConfig.get().mappings.put(templateId, mapping);
                            ddraig.net.rpgmounts.config.AnimationMappingConfig.save();
                            syncConfigToAll(player.server);
                            player.sendSystemMessage(Component.literal("§aSaved animation mappings for mount '" + templateId + "'."));
                        }
                    } catch (Exception e) {
                        RPGMounts.LOGGER.error("Failed to save animation mappings for " + templateId, e);
                    }
                }
            });
        });

        // C2S Delete Template Packet
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_DELETE_TEMPLATE, (buf, context) -> {
            String id = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
                    java.io.File folder = new java.io.File(baseDir, "Mounts/Unpacked/" + id);
                    if (folder.exists()) {
                        deleteDirRecursively(folder);
                        String adminName = player.getName().getString();
                        ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "DELETE_MOUNT_TEMPLATE " + id, id);
                        player.sendSystemMessage(Component.literal("Deleted mount template: " + id));
                    }
                    
                    // Remove from loaded mounts config
                    if (ddraig.net.rpgmounts.config.ModConfig.get().general.loaded_mounts.remove(id)) {
                        ddraig.net.rpgmounts.config.ModConfig.get().save();
                    }
                    
                    MountRegistry.reloadTemplates();
                    syncTemplatesToAll(player.server);
                }
            });
        });

        // C2S Gear Click Receiver
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_GEAR_CLICK, (buf, context) -> {
            int entityId = buf.readInt();
            int slot = buf.readInt();
            int button = buf.readInt(); // 0 = left click, 1 = right click, 2 = shift click
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                Entity ent = player.level().getEntity(entityId);
                if (ent instanceof RPGMountEntity mount) {
                    if (player.getUUID().equals(mount.getOwnerUuid()) || player.hasPermissions(2)) {
                        // Slot bounds checks
                        int totalSlots = 7; // Saddle + Armor + Cargo + 4 Enhancers
                        MountData data = MountRegistry.getTemplate(mount.getTemplateId());
                        int cargoCap = 0;
                        if (data != null) {
                            net.minecraft.world.item.ItemStack cargoStack = mount.getInventory().getItem(2);
                            if (!cargoStack.isEmpty()) {
                                String cargoId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cargoStack.getItem()).toString();
                                cargoCap = data.allowed_cargo_map.getOrDefault(cargoId, 0);
                            }
                        }
                        int maxSlots = totalSlots + cargoCap;

                        if (slot >= 0 && slot < maxSlots) {
                            net.minecraft.world.item.ItemStack cursorItem = player.containerMenu.getCarried();
                            net.minecraft.world.item.ItemStack slotItem = mount.getInventory().getItem(slot);

                            if (button == 2) { // Shift Click: quick move to player inventory
                                if (!slotItem.isEmpty()) {
                                    if (player.getInventory().add(slotItem)) {
                                        mount.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                                    }
                                }
                            } else { // Normal swap
                                // Slot validations
                                if (slot == 0 && !cursorItem.isEmpty() && !(cursorItem.getItem() instanceof net.minecraft.world.item.SaddleItem)) {
                                    return;
                                }
                                if (slot == 1 && !cursorItem.isEmpty() && !(cursorItem.getItem() instanceof net.minecraft.world.item.HorseArmorItem)) {
                                    return;
                                }
                                if (slot == 2 && !cursorItem.isEmpty()) {
                                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cursorItem.getItem()).toString();
                                    if (data == null || !data.allowed_cargo_map.containsKey(id)) {
                                        return;
                                    }
                                }
                                if (slot >= 3 && slot <= 6 && !cursorItem.isEmpty() && !(cursorItem.getItem() instanceof MountEnhancerItem)) {
                                    return;
                                }

                                if (cursorItem.isEmpty()) {
                                    player.containerMenu.setCarried(slotItem);
                                    mount.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                                } else {
                                    if (slotItem.isEmpty()) {
                                        mount.getInventory().setItem(slot, cursorItem.copy());
                                        player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                                    } else {
                                        mount.getInventory().setItem(slot, cursorItem.copy());
                                        player.containerMenu.setCarried(slotItem);
                                    }
                                }
                            }
                            syncGear(player, mount);
                        } else if (slot >= 1000 && slot < 1036) {
                            int playerSlot = slot - 1000;
                            net.minecraft.world.item.ItemStack slotItem = player.getInventory().getItem(playerSlot);

                            if (button == 2) { // Shift Click
                                if (!slotItem.isEmpty()) {
                                    boolean inserted = false;
                                    if (slotItem.getItem() instanceof net.minecraft.world.item.SaddleItem) {
                                        if (mount.getInventory().getItem(0).isEmpty()) {
                                            mount.getInventory().setItem(0, slotItem.copy());
                                            player.getInventory().setItem(playerSlot, net.minecraft.world.item.ItemStack.EMPTY);
                                            inserted = true;
                                        }
                                    } else if (slotItem.getItem() instanceof net.minecraft.world.item.HorseArmorItem) {
                                        if (mount.getInventory().getItem(1).isEmpty()) {
                                            mount.getInventory().setItem(1, slotItem.copy());
                                            player.getInventory().setItem(playerSlot, net.minecraft.world.item.ItemStack.EMPTY);
                                            inserted = true;
                                        }
                                    } else if (slotItem.getItem() instanceof MountEnhancerItem) {
                                        for (int i = 3; i <= 6; i++) {
                                            if (mount.getInventory().getItem(i).isEmpty()) {
                                                mount.getInventory().setItem(i, slotItem.copy());
                                                player.getInventory().setItem(playerSlot, net.minecraft.world.item.ItemStack.EMPTY);
                                                inserted = true;
                                                break;
                                            }
                                        }
                                    } else if (data != null && data.allowed_cargo_map.containsKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slotItem.getItem()).toString())) {
                                        if (mount.getInventory().getItem(2).isEmpty()) {
                                            mount.getInventory().setItem(2, slotItem.copy());
                                            player.getInventory().setItem(playerSlot, net.minecraft.world.item.ItemStack.EMPTY);
                                            inserted = true;
                                        }
                                    }

                                    if (!inserted) {
                                        // Try cargo slots
                                        net.minecraft.world.item.ItemStack cargoStack = mount.getInventory().getItem(2);
                                        if (!cargoStack.isEmpty()) {
                                            String cargoId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cargoStack.getItem()).toString();
                                            int cargoCapLimit = data.allowed_cargo_map.getOrDefault(cargoId, 0);
                                            for (int i = 7; i < 7 + cargoCapLimit; i++) {
                                                if (mount.getInventory().getItem(i).isEmpty()) {
                                                    mount.getInventory().setItem(i, slotItem.copy());
                                                    player.getInventory().setItem(playerSlot, net.minecraft.world.item.ItemStack.EMPTY);
                                                    inserted = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    if (inserted) {
                                        syncGear(player, mount);
                                    }
                                }
                            } else {
                                net.minecraft.world.item.ItemStack cursorItem = player.containerMenu.getCarried();
                                if (cursorItem.isEmpty()) {
                                    player.containerMenu.setCarried(slotItem);
                                    player.getInventory().setItem(playerSlot, net.minecraft.world.item.ItemStack.EMPTY);
                                } else {
                                    if (slotItem.isEmpty()) {
                                        player.getInventory().setItem(playerSlot, cursorItem.copy());
                                        player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                                    } else {
                                        player.getInventory().setItem(playerSlot, cursorItem.copy());
                                        player.containerMenu.setCarried(slotItem);
                                    }
                                }
                            }
                        }
                    }
                }
            });
        });

        // C2S Switch Ability Receiver
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SWITCH_ABILITY, (buf, context) -> {
            int entityId = buf.readInt();
            int slot = buf.readInt(); // 1 or 2
            int abilityIndex = buf.readInt(); // Index in availableAbilities
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                Entity ent = player.level().getEntity(entityId);
                if (ent instanceof RPGMountEntity mount) {
                    if (player.getUUID().equals(mount.getOwnerUuid()) || player.hasPermissions(2)) {
                        if (slot == 1) {
                            mount.setAbility1Index(abilityIndex);
                        } else {
                            mount.setAbility2Index(abilityIndex);
                        }
                    }
                }
            });
        });

        // C2S Toggle Passive Receiver
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_TOGGLE_PASSIVE, (buf, context) -> {
            int entityId = buf.readInt();
            String passiveName = buf.readUtf();
            boolean enable = buf.readBoolean();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                Entity ent = player.level().getEntity(entityId);
                if (ent instanceof RPGMountEntity mount) {
                    if (player.getUUID().equals(mount.getOwnerUuid()) || player.hasPermissions(2)) {
                        mount.togglePassive(passiveName, enable);
                    }
                }
            });
        });

        // C2S Request Open Gear Receiver
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_REQUEST_OPEN_GEAR, (buf, context) -> {
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.getVehicle() instanceof RPGMountEntity mount) {
                    syncGear(player, mount);
                    FriendlyByteBuf openBuf = new FriendlyByteBuf(Unpooled.buffer());
                    openBuf.writeInt(mount.getId());
                    NetworkManager.sendToPlayer(player, S2C_OPEN_GEAR, openBuf);
                }
            });
        });

        // C2S Create Enhancer Receiver
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_CREATE_ENHANCER, (buf, context) -> {
            String category = buf.readUtf();
            String type = buf.readUtf();
            double value = buf.readDouble();
            String abilityName = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    net.minecraft.world.item.Item itemType = ddraig.net.rpgmounts.registry.ModItems.DEFENSE_ENHANCER.get();
                    if (category.equalsIgnoreCase("movement")) itemType = ddraig.net.rpgmounts.registry.ModItems.MOVEMENT_ENHANCER.get();
                    else if (category.equalsIgnoreCase("damage")) itemType = ddraig.net.rpgmounts.registry.ModItems.DAMAGE_ENHANCER.get();
                    else if (category.equalsIgnoreCase("ability")) itemType = ddraig.net.rpgmounts.registry.ModItems.ABILITY_ENHANCER.get();

                    net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(itemType);
                    net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
                    tag.putString("EnhancerCategory", category);
                    tag.putString("EnhancerType", type);
                    tag.putDouble("EnhancerValue", value);
                    if (type.equalsIgnoreCase("grant_ability") && !abilityName.isEmpty()) {
                        tag.putString("EnhancerAbility", abilityName);
                    }
                    if (player.getInventory().add(stack)) {
                        String detail = type.equalsIgnoreCase("grant_ability") ? abilityName : String.valueOf(value);
                        String adminName = player.getName().getString();
                        ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "CREATE_ENHANCER " + category + " / " + type + " (" + detail + ")", category);
                        player.sendSystemMessage(Component.literal("§aCreated enhancer: " + category + " / " + type + " (" + detail + ")"));
                    } else {
                        player.sendSystemMessage(Component.literal("§cInventory full!"));
                    }
                }
            });
        });

        // C2S Create Ability Receiver
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_CREATE_ABILITY, (buf, context) -> {
            String json = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    MountData.AbilityData ability = gson.fromJson(json, MountData.AbilityData.class);
                    if (ability != null && !ability.name.isEmpty()) {
                        MountRegistry.saveCustomAbility(ability);
                        String adminName = player.getName().getString();
                        ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "SAVE_ABILITY " + ability.name, ability.name);
                        player.sendSystemMessage(Component.literal("§aSaved custom ability: " + ability.name));
                        MountRegistry.reloadAbilities();
                        syncAbilitiesToAll(player.server);
                    }
                }
            });
        });

        // C2S Request Audits Receiver
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_REQUEST_AUDITS, (buf, context) -> {
            int page = buf.readInt();
            int pageSize = buf.readInt();
            String queryFilter = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    java.util.List<String> logs = ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.getAuditLogs(page, pageSize, queryFilter);
                    int totalCount = ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.getAuditLogsCount(queryFilter);
                    
                    FriendlyByteBuf replyBuf = new FriendlyByteBuf(Unpooled.buffer());
                    replyBuf.writeInt(totalCount);
                    replyBuf.writeInt(logs.size());
                    for (String log : logs) {
                        replyBuf.writeUtf(log);
                    }
                    NetworkManager.sendToPlayer(player, S2C_SYNC_AUDITS, replyBuf);
                }
            });
        });

        // C2S Save Config Receiver
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SAVE_CONFIG, (buf, context) -> {
            String json = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    ddraig.net.rpgmounts.config.ModConfig config = gson.fromJson(json, ddraig.net.rpgmounts.config.ModConfig.class);
                    if (config != null) {
                        ddraig.net.rpgmounts.config.ModConfig.setInstance(config);
                        ddraig.net.rpgmounts.config.ModConfig.get().save();
                        String adminName = player.getName().getString();
                        ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "SAVE_CONFIG", "server_config");
                        player.sendSystemMessage(Component.literal("§aServer configuration saved and updated."));
                        syncConfigToAll(player.server);
                    }
                }
            });
        });
    }

    private static void deleteDirRecursively(java.io.File file) {
        java.io.File[] contents = file.listFiles();
        if (contents != null) {
            for (java.io.File f : contents) {
                deleteDirRecursively(f);
            }
        }
        file.delete();
    }

    public static boolean canSummonAt(ServerPlayer player, MountData template) {
        // 0. Ownership check
        if (!player.hasPermissions(2)) {
            Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(player.getUUID());
            boolean ownsTemplate = false;
            if (owned != null) {
                for (DatabaseManager.UnlockedMountData uData : owned.values()) {
                    if (template.id.equals(uData.mountId)) {
                        ownsTemplate = true;
                        break;
                    }
                }
            }
            if (!ownsTemplate) {
                player.sendSystemMessage(Component.literal("§cYou have not unlocked this mount template."));
                return false;
            }
        }

        // 1. Dimension checks
        String dimId = player.level().dimension().location().toString();
        if (ModConfig.get().general.useWhitelist) {
            if (!ModConfig.get().general.dimensionWhitelist.contains(dimId)) {
                player.sendSystemMessage(Component.literal("§cSummoning is disabled in this dimension."));
                return false;
            }
        } else {
            if (ModConfig.get().general.dimensionBlacklist.contains(dimId)) {
                player.sendSystemMessage(Component.literal("§cSummoning is blacklisted in this dimension."));
                return false;
            }
        }

        // 2. Liquid checks
        BlockPos pos = player.blockPosition();
        net.minecraft.world.level.material.FluidState fluid = player.level().getFluidState(pos);
        boolean inWater = fluid.is(net.minecraft.tags.FluidTags.WATER);
        boolean inLava = fluid.is(net.minecraft.tags.FluidTags.LAVA);
        boolean submerged = player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);

        boolean isAquatic = template.category.equalsIgnoreCase("AQUATIC");
        boolean isSurfaceWater = template.category.equalsIgnoreCase("SURFACE_WATER");
        boolean canSummonInWater = isAquatic || isSurfaceWater;

        if (isAquatic && !inWater && !inLava) {
            player.sendSystemMessage(Component.literal("§cCannot summon aquatic mounts out of water or lava."));
            return false;
        }

        if (inWater) {
            if (!canSummonInWater && !ModConfig.get().general.allowSummoningInWater) {
                player.sendSystemMessage(Component.literal("§cCannot summon non-aquatic mounts in water."));
                return false;
            }
            if (submerged && !canSummonInWater && !ModConfig.get().general.allowSummoningSubmerged) {
                player.sendSystemMessage(Component.literal("§cCannot summon mounts while submerged."));
                return false;
            }
        }
        if (inLava) {
            if (!ModConfig.get().general.allowSummoningInLava) {
                player.sendSystemMessage(Component.literal("§cCannot summon mounts in lava."));
                return false;
            }
        }

        return true;
    }

    private static void dismissExistingMounts(ServerPlayer player) {
        if (player.server != null) {
            for (ServerLevel level : player.server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof RPGMountEntity mount) {
                        if (player.getUUID().equals(mount.getOwnerUuid())) {
                            mount.discard();
                        }
                    }
                }
            }
        }
    }

    public static void syncUnlockedMounts(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(player.getUUID());
        if (owned == null) {
            buf.writeInt(0);
        } else {
            buf.writeInt(owned.size());
            for (Map.Entry<String, DatabaseManager.UnlockedMountData> entry : owned.entrySet()) {
                DatabaseManager.UnlockedMountData data = entry.getValue();
                buf.writeUtf(entry.getKey()); // instanceId
                buf.writeUtf(data.mountId);   // mountId
                buf.writeUtf(data.customName == null ? "" : data.customName); // customName
                buf.writeInt(data.bondingScore);
                buf.writeInt(data.level);
                buf.writeFloat((float) data.xp);
                buf.writeUtf("HEALTHY");
                buf.writeInt(0);
                buf.writeBoolean(data.isChroma);
                buf.writeDouble(data.damageDealt);
                buf.writeDouble(data.damageTaken);
                buf.writeInt(data.hpZeroCount);
                buf.writeDouble(data.distanceTravelled);
                buf.writeUtf(data.ancestryLog == null ? "[]" : data.ancestryLog);
            }
        }
        NetworkManager.sendToPlayer(player, S2C_SYNC_UNLOCKED, buf);
        syncBestiaryDiscoveries(player);
    }

    public static void syncBestiaryDiscoveries(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        java.util.List<String> list = DatabaseManager.bestiaryCache.get(player.getUUID());
        if (list == null) {
            buf.writeInt(0);
        } else {
            buf.writeInt(list.size());
            for (String mountId : list) {
                buf.writeUtf(mountId);
            }
        }
        NetworkManager.sendToPlayer(player, S2C_SYNC_BESTIARY, buf);
    }

    public static void syncTemplates(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(MountRegistry.loadedTemplates.size());
        com.google.gson.Gson gson = new com.google.gson.Gson();
        for (MountData data : MountRegistry.loadedTemplates.values()) {
            buf.writeUtf(gson.toJson(data));
        }
        NetworkManager.sendToPlayer(player, S2C_SYNC_TEMPLATES, buf);
    }

    public static void syncTemplatesToAll(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncTemplates(player);
            }
        }
    }

    public static void syncGear(ServerPlayer player, RPGMountEntity mount) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(mount.getId());
        CompoundTag nbt = new CompoundTag();
        nbt.put("Items", RPGMountEntity.saveContainerToTag(mount.getInventory()));
        buf.writeNbt(nbt);
        NetworkManager.sendToPlayer(player, S2C_SYNC_GEAR, buf);
    }

    public static void syncAbilities(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(MountRegistry.customAbilities.size());
        com.google.gson.Gson gson = new com.google.gson.Gson();
        for (MountData.AbilityData ab : MountRegistry.customAbilities.values()) {
            buf.writeUtf(gson.toJson(ab));
        }
        NetworkManager.sendToPlayer(player, S2C_SYNC_ABILITIES, buf);
    }

    public static void syncAbilitiesToAll(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncAbilities(player);
            }
        }
    }

    public static void syncConfig(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        String json = new com.google.gson.Gson().toJson(ddraig.net.rpgmounts.config.ModConfig.get());
        buf.writeUtf(json);
        String animJson = new com.google.gson.Gson().toJson(ddraig.net.rpgmounts.config.AnimationMappingConfig.get());
        buf.writeUtf(animJson);
        NetworkManager.sendToPlayer(player, S2C_SYNC_CONFIG, buf);
    }

    public static void syncConfigToAll(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncConfig(player);
            }
        }
    }

    private static void copyModelFiles(java.io.File srcDir, java.io.File destDir) {
        if (srcDir == null || !srcDir.exists() || !srcDir.isDirectory()) return;
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        java.io.File[] files = srcDir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isFile() && !f.getName().equals("mount.json")) {
                    java.io.File target = new java.io.File(destDir, f.getName());
                    try {
                        java.nio.file.Files.copy(f.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.io.IOException e) {
                        ddraig.net.rpgmounts.RPGMounts.LOGGER.error("Failed to copy file: " + f.getName(), e);
                    }
                }
            }
        }
    }

    public static void sendPlaySoundPacketToTrackers(Entity entity, String soundName, float volume, float pitch) {
        if (entity.level().isClientSide) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(soundName);
        buf.writeDouble(entity.getX());
        buf.writeDouble(entity.getY());
        buf.writeDouble(entity.getZ());
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
        
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(entity, NetworkManager.toPacket(NetworkManager.s2c(), S2C_PLAY_SOUND, buf));
        }
    }
}
