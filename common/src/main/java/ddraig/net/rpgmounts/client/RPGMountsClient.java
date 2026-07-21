package ddraig.net.rpgmounts.client;

import com.mojang.blaze3d.platform.InputConstants;
import ddraig.net.rpgmounts.client.gui.MountHUDScreen;
import ddraig.net.rpgmounts.client.gui.MountStaminaOverlay;
import ddraig.net.rpgmounts.client.integration.RPGWaypointsIntegration;
import ddraig.net.rpgmounts.client.renderer.RPGMountRenderer;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.network.ModPackets;
import ddraig.net.rpgmounts.registry.ModEntities;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.FriendlyByteBuf;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.ArrayList;

/**
 * Common client-side initializer for RPG Mounts.
 * 
 * Change Log:
 * - 2026-06-19: [Initial Creation] - Registered entity renderer, HUD stamina overlay, and keybinds for UI and abilities.
 * - 2026-06-19: [Bugfixes] - Swapped HudRenderCallback for ClientGuiEvent.RENDER_HUD, added RPG Waypoints init.
 * - 2026-06-19: [S2C Packets] - Registered client S2C network receivers for creator UI, config editor, and admin mode sync.
 * - 2026-06-19: [Enhancer Creator] - Added client receiver to open Enhancer Creator Screen.
 */
public class RPGMountsClient {
    public static KeyMapping hudKey;
    public static KeyMapping ability1Key;
    public static KeyMapping ability2Key;
    public static KeyMapping whistleKey;

    private static boolean lastJumpState = false;
    private static boolean lastSneakState = false;
    private static boolean lastSprintState = false;

    public static final java.util.Map<String, UnlockedMountInfo> unlockedMounts = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Set<String> discoveredMounts = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public static class UnlockedMountInfo {
        public final String instanceId;
        public final String mountId;
        public final String customName;
        public final int bondingScore;
        public final String status;
        public final int timerRemaining;
        public final int level;
        public final float xp;
        public final boolean isChroma;
        public final double damageDealt;
        public final double damageTaken;
        public final int hpZeroCount;
        public final double distanceTravelled;
        public final String ancestryLog;

        public UnlockedMountInfo(String instanceId, String mountId, String customName, int bondingScore, String status, int timerRemaining, int level, float xp,
                                 boolean isChroma, double damageDealt, double damageTaken, int hpZeroCount, double distanceTravelled, String ancestryLog) {
            this.instanceId = instanceId;
            this.mountId = mountId;
            this.customName = customName;
            this.bondingScore = bondingScore;
            this.status = status;
            this.timerRemaining = timerRemaining;
            this.level = level;
            this.xp = xp;
            this.isChroma = isChroma;
            this.damageDealt = damageDealt;
            this.damageTaken = damageTaken;
            this.hpZeroCount = hpZeroCount;
            this.distanceTravelled = distanceTravelled;
            this.ancestryLog = ancestryLog;
        }
    }

    public static void init() {
        // Register Entity Renderer
        EntityRendererRegistry.register(ModEntities.RPG_MOUNT, RPGMountRenderer::new);


        // Initialize RPG Waypoints Integration
        RPGWaypointsIntegration.init();

  
 
        // Register HUD Overlay
        ClientGuiEvent.RENDER_HUD.register((graphics, partialTicks) -> {
            MountStaminaOverlay.render(graphics, partialTicks);
        });

        // Register S2C receiver for unlocked mounts sync
        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_UNLOCKED, (buf, context) -> {
            int count = buf.readInt();
            java.util.List<UnlockedMountInfo> list = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                String instId = buf.readUtf();
                String mId = buf.readUtf();
                String cName = buf.readUtf();
                int bonding = buf.readInt();
                int level = buf.readInt();
                float xp = buf.readFloat();
                String status = buf.readUtf();
                int timer = buf.readInt();
                boolean isChroma = buf.readBoolean();
                double damageDealt = buf.readDouble();
                double damageTaken = buf.readDouble();
                int hpZeroCount = buf.readInt();
                double distanceTravelled = buf.readDouble();
                String ancestryLog = buf.readUtf();
                list.add(new UnlockedMountInfo(instId, mId, cName, bonding, status, timer, level, xp, isChroma, damageDealt, damageTaken, hpZeroCount, distanceTravelled, ancestryLog));
            }
            context.queue(() -> {
                unlockedMounts.clear();
                for (UnlockedMountInfo info : list) {
                    unlockedMounts.put(info.instanceId, info);
                }
                net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
                if (screen instanceof ddraig.net.rpgmounts.client.gui.MountHUDScreen hudScreen) {
                    hudScreen.init(net.minecraft.client.Minecraft.getInstance(), hudScreen.width, hudScreen.height);
                }
            });
        });

        // Register S2C receiver for bestiary discoveries sync
        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_BESTIARY, (buf, context) -> {
            int count = buf.readInt();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                list.add(buf.readUtf());
            }
            context.queue(() -> {
                discoveredMounts.clear();
                discoveredMounts.addAll(list);
                net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
                if (screen instanceof ddraig.net.rpgmounts.client.gui.BestiaryScreen bestiaryScreen) {
                    bestiaryScreen.init(net.minecraft.client.Minecraft.getInstance(), bestiaryScreen.width, bestiaryScreen.height);
                }
            });
        });

        // Register S2C receivers for admin screens and modes
        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_ADMIN_MODE, (buf, context) -> {
            boolean active = buf.readBoolean();
            context.queue(() -> {
                RPGWaypointsIntegration.setAdminMode(active);
                if (net.minecraft.client.Minecraft.getInstance().player != null) {
                    net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("Client Admin Mode: " + (active ? "ENABLED" : "DISABLED"))
                    );
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_CREATOR, (buf, context) -> {
            context.queue(() -> {
                net.minecraft.client.Minecraft.getInstance().setScreen(new ddraig.net.rpgmounts.client.gui.MountCreatorScreen());
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_CONFIG, (buf, context) -> {
            context.queue(() -> {
                net.minecraft.client.Minecraft.getInstance().setScreen(new ddraig.net.rpgmounts.client.gui.ConfigEditorScreen());
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_ENHANCER_CREATOR, (buf, context) -> {
            context.queue(() -> {
                net.minecraft.client.Minecraft.getInstance().setScreen(new ddraig.net.rpgmounts.client.gui.EnhancerCreatorScreen());
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_ABILITY_CREATOR, (buf, context) -> {
            context.queue(() -> {
                net.minecraft.client.Minecraft.getInstance().setScreen(new ddraig.net.rpgmounts.client.gui.AbilityCreatorScreen());
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_GEAR, (buf, context) -> {
            int id = buf.readInt();
            context.queue(() -> {
                net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null) {
                    net.minecraft.world.entity.Entity ent = level.getEntity(id);
                    if (ent instanceof RPGMountEntity mount) {
                        net.minecraft.client.Minecraft.getInstance().setScreen(new ddraig.net.rpgmounts.client.gui.MountGearScreen(mount));
                    }
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_GEAR, (buf, context) -> {
            int id = buf.readInt();
            net.minecraft.nbt.CompoundTag nbt = buf.readNbt();
            context.queue(() -> {
                net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null) {
                    net.minecraft.world.entity.Entity ent = level.getEntity(id);
                    if (ent instanceof RPGMountEntity mount && nbt != null) {
                        RPGMountEntity.loadContainerFromTag(mount.getInventory(), nbt.getList("Items", 10));
                        mount.updateSynchedData();
                    }
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_OPEN_BESTIARY, (buf, context) -> {
            context.queue(() -> {
                net.minecraft.client.Minecraft.getInstance().setScreen(new ddraig.net.rpgmounts.client.gui.BestiaryScreen());
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_CONFIG, (buf, context) -> {
            String json = buf.readUtf();
            String animJson = buf.isReadable() ? buf.readUtf() : null;
            context.queue(() -> {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                ddraig.net.rpgmounts.config.ModConfig config = gson.fromJson(json, ddraig.net.rpgmounts.config.ModConfig.class);
                if (config != null) {
                    ddraig.net.rpgmounts.config.ModConfig.setInstance(config);
                    // Refresh Config Editor screen if active
                    net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
                    if (screen instanceof ddraig.net.rpgmounts.client.gui.ConfigEditorScreen editorScreen) {
                        editorScreen.init(net.minecraft.client.Minecraft.getInstance(), editorScreen.width, editorScreen.height);
                    }
                }
                if (animJson != null) {
                    ddraig.net.rpgmounts.config.AnimationMappingConfig animConfig = gson.fromJson(animJson, ddraig.net.rpgmounts.config.AnimationMappingConfig.class);
                    if (animConfig != null) {
                        ddraig.net.rpgmounts.config.AnimationMappingConfig.setInstance(animConfig);
                    }
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_ABILITIES, (buf, context) -> {
            int count = buf.readInt();
            java.util.List<MountData.AbilityData> list = new java.util.ArrayList<>();
            com.google.gson.Gson gson = new com.google.gson.Gson();
            for (int i = 0; i < count; i++) {
                String json = buf.readUtf();
                MountData.AbilityData ab = gson.fromJson(json, MountData.AbilityData.class);
                if (ab != null) {
                    list.add(ab);
                }
            }
            context.queue(() -> {
                MountRegistry.customAbilities.clear();
                for (MountData.AbilityData ab : list) {
                    MountRegistry.customAbilities.put(ab.name, ab);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_TEMPLATES, (buf, context) -> {
            int count = buf.readInt();
            java.util.List<MountData> list = new java.util.ArrayList<>();
            com.google.gson.Gson gson = new com.google.gson.Gson();
            for (int i = 0; i < count; i++) {
                String json = buf.readUtf();
                MountData data = gson.fromJson(json, MountData.class);
                if (data != null) {
                    list.add(data);
                }
            }
            context.queue(() -> {
                ddraig.net.rpgmounts.client.renderer.JavaModelLoader.clearCaches();
                MountRegistry.loadedTemplates.clear();
                for (MountData data : list) {
                    MountRegistry.loadedTemplates.put(data.id, data);
                }
                // Refresh active screens if open
                net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
                if (screen instanceof ddraig.net.rpgmounts.client.gui.MountCreatorScreen creatorScreen) {
                    creatorScreen.init(net.minecraft.client.Minecraft.getInstance(), creatorScreen.width, creatorScreen.height);
                }
                if (screen instanceof ddraig.net.rpgmounts.client.gui.MountHUDScreen hudScreen) {
                    hudScreen.init(net.minecraft.client.Minecraft.getInstance(), hudScreen.width, hudScreen.height);
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), ModPackets.S2C_SYNC_AUDITS, (buf, context) -> {
            int total = buf.readInt();
            int count = buf.readInt();
            java.util.List<String> list = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                list.add(buf.readUtf());
            }
            context.queue(() -> {
                RPGWaypointsIntegration.setReceivedAudits(total, list);
            });
        });

        // Register Keybinds
        hudKey = new KeyMapping(
                "key.rpgmounts.open_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.rpgmounts.general"
        );
        ability1Key = new KeyMapping(
                "key.rpgmounts.ability1",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.rpgmounts.general"
        );
        ability2Key = new KeyMapping(
                "key.rpgmounts.ability2",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "category.rpgmounts.general"
        );
        whistleKey = new KeyMapping(
                "key.rpgmounts.whistle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.rpgmounts.general"
        );

        KeyMappingRegistry.register(hudKey);
        KeyMappingRegistry.register(ability1Key);
        KeyMappingRegistry.register(ability2Key);
        KeyMappingRegistry.register(whistleKey);

        // Register client tick listener for key inputs
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (client.player == null) return;

            while (hudKey.consumeClick()) {
                client.setScreen(new MountHUDScreen());
            }

            if (client.player.getVehicle() instanceof RPGMountEntity mount) {
                while (ability1Key.consumeClick()) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(3); // Ability 1
                    NetworkManager.sendToServer(ModPackets.C2S_ACTION, buf);
                }
                while (ability2Key.consumeClick()) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(4); // Ability 2
                    NetworkManager.sendToServer(ModPackets.C2S_ACTION, buf);
                }

                // Intercept inventory open key (E) while riding
                if (client.screen == null) {
                    while (client.options.keyInventory.consumeClick()) {
                        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                        NetworkManager.sendToServer(ModPackets.C2S_REQUEST_OPEN_GEAR, buf);
                    }
                }

                boolean isFlyingOrAquatic = false;
                MountData data = MountRegistry.getTemplate(mount.getTemplateId());
                if (data != null && (data.category.equalsIgnoreCase("FLYING") || data.category.equalsIgnoreCase("AQUATIC"))) {
                    isFlyingOrAquatic = true;
                }

                boolean sprint = client.options.keySprint.isDown() || client.player.isSprinting();
                mount.inputFlyUp = client.options.keyJump.isDown();
                mount.inputFlyDown = isFlyingOrAquatic ? client.options.keySprint.isDown() : client.options.keyShift.isDown();
                mount.inputSprint = isFlyingOrAquatic ? false : sprint;

                // Track jump, sneak & sprint state transitions for flying/swimming/sprinting movement
                boolean jump = client.options.keyJump.isDown();
                boolean sneak = isFlyingOrAquatic ? client.options.keySprint.isDown() : client.options.keyShift.isDown();
                boolean sprintInput = isFlyingOrAquatic ? false : sprint;

                if (jump != lastJumpState) {
                    lastJumpState = jump;
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(jump ? 5 : 6); // 5 = FLY UP ON, 6 = FLY UP OFF
                    NetworkManager.sendToServer(ModPackets.C2S_ACTION, buf);
                }
                if (sneak != lastSneakState) {
                    lastSneakState = sneak;
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(sneak ? 7 : 8); // 7 = FLY DOWN ON, 8 = FLY DOWN OFF
                    NetworkManager.sendToServer(ModPackets.C2S_ACTION, buf);
                }
                if (sprintInput != lastSprintState) {
                    lastSprintState = sprintInput;
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(sprintInput ? 9 : 10); // 9 = SPRINT ON, 10 = SPRINT OFF
                    NetworkManager.sendToServer(ModPackets.C2S_ACTION, buf);
                }
            } else {
                lastJumpState = false;
                lastSneakState = false;
                lastSprintState = false;
            }

            while (whistleKey.consumeClick()) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                NetworkManager.sendToServer(ModPackets.C2S_WHISTLE, buf);
            }
        });
    }
}
