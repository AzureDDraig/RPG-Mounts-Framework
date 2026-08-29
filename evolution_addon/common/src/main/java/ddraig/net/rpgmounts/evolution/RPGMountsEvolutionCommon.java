package ddraig.net.rpgmounts.evolution;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.api.EvolutionAPI;
import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.evolution.api.AddonEvolutionProvider;
import ddraig.net.rpgmounts.evolution.config.EvolutionGraphValidator;
import ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager;
import ddraig.net.rpgmounts.evolution.network.EvolutionPackets;
import ddraig.net.rpgmounts.network.ModPackets;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Common initializer entry point for the RPG Mounts Evolution Framework Addon.
 */
public class RPGMountsEvolutionCommon {
    public static final String MOD_ID = "rpg_mounts_evolution_framework";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Initializing RPG Mounts Evolution Framework Addon...");

        // Register custom evolution provider
        EvolutionAPI.registerProvider(new AddonEvolutionProvider());

        // Initialize Tree configurations and database
        EvolutionTreeManager.init();
        EvolutionPackets.init();

        // Database table hooks
        LifecycleEvent.SERVER_STARTED.register(server -> {
            AddonEvolutionProvider.initDatabase();
            ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager.executeOrphanMountRecovery();
        });

        // Sync trees to player on join
        PlayerEvent.PLAYER_JOIN.register(player -> {
            try {
                EvolutionPackets.syncTreesToPlayer(player);
            } catch (Exception e) {
                LOGGER.error("Failed to sync evolution trees for player " + player.getName().getString(), e);
            }
        });

        // Flush and evict caches on logout
        PlayerEvent.PLAYER_QUIT.register(player -> {
            try {
                DatabaseManager.flushPlayerCachesSynchronously(player.getUUID());
                DatabaseManager.unlockedMountsCache.remove(player.getUUID());
                DatabaseManager.mountGearCache.remove(player.getUUID());
                DatabaseManager.activeMountsCache.remove(player.getUUID());
                DatabaseManager.bestiaryCache.remove(player.getUUID());
            } catch (Exception e) {
                LOGGER.error("Failed to flush and evict caches on logout for player " + player.getName().getString(), e);
            }
        });

    }

    public static void registerEvolutionSubcommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> rootNode = dispatcher.getRoot().getChild("rpg_mounts");
        if (rootNode == null) return;

            // 1. /rpg_mounts evolve <target_id>
            rootNode.addChild(
                Commands.literal("evolve")
                    .then(Commands.argument("target_id", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            try {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                if (player.getVehicle() instanceof RPGMountEntity mount) {
                                    EvolutionAPI.getProvider().getEvolutionPaths(mount).forEach(path -> {
                                        builder.suggest(path.targetTemplateId);
                                    });
                                }
                            } catch (Exception e) {}
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (player.getVehicle() instanceof RPGMountEntity mount) {
                                String targetId = StringArgumentType.getString(context, "target_id");
                                boolean success = EvolutionAPI.getProvider().processServerEvolution(player, mount, targetId);
                                return success ? 1 : 0;
                            } else {
                                context.getSource().sendFailure(Component.literal("§cYou must be riding a mount to evolve it."));
                                return 0;
                            }
                        })
                    )
                    .build()
            );

            // 2. /rpg_mounts evolution-tree
            rootNode.addChild(
                Commands.literal("evolution-tree")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        if (player.getVehicle() instanceof RPGMountEntity mount) {
                            EvolutionAPI.getProvider().openEvolutionScreen(mount);
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("§cYou must be riding a mount to view its evolution tree."));
                            return 0;
                        }
                    })
                    .build()
            );

            // 3. Admin subcommands under /rpg_mounts admin
            CommandNode<CommandSourceStack> adminNode = rootNode.getChild("admin");
            if (adminNode != null) {
                adminNode.addChild(
                    Commands.literal("evolution")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("editor")
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                NetworkManager.sendToPlayer(player, EvolutionPackets.S2C_OPEN_EVOLUTION_EDITOR, new FriendlyByteBuf(Unpooled.buffer()));
                                return 1;
                            })
                        )
                        .then(Commands.literal("force-evolve")
                            .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("target_id", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        MountRegistry.loadedTemplates.keySet().forEach(builder::suggest);
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        String targetId = StringArgumentType.getString(context, "target_id");
                                        MountData targetData = MountRegistry.getTemplate(targetId);
                                        if (targetData == null) {
                                            context.getSource().sendFailure(Component.literal("Invalid target template ID."));
                                            return 0;
                                        }
                                        if (target.getVehicle() instanceof RPGMountEntity mount) {
                                            UUID playerUuid = target.getUUID();
                                            Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(playerUuid);
                                            String instanceId = mount.getInstanceId();
                                            if (ddraig.net.rpgmounts.config.ModConfig.get().general.prevent_duplicate_mounts && owned != null) {
                                                boolean alreadyOwns = owned.values().stream().anyMatch(d -> 
                                                    !d.instanceId.equals(instanceId) && DatabaseManager.isSameTemplate(d.mountId, targetId));
                                                if (alreadyOwns) {
                                                    context.getSource().sendFailure(Component.literal("§cCannot evolve: Player already owns a mount of type " + targetData.name + "."));
                                                    return 0;
                                                }
                                            }
                                            if (owned != null && owned.containsKey(instanceId)) {
                                                DatabaseManager.UnlockedMountData uData = owned.get(instanceId);
                                                DatabaseManager.saveUnlockedMountDataAsync(
                                                    playerUuid,
                                                    instanceId,
                                                    targetId,
                                                    0,
                                                    uData.level,
                                                    uData.xp,
                                                    uData.damageDealt,
                                                    uData.damageTaken,
                                                    uData.hpZeroCount,
                                                    uData.distanceTravelled,
                                                    uData.isChroma,
                                                    uData.ancestryLog,
                                                    uData.customName
                                                );
                                            }
                                            mount.discard();
                                            target.sendSystemMessage(Component.literal("Your mount was force evolved into " + targetData.name + "!"));
                                            ModPackets.syncUnlockedMounts(target);
                                            context.getSource().sendSuccess(() -> Component.literal("Force evolved mount for " + target.getName().getString()), true);
                                            return 1;
                                        } else {
                                            context.getSource().sendFailure(Component.literal("Target player must be riding a mount."));
                                            return 0;
                                        }
                                    })
                                )
                            )
                        )
                        .then(Commands.literal("set-chroma")
                            .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("status", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                    .executes(context -> {
                                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                        boolean status = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "status");
                                        if (target.getVehicle() instanceof RPGMountEntity mount) {
                                            mount.getEntityData().set(RPGMountEntity.IS_CHROMA, status);
                                            UUID playerUuid = target.getUUID();
                                            Map<String, DatabaseManager.UnlockedMountData> owned = DatabaseManager.unlockedMountsCache.get(playerUuid);
                                            String instanceId = mount.getInstanceId();
                                            if (owned != null && owned.containsKey(instanceId)) {
                                                DatabaseManager.UnlockedMountData uData = owned.get(instanceId);
                                                uData.isChroma = status;
                                                DatabaseManager.saveUnlockedMountDataAsync(uData);
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("Set chroma status to " + status + " for " + target.getName().getString() + "'s mount."), true);
                                            ModPackets.syncUnlockedMounts(target);
                                            return 1;
                                        } else {
                                            context.getSource().sendFailure(Component.literal("Target player must be riding a mount."));
                                            return 0;
                                        }
                                    })
                                )
                            )
                        )
                        .then(Commands.literal("reload-trees")
                            .executes(context -> {
                                DatabaseManager.flushAllDirtyUnlockedMountsSynchronously();
                                EvolutionTreeManager.reloadTrees();
                                EvolutionPackets.syncTreesToAll(context.getSource().getServer());
                                context.getSource().sendSuccess(() -> Component.literal("Reloaded all evolution config trees and synchronized online clients."), true);
                                return 1;
                            })
                        )
                        .then(Commands.literal("validate")
                            .executes(context -> {
                                EvolutionGraphValidator.ValidationReport report = EvolutionTreeManager.runGraphValidationDiagnostics();
                                if (report.isValid) {
                                    context.getSource().sendSuccess(() -> Component.literal("Evolution Trees Validation Success: DAG check passed. No cycles found."), true);
                                } else {
                                    context.getSource().sendFailure(Component.literal("Validation Failed: Check console logs for cyclical loops. Found " + report.errorMessages.size() + " errors."));
                                }
                                return 1;
                            })
                        )
                        .build()
                );
            }
    }
}
