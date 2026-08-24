package ddraig.net.rpgmounts.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.config.ModConfig;
import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.registry.ModEntities;
import ddraig.net.rpgmounts.network.ModPackets;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RPG Mounts Commands Registry class
 * Handles commands node registration using Brigadier, with autocompletions and permission checks.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented Brigadier command tree for /rpg_mounts summon/dismiss/whistle/evolve/admin commands.
 * - 2026-06-19: [Implement Admin Commands] - Added admin-mode, config-editor, creator-ui, load-mount, unload-mount, create-mount, edit-mount, delete-mount commands.
 * - 2026-06-19: [Autocompletion] - Added custom suggesters for loaded/unloaded templates, properties, and values.
 * - 2026-06-19: [Enhancer Command & Load/Unload Fixes] - Implemented create-enhancer command, updated load-mount and unload-mount to persist to server_config.json and broadcast templates.
 * - 2026-06-19: [Enhancer Creator GUI Command] - Added enhancer-creator admin command to open visual UI.
 */
public class MountCommands {
    public static final Map<UUID, Boolean> adminModePlayers = new java.util.concurrent.ConcurrentHashMap<>();

    public static SuggestionProvider<CommandSourceStack> OWNED_MOUNTS_SUGGESTER;
    public static SuggestionProvider<CommandSourceStack> TARGET_OWNED_MOUNTS_SUGGESTER;
    public static SuggestionProvider<CommandSourceStack> LOADED_TEMPLATES_SUGGESTER;
    public static SuggestionProvider<CommandSourceStack> UNLOADED_TEMPLATES_SUGGESTER;
    public static SuggestionProvider<CommandSourceStack> EDITABLE_PROPERTIES_SUGGESTER;
    public static SuggestionProvider<CommandSourceStack> PROPERTY_VALUES_SUGGESTER;
    public static SuggestionProvider<CommandSourceStack> PACKED_TEMPLATES_SUGGESTER;
    public static SuggestionProvider<CommandSourceStack> ANIMATION_SUGGESTER;

    @SuppressWarnings("unchecked")
    public static void init() {
        OWNED_MOUNTS_SUGGESTER = (SuggestionProvider<CommandSourceStack>) (SuggestionProvider<?>) SuggestionProviders.register(
                new ResourceLocation(RPGMounts.MOD_ID, "owned_mounts"),
                (context, builder) -> {
                    String remaining = builder.getRemaining();
                    java.util.Set<DatabaseManager.UnlockedMountData> allOwned = new java.util.HashSet<>();
                    for (Map<String, DatabaseManager.UnlockedMountData> map : DatabaseManager.unlockedMountsCache.values()) {
                        allOwned.addAll(map.values());
                    }
                    if (!allOwned.isEmpty()) {
                        suggestOwnedMounts(remaining, builder, allOwned);
                    } else {
                        suggestTemplates(remaining, builder, MountRegistry.loadedTemplates.values());
                    }
                    return builder.buildFuture();
                }
        );

        TARGET_OWNED_MOUNTS_SUGGESTER = (SuggestionProvider<CommandSourceStack>) (SuggestionProvider<?>) SuggestionProviders.register(
                new ResourceLocation(RPGMounts.MOD_ID, "target_owned_mounts"),
                (context, builder) -> {
                    String remaining = builder.getRemaining();
                    java.util.Set<DatabaseManager.UnlockedMountData> allOwned = new java.util.HashSet<>();
                    for (Map<String, DatabaseManager.UnlockedMountData> map : DatabaseManager.unlockedMountsCache.values()) {
                        allOwned.addAll(map.values());
                    }
                    if (!allOwned.isEmpty()) {
                        suggestOwnedMounts(remaining, builder, allOwned);
                    } else {
                        suggestTemplates(remaining, builder, MountRegistry.loadedTemplates.values());
                    }
                    return builder.buildFuture();
                }
        );

        LOADED_TEMPLATES_SUGGESTER = (SuggestionProvider<CommandSourceStack>) (SuggestionProvider<?>) SuggestionProviders.register(
                new ResourceLocation(RPGMounts.MOD_ID, "loaded_templates"),
                (context, builder) -> {
                    suggestTemplates(builder.getRemaining(), builder, MountRegistry.loadedTemplates.values());
                    return builder.buildFuture();
                }
        );

        ANIMATION_SUGGESTER = (SuggestionProvider<CommandSourceStack>) (SuggestionProvider<?>) SuggestionProviders.register(
                new ResourceLocation(RPGMounts.MOD_ID, "mount_animations"),
                (context, builder) -> {
                    String remaining = builder.getRemaining();
                    try {
                        String templateId = StringArgumentType.getString(context, "template_id");
                        java.util.List<String> anims = MountRegistry.getAnimationSuggestions(templateId, remaining);
                        for (String anim : anims) {
                            builder.suggest(anim);
                        }
                    } catch (Exception e) {
                        for (MountData data : MountRegistry.loadedTemplates.values()) {
                            java.util.List<String> anims = MountRegistry.getAnimationSuggestions(data.id, remaining);
                            for (String anim : anims) {
                                builder.suggest(anim);
                            }
                        }
                    }
                    return builder.buildFuture();
                }
        );

        UNLOADED_TEMPLATES_SUGGESTER = (SuggestionProvider<CommandSourceStack>) (SuggestionProvider<?>) SuggestionProviders.register(
                new ResourceLocation(RPGMounts.MOD_ID, "unloaded_templates"),
                (context, builder) -> {
                    java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
                    java.io.File mountsFolder = new java.io.File(baseDir, "Mounts/Unpacked");
                    if (mountsFolder.exists()) {
                        java.io.File[] files = mountsFolder.listFiles();
                        if (files != null) {
                            for (java.io.File file : files) {
                                if (file.isDirectory() && !MountRegistry.loadedTemplates.containsKey(file.getName())) {
                                    builder.suggest(file.getName());
                                }
                            }
                        }
                    }
                    return builder.buildFuture();
                }
        );

        EDITABLE_PROPERTIES_SUGGESTER = (SuggestionProvider<CommandSourceStack>) (SuggestionProvider<?>) SuggestionProviders.register(
                new ResourceLocation(RPGMounts.MOD_ID, "editable_properties"),
                (context, builder) -> {
                    List.of("name", "description", "category", "modelType", "modelId", "scale", "maxHealth", "movementSpeed", "jumpHeight", "flySpeed")
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                }
        );

        PROPERTY_VALUES_SUGGESTER = (SuggestionProvider<CommandSourceStack>) (SuggestionProvider<?>) SuggestionProviders.register(
                new ResourceLocation(RPGMounts.MOD_ID, "property_values"),
                (context, builder) -> {
                    try {
                        String property = StringArgumentType.getString(context, "property");
                        if (property.equalsIgnoreCase("category")) {
                            List.of("GROUND", "AQUATIC", "FLYING").forEach(builder::suggest);
                        } else if (property.equalsIgnoreCase("modelType")) {
                            List.of("vanilla", "geckolib", "modelengine").forEach(builder::suggest);
                        } else if (property.equalsIgnoreCase("modelId")) {
                            List.of("minecraft:horse", "minecraft:wolf", "minecraft:ender_dragon", "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:spider", "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper").forEach(builder::suggest);
                        }
                    } catch (IllegalArgumentException e) {
                        // Ignore
                    }
                    return builder.buildFuture();
                }
        );

        PACKED_TEMPLATES_SUGGESTER = (SuggestionProvider<CommandSourceStack>) (SuggestionProvider<?>) SuggestionProviders.register(
                new ResourceLocation(RPGMounts.MOD_ID, "packed_templates"),
                (context, builder) -> {
                    java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
                    java.io.File packsFolder = new java.io.File(baseDir, "Mounts/Packs");
                    if (packsFolder.exists()) {
                        java.io.File[] files = packsFolder.listFiles();
                        if (files != null) {
                            for (java.io.File file : files) {
                                if (file.isFile() && file.getName().endsWith(".zip")) {
                                    String name = file.getName().substring(0, file.getName().length() - 4);
                                    builder.suggest(name);
                                }
                            }
                        }
                    }
                    return builder.buildFuture();
                }
        );

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            register(dispatcher);
        });
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rpg_mounts")
                // /rpg_mounts summon <mount_id>
                .then(Commands.literal("summon")
                        .then(Commands.argument("mount_id", StringArgumentType.word())
                                .suggests(OWNED_MOUNTS_SUGGESTER)
                                .executes(context -> {
                                    String mountId = StringArgumentType.getString(context, "mount_id");
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return summonMount(context.getSource(), player, mountId);
                                })
                        )
                )
                // /rpg_mounts dismiss
                .then(Commands.literal("dismiss")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            return dismissMount(context.getSource(), player);
                        })
                )
                // /rpg_mounts whistle
                .then(Commands.literal("whistle")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            return whistleRecall(context.getSource(), player);
                        })
                )
                // /rpg_mounts status
                .then(Commands.literal("status")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            return showStatus(context.getSource(), player);
                        })
                )
                // Admin node
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        // /rpg_mounts admin admin-mode
                        .then(Commands.literal("admin-mode")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean active = !adminModePlayers.getOrDefault(player.getUUID(), false);
                                    adminModePlayers.put(player.getUUID(), active);
                                    
                                    net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                                    buf.writeBoolean(active);
                                    dev.architectury.networking.NetworkManager.sendToPlayer(player, ddraig.net.rpgmounts.network.ModPackets.S2C_SYNC_ADMIN_MODE, buf);
                                    
                                    context.getSource().sendSuccess(() -> Component.literal("Admin mode " + (active ? "enabled" : "disabled") + "."), true);
                                    return 1;
                                })
                        )
                        // /rpg_mounts admin config-editor
                        .then(Commands.literal("config-editor")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    dev.architectury.networking.NetworkManager.sendToPlayer(player, ddraig.net.rpgmounts.network.ModPackets.S2C_OPEN_CONFIG, new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()));
                                    return 1;
                                })
                        )
                        // /rpg_mounts admin creator-ui
                        .then(Commands.literal("creator-ui")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    dev.architectury.networking.NetworkManager.sendToPlayer(player, ddraig.net.rpgmounts.network.ModPackets.S2C_OPEN_CREATOR, new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()));
                                    return 1;
                                })
                        )
                        // /rpg_mounts admin enhancer-creator
                        .then(Commands.literal("enhancer-creator")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    dev.architectury.networking.NetworkManager.sendToPlayer(player, ddraig.net.rpgmounts.network.ModPackets.S2C_OPEN_ENHANCER_CREATOR, new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()));
                                    return 1;
                                })
                        )
                        // /rpg_mounts admin ability-creator
                        .then(Commands.literal("ability-creator")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    dev.architectury.networking.NetworkManager.sendToPlayer(player, ddraig.net.rpgmounts.network.ModPackets.S2C_OPEN_ABILITY_CREATOR, new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()));
                                    return 1;
                                })
                        )
                        // /rpg_mounts admin add-mount <player> <mount_id>
                        .then(Commands.literal("add-mount")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("mount_id", StringArgumentType.string())
                                                .suggests(LOADED_TEMPLATES_SUGGESTER)
                                                .executes(context -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                    String inputId = StringArgumentType.getString(context, "mount_id");
                                                    String resolvedId = resolveTemplateId(inputId);
                                                    
                                                    if (MountRegistry.getTemplate(resolvedId) == null) {
                                                        context.getSource().sendFailure(Component.literal("§cMount template '" + inputId + "' not found."));
                                                        return 0;
                                                    }
                                                    
                                                    if (ddraig.net.rpgmounts.config.ModConfig.get().general.prevent_duplicate_mounts) {
                                                        Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(target.getUUID());
                                                        if (owned != null && owned.values().stream().anyMatch(d -> d.mountId.equalsIgnoreCase(resolvedId))) {
                                                            context.getSource().sendFailure(Component.literal("§cPlayer " + target.getName().getString() + " already owns a mount of type '" + resolvedId + "'."));
                                                            return 0;
                                                        }
                                                    }
                                                    
                                                    String instanceId = UUID.randomUUID().toString();
                                                    DatabaseManager.saveUnlockedMountAsync(target.getUUID(), instanceId, resolvedId, 0);
                                                    ModPackets.syncUnlockedMounts(target);
                                                    String adminName = context.getSource().getTextName();
                                                    ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "ADD_MOUNT " + resolvedId + " (Instance: " + instanceId + ") to player " + target.getName().getString(), resolvedId);
                                                    context.getSource().sendSuccess(() -> Component.literal("Added mount " + resolvedId + " (Instance: " + instanceId + ") to player " + target.getName().getString()), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        // /rpg_mounts admin remove-mount <player> <instance_id>
                        .then(Commands.literal("remove-mount")
                                .then(Commands.argument("player", net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
                                        .then(Commands.argument("instance_id", StringArgumentType.string())
                                                .suggests(TARGET_OWNED_MOUNTS_SUGGESTER)
                                                .executes(context -> {
                                                    java.util.Collection<com.mojang.authlib.GameProfile> profiles = 
                                                            net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(context, "player");
                                                    if (profiles.isEmpty()) {
                                                        context.getSource().sendFailure(Component.literal("§cPlayer profile not found."));
                                                        return 0;
                                                    }
                                                    com.mojang.authlib.GameProfile targetProfile = profiles.iterator().next();
                                                    UUID targetUuid = targetProfile.getId();
                                                    String targetName = targetProfile.getName();

                                                    String rawInstanceId = StringArgumentType.getString(context, "instance_id");
                                                    if (rawInstanceId.startsWith("\"") && rawInstanceId.endsWith("\"") && rawInstanceId.length() > 1) {
                                                        rawInstanceId = rawInstanceId.substring(1, rawInstanceId.length() - 1);
                                                    }

                                                    final String queryStr = rawInstanceId;

                                                    // Despawn active mount entity in world if target player is currently online
                                                    ServerPlayer onlinePlayer = context.getSource().getServer().getPlayerList().getPlayer(targetUuid);
                                                    if (onlinePlayer != null && onlinePlayer.server != null) {
                                                        for (ServerLevel level : onlinePlayer.server.getAllLevels()) {
                                                            for (Entity entity : level.getAllEntities()) {
                                                                if (entity instanceof RPGMountEntity mount) {
                                                                    if (targetUuid.equals(mount.getOwnerUuid())) {
                                                                        String mInst = mount.getInstanceId();
                                                                        String mId = mount.getTemplateId();
                                                                        if ((mInst != null && queryStr.toLowerCase().contains(mInst.toLowerCase())) ||
                                                                            (mId != null && queryStr.toLowerCase().contains(mId.toLowerCase())) ||
                                                                            queryStr.equalsIgnoreCase(mInst) || queryStr.equalsIgnoreCase(mId)) {
                                                                            mount.ejectPassengers();
                                                                            mount.discard();
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Clear active mount record if it matches
                                                    DatabaseManager.ActiveMountData active = DatabaseManager.activeMountsCache.get(targetUuid);
                                                    if (active != null && active.activeMountUuid != null) {
                                                        if (queryStr.toLowerCase().contains(active.activeMountUuid.toLowerCase()) ||
                                                            queryStr.equalsIgnoreCase(active.activeMountUuid)) {
                                                            DatabaseManager.saveActiveMountAsync(targetUuid, null);
                                                        }
                                                    }

                                                    DatabaseManager.removeMatchingUnlockedMountsAsync(targetUuid, queryStr).thenAccept(removedIds -> {
                                                        if (onlinePlayer != null) {
                                                            ModPackets.syncUnlockedMounts(onlinePlayer);
                                                        }
                                                        int count = removedIds.size();
                                                        String adminName = context.getSource().getTextName();
                                                        ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(
                                                            adminName, "REMOVE_MOUNT " + queryStr + " (Removed " + count + " instances) from player " + targetName, queryStr);
                                                        
                                                        context.getSource().sendSuccess(() -> Component.literal("§aRemoved " + count + " mount instance(s) matching '" + queryStr + "' from player " + targetName + "."), true);
                                                    });

                                                    return 1;
                                                })
                                        )
                                )
                        )
                        // /rpg_mounts admin view-mounts <player>
                        .then(Commands.literal("view-mounts")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(target.getUUID());
                                            if (owned == null || owned.isEmpty()) {
                                                context.getSource().sendSuccess(() -> Component.literal("Player " + target.getName().getString() + " does not own any mounts."), false);
                                                return 1;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("=== Owned Mounts for " + target.getName().getString() + " ==="), false);
                                            for (DatabaseManager.UnlockedMountData data : owned.values()) {
                                                String name = (data.customName != null && !data.customName.isEmpty()) ? data.customName : data.mountId;
                                                context.getSource().sendSuccess(() -> Component.literal("- " + name + " (ID: " + data.mountId + ") [Instance: " + data.instanceId + "] Lvl: " + data.level + ", Bond: " + data.bondingScore + "%"), false);
                                            }
                                            return 1;
                                        })
                                )
                        )
                        // /rpg_mounts admin scale-mount <mount_id> <scale>
                        .then(Commands.literal("scale-mount")
                                .then(Commands.argument("mount_id", StringArgumentType.word())
                                        .suggests(LOADED_TEMPLATES_SUGGESTER)
                                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1f, 10.0f))
                                                .executes(context -> {
                                                    String mountId = StringArgumentType.getString(context, "mount_id");
                                                    float scale = FloatArgumentType.getFloat(context, "scale");
                                                    MountData data = MountRegistry.getTemplate(mountId);
                                                    if (data != null) {
                                                        data.scale = scale;
                                                        String adminName = context.getSource().getTextName();
                                                        ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "SCALE_MOUNT " + mountId + " to " + scale, mountId);
                                                        context.getSource().sendSuccess(() -> Component.literal("Scaled mount " + mountId + " to " + scale), true);
                                                        return 1;
                                                    }
                                                    context.getSource().sendFailure(Component.literal("Mount " + mountId + " not found."));
                                                    return 0;
                                                })
                                        )
                                )
                        )
                        // /rpg_mounts admin create-enhancer <category> <type> <value>
                        // /rpg_mounts admin create-enhancer <category> <type> <value>
                        .then(Commands.literal("create-enhancer")
                                .then(Commands.literal("ability")
                                        .then(Commands.literal("grant_ability")
                                                .then(Commands.argument("ability_name", StringArgumentType.string())
                                                        .suggests((ctx, builder) -> {
                                                            MountRegistry.customAbilities.keySet().forEach(builder::suggest);
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                                            String abName = StringArgumentType.getString(context, "ability_name");
                                                            
                                                            net.minecraft.world.item.Item itemType = ddraig.net.rpgmounts.registry.ModItems.ABILITY_ENHANCER.get();
                                                            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(itemType);
                                                            net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
                                                            tag.putString("EnhancerCategory", "ability");
                                                            tag.putString("EnhancerType", "grant_ability");
                                                            tag.putDouble("EnhancerValue", 1.0);
                                                            tag.putString("EnhancerAbility", abName);
                                                            
                                                            if (player.getInventory().add(stack)) {
                                                                String adminName = context.getSource().getTextName();
                                                                ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "CREATE_ENHANCER ability / grant_ability (" + abName + ")", "ability");
                                                                context.getSource().sendSuccess(() -> Component.literal("Created enhancer: ability / grant_ability (" + abName + ")"), true);
                                                                return 1;
                                                            } else {
                                                                context.getSource().sendFailure(Component.literal("Inventory full!"));
                                                                return 0;
                                                            }
                                                        })
                                                )
                                        )
                                )
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            List.of("defense", "movement", "damage", "ability").forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    try {
                                                        String category = StringArgumentType.getString(ctx, "category");
                                                        if (category.equalsIgnoreCase("defense")) {
                                                            List.of("max_health", "armor", "flat_damage_reduction").forEach(builder::suggest);
                                                        } else if (category.equalsIgnoreCase("movement")) {
                                                            List.of("speed", "swim_speed", "fly_speed", "jump_height", "jump_strength").forEach(builder::suggest);
                                                        } else if (category.equalsIgnoreCase("damage")) {
                                                            List.of("damage_boost", "strength", "attack_speed").forEach(builder::suggest);
                                                        } else if (category.equalsIgnoreCase("ability")) {
                                                            List.of("cooldown_reduction", "stamina_cost_reduction", "grant_ability").forEach(builder::suggest);
                                                        }
                                                    } catch (Exception e) {
                                                        List.of("max_health", "speed", "damage_boost", "cooldown_reduction").forEach(builder::suggest);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                                            String category = StringArgumentType.getString(context, "category");
                                                            String type = StringArgumentType.getString(context, "type");
                                                            double value = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "value");
                                                            
                                                            net.minecraft.world.item.Item itemType = ddraig.net.rpgmounts.registry.ModItems.DEFENSE_ENHANCER.get();
                                                            if (category.equalsIgnoreCase("movement")) itemType = ddraig.net.rpgmounts.registry.ModItems.MOVEMENT_ENHANCER.get();
                                                            else if (category.equalsIgnoreCase("damage")) itemType = ddraig.net.rpgmounts.registry.ModItems.DAMAGE_ENHANCER.get();
                                                            else if (category.equalsIgnoreCase("ability")) itemType = ddraig.net.rpgmounts.registry.ModItems.ABILITY_ENHANCER.get();

                                                            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(itemType);
                                                            net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
                                                            tag.putString("EnhancerCategory", category);
                                                            tag.putString("EnhancerType", type);
                                                            tag.putDouble("EnhancerValue", value);
                                                            
                                                            if (player.getInventory().add(stack)) {
                                                                String adminName = context.getSource().getTextName();
                                                                ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "CREATE_ENHANCER " + category + " / " + type + " (" + value + ")", category);
                                                                context.getSource().sendSuccess(() -> Component.literal("Created enhancer: " + category + " / " + type + " (" + value + ")"), true);
                                                                return 1;
                                                            } else {
                                                                context.getSource().sendFailure(Component.literal("Inventory full!"));
                                                                return 0;
                                                            }
                                                        })
                                                )
                                        )
                                )
                        )
                        // /rpg_mounts admin load-mount <mount_id>
                        .then(Commands.literal("load-mount")
                                .then(Commands.argument("mount_id", StringArgumentType.word())
                                        .suggests(UNLOADED_TEMPLATES_SUGGESTER)
                                        .executes(context -> {
                                            String mountId = StringArgumentType.getString(context, "mount_id");
                                            
                                            // Perform dynamic load from file
                                            java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
                                            java.io.File file = new java.io.File(baseDir, "Mounts/Unpacked/" + mountId.toLowerCase(java.util.Locale.ROOT) + "/mount.json");
                                            if (file.exists()) {
                                                try (java.io.FileReader reader = new java.io.FileReader(file)) {
                                                    MountData data = new com.google.gson.Gson().fromJson(reader, MountData.class);
                                                    if (data != null) {
                                                        MountRegistry.loadedTemplates.put(data.id, data);
                                                        if (!ModConfig.get().general.loaded_mounts.contains(data.id)) {
                                                            ModConfig.get().general.loaded_mounts.add(data.id);
                                                            ModConfig.get().save();
                                                        }
                                                        ddraig.net.rpgmounts.network.ModPackets.syncTemplatesToAll(context.getSource().getServer());
                                                        String adminName = context.getSource().getTextName();
                                                        ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "LOAD_MOUNT " + mountId, mountId);
                                                        context.getSource().sendSuccess(() -> Component.literal("Successfully loaded and registered template: " + mountId), true);
                                                        return 1;
                                                    }
                                                } catch (Exception e) {
                                                    context.getSource().sendFailure(Component.literal("Error parsing config file: " + e.getMessage()));
                                                    return 0;
                                                }
                                            }
                                            context.getSource().sendFailure(Component.literal("Config file not found in Mounts/Unpacked/" + mountId.toLowerCase(java.util.Locale.ROOT) + "/mount.json"));
                                            return 0;
                                        })
                                 )
                        )
                        // /rpg_mounts admin unload-mount <mount_id>
                        .then(Commands.literal("unload-mount")
                                .then(Commands.argument("mount_id", StringArgumentType.string())
                                        .suggests(LOADED_TEMPLATES_SUGGESTER)
                                        .executes(context -> {
                                            String inputId = StringArgumentType.getString(context, "mount_id");
                                            String mountId = resolveTemplateId(inputId);
                                            MountRegistry.loadedTemplates.remove(mountId);
                                            if (ModConfig.get().general.loaded_mounts.contains(mountId)) {
                                                ModConfig.get().general.loaded_mounts.remove(mountId);
                                                ModConfig.get().save();
                                            }
                                            ddraig.net.rpgmounts.network.ModPackets.syncTemplatesToAll(context.getSource().getServer());
                                            String adminName = context.getSource().getTextName();
                                            ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "UNLOAD_MOUNT " + mountId, mountId);
                                            context.getSource().sendSuccess(() -> Component.literal("Unloaded mount template: " + mountId), true);
                                            return 1;
                                        })
                                )
                        )
                        // /rpg_mounts admin create-mount <mount_id>
                        .then(Commands.literal("create-mount")
                                .then(Commands.argument("mount_id", StringArgumentType.word())
                                        .executes(context -> {
                                            String mountId = StringArgumentType.getString(context, "mount_id");
                                            MountData data = new MountData();
                                            data.id = mountId;
                                            data.name = mountId.substring(0, 1).toUpperCase() + mountId.substring(1);
                                            data.category = "GROUND";
                                            data.modelType = "vanilla";
                                            data.modelId = "minecraft:wolf";
                                            data.scale = 1.0f;
                                            data.stats.maxHealth = 20.0;
                                            data.stats.movementSpeed = 0.25;
                                            
                                            // Write to directory
                                            java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
                                            java.io.File folder = new java.io.File(baseDir, "Mounts/Unpacked/" + mountId.toLowerCase(java.util.Locale.ROOT));
                                            if (!folder.exists()) folder.mkdirs();
                                            java.io.File file = new java.io.File(folder, "mount.json");
                                            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                                                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
                                                if (!ddraig.net.rpgmounts.config.ModConfig.get().general.loaded_mounts.contains(mountId)) {
                                                    ddraig.net.rpgmounts.config.ModConfig.get().general.loaded_mounts.add(mountId);
                                                    ddraig.net.rpgmounts.config.ModConfig.get().save();
                                                }
                                                MountRegistry.loadedTemplates.put(mountId, data);
                                                String adminName = context.getSource().getTextName();
                                                ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "CREATE_MOUNT " + mountId, mountId);
                                                context.getSource().sendSuccess(() -> Component.literal("Created and registered new template: " + mountId), true);
                                                return 1;
                                            } catch (Exception e) {
                                                context.getSource().sendFailure(Component.literal("Failed to save template file: " + e.getMessage()));
                                                return 0;
                                            }
                                        })
                                )
                        )
                        // /rpg_mounts admin edit-mount <mount_id> <property> <value>
                        .then(Commands.literal("edit-mount")
                                .then(Commands.argument("mount_id", StringArgumentType.string())
                                        .suggests(LOADED_TEMPLATES_SUGGESTER)
                                        .then(Commands.argument("property", StringArgumentType.word())
                                                .suggests(EDITABLE_PROPERTIES_SUGGESTER)
                                                .then(Commands.argument("value", StringArgumentType.word())
                                                        .suggests(PROPERTY_VALUES_SUGGESTER)
                                                        .executes(context -> {
                                                            String inputId = StringArgumentType.getString(context, "mount_id");
                                                            String mountId = resolveTemplateId(inputId);
                                                            String property = StringArgumentType.getString(context, "property");
                                                            String value = StringArgumentType.getString(context, "value");
                                                            
                                                            MountData data = MountRegistry.getTemplate(mountId);
                                                            if (data != null) {
                                                                try {
                                                                    if (property.equalsIgnoreCase("name")) {
                                                                        data.name = value.replace("_", " ");
                                                                    } else if (property.equalsIgnoreCase("description")) {
                                                                        data.description = value.replace("_", " ");
                                                                    } else if (property.equalsIgnoreCase("category")) {
                                                                        data.category = value.toUpperCase();
                                                                    } else if (property.equalsIgnoreCase("modelType")) {
                                                                        data.modelType = value.toLowerCase();
                                                                    } else if (property.equalsIgnoreCase("modelId")) {
                                                                        data.modelId = value;
                                                                    } else if (property.equalsIgnoreCase("scale")) {
                                                                        data.scale = Float.parseFloat(value);
                                                                    } else if (property.equalsIgnoreCase("maxHealth")) {
                                                                        data.stats.maxHealth = Double.parseDouble(value);
                                                                    } else if (property.equalsIgnoreCase("movementSpeed")) {
                                                                        data.stats.movementSpeed = Double.parseDouble(value);
                                                                    } else if (property.equalsIgnoreCase("jumpHeight")) {
                                                                        data.stats.jumpHeight = Double.parseDouble(value);
                                                                    } else if (property.equalsIgnoreCase("flySpeed")) {
                                                                        data.stats.flySpeed = Double.parseDouble(value);
                                                                    } else {
                                                                        context.getSource().sendFailure(Component.literal("Unknown property: " + property));
                                                                        return 0;
                                                                    }
                                                                    
                                                                    // Save to file
                                                                    java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
                                                                    java.io.File file = new java.io.File(baseDir, "Mounts/Unpacked/" + mountId.toLowerCase(java.util.Locale.ROOT) + "/mount.json");
                                                                    try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                                                                        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data, writer);
                                                                        String adminName = context.getSource().getTextName();
                                                                        ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "EDIT_MOUNT " + mountId + " " + property + " -> " + value, mountId);
                                                                        context.getSource().sendSuccess(() -> Component.literal("Edited mount " + mountId + " property " + property + " -> " + value), true);
                                                                        return 1;
                                                                    }
                                                                } catch (Exception e) {
                                                                    context.getSource().sendFailure(Component.literal("Invalid value for property: " + e.getMessage()));
                                                                    return 0;
                                                                }
                                                            }
                                                            context.getSource().sendFailure(Component.literal("Mount " + mountId + " not found."));
                                                            return 0;
                                                        })
                                                )
                                        )
                                )
                        )
                        // /rpg_mounts admin delete-mount <mount_id>
                        .then(Commands.literal("delete-mount")
                                .then(Commands.argument("mount_id", StringArgumentType.string())
                                        .suggests(LOADED_TEMPLATES_SUGGESTER)
                                        .executes(context -> {
                                            String inputId = StringArgumentType.getString(context, "mount_id");
                                            String mountId = resolveTemplateId(inputId);
                                            
                                            java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
                                            java.io.File folder = new java.io.File(baseDir, "Mounts/Unpacked/" + mountId.toLowerCase(java.util.Locale.ROOT));
                                            if (folder.exists()) {
                                                deleteDirRecursively(folder);
                                            }
                                            MountRegistry.loadedTemplates.remove(mountId);
                                            String adminName = context.getSource().getTextName();
                                            ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "DELETE_MOUNT " + mountId, mountId);
                                            context.getSource().sendSuccess(() -> Component.literal("Deleted mount template: " + mountId), true);
                                            return 1;
                                        })
                                )
                        )
                        // /rpg_mounts admin pack-mount <mount_id>
                        .then(Commands.literal("pack-mount")
                                .then(Commands.argument("mount_id", StringArgumentType.string())
                                        .suggests(LOADED_TEMPLATES_SUGGESTER)
                                        .executes(context -> {
                                            String inputId = StringArgumentType.getString(context, "mount_id");
                                            String mountId = resolveTemplateId(inputId);
                                            if (MountRegistry.packTemplate(mountId)) {
                                                String adminName = context.getSource().getTextName();
                                                ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "PACK_MOUNT " + mountId, mountId);
                                                context.getSource().sendSuccess(() -> Component.literal("Successfully packed mount " + mountId + " to Packs/" + mountId + ".zip"), true);
                                                return 1;
                                            } else {
                                                context.getSource().sendFailure(Component.literal("Failed to pack mount template: " + mountId));
                                                return 0;
                                            }
                                        })
                                )
                        )
                        // /rpg_mounts admin load-packed-mount <mount_name>
                        .then(Commands.literal("load-packed-mount")
                                .then(Commands.argument("mount_name", StringArgumentType.word())
                                        .suggests(PACKED_TEMPLATES_SUGGESTER)
                                        .executes(context -> {
                                            String mountName = StringArgumentType.getString(context, "mount_name");
                                            if (MountRegistry.unpackTemplate(mountName)) {
                                                MountRegistry.reloadTemplates();
                                                ddraig.net.rpgmounts.network.ModPackets.syncTemplatesToAll(context.getSource().getServer());
                                                String adminName = context.getSource().getTextName();
                                                ddraig.net.rpgmounts.integration.RPGWaypointsServerIntegration.logAudit(adminName, "LOAD_PACKED_MOUNT " + mountName, mountName);
                                                context.getSource().sendSuccess(() -> Component.literal("Successfully unpacked and loaded mount pack: " + mountName), true);
                                                return 1;
                                            } else {
                                                context.getSource().sendFailure(Component.literal("Failed to unpack and load mount pack: " + mountName));
                                                return 0;
                                            }
                                        })
                                )
                        )
                )
        );
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

    private static int summonMount(CommandSourceStack source, ServerPlayer player, String instanceId) {
        Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(player.getUUID());
        DatabaseManager.UnlockedMountData uData = null;
        if (owned != null) {
            uData = owned.get(instanceId);
        }

        boolean isOp = player.hasPermissions(2);
        if (uData == null && !isOp) {
            source.sendFailure(Component.literal("You do not own this mount instance."));
            return 0;
        }

        String templateId = uData != null ? uData.mountId : instanceId;
        MountData data = MountRegistry.getTemplate(templateId);
        if (data == null) {
            source.sendFailure(Component.literal("Mount template " + templateId + " does not exist."));
            return 0;
        }

        if (!ddraig.net.rpgmounts.network.ModPackets.canSummonAt(player, data)) {
            return 0;
        }

        // Check active / injured status
        DatabaseManager.ActiveMountData active = DatabaseManager.activeMountsCache.get(player.getUUID());
        if (active != null && active.status.equalsIgnoreCase("INJURED")) {
            source.sendFailure(Component.literal("Your mount is currently injured and recovering."));
            return 0;
        }

        // Dismiss existing first
        dismissExistingMounts(player);

        RPGMountEntity mount = new RPGMountEntity(ModEntities.RPG_MOUNT.get(), player.level());
        mount.setTemplateId(templateId);
        mount.setOwnerUuid(player.getUUID());
        mount.setPos(player.getX(), player.getY(), player.getZ());
        mount.setYRot(player.getYRot());

        if (uData != null) {
            mount.setInstanceId(instanceId);
            mount.setBonding(uData.bondingScore);
            mount.setLevel(uData.level);
            mount.setXp((float) uData.xp);
            if (uData.customName != null && !uData.customName.isEmpty()) {
                mount.setCustomName(Component.literal(uData.customName));
            }
        } else {
            // Admin summoning template directly
            mount.setInstanceId(instanceId);
        }

        player.level().addFreshEntity(mount);
        player.startRiding(mount);

        String displayName = (uData != null && uData.customName != null && !uData.customName.isEmpty()) ? uData.customName : data.name;
        source.sendSuccess(() -> Component.literal("Summoned " + displayName + "!"), true);
        return 1;
    }

    private static int dismissMount(CommandSourceStack source, ServerPlayer player) {
        if (dismissExistingMounts(player)) {
            source.sendSuccess(() -> Component.literal("Dismissed active mount."), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("You do not have an active summoned mount."));
            return 0;
        }
    }

    private static boolean dismissExistingMounts(ServerPlayer player) {
        boolean dismissed = false;
        if (player.server != null) {
            for (ServerLevel level : player.server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof RPGMountEntity mount) {
                        if (player.getUUID().equals(mount.getOwnerUuid())) {
                            mount.discard();
                            dismissed = true;
                        }
                    }
                }
            }
        }
        return dismissed;
    }

    private static int whistleRecall(CommandSourceStack source, ServerPlayer player) {
        boolean found = false;
        if (player.server != null) {
            for (ServerLevel level : player.server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof RPGMountEntity mount) {
                        if (player.getUUID().equals(mount.getOwnerUuid())) {
                            if (mount.level() != player.level()) {
                                mount.changeDimension(player.serverLevel());
                            }
                            if (mount.distanceToSqr(player) > 1024) {
                                mount.teleportTo(player.getX(), player.getY(), player.getZ());
                            } else {
                                mount.getNavigation().moveTo(player, 1.25);
                            }
                            found = true;
                        }
                    }
                }
            }
        }
        if (found) {
            source.sendSuccess(() -> Component.translatable("message.rpg_mounts.whistle.called"), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("message.rpg_mounts.whistle.not_found"));
            return 0;
        }
    }

    private static int showStatus(CommandSourceStack source, ServerPlayer player) {
        Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(player.getUUID());
        if (owned == null || owned.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You have no unlocked mounts."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Unlocked Mounts:"), false);
        for (Map.Entry<String, DatabaseManager.UnlockedMountData> entry : owned.entrySet()) {
            MountData data = MountRegistry.getTemplate(entry.getValue().mountId);
            String name = (data != null) ? data.name : entry.getValue().mountId;
            String displayName = (entry.getValue().customName != null && !entry.getValue().customName.isEmpty())
                    ? entry.getValue().customName + " (" + name + ")"
                    : name;
            source.sendSuccess(() -> Component.literal(" - " + displayName + " [ID: " + entry.getKey() + "] (Level: " + entry.getValue().level + ", XP: " + String.format("%.1f", entry.getValue().xp) + ", Bonding: " + entry.getValue().bondingScore + "/100)"), false);
        }
        return 1;
    }

    private static String resolveTemplateId(String input) {
        if (input == null) return null;

        // Handle search completion format like: "typed (template_id)" or "typed -> template_id"
        if (input.contains(" (") && input.endsWith(")")) {
            int openParen = input.lastIndexOf(" (");
            String extracted = input.substring(openParen + 2, input.length() - 1);
            if (MountRegistry.loadedTemplates.containsKey(extracted)) {
                return extracted;
            }
            input = extracted;
        }

        if (MountRegistry.loadedTemplates.containsKey(input)) {
            return input;
        }
        for (MountData data : MountRegistry.loadedTemplates.values()) {
            if (data.name != null && data.name.equalsIgnoreCase(input)) {
                return data.id;
            }
        }
        return input;
    }

    private static final java.util.regex.Pattern UUID_PATTERN = 
        java.util.regex.Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        
    private static String extractInstanceId(String input) {
        if (input == null) return null;
        java.util.regex.Matcher matcher = UUID_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return input;
    }

    private static boolean matchesInitials(String typed, String name) {
        if (typed == null || typed.isEmpty()) return false;
        String[] words = name.toLowerCase().split("[\\s_\\-:]+");
        if (words.length < typed.length()) return false;
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (words[i].isEmpty() || words[i].charAt(0) != c) {
                return false;
            }
        }
        return true;
    }

    private static void suggestTemplates(String typed, com.mojang.brigadier.suggestion.SuggestionsBuilder builder, java.util.Collection<MountData> templates) {
        String typedLower = typed.toLowerCase();
        java.util.Set<String> suggested = new java.util.HashSet<>();
        for (MountData data : templates) {
            String id = data.id;
            String idLower = id.toLowerCase();
            String name = data.name != null ? data.name : "";
            String nameLower = name.toLowerCase();

            if (idLower.startsWith(typedLower)) {
                if (suggested.add(id)) builder.suggest(id);
            } else if (!name.isEmpty() && nameLower.startsWith(typedLower)) {
                if (suggested.add(name)) builder.suggest(name);
            } else if (idLower.contains(typedLower)) {
                String s = typed + " (" + id + ")";
                if (suggested.add(s)) builder.suggest(s);
            } else if (!name.isEmpty() && nameLower.contains(typedLower)) {
                String s = typed + " (" + id + ")";
                if (suggested.add(s)) builder.suggest(s);
            } else if (matchesInitials(typedLower, idLower) || (!name.isEmpty() && matchesInitials(typedLower, nameLower))) {
                String s = typed + " (" + id + ")";
                if (suggested.add(s)) builder.suggest(s);
            }
        }
    }

    private static void suggestOwnedMounts(String typed, com.mojang.brigadier.suggestion.SuggestionsBuilder builder, java.util.Collection<DatabaseManager.UnlockedMountData> owned) {
        String query = typed.startsWith("\"") ? typed.substring(1) : typed;
        String typedLower = query.toLowerCase();
        java.util.Set<String> suggested = new java.util.HashSet<>();
        for (DatabaseManager.UnlockedMountData data : owned) {
            String instanceId = data.instanceId;
            MountData template = MountRegistry.getTemplate(data.mountId);
            String templateName = (template != null && template.name != null) ? template.name : data.mountId;
            String customName = data.customName != null ? data.customName : "";
            
            // 1. Suggest raw template mountId
            if (data.mountId.toLowerCase().contains(typedLower) || matchesInitials(typedLower, data.mountId.toLowerCase())) {
                if (suggested.add(data.mountId)) builder.suggest(data.mountId);
            }
            
            // 2. Suggest raw instanceId UUID
            if (instanceId.toLowerCase().contains(typedLower)) {
                if (suggested.add(instanceId)) builder.suggest(instanceId);
            }

            // 3. Formatted display string
            String suggestionText = !customName.isEmpty() ? 
                customName + " (" + templateName + ")" : 
                templateName;
            
            String suggestionTextLower = suggestionText.toLowerCase();
            String customNameLower = customName.toLowerCase();
            String templateNameLower = templateName.toLowerCase();

            if (suggestionTextLower.contains(typedLower) || 
                (!customName.isEmpty() && customNameLower.contains(typedLower)) ||
                templateNameLower.contains(typedLower) ||
                matchesInitials(typedLower, templateNameLower) ||
                (!customName.isEmpty() && matchesInitials(typedLower, customNameLower))) {
                
                String formatted = suggestionText.contains(" ") ? "\"" + suggestionText + "\"" : suggestionText;
                if (suggested.add(formatted)) builder.suggest(formatted);
            }
        }
        
        // Baseline fallback to loaded template IDs
        suggestTemplates(typed, builder, MountRegistry.loadedTemplates.values());
    }
}
