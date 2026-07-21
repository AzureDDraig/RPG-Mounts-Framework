package ddraig.net.rpgmounts.client.gui;

import ddraig.net.rpgmounts.client.integration.RPGWaypointsIntegration;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.network.ModPackets;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RPG Mounts Creator UI Screen
 * Allows administrators to add, delete, edit, and export mount configurations.
 * Features a real-time rotating 3D preview viewport of the customized mount model,
 * seat editor projections, dummy Steve passenger previews, dropdowns, and popup overlays.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented layout stubs.
 * - 2026-06-19: [Complete Redesign] - Restored 6 tabs, added click-and-drag rotation, seat projections, Steve riders, and item selectors.
 */
public class MountCreatorScreen extends Screen {
    private final List<MountData> templatesList = new ArrayList<>();
    private MountData selectedTemplate;
    private String activeTab = "General"; // General, Model & Anims, Stats, Combat, Sounds & FX, Seating & Rules

    private static String[] cachedVanillaModels = null;

    private static String[] getVanillaModels() {
        if (cachedVanillaModels == null) {
            List<String> list = new ArrayList<>();
            String[] defaults = {
                "minecraft:horse", "minecraft:wolf", "minecraft:ender_dragon",
                "minecraft:cow", "minecraft:pig", "minecraft:sheep",
                "minecraft:spider", "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
                "minecraft:squid", "minecraft:dolphin", "minecraft:turtle", "minecraft:glow_squid",
                "minecraft:axolotl", "minecraft:phantom", "minecraft:bat", "minecraft:parrot",
                "minecraft:bee", "minecraft:chicken", "minecraft:rabbit", "minecraft:llama",
                "minecraft:panda", "minecraft:fox", "minecraft:cat", "minecraft:ocelot",
                "minecraft:polar_bear", "minecraft:donkey", "minecraft:mule", "minecraft:strider",
                "minecraft:frog", "minecraft:ghast", "minecraft:blaze", "minecraft:wither",
                "minecraft:ravager", "minecraft:iron_golem", "minecraft:snow_golem", "minecraft:warden",
                "minecraft:camel", "minecraft:sniffer", "minecraft:allay"
            };
            for (String def : defaults) {
                if (!list.contains(def)) {
                    list.add(def);
                }
            }
            try {
                for (net.minecraft.resources.ResourceLocation loc : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet()) {
                    String id = loc.toString();
                    if (loc.getNamespace().equals("minecraft") && !list.contains(id)) {
                        net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(loc);
                        if (type != null) {
                            Class<?> baseClass = type.getBaseClass();
                            if (baseClass != null && net.minecraft.world.entity.LivingEntity.class.isAssignableFrom(baseClass)) {
                                list.add(id);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                ddraig.net.rpgmounts.RPGMounts.LOGGER.error("Failed to query vanilla entity types registry: ", e);
            }
            cachedVanillaModels = list.toArray(new String[0]);
        }
        return cachedVanillaModels;
    }

    // Textfields
    private EditBox nameField;
    private MultiLineEditBox descField;
    private int panelW = 440;
    private int panelH = 280;
    private EditBox modelIdField;
    private EditBox textureField;
    private EditBox animField;

    // Combat EditBoxes
    private EditBox abilityNameField;
    private EditBox abilityDescField;
    private EditBox abilityParticleField;
    private EditBox abilitySoundField;
    private EditBox abilityAnimField;
    private EditBox combatStrengthField;
    private EditBox combatAttackSpeedField;

    // Sounds & FX EditBoxes
    private EditBox soundAmbientField;
    private EditBox soundStepField;
    private EditBox soundHurtField;
    private EditBox soundDeathField;
    private EditBox spawnParticleField;
    private EditBox spawnSoundField;
    private EditBox flightParticleField;
    private EditBox groundParticleField;

    // Preview
    private RPGMountEntity previewEntity;
    private float previewRotation = 0.0f;
    private float previewZoom = 1.0f;
    private boolean showSteve = true;

    // Active state for tabs
    private int selectedAbilityIndex = 1; // 1 = Ability 1, 2 = Ability 2
    private int selectedSeatIndex = 0;
    private int abilityScrollOffset = 0;

    // Item selector popup overlay
    private boolean isItemSelectorOpen = false;
    private EditBox itemSearchBox;
    private String itemSelectorTarget = ""; // "catalyst" or "cargo"
    private final List<Item> filteredItems = new ArrayList<>();
    private int itemScrollOffset = 0;

    private List<net.minecraft.network.chat.Component> hoveredTooltip = null;
    private boolean showModelSuggestions = false;
    private List<String> activeSuggestions = new ArrayList<>();
    private int suggestionsScrollOffset = 0;
    private boolean showSoundSuggestions = false;
    private EditBox activeSoundField = null;
    private int soundSuggestionYOffset = 0;
    private EditBox activeModelField = null;
    private int modelSuggestionYOffset = 58;
    private static List<String> cachedSoundList = null;
    private static List<String> cachedModelList = null;
    private static List<String> cachedParticleList = null;

    public MountCreatorScreen() {
        super(Component.translatable("gui.rpg_mounts.creator.title"));
    }

    @Override
    protected void init() {
        cachedSoundList = null;
        cachedModelList = null;
        templatesList.clear();
        templatesList.addAll(MountRegistry.loadedTemplates.values());

        if (selectedTemplate == null && !templatesList.isEmpty()) {
            selectedTemplate = templatesList.get(0);
        }

        this.panelW = Math.max(440, Math.min(540, (int)(this.width * 0.85)));
        this.panelH = Math.max(280, Math.min(340, (int)(this.height * 0.85)));
        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;

        int formX = left + 110;
        int formY = top + 42;
        int formW = (int) ((this.panelW - 120) * 0.6);

        // Initialize general tab textfields
        this.nameField = new EditBox(this.font, formX + 8, formY + 20, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.name"));
        this.descField = new MultiLineEditBox(this.font, formX + 8, formY + 46, formW - 16, 40, Component.translatable("gui.rpg_mounts.creator.placeholder.description"), Component.translatable("gui.rpg_mounts.creator.placeholder.description"));

        // Model fields
        this.modelIdField = new EditBox(this.font, formX + 8, formY + 46, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.model_path"));
        this.textureField = new EditBox(this.font, formX + 8, formY + 72, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.texture_path"));
        this.animField = new EditBox(this.font, formX + 8, formY + 98, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.animation_path"));

        // Ability fields
        this.abilityNameField = new EditBox(this.font, formX + 8, formY + 44, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.ability_name"));
        this.abilityDescField = new EditBox(this.font, formX + 8, formY + 68, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.ability_desc"));
        this.abilityParticleField = new EditBox(this.font, formX + 8, formY + 92, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.ability_particle"));
        this.abilitySoundField = new EditBox(this.font, formX + 8, formY + 116, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.ability_sound"));
        this.abilityAnimField = new EditBox(this.font, formX + 8, formY + 140, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.ability_anim"));

        // Sounds fields
        this.soundAmbientField = new EditBox(this.font, formX + 8, formY + 16, formW - 28, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.ambient_sound"));
        this.soundStepField = new EditBox(this.font, formX + 8, formY + 40, formW - 28, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.step_sound"));
        this.soundHurtField = new EditBox(this.font, formX + 8, formY + 64, formW - 28, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.hurt_sound"));
        this.soundDeathField = new EditBox(this.font, formX + 8, formY + 88, formW - 28, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.death_sound"));
        this.spawnParticleField = new EditBox(this.font, formX + 8, formY + 112, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.spawn_particle"));
        this.spawnSoundField = new EditBox(this.font, formX + 8, formY + 136, formW - 28, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.spawn_sound"));

        // Combat stats fields
        this.combatStrengthField = new EditBox(this.font, formX + 8, formY + 44, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.strength"));
        this.combatAttackSpeedField = new EditBox(this.font, formX + 8, formY + 70, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.attack_speed"));

        this.flightParticleField = new EditBox(this.font, formX + 8, formY + 176, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.flight_particle"));
        this.groundParticleField = new EditBox(this.font, formX + 8, formY + 176, formW - 16, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.ground_particle"));

        // Popup Item Selector Search Box
        this.itemSearchBox = new EditBox(this.font, (this.width - 160) / 2 + 10, (this.height - 180) / 2 + 20, 140, 12, Component.translatable("gui.rpg_mounts.creator.placeholder.search"));

        // Register edit boxes as widgets
        this.addWidget(this.nameField);
        this.addWidget(this.descField);
        this.addWidget(this.modelIdField);
        this.addWidget(this.textureField);
        this.addWidget(this.animField);
        this.addWidget(this.abilityNameField);
        this.addWidget(this.abilityDescField);
        this.addWidget(this.abilityParticleField);
        this.addWidget(this.abilitySoundField);
        this.addWidget(this.abilityAnimField);
        this.addWidget(this.soundAmbientField);
        this.addWidget(this.soundStepField);
        this.addWidget(this.soundHurtField);
        this.addWidget(this.soundDeathField);
        this.addWidget(this.spawnParticleField);
        this.addWidget(this.spawnSoundField);
        this.addWidget(this.combatStrengthField);
        this.addWidget(this.combatAttackSpeedField);
        this.addWidget(this.flightParticleField);
        this.addWidget(this.groundParticleField);
        this.addWidget(this.itemSearchBox);

        updateFieldValues();
        updateWidgetsVisibility();
        updateDummyRiders();
    }

    private void updateFieldValues() {
        if (selectedTemplate == null) return;
        if (selectedTemplate.sounds == null) {
            selectedTemplate.sounds = new MountData.SoundsData();
        }
        if (selectedTemplate.spawnEffects == null) {
            selectedTemplate.spawnEffects = new MountData.SpawnEffectsData();
        }
        if (selectedTemplate.combat == null) {
            selectedTemplate.combat = new MountData.CombatData();
        }
        if (selectedTemplate.stats == null) {
            selectedTemplate.stats = new MountData.StatsData();
        }
        if (selectedTemplate.rarity == null) {
            selectedTemplate.rarity = "COMMON";
        }

        nameField.setValue(selectedTemplate.name != null ? selectedTemplate.name : "");
        descField.setValue(selectedTemplate.description != null ? selectedTemplate.description : "");
        modelIdField.setValue(selectedTemplate.modelId != null ? selectedTemplate.modelId : "");
        textureField.setValue(selectedTemplate.texturePath != null ? selectedTemplate.texturePath : "");
        animField.setValue(selectedTemplate.animationPath != null ? selectedTemplate.animationPath : "");

        MountData.AbilityData ability = (selectedAbilityIndex == 1) ? selectedTemplate.combat.ability1 : selectedTemplate.combat.ability2;
        if (ability != null) {
            abilityNameField.setValue(ability.name != null ? ability.name : "");
            abilityDescField.setValue(ability.description != null ? ability.description : "");
            abilityParticleField.setValue(ability.particle != null ? ability.particle : "");
            abilitySoundField.setValue(ability.sound != null ? ability.sound : "");
            abilityAnimField.setValue(ability.animationName != null ? ability.animationName : "");
        }

        soundAmbientField.setValue(selectedTemplate.sounds.ambient != null ? selectedTemplate.sounds.ambient : "");
        soundStepField.setValue(selectedTemplate.sounds.step != null ? selectedTemplate.sounds.step : "");
        soundHurtField.setValue(selectedTemplate.sounds.hurt != null ? selectedTemplate.sounds.hurt : "");
        soundDeathField.setValue(selectedTemplate.sounds.death != null ? selectedTemplate.sounds.death : "");
        
        spawnParticleField.setValue(selectedTemplate.spawnEffects.particle != null ? selectedTemplate.spawnEffects.particle : "");
        spawnSoundField.setValue(selectedTemplate.spawnEffects.sound != null ? selectedTemplate.spawnEffects.sound : "");
        
        combatStrengthField.setValue(String.valueOf(selectedTemplate.combat.strength));
        combatAttackSpeedField.setValue(String.valueOf(selectedTemplate.combat.attackSpeed));

        flightParticleField.setValue(selectedTemplate.flightParticle != null ? selectedTemplate.flightParticle : "minecraft:cloud");
        groundParticleField.setValue(selectedTemplate.groundParticle != null ? selectedTemplate.groundParticle : "minecraft:crit");
    }

    private void updateWidgetsVisibility() {
        boolean general = "General".equals(activeTab) && selectedTemplate != null && !isItemSelectorOpen;
        nameField.visible = general;
        nameField.active = general;
        descField.visible = general;
        descField.active = general;

        boolean model = "Model & Anims".equals(activeTab) && selectedTemplate != null && !isItemSelectorOpen;
        boolean customModel = model && !selectedTemplate.modelType.equals("vanilla") && !selectedTemplate.modelType.equals("mcmodel");
        modelIdField.visible = customModel;
        modelIdField.active = customModel;
        textureField.visible = model && !selectedTemplate.modelType.equals("mcmodel");
        textureField.active = model && !selectedTemplate.modelType.equals("mcmodel");
        animField.visible = model && selectedTemplate.modelType.equals("geckolib");
        animField.active = model && selectedTemplate.modelType.equals("geckolib");

        boolean showFlight = model && selectedTemplate.category.equalsIgnoreCase("FLYING") && !selectedTemplate.modelType.equals("mcmodel");
        flightParticleField.visible = showFlight;
        flightParticleField.active = showFlight;

        boolean showGround = model && (selectedTemplate.category.equalsIgnoreCase("GROUND") || selectedTemplate.category.equalsIgnoreCase("SURFACE_WATER")) && !selectedTemplate.modelType.equals("mcmodel");
        groundParticleField.visible = showGround;
        groundParticleField.active = showGround;

        boolean combat = "Combat".equals(activeTab) && selectedTemplate != null && !isItemSelectorOpen;
        boolean combatEnabled = combat && selectedTemplate.combat.enableCombat;
        abilityNameField.visible = false;
        abilityNameField.active = false;
        abilityDescField.visible = false;
        abilityDescField.active = false;
        abilityParticleField.visible = false;
        abilityParticleField.active = false;
        abilitySoundField.visible = false;
        abilitySoundField.active = false;
        abilityAnimField.visible = false;
        abilityAnimField.active = false;
        combatStrengthField.visible = combatEnabled;
        combatStrengthField.active = combatEnabled;
        combatAttackSpeedField.visible = combatEnabled;
        combatAttackSpeedField.active = combatEnabled;

        boolean sfx = "Sounds & FX".equals(activeTab) && selectedTemplate != null && !isItemSelectorOpen;
        soundAmbientField.visible = sfx;
        soundAmbientField.active = sfx;
        soundStepField.visible = sfx;
        soundStepField.active = sfx;
        soundHurtField.visible = sfx;
        soundHurtField.active = sfx;
        soundDeathField.visible = sfx;
        soundDeathField.active = sfx;
        spawnParticleField.visible = sfx;
        spawnParticleField.active = sfx;
        spawnSoundField.visible = sfx;
        spawnSoundField.active = sfx;

        itemSearchBox.visible = isItemSelectorOpen;
        itemSearchBox.active = isItemSelectorOpen;
    }

    private RPGMountEntity getPreviewEntity() {
        if (previewEntity == null && this.minecraft != null && this.minecraft.level != null) {
            previewEntity = new RPGMountEntity(ddraig.net.rpgmounts.registry.ModEntities.RPG_MOUNT.get(), this.minecraft.level);
        }
        if (previewEntity != null && selectedTemplate != null) {
            previewEntity.setTemplateId(selectedTemplate.id);
            previewEntity.setHealth((float) selectedTemplate.stats.maxHealth);
        }
        return previewEntity;
    }

    private void updateDummyRiders() {
        RPGMountEntity preview = getPreviewEntity();
        if (preview == null) return;
        preview.ejectPassengers();
        if (selectedTemplate != null) {
            for (int i = 0; i < selectedTemplate.seats.size(); i++) {
                MountData.SeatOffset seat = selectedTemplate.seats.get(i);
                updateCreatorSeatBone(preview, i, seat.x, seat.y, seat.z);
            }
        }
        if (showSteve && selectedTemplate != null) {
            for (int i = 0; i < selectedTemplate.seats.size(); i++) {
                net.minecraft.client.player.RemotePlayer rider = new net.minecraft.client.player.RemotePlayer(
                        this.minecraft.level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Steve_" + (i + 1))
                );
                rider.addTag("rpg_mount_preview_rider");
                rider.startRiding(preview, true);
            }
        }
    }

    private void updateCreatorSeatBone(RPGMountEntity mount, int seatIndex, double x, double y, double z) {
        try {
            Object dispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
            Object renderer = ((net.minecraft.client.renderer.entity.EntityRenderDispatcher) dispatcher).getRenderer(mount);
            if (renderer instanceof ddraig.net.rpgmounts.client.renderer.RPGMountRenderer rpgRenderer) {
                Object geoRenderer = rpgRenderer.getGeckoLibRenderer();
                if (geoRenderer != null) {
                    java.lang.reflect.Method getGeoModelMethod = geoRenderer.getClass().getMethod("getGeoModel");
                    Object model = getGeoModelMethod.invoke(geoRenderer);
                    if (model != null) {
                        java.lang.reflect.Method getModelResourceMethod = model.getClass().getMethod("getModelResource", ddraig.net.rpgmounts.entity.RPGMountEntity.class);
                        net.minecraft.resources.ResourceLocation modelLoc = (net.minecraft.resources.ResourceLocation) getModelResourceMethod.invoke(model, mount);
                        
                        java.lang.reflect.Method getBakedModelMethod = model.getClass().getMethod("getBakedModel", net.minecraft.resources.ResourceLocation.class);
                        Object bakedModel = getBakedModelMethod.invoke(model, modelLoc);
                        
                        if (bakedModel != null) {
                            java.lang.reflect.Method getBonesMethod = bakedModel.getClass().getMethod("getBones");
                            Object rootBones = getBonesMethod.invoke(bakedModel);
                            Object bone = null;
                            String boneName = "programmatic_seat_" + mount.getTemplateId() + "_" + seatIndex;
                            if (rootBones instanceof java.util.Collection) {
                                for (Object rootObj : (java.util.Collection<?>) rootBones) {
                                    bone = findBoneRecursive(rootObj, boneName);
                                    if (bone != null) {
                                        break;
                                    }
                                }
                            }
                            if (bone != null) {
                                software.bernie.geckolib.cache.object.GeoBone geoBone = (software.bernie.geckolib.cache.object.GeoBone) bone;
                                software.bernie.geckolib.cache.object.GeoBone parent = geoBone.getParent();
                                float px = 0;
                                float py = 0;
                                float pz = 0;
                                if (parent != null) {
                                    float[] absPivot = computeAbsolutePivot(parent);
                                    px = absPivot[0];
                                    py = absPivot[1];
                                    pz = absPivot[2];
                                }
                                geoBone.setPosX((float)(x * 16.0) - px);
                                geoBone.setPosY((float)(y * 16.0) - py);
                                geoBone.setPosZ((float)(z * 16.0) - pz);
                                try {
                                    java.lang.reflect.Field snapshotField = geoBone.getClass().getDeclaredField("initialSnapshot");
                                    snapshotField.setAccessible(true);
                                    snapshotField.set(geoBone, null);
                                } catch (Exception ignored) {}
                                geoBone.saveInitialSnapshot();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private float[] computeAbsolutePivot(software.bernie.geckolib.cache.object.GeoBone bone) {
        float x = bone.getPivotX();
        float y = bone.getPivotY();
        float z = bone.getPivotZ();
        software.bernie.geckolib.cache.object.GeoBone parent = bone.getParent();
        if (parent != null) {
            float[] p = computeAbsolutePivot(parent);
            x += p[0];
            y += p[1];
            z += p[2];
        }
        return new float[]{x, y, z};
    }

    private static Object findBoneRecursive(Object boneObj, String name) {
        if (boneObj == null) return null;
        try {
            Class<?> boneClass = boneObj.getClass();
            java.lang.reflect.Method getNameMethod = boneClass.getMethod("getName");
            String boneName = (String) getNameMethod.invoke(boneObj);
            if (name.equals(boneName)) {
                return boneObj;
            }
            java.lang.reflect.Method getChildBonesMethod = boneClass.getMethod("getChildBones");
            Object children = getChildBonesMethod.invoke(boneObj);
            if (children instanceof java.util.Collection) {
                for (Object child : (java.util.Collection<?>) children) {
                    Object found = findBoneRecursive(child, name);
                    if (found != null) {
                        return found;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void tick() {
        if (nameField != null) nameField.tick();
        if (descField != null) descField.tick();
        if (modelIdField != null) modelIdField.tick();
        if (textureField != null) textureField.tick();
        if (animField != null) animField.tick();
        if (combatStrengthField != null) combatStrengthField.tick();
        if (combatAttackSpeedField != null) combatAttackSpeedField.tick();
        if (flightParticleField != null) flightParticleField.tick();
        if (groundParticleField != null) groundParticleField.tick();

        if (this.previewEntity != null) {
            this.previewEntity.tickCount++;
        }

        if (activeTab.equals("Model & Anims")) {
            EditBox focused = null;
            int yOffset = 0;
            if (modelIdField != null && modelIdField.isFocused()) {
                focused = modelIdField;
                yOffset = 58;
            } else if (flightParticleField != null && flightParticleField.isFocused() && flightParticleField.visible) {
                focused = flightParticleField;
                yOffset = 188;
            } else if (groundParticleField != null && groundParticleField.isFocused() && groundParticleField.visible) {
                focused = groundParticleField;
                yOffset = 188;
            }

            if (focused != null) {
                if (focused == flightParticleField || focused == groundParticleField) {
                    activeSuggestions = getParticleSuggestions(focused.getValue());
                } else {
                    activeSuggestions = getModelSuggestions(focused.getValue());
                }
                showModelSuggestions = true;
                activeModelField = focused;
                modelSuggestionYOffset = yOffset;
            } else {
                showModelSuggestions = false;
                activeModelField = null;
            }
        } else {
            showModelSuggestions = false;
            activeModelField = null;
        }

        if (activeTab.equals("Sounds & FX")) {
            EditBox focused = null;
            int yOffset = 0;
            if (soundAmbientField != null && soundAmbientField.isFocused()) {
                focused = soundAmbientField;
                yOffset = 16 + 12;
            } else if (soundStepField != null && soundStepField.isFocused()) {
                focused = soundStepField;
                yOffset = 40 + 12;
            } else if (soundHurtField != null && soundHurtField.isFocused()) {
                focused = soundHurtField;
                yOffset = 64 + 12;
            } else if (soundDeathField != null && soundDeathField.isFocused()) {
                focused = soundDeathField;
                yOffset = 88 + 12;
            } else if (spawnParticleField != null && spawnParticleField.isFocused()) {
                focused = spawnParticleField;
                yOffset = 112 + 12;
            } else if (spawnSoundField != null && spawnSoundField.isFocused()) {
                focused = spawnSoundField;
                yOffset = 136 + 12;
            }

            if (focused != null) {
                if (focused == spawnParticleField) {
                    activeSuggestions = getParticleSuggestions(focused.getValue());
                } else {
                    activeSuggestions = getSoundSuggestions(focused.getValue());
                }
                showSoundSuggestions = true;
                activeSoundField = focused;
                soundSuggestionYOffset = yOffset;
            } else {
                showSoundSuggestions = false;
                activeSoundField = null;
            }
        } else {
            showSoundSuggestions = false;
            activeSoundField = null;
        }
    }

    private static class TabBounds {
        final String id;
        final Component label;
        int x;
        int y;
        int w;
        int h;

        TabBounds(String id, Component label) {
            this.id = id;
            this.label = label;
        }
    }

    private List<TabBounds> calculateTabBounds(int left, int top) {
        List<TabBounds> bounds = new ArrayList<>();
        String[] tabIds = {"General", "Model & Anims", "Stats", "Combat", "Abilities", "Sounds & FX", "Seating & Rules"};
        String[] tabKeys = {
            "gui.rpg_mounts.creator.tab.general",
            "gui.rpg_mounts.creator.tab.model",
            "gui.rpg_mounts.creator.tab.stats",
            "gui.rpg_mounts.creator.tab.combat",
            "gui.rpg_mounts.creator.tab.abilities",
            "gui.rpg_mounts.creator.tab.sounds",
            "gui.rpg_mounts.creator.tab.seating"
        };

        List<List<TabBounds>> rows = new ArrayList<>();
        List<TabBounds> currentRow = new ArrayList<>();
        int currentX = left + 110;
        int maxX = left + this.panelW - 10;

        for (int i = 0; i < tabIds.length; i++) {
            Component labelComp = Component.translatable(tabKeys[i]);
            int w = this.font.width(labelComp) + 12;
            TabBounds tb = new TabBounds(tabIds[i], labelComp);
            tb.w = w;
            tb.h = 15;

            if (currentX + w > maxX && !currentRow.isEmpty()) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                currentX = left + 110;
            }
            tb.x = currentX;
            currentRow.add(tb);
            currentX += w + 2;
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        int numRows = rows.size();
        for (int r = 0; r < numRows; r++) {
            int rowY = top + 25 - (numRows - 1 - r) * 15;
            for (TabBounds tb : rows.get(r)) {
                tb.y = rowY;
                bounds.add(tb);
            }
        }
        return bounds;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.hoveredTooltip = null;
        updateWidgetsVisibility();
        this.renderBackground(graphics);

        int panelW = this.panelW;
        int panelH = this.panelH;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int borderC = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0); // Gold
        int bgC = RPGWaypointsIntegration.getThemeColor("panelBg", 0xFF2D2D2D);
        int slotC = RPGWaypointsIntegration.getThemeColor("slotBg", 0xFF1C1C1C);
        int textActiveC = RPGWaypointsIntegration.getThemeColor("textActiveColor", 0xFFD4AF37); // Gold text
        int textNormalC = RPGWaypointsIntegration.getThemeColor("textColor", 0xFFCCCCCC);

        // Draw double borders
        UIHelper.drawBeveledPanel(graphics, left, top, panelW, panelH, borderC, bgC);

        // Header Title
        graphics.drawString(this.font, this.title, left + 12, top + 10, textActiveC, false);

        // Left Panel (Sidebar lists templates)
        int listX = left + 10;
        int listY = top + 25;
        int listW = 90;
        int listH = panelH - 72;

        UIHelper.drawRecessedSlot(graphics, listX, listY, listW, listH, borderC, slotC);

        int itemY = listY + 5;
        for (MountData m : templatesList) {
            int c = (m == selectedTemplate) ? textActiveC : textNormalC;
            graphics.drawString(this.font, truncate(m.name, 12), listX + 5, itemY, c, false);
            itemY += 12;
        }

        // Add/Del Buttons
        int addY = listY + listH + 4;
        boolean hoverAdd = mouseX >= listX && mouseX <= listX + 42 && mouseY >= addY && mouseY <= addY + 14;
        String addLabel = Component.translatable("gui.rpg_mounts.creator.btn.add").getString();
        UIHelper.drawShadedButton(graphics, listX, addY, 42, 14, hoverAdd, 0xFF005500);
        graphics.drawString(this.font, addLabel, listX + 6, addY + 3, 0xFFFFFFFF, false);
 
        boolean hoverDel = mouseX >= listX + 48 && mouseX <= listX + listW && mouseY >= addY && mouseY <= addY + 14;
        String delLabel = Component.translatable("gui.rpg_mounts.creator.btn.delete").getString();
        UIHelper.drawShadedButton(graphics, listX + 48, addY, 42, 14, hoverDel, 0xFF550000);
        graphics.drawString(this.font, delLabel, listX + 54, addY + 3, 0xFFFFFFFF, false);

        int copyY = addY + 18;
        boolean hoverCopy = mouseX >= listX && mouseX <= listX + listW && mouseY >= copyY && mouseY <= copyY + 14;
        String copyLabel = Component.translatable("gui.rpg_mounts.creator.btn.copy").getString();
        UIHelper.drawShadedButton(graphics, listX, copyY, listW, 14, hoverCopy, 0xFF3D2C1E);
        graphics.drawString(this.font, copyLabel, listX + (listW - this.font.width(copyLabel)) / 2, copyY + 3, 0xFFFFFFFF, false);

        if (selectedTemplate != null) {
            // Draw tabs dynamically
            List<TabBounds> tabBoundsList = calculateTabBounds(left, top);
            for (TabBounds tb : tabBoundsList) {
                boolean active = tb.id.equals(activeTab);
                boolean hoverTab = mouseX >= tb.x && mouseX <= tb.x + tb.w && mouseY >= tb.y && mouseY <= tb.y + tb.h;
                int color = active ? slotC : UIHelper.adjustBrightness(bgC, -15);
                UIHelper.drawShadedButton(graphics, tb.x, tb.y, tb.w, tb.h, hoverTab, color);
                graphics.drawString(this.font, tb.label, tb.x + 6, tb.y + 4, active ? textActiveC : textNormalC, false);
            }

            // Options Details Area
            int formX = left + 110;
            int formY = top + 42;
            int formW = (int) ((panelW - 120) * 0.6);
            int formH = panelH - 80;

            UIHelper.drawRecessedSlot(graphics, formX, formY, formW, formH, borderC, slotC);

            // RENDER ACTIVE TAB FORMS
            renderTabForm(graphics, formX, formY, formW, formH, mouseX, mouseY, textNormalC, textActiveC);

            // Right Panel (3D Viewport)
            int viewportX = formX + formW + 10;
            int viewportY = top + 42;
            int viewportW = panelW - 10 - (viewportX - left);
            int viewportH = panelH - 80;

            UIHelper.drawRecessedSlot(graphics, viewportX, viewportY, viewportW, viewportH, borderC, slotC);

            // Viewport Model Rendering
            int viewCenterX = viewportX + viewportW / 2;

            RPGMountEntity preview = getPreviewEntity();
            if (preview != null) {
                preview.setYRot(0.0f);
                preview.setYHeadRot(0.0f);
                preview.setXRot(0.0f);
                preview.refreshDimensions();

                // Auto-scale to fit the viewport based on dynamic bounding box
                float baseScale = Math.min((viewportH * 0.70f) / preview.getBbHeight(), (viewportW * 0.70f) / preview.getBbWidth());
                if (baseScale < 0.2F) {
                    baseScale = 0.2F;
                }
                int scaleFactor = (int) (baseScale * previewZoom);

                // Center vertically dynamically based on model height
                int centeredY = (viewportY + viewportH / 2) + (int) ((preview.getBbHeight() * scaleFactor) / 2);

                double scaleVal = this.minecraft.getWindow().getGuiScale();
                int scissorX = (int) (viewportX * scaleVal);
                int scissorY = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - (viewportY + viewportH)) * scaleVal);
                int scissorW = (int) (viewportW * scaleVal);
                int scissorH = (int) (viewportH * scaleVal);

                com.mojang.blaze3d.systems.RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
                renderMountPreview(graphics, viewCenterX, centeredY, scaleFactor, preview);
                com.mojang.blaze3d.systems.RenderSystem.disableScissor();

                // Draw Zoom Buttons
                int btnY = viewportY + viewportH - 14;
                int btnMinusX = viewportX + viewportW - 27;
                int btnPlusX = viewportX + viewportW - 14;

                boolean hoverMinus = mouseX >= btnMinusX && mouseX < btnMinusX + 12 && mouseY >= btnY && mouseY < btnY + 12;
                UIHelper.drawShadedButton(graphics, btnMinusX, btnY, 12, 12, hoverMinus, bgC);
                graphics.drawString(this.font, "-", btnMinusX + 4, btnY + 2, 0xFFFFFFFF, false);

                boolean hoverPlus = mouseX >= btnPlusX && mouseX < btnPlusX + 12 && mouseY >= btnY && mouseY < btnY + 12;
                UIHelper.drawShadedButton(graphics, btnPlusX, btnY, 12, 12, hoverPlus, bgC);
                graphics.drawString(this.font, "+", btnPlusX + 3, btnY + 2, 0xFFFFFFFF, false);

                // RENDER SEATING PERSPECTIVE DOTS OVERLAY
                if ("Seating & Rules".equals(activeTab)) {
                    for (int i = 0; i < selectedTemplate.seats.size(); i++) {
                        MountData.SeatOffset offset = selectedTemplate.seats.get(i);
                        
                        double yawRad = previewRotation * (Math.PI / 180.0);
                        double pitchRad = -20.0 * (Math.PI / 180.0);

                        double cosY = Math.cos(yawRad);
                        double sinY = Math.sin(yawRad);
                        double cosP = Math.cos(pitchRad);
                        double sinP = Math.sin(pitchRad);

                        double ox = -offset.x * selectedTemplate.scale;
                        double oy = offset.y * selectedTemplate.scale;
                        double oz = offset.z * selectedTemplate.scale;

                        double x1 = ox * cosY - oz * sinY;
                        double z1 = ox * sinY + oz * cosY;
                        double y1 = oy;

                        double rx = x1;
                        double ry = y1 * cosP - z1 * sinP;

                        int dotX = viewCenterX + (int) (rx * scaleFactor);
                        int dotY = centeredY - (int) (ry * scaleFactor);

                        // Highlight selected seat
                        int dotColor = (i == selectedSeatIndex) ? 0xFF00FF00 : 0xFFFF0000;
                        graphics.fill(dotX - 2, dotY - 2, dotX + 2, dotY + 2, dotColor);
                        UIHelper.drawOutline(graphics, dotX - 3, dotY - 3, 6, 6, 0xFFFFFFFF);
                    }
                }
            }
        }

        // Bottom Save & Discard Buttons
        int saveX = left + 110;
        int saveY = top + panelH - 30;
        boolean hoverSave = mouseX >= saveX && mouseX <= saveX + 140 && mouseY >= saveY && mouseY <= saveY + 18;
        String saveText = Component.translatable("gui.rpg_mounts.creator.save").getString();
        UIHelper.drawShadedButton(graphics, saveX, saveY, 140, 18, hoverSave, 0xFF005500);
        graphics.drawString(this.font, saveText, saveX + (140 - this.font.width(saveText)) / 2, saveY + 5, 0xFFFFFFFF, false);
 
        int discardX = left + 270;
        boolean hoverDiscard = mouseX >= discardX && mouseX <= discardX + 140 && mouseY >= saveY && mouseY <= saveY + 18;
        String discardText = Component.translatable("gui.rpg_mounts.creator.discard").getString();
        UIHelper.drawShadedButton(graphics, discardX, saveY, 140, 18, hoverDiscard, 0xFF550000);
        graphics.drawString(this.font, discardText, discardX + (140 - this.font.width(discardText)) / 2, saveY + 5, 0xFFFFFFFF, false);

        // RENDER ITEM SELECTOR OVERLAY IF OPEN
        if (isItemSelectorOpen) {
            renderItemSelectorPopup(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
        if (this.hoveredTooltip != null) {
            graphics.renderComponentTooltip(this.font, this.hoveredTooltip, mouseX, mouseY);
        }
    }

    private void renderTabForm(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY, int normalC, int activeC) {
        if (activeTab.equals("General")) {
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.general.mount_id", selectedTemplate.id), x + 8, y + 8, normalC, false);
            
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.general.display_name"), x + 8, y + 20, normalC, false);
            nameField.setX(x + 8);
            nameField.setY(y + 30);
            nameField.render(graphics, mouseX, mouseY, 0.0f);

            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.general.description"), x + 8, y + 46, normalC, false);
            descField.setX(x + 8);
            descField.setY(y + 56);
            descField.render(graphics, mouseX, mouseY, 0.0f);

            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.general.category"), x + 8, y + 102, normalC, false);
            int catBtnX = x + 8;
            int catBtnY = y + 112;
            graphics.fill(catBtnX, catBtnY, catBtnX + (w - 16), catBtnY + 14, 0xFF3C3C3C);
            UIHelper.drawOutline(graphics, catBtnX, catBtnY, w - 16, 14, 0xFF888888);
            String catLocalized = Component.translatable("gui.rpg_mounts.category." + selectedTemplate.category.toLowerCase()).getString();
            graphics.drawString(this.font, catLocalized, catBtnX + 6, catBtnY + 3, 0xFFFFFFFF, false);

            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.general.enhancer_slots"), x + 8, y + 130, normalC, false);
            drawPlusMinus(graphics, String.valueOf(selectedTemplate.enhancerSlots), x + 8, y + 140, w - 16, mouseX, mouseY);

            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.general.rarity"), x + 8, y + 156, normalC, false);
            int rarityBtnX = x + 8;
            int rarityBtnY = y + 166;
            graphics.fill(rarityBtnX, rarityBtnY, rarityBtnX + (w - 16), rarityBtnY + 14, 0xFF3C3C3C);
            UIHelper.drawOutline(graphics, rarityBtnX, rarityBtnY, w - 16, 14, 0xFF888888);
            String rarityVal = selectedTemplate.rarity != null ? selectedTemplate.rarity.toUpperCase() : "COMMON";
            int rarityColor = UIHelper.getRarityColor(rarityVal);
            String rarityLocalized = Component.translatable("gui.rpg_mounts.rarity." + rarityVal.toLowerCase()).getString();
            graphics.drawString(this.font, rarityLocalized, rarityBtnX + 6, rarityBtnY + 3, rarityColor, false);

        } else if (activeTab.equals("Model & Anims")) {
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.type"), x + 8, y + 8, normalC, false);
            int typeBtnX = x + 8;
            int typeBtnY = y + 18;
            graphics.fill(typeBtnX, typeBtnY, typeBtnX + 138, typeBtnY + 14, 0xFF3C3C3C);
            UIHelper.drawOutline(graphics, typeBtnX, typeBtnY, 138, 14, 0xFF888888);
            graphics.drawString(this.font, selectedTemplate.modelType.toUpperCase(), typeBtnX + 6, typeBtnY + 3, 0xFFFFFFFF, false);

            if (selectedTemplate.modelType.equals("mcmodel")) {
                int infoY = y + 36;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.mcmodel.header"), x + 8, infoY, 0xFFFFD700, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.mcmodel.step1"), x + 8, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.mcmodel.step2"), x + 8, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.mcmodel.step3"), x + 8, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.mcmodel.step4"), x + 8, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.mcmodel.step5"), x + 8, infoY, 0xFF55FFFF, false);
                infoY += 12;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.mcmodel.step6"), x + 8, infoY, 0xFF55FFFF, false);
            } else {
                if (selectedTemplate.modelType.equals("vanilla")) {
                    graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.vanilla_entity"), x + 8, y + 36, normalC, false);
                    int modelBtnX = x + 8;
                    int modelBtnY = y + 46;
                    graphics.fill(modelBtnX, modelBtnY, modelBtnX + 138, modelBtnY + 14, 0xFF3C3C3C);
                    UIHelper.drawOutline(graphics, modelBtnX, modelBtnY, 138, 14, 0xFF888888);
                    graphics.drawString(this.font, truncate(selectedTemplate.modelId, 18), modelBtnX + 6, modelBtnY + 3, 0xFFFFFFFF, false);
                } else if (selectedTemplate.modelType.equals("java")) {
                    graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.class_loc"), x + 8, y + 36, normalC, false);
                    modelIdField.setX(x + 8);
                    modelIdField.setY(y + 46);
                    modelIdField.render(graphics, mouseX, mouseY, 0.0f);
                } else {
                    graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.loc"), x + 8, y + 36, normalC, false);
                    modelIdField.setX(x + 8);
                    modelIdField.setY(y + 46);
                    modelIdField.render(graphics, mouseX, mouseY, 0.0f);
                }

                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.texture_loc"), x + 8, y + 62, normalC, false);
                textureField.setX(x + 8);
                textureField.setY(y + 72);
                textureField.render(graphics, mouseX, mouseY, 0.0f);

                if (selectedTemplate.modelType.equals("geckolib")) {
                    graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.anim_loc"), x + 8, y + 88, normalC, false);
                    animField.setX(x + 8);
                    animField.setY(y + 98);
                    animField.render(graphics, mouseX, mouseY, 0.0f);
                }

                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.scale"), x + 8, y + 114, normalC, false);
                drawPlusMinus(graphics, String.format("%.2fx", selectedTemplate.scale), x + 8, y + 124, 138, mouseX, mouseY);

                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.stamina_icon"), x + 8, y + 140, normalC, false);
                int iconBtnX = x + 8;
                int iconBtnY = y + 150;
                graphics.fill(iconBtnX, iconBtnY, iconBtnX + 80, iconBtnY + 14, 0xFF3C3C3C);
                UIHelper.drawOutline(graphics, iconBtnX, iconBtnY, 80, 14, 0xFF888888);
                String stamIconTypeStr = Component.translatable("gui.rpg_mounts.creator.model.stamina_type", selectedTemplate.staminaIconType + 1).getString();
                graphics.drawString(this.font, stamIconTypeStr, iconBtnX + 6, iconBtnY + 3, 0xFFFFFFFF, false);

                ResourceLocation STAMINA_TEX = new ResourceLocation("rpg_mounts", "textures/gui/stamina_bars.png");
                int texY = 0;
                if (selectedTemplate.category.equalsIgnoreCase("AQUATIC")) {
                    texY = 24;
                } else if (selectedTemplate.category.equalsIgnoreCase("FLYING")) {
                    texY = 48;
                }
                int texXEmpty = selectedTemplate.staminaIconType * 48;
                int texXFilled = selectedTemplate.staminaIconType * 48 + 24;

                graphics.blit(STAMINA_TEX, iconBtnX + 90, iconBtnY - 5, (float) texXEmpty, (float) texY, 24, 24, 256, 128);
                graphics.blit(STAMINA_TEX, iconBtnX + 116, iconBtnY - 5, (float) texXFilled, (float) texY, 24, 24, 256, 128);

                if (selectedTemplate.category.equalsIgnoreCase("FLYING")) {
                    graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.flight_particle"), x + 8, y + 166, normalC, false);
                    flightParticleField.setX(x + 8);
                    flightParticleField.setY(y + 176);
                    flightParticleField.render(graphics, mouseX, mouseY, 0.0f);
                } else if (selectedTemplate.category.equalsIgnoreCase("GROUND") || selectedTemplate.category.equalsIgnoreCase("SURFACE_WATER")) {
                    graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.model.ground_particle"), x + 8, y + 166, normalC, false);
                    groundParticleField.setX(x + 8);
                    groundParticleField.setY(y + 176);
                    groundParticleField.render(graphics, mouseX, mouseY, 0.0f);
                }
            }

            // HOVER TOOLTIPS
            if (selectedTemplate.modelType.equals("mcmodel")) {
                if (mouseX >= x + 8 && mouseX <= x + 8 + 138) {
                    if (mouseY >= y + 48 && mouseY <= y + 48 + 10) {
                        java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step1.tooltip1"));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step1.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step1.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY));
                        this.hoveredTooltip = tooltip;
                    } else if (mouseY >= y + 60 && mouseY <= y + 60 + 10) {
                        java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step2.tooltip1"));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step2.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step2.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY));
                        this.hoveredTooltip = tooltip;
                    } else if (mouseY >= y + 72 && mouseY <= y + 72 + 10) {
                        java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step3.tooltip1"));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step3.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                        this.hoveredTooltip = tooltip;
                    } else if (mouseY >= y + 84 && mouseY <= y + 84 + 10) {
                        java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step4.tooltip1"));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step4.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                        this.hoveredTooltip = tooltip;
                    } else if (mouseY >= y + 96 && mouseY <= y + 96 + 10) {
                        java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step5.tooltip1"));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step5.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step5.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY));
                        this.hoveredTooltip = tooltip;
                    } else if (mouseY >= y + 108 && mouseY <= y + 108 + 10) {
                        java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step6.tooltip1"));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step6.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                        tooltip.add(Component.translatable("gui.rpg_mounts.mcmodel.step6.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY));
                        this.hoveredTooltip = tooltip;
                    }
                }
            } else {
                if (mouseX >= x + 8 && mouseX <= x + 8 + 138 && mouseY >= y + 46 && mouseY <= y + 46 + 12) {
                    java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                    if (selectedTemplate.modelType.equals("vanilla")) {
                        tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.vanilla_id"));
                    } else if (selectedTemplate.modelType.equals("java")) {
                        tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.custom_class"));
                        tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.custom_class_expected").withStyle(net.minecraft.ChatFormatting.GRAY));
                        tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.custom_class_anim_expected").withStyle(net.minecraft.ChatFormatting.GRAY));
                    } else {
                        tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.custom_model"));
                        tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.custom_model_expected").withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                    this.hoveredTooltip = tooltip;
                } else if (mouseX >= x + 8 && mouseX <= x + 8 + 138 && mouseY >= y + 72 && mouseY <= y + 72 + 12) {
                    java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.texture"));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.texture_expected").withStyle(net.minecraft.ChatFormatting.GRAY));
                    this.hoveredTooltip = tooltip;
                } else if (selectedTemplate.modelType.equals("geckolib") && mouseX >= x + 8 && mouseX <= x + 8 + 138 && mouseY >= y + 98 && mouseY <= y + 98 + 12) {
                    java.util.List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.anim"));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.tooltip.anim_expected").withStyle(net.minecraft.ChatFormatting.GRAY));
                    this.hoveredTooltip = tooltip;
                }
            }

            // MODEL AUTOCOMPLETE SUGGESTIONS DROPDOWN
            if (showModelSuggestions && !activeSuggestions.isEmpty() && activeModelField != null) {
                int dropX = x + 8;
                int dropY = y + modelSuggestionYOffset;
                int dropW = w - 16;
                int rowH = 12;
                int maxVisibleRows = 5;
                int visibleRows = Math.min(maxVisibleRows, activeSuggestions.size());
                int dropH = visibleRows * rowH;

                if (modelSuggestionYOffset + dropH > 210) {
                    dropY = y + (modelSuggestionYOffset - 12) - dropH;
                }

                int borderC = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0);

                graphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF1C1C1C);
                UIHelper.drawOutline(graphics, dropX, dropY, dropW, dropH, borderC);

                for (int i = 0; i < visibleRows; i++) {
                    int idx = i + suggestionsScrollOffset;
                    if (idx >= activeSuggestions.size()) break;
                    String suggestion = activeSuggestions.get(idx);
                    int itemY = dropY + i * rowH;

                    boolean hovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY <= itemY + rowH;
                    int textColor = hovered ? activeC : normalC;
                    if (hovered) {
                        graphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + rowH, 0xFF2D2D2D);
                    }
                    
                    String display = suggestion;
                    int maxTextWidth = dropW - 8;
                    if (this.font.width(display) > maxTextWidth) {
                        display = this.font.plainSubstrByWidth(display, maxTextWidth - this.font.width("...")) + "...";
                    }
                    graphics.drawString(this.font, display, dropX + 4, itemY + 2, textColor, false);
                }
            }

        } else if (activeTab.equals("Stats")) {
            int rowY = y + 6;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.stats.hp"), x + 6, rowY + 3, normalC, false);
            drawPlusMinus(graphics, String.format("%d HP", (int)selectedTemplate.stats.maxHealth), x + 90, rowY, 90, mouseX, mouseY);

            rowY += 16;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.stats.speed"), x + 6, rowY + 3, normalC, false);
            drawPlusMinus(graphics, String.format("%.2f", selectedTemplate.stats.movementSpeed), x + 90, rowY, 90, mouseX, mouseY);

            rowY += 16;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.stats.swim_speed"), x + 6, rowY + 3, normalC, false);
            drawPlusMinus(graphics, String.format("%.2f", selectedTemplate.stats.swimSpeed), x + 90, rowY, 90, mouseX, mouseY);

            rowY += 16;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.stats.fly_speed"), x + 6, rowY + 3, normalC, false);
            drawPlusMinus(graphics, String.format("%.2f", selectedTemplate.stats.flySpeed), x + 90, rowY, 90, mouseX, mouseY);

            rowY += 16;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.stats.jump_height"), x + 6, rowY + 3, normalC, false);
            drawPlusMinus(graphics, String.format("%.2f", selectedTemplate.stats.jumpHeight), x + 90, rowY, 90, mouseX, mouseY);

            rowY += 16;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.stats.stamina"), x + 6, rowY + 3, normalC, false);
            drawPlusMinus(graphics, String.format("%d ST", (int)selectedTemplate.stats.maxStamina), x + 90, rowY, 90, mouseX, mouseY);

            rowY += 16;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.stats.recovery"), x + 6, rowY + 3, normalC, false);
            drawPlusMinus(graphics, String.format("%.1f/s", selectedTemplate.stats.staminaRecoveryRate), x + 90, rowY, 90, mouseX, mouseY);

        } else if (activeTab.equals("Combat")) {
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.combat.enable"), x + 8, y + 8, normalC, false);
            int comBtnX = x + 100;
            graphics.fill(comBtnX, y + 5, comBtnX + 50, y + 15, selectedTemplate.combat.enableCombat ? 0xFF006600 : 0xFF3D3D3D);
            UIHelper.drawOutline(graphics, comBtnX, y + 5, 50, 10, 0xFF888888);
            String statusText = Component.translatable(selectedTemplate.combat.enableCombat ? "gui.rpg_mounts.creator.status.on" : "gui.rpg_mounts.creator.status.off").getString();
            graphics.drawString(this.font, statusText, comBtnX + (50 - this.font.width(statusText)) / 2, y + 6, 0xFFFFFFFF, false);

            if (selectedTemplate.combat.enableCombat) {
                int rowY = y + 20;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.combat.strength"), x + 8, rowY, normalC, false);
                combatStrengthField.setX(x + 8);
                combatStrengthField.setY(rowY + 10);
                combatStrengthField.render(graphics, mouseX, mouseY, 0.0f);

                rowY += 26;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.combat.attack_speed"), x + 8, rowY, normalC, false);
                combatAttackSpeedField.setX(x + 8);
                combatAttackSpeedField.setY(rowY + 10);
                combatAttackSpeedField.render(graphics, mouseX, mouseY, 0.0f);

                rowY += 26;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.combat.ai"), x + 8, rowY, normalC, false);
                int aiBtnX = x + 8;
                int aiBtnY = rowY + 10;
                graphics.fill(aiBtnX, aiBtnY, aiBtnX + 138, aiBtnY + 14, 0xFF3C3C3C);
                UIHelper.drawOutline(graphics, aiBtnX, aiBtnY, 138, 14, 0xFF888888);
                graphics.drawString(this.font, selectedTemplate.combat.combatAi, aiBtnX + 6, aiBtnY + 3, 0xFFFFFFFF, false);
            }

        } else if (activeTab.equals("Abilities")) {
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.abilities.available"), x + 8, y + 8, normalC, false);
            int listBoxY = y + 20;
            int listBoxH = h - 28;
            int borderC = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0);
            graphics.fill(x + 8, listBoxY, x + w - 16, listBoxY + listBoxH, 0xFF121212);
            UIHelper.drawOutline(graphics, x + 8, listBoxY, w - 24, listBoxH, borderC);

            List<MountData.AbilityData> passives = new ArrayList<>();
            List<MountData.AbilityData> actives = new ArrayList<>();
            for (MountData.AbilityData ab : MountRegistry.customAbilities.values()) {
                if (ab.isPassive) {
                    passives.add(ab);
                } else {
                    actives.add(ab);
                }
            }
            passives.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            actives.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

            List<Object> items = new ArrayList<>();
            items.add(Component.translatable("gui.rpg_mounts.creator.abilities.passives"));
            items.addAll(passives);
            items.add(Component.translatable("gui.rpg_mounts.creator.abilities.actives"));
            items.addAll(actives);

            int visibleRows = 9;
            int rowHeight = 18;
            int itemX = x + 12;

            for (int i = 0; i < visibleRows; i++) {
                int idx = i + abilityScrollOffset;
                if (idx >= items.size()) break;
                Object item = items.get(idx);
                int rowY = listBoxY + 2 + i * rowHeight;

                if (item instanceof Component header) {
                    graphics.drawString(this.font, header.copy().withStyle(net.minecraft.ChatFormatting.YELLOW), itemX, rowY + 5, 0xFFFFFFFF, false);
                    graphics.fill(itemX + this.font.width(header) + 4, rowY + 9, x + w - 28, rowY + 10, 0xFF333333);
                } else if (item instanceof MountData.AbilityData ab) {
                    boolean hasAbility = selectedTemplate != null && selectedTemplate.availableAbilities.stream().anyMatch(a -> a.name.equalsIgnoreCase(ab.name));
                    
                    int checkboxX = itemX + 2;
                    int checkboxY = rowY + 4;
                    int checkboxSize = 10;
                    if (hasAbility) {
                        graphics.fill(checkboxX, checkboxY, checkboxX + checkboxSize, checkboxY + checkboxSize, 0xFF00AA00);
                        UIHelper.drawOutline(graphics, checkboxX, checkboxY, checkboxSize, checkboxSize, 0xFF00FF00);
                        graphics.fill(checkboxX + 3, checkboxY + 3, checkboxX + 7, checkboxY + 7, 0xFFFFFFFF);
                    } else {
                        graphics.fill(checkboxX, checkboxY, checkboxX + checkboxSize, checkboxY + checkboxSize, 0xFF222222);
                        UIHelper.drawOutline(graphics, checkboxX, checkboxY, checkboxSize, checkboxSize, 0xFF555555);
                    }

                    String baseKey = "ability.rpg_mounts." + ab.name.toLowerCase().replace(" ", "_");
                    Component titleComp = Component.translatable(baseKey);
                    String titleStr = titleComp.getString();
                    if (titleStr.equals(baseKey)) {
                        titleStr = ab.name;
                    }

                    graphics.drawString(this.font, truncate(titleStr, 18), itemX + 16, rowY + 5, 0xFFCCCCCC, false);

                    if (mouseX >= x + 8 && mouseX <= x + w - 16 && mouseY >= rowY && mouseY <= rowY + rowHeight) {
                        List<Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.literal("§6" + titleStr));
                        tooltip.add(Component.translatable(ab.isPassive ? "gui.rpg_mounts.creator.ability.passive" : "gui.rpg_mounts.creator.ability.active", ab.type).withStyle(net.minecraft.ChatFormatting.YELLOW));
                        if (!ab.isPassive) {
                            tooltip.add(Component.translatable("gui.rpg_mounts.creator.ability.stamina_cost", ab.staminaCost).withStyle(net.minecraft.ChatFormatting.YELLOW));
                            tooltip.add(Component.translatable("gui.rpg_mounts.creator.ability.cooldown", ab.cooldownTicks).withStyle(net.minecraft.ChatFormatting.YELLOW));
                        }
                        if (ab.damage > 0) {
                            tooltip.add(Component.translatable("gui.rpg_mounts.creator.ability.damage", ab.damage, ab.damageType).withStyle(net.minecraft.ChatFormatting.YELLOW));
                        }
                        
                        Component descComp = Component.translatable(baseKey + ".desc");
                        String descStr = descComp.getString();
                        if (descStr.equals(baseKey + ".desc")) {
                            descStr = ab.description;
                        }
                        if (!descStr.isEmpty()) {
                            tooltip.add(Component.literal("§7" + descStr));
                        }
                        
                        if (ab.allowedCategories != null && !ab.allowedCategories.isEmpty()) {
                            tooltip.add(Component.translatable("gui.rpg_mounts.creator.ability.allowed_categories", String.join(", ", ab.allowedCategories)).withStyle(net.minecraft.ChatFormatting.GRAY));
                        }
                        if (ab.allowedMountIds != null && !ab.allowedMountIds.isEmpty()) {
                            tooltip.add(Component.translatable("gui.rpg_mounts.creator.ability.allowed_mounts", String.join(", ", ab.allowedMountIds)).withStyle(net.minecraft.ChatFormatting.GRAY));
                        }
                        this.hoveredTooltip = tooltip;
                    }
                }
            }

            if (items.size() > visibleRows) {
                int scrollbarX = x + w - 22;
                int scrollbarY = listBoxY + 2;
                int scrollbarW = 4;
                int scrollbarH = listBoxH - 4;
                graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0xFF3C3C3C);

                int thumbH = Math.max(10, scrollbarH * visibleRows / items.size());
                int thumbY = scrollbarY + (scrollbarH - thumbH) * abilityScrollOffset / (items.size() - visibleRows);
                graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFF888888);
            }

        } else if (activeTab.equals("Sounds & FX")) {
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.sounds.ambient"), x + 8, y + 6, normalC, false);
            soundAmbientField.setX(x + 8);
            soundAmbientField.setY(y + 16);
            soundAmbientField.render(graphics, mouseX, mouseY, 0.0f);
            drawPlaySoundButton(graphics, x + w - 18, y + 16, mouseX, mouseY);

            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.sounds.step"), x + 8, y + 30, normalC, false);
            soundStepField.setX(x + 8);
            soundStepField.setY(y + 40);
            soundStepField.render(graphics, mouseX, mouseY, 0.0f);
            drawPlaySoundButton(graphics, x + w - 18, y + 40, mouseX, mouseY);

            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.sounds.hurt"), x + 8, y + 54, normalC, false);
            soundHurtField.setX(x + 8);
            soundHurtField.setY(y + 64);
            soundHurtField.render(graphics, mouseX, mouseY, 0.0f);
            drawPlaySoundButton(graphics, x + w - 18, y + 64, mouseX, mouseY);

            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.sounds.death"), x + 8, y + 78, normalC, false);
            soundDeathField.setX(x + 8);
            soundDeathField.setY(y + 88);
            soundDeathField.render(graphics, mouseX, mouseY, 0.0f);
            drawPlaySoundButton(graphics, x + w - 18, y + 88, mouseX, mouseY);

            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.sounds.spawn_particle"), x + 8, y + 102, normalC, false);
            spawnParticleField.setX(x + 8);
            spawnParticleField.setY(y + 112);
            spawnParticleField.render(graphics, mouseX, mouseY, 0.0f);

            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.sounds.spawn_sound"), x + 8, y + 126, normalC, false);
            spawnSoundField.setX(x + 8);
            spawnSoundField.setY(y + 136);
            spawnSoundField.render(graphics, mouseX, mouseY, 0.0f);
            drawPlaySoundButton(graphics, x + w - 18, y + 136, mouseX, mouseY);

            // HOVER TOOLTIPS FOR SOUNDS & FX
            if (mouseX >= x + 8 && mouseX <= x + 8 + 138) {
                if (mouseY >= y + 16 && mouseY <= y + 16 + 12) {
                    java.util.List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.ambient.tooltip1"));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.ambient.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.ambient.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY));
                    this.hoveredTooltip = tooltip;
                } else if (mouseY >= y + 40 && mouseY <= y + 40 + 12) {
                    java.util.List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.step.tooltip1"));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.step.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.step.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY));
                    this.hoveredTooltip = tooltip;
                } else if (mouseY >= y + 64 && mouseY <= y + 64 + 12) {
                    java.util.List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.hurt.tooltip1"));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.hurt.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.hurt.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY));
                    this.hoveredTooltip = tooltip;
                } else if (mouseY >= y + 88 && mouseY <= y + 88 + 12) {
                    java.util.List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.death.tooltip1"));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.death.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.death.tooltip3").withStyle(net.minecraft.ChatFormatting.GRAY));
                    this.hoveredTooltip = tooltip;
                } else if (mouseY >= y + 112 && mouseY <= y + 112 + 12) {
                    java.util.List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.spawn_particle.tooltip1"));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.spawn_particle.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                    this.hoveredTooltip = tooltip;
                } else if (mouseY >= y + 136 && mouseY <= y + 136 + 12) {
                    java.util.List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.spawn_sound.tooltip1"));
                    tooltip.add(Component.translatable("gui.rpg_mounts.creator.sounds.spawn_sound.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
                    this.hoveredTooltip = tooltip;
                }
            }

            // SOUND AUTOCOMPLETE SUGGESTIONS DROPDOWN
            if (showSoundSuggestions && !activeSuggestions.isEmpty() && activeSoundField != null) {
                int dropX = x + 8;
                int dropY = y + soundSuggestionYOffset;
                int dropW = w - 16;
                int rowH = 12;
                int maxVisibleRows = 5;
                int visibleRows = Math.min(maxVisibleRows, activeSuggestions.size());
                int dropH = visibleRows * rowH;

                if (soundSuggestionYOffset + dropH > 210) {
                    dropY = y + (soundSuggestionYOffset - 12) - dropH;
                }

                int borderC = RPGWaypointsIntegration.getThemeColor("panelBorder", 0xFFDFD0A0);

                graphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF1C1C1C);
                UIHelper.drawOutline(graphics, dropX, dropY, dropW, dropH, borderC);

                for (int i = 0; i < visibleRows; i++) {
                    int idx = i + suggestionsScrollOffset;
                    if (idx >= activeSuggestions.size()) break;
                    String suggestion = activeSuggestions.get(idx);
                    int itemY = dropY + i * rowH;

                    boolean hovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY <= itemY + rowH;
                    int textColor = hovered ? activeC : normalC;
                    if (hovered) {
                        graphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + rowH, 0xFF2D2D2D);
                    }
                    
                    String display = suggestion;
                    int maxTextWidth = dropW - 8;
                    if (this.font.width(display) > maxTextWidth) {
                        display = this.font.plainSubstrByWidth(display, maxTextWidth - this.font.width("...")) + "...";
                    }
                    graphics.drawString(this.font, display, dropX + 4, itemY + 2, textColor, false);
                }
            }

        } else if (activeTab.equals("Seating & Rules")) {
            // Seat Count
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.seating.total_seats"), x + 8, y + 8, normalC, false);
            drawPlusMinus(graphics, String.valueOf(selectedTemplate.seats.size()), x + 100, y + 5, 80, mouseX, mouseY);

            // Active seat coordinates adjusters
            if (!selectedTemplate.seats.isEmpty()) {
                selectedSeatIndex = Math.min(selectedSeatIndex, selectedTemplate.seats.size() - 1);
                MountData.SeatOffset seat = selectedTemplate.seats.get(selectedSeatIndex);

                int rowY = y + 22;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.seating.selector"), x + 8, rowY + 3, normalC, false);
                drawSeatSelector(graphics, Component.translatable("gui.rpg_mounts.creator.seating.seat_label", selectedSeatIndex + 1).getString(), x + 100, rowY, 80);

                rowY += 16;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.seating.seat_x"), x + 8, rowY + 3, normalC, false);
                drawPlusMinus(graphics, String.format("%.2f", seat.x), x + 100, rowY, 80, mouseX, mouseY);

                rowY += 16;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.seating.seat_y"), x + 8, rowY + 3, normalC, false);
                drawPlusMinus(graphics, String.format("%.2f", seat.y), x + 100, rowY, 80, mouseX, mouseY);

                rowY += 16;
                graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.seating.seat_z"), x + 8, rowY + 3, normalC, false);
                drawPlusMinus(graphics, String.format("%.2f", seat.z), x + 100, rowY, 80, mouseX, mouseY);
            }

            // Steve rider toggle
            int toggleY = y + 94;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.seating.show_riders"), x + 8, toggleY + 2, normalC, false);
            int tgX = x + 100;
            graphics.fill(tgX, toggleY, tgX + 80, toggleY + 12, showSteve ? 0xFF006600 : 0xFF3D3D3D);
            UIHelper.drawOutline(graphics, tgX, toggleY, 80, 12, 0xFF888888);
            String showHideText = Component.translatable(showSteve ? "gui.rpg_mounts.creator.seating.show" : "gui.rpg_mounts.creator.seating.hide").getString();
            graphics.drawString(this.font, showHideText, tgX + (80 - this.font.width(showHideText)) / 2, toggleY + 2, 0xFFFFFFFF, false);

            // Item selectors trigger: allowed cargo
            int cargoY = y + 110;
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.seating.cargo_config"), x + 8, cargoY + 2, normalC, false);
            int cargoBtnX = x + 100;
            graphics.fill(cargoBtnX, cargoY, cargoBtnX + 80, cargoY + 12, 0xFF3C3C3C);
            UIHelper.drawOutline(graphics, cargoBtnX, cargoY, 80, 12, 0xFF777777);
            graphics.drawString(this.font, Component.translatable("gui.rpg_mounts.creator.seating.configure_btn").getString(), cargoBtnX + 12, cargoY + 2, 0xFFFFFFFF, false);
        }
    }

    private void drawSeatSelector(GuiGraphics graphics, String label, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 12, 0xFF3C3C3C);
        UIHelper.drawOutline(graphics, x, y, width, 12, 0xFF777777);

        // [<] button
        graphics.drawString(this.font, "<", x + 4, y + 2, 0xFFFFFFFF, false);
        // label
        graphics.drawString(this.font, label, x + (width - this.font.width(label)) / 2, y + 2, 0xFFFFFFFF, false);
        // [>] button
        graphics.drawString(this.font, ">", x + width - 10, y + 2, 0xFFFFFFFF, false);
    }

    private void drawPlusMinus(GuiGraphics graphics, String value, int x, int y, int width, int mouseX, int mouseY) {
        int btnW = 12;
        int btnH = 12;
        
        graphics.fill(x, y, x + width, y + 12, 0xFF3C3C3C);
        UIHelper.drawOutline(graphics, x, y, width, 12, 0xFF888888);

        // Minus
        graphics.fill(x + 1, y + 1, x + btnW, y + 11, 0xFF2D2D2D);
        graphics.drawString(this.font, "-", x + 4, y + 2, 0xFFFFFFFF, false);

        // Value
        graphics.drawString(this.font, value, x + (width - this.font.width(value)) / 2, y + 2, 0xFFFFFFFF, false);

        // Plus
        graphics.fill(x + width - btnW, y + 1, x + width - 1, y + 11, 0xFF2D2D2D);
        graphics.drawString(this.font, "+", x + width - 9, y + 2, 0xFFFFFFFF, false);
    }

    // ITEM SELECTOR POPUP OVERLAY
    private void renderItemSelectorPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        int w = 180;
        int h = 180;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // Dim background
        graphics.fill(0, 0, this.width, this.height, 0x90000000);

        // Popup Box
        graphics.fill(x, y, x + w, y + h, 0xFF202020);
        UIHelper.drawOutline(graphics, x, y, w, h, 0xFFDFD0A0);

        // Title
        graphics.drawString(this.font, "§6" + Component.translatable("gui.rpg_mounts.creator.cargo.select_title").getString(), x + 10, y + 8, 0xFFFFFFFF, false);

        // Render SearchBox
        itemSearchBox.setX(x + 10);
        itemSearchBox.setY(y + 20);
        itemSearchBox.render(graphics, mouseX, mouseY, 0.0f);

        // Load filtered items
        String query = itemSearchBox.getValue().toLowerCase();
        filteredItems.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            String key = BuiltInRegistries.ITEM.getKey(item).toString();
            if (key.contains(query) && (key.contains("chest") || key.contains("barrel") || key.contains("box"))) {
                filteredItems.add(item);
            }
        }

        // Render scrollable grid of icons
        int gridX = x + 10;
        int gridY = y + 36;
        int rows = 6;
        int cols = 8;
        
        int idx = itemScrollOffset * cols;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int slotX = gridX + c * 20;
                int slotY = gridY + r * 20;

                if (idx < filteredItems.size()) {
                    Item item = filteredItems.get(idx);
                    ItemStack stack = new ItemStack(item);
                    graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF141414);
                    UIHelper.drawOutline(graphics, slotX, slotY, 18, 18, 0xFF555555);
                    graphics.renderItem(stack, slotX + 1, slotY + 1);

                    // hover highlight
                    if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= slotY && mouseY <= slotY + 18) {
                        graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0x40FFFFFF);
                    }
                    idx++;
                } else {
                    graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF181818);
                    UIHelper.drawOutline(graphics, slotX, slotY, 18, 18, 0xFF333333);
                }
            }
        }

        // Close button at bottom
        int closeW = 60;
        int closeX = x + (w - closeW) / 2;
        int closeY = y + h - 20;
        graphics.fill(closeX, closeY, closeX + closeW, closeY + 14, 0xFF550000);
        UIHelper.drawOutline(graphics, closeX, closeY, closeW, 14, 0xFF888888);
        String closeText = Component.translatable("gui.rpg_mounts.creator.btn.close").getString();
        graphics.drawString(this.font, closeText, closeX + (closeW - this.font.width(closeText)) / 2, closeY + 3, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isItemSelectorOpen) {
            int w = 180;
            int h = 180;
            int x = (this.width - w) / 2;
            int y = (this.height - h) / 2;

            if (itemSearchBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            // Click close button
            int closeW = 60;
            int closeX = x + (w - closeW) / 2;
            int closeY = y + h - 20;
            if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + 14) {
                isItemSelectorOpen = false;
                updateWidgetsVisibility();
                return true;
            }

            // Click items grid
            int gridX = x + 10;
            int gridY = y + 36;
            int cols = 8;
            int idx = itemScrollOffset * cols;

            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < cols; c++) {
                    int slotX = gridX + c * 20;
                    int slotY = gridY + r * 20;
                    if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= slotY && mouseY <= slotY + 18) {
                        if (idx < filteredItems.size()) {
                            Item item = filteredItems.get(idx);
                            String itemKey = BuiltInRegistries.ITEM.getKey(item).toString();
                            
                            // Map default capacities
                            int cap = itemKey.contains("shulker") ? 27 : (itemKey.contains("barrel") ? 18 : 9);
                            if (selectedTemplate != null) {
                                selectedTemplate.allowed_cargo_map.put(itemKey, cap);
                            }
                            isItemSelectorOpen = false;
                            updateWidgetsVisibility();
                            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            return true;
                        }
                    }
                    idx++;
                }
            }
            return true; // Blocks background clicks when popup is open
        }

        int panelW = this.panelW;
        int panelH = this.panelH;
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        if (activeTab.equals("Sounds & FX") && button == 0) {
            int formX = left + 110;
            int formY = top + 42;
            int w = (int) ((panelW - 120) * 0.6);
            int btnX = formX + w - 18;
            
            if (mouseX >= btnX && mouseX < btnX + 12) {
                String soundId = null;
                if (mouseY >= formY + 16 && mouseY < formY + 16 + 12) {
                    soundId = soundAmbientField.getValue();
                } else if (mouseY >= formY + 40 && mouseY < formY + 40 + 12) {
                    soundId = soundStepField.getValue();
                } else if (mouseY >= formY + 64 && mouseY < formY + 64 + 12) {
                    soundId = soundHurtField.getValue();
                } else if (mouseY >= formY + 88 && mouseY < formY + 88 + 12) {
                    soundId = soundDeathField.getValue();
                } else if (mouseY >= formY + 136 && mouseY < formY + 136 + 12) {
                    soundId = spawnSoundField.getValue();
                }
                
                if (soundId != null && !soundId.isEmpty()) {
                    try {
                        net.minecraft.resources.ResourceLocation resLoc = new net.minecraft.resources.ResourceLocation(soundId);
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(resLoc), 1.0F));
                    } catch (Exception e) {
                        // ignore malformed resource location
                    }
                    return true;
                }
            }
        }

        if (showModelSuggestions && !activeSuggestions.isEmpty() && activeTab.equals("Model & Anims") && activeModelField != null) {
            int formX = left + 110;
            int formY = top + 42;
            int dropX = formX + 8;
            int dropY = formY + modelSuggestionYOffset;
            int rowH = 12;
            int maxVisibleRows = 5;
            int visibleRows = Math.min(maxVisibleRows, activeSuggestions.size());
            int dropH = visibleRows * rowH;

            if (modelSuggestionYOffset + dropH > 210) {
                dropY = formY + (modelSuggestionYOffset - 12) - dropH;
            }

            int formW = (int) ((this.panelW - 120) * 0.6);
            int dropW = formW - 16;
            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH) {
                int clickedRow = (int) ((mouseY - dropY) / rowH);
                int idx = clickedRow + suggestionsScrollOffset;
                if (idx < activeSuggestions.size()) {
                    String selected = activeSuggestions.get(idx);
                    
                    if (activeModelField == modelIdField) {
                        modelIdField.setValue(selected);
                        selectedTemplate.modelId = selected; // Keep backing field updated!

                        if (selected.startsWith("minecraft:")) {
                            selectedTemplate.modelType = "vanilla";
                            selectedTemplate.texturePath = "";
                            selectedTemplate.animationPath = "";
                            textureField.setValue("");
                            animField.setValue("");
                        } else {
                            java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
                            java.io.File folder = new java.io.File(baseDir, "Mounts/Unpacked/" + selected);
                            boolean hasJava = false;
                            if (folder.exists() && folder.isDirectory()) {
                                java.io.File[] files = folder.listFiles();
                                if (files != null) {
                                    for (java.io.File f : files) {
                                        if (f.getName().endsWith(".java")) {
                                            hasJava = true;
                                            break;
                                        }
                                    }
                                }
                             }
                            selectedTemplate.modelType = hasJava ? "java" : "geckolib";
                            selectedTemplate.texturePath = selected + ".png";
                            textureField.setValue(selected + ".png");
                            if (selectedTemplate.modelType.equals("geckolib")) {
                                selectedTemplate.animationPath = selected + ".animation.json";
                                animField.setValue(selected + ".animation.json");
                            } else {
                                selectedTemplate.animationPath = "";
                                animField.setValue("");
                            }
                        }
                        selectedTemplate.resetDimensions();
                    } else if (activeModelField == flightParticleField) {
                        flightParticleField.setValue(selected);
                        selectedTemplate.flightParticle = selected;
                    } else if (activeModelField == groundParticleField) {
                        groundParticleField.setValue(selected);
                        selectedTemplate.groundParticle = selected;
                    }

                    showModelSuggestions = false;
                    activeModelField = null;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            } else {
                showModelSuggestions = false;
                activeModelField = null;
            }
        }

        if (showSoundSuggestions && !activeSuggestions.isEmpty() && activeTab.equals("Sounds & FX") && activeSoundField != null) {
            int formX = left + 110;
            int formY = top + 42;
            int dropX = formX + 8;
            int dropY = formY + soundSuggestionYOffset;
            int rowH = 12;
            int maxVisibleRows = 5;
            int visibleRows = Math.min(maxVisibleRows, activeSuggestions.size());
            int dropH = visibleRows * rowH;

            if (soundSuggestionYOffset + dropH > 210) {
                dropY = formY + (soundSuggestionYOffset - 12) - dropH;
            }

            int formW = (int) ((this.panelW - 120) * 0.6);
            int dropW = formW - 16;
            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH) {
                int clickedRow = (int) ((mouseY - dropY) / rowH);
                int idx = clickedRow + suggestionsScrollOffset;
                if (idx < activeSuggestions.size()) {
                    String selected = activeSuggestions.get(idx);
                    activeSoundField.setValue(selected);
                    
                    if (selectedTemplate.sounds != null) {
                        selectedTemplate.sounds.ambient = soundAmbientField.getValue();
                        selectedTemplate.sounds.step = soundStepField.getValue();
                        selectedTemplate.sounds.hurt = soundHurtField.getValue();
                        selectedTemplate.sounds.death = soundDeathField.getValue();
                    }
                    if (selectedTemplate.spawnEffects != null) {
                        selectedTemplate.spawnEffects.sound = spawnSoundField.getValue();
                        selectedTemplate.spawnEffects.particle = spawnParticleField.getValue();
                    }
                    
                    showSoundSuggestions = false;
                    activeSoundField = null;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            } else {
                showSoundSuggestions = false;
                activeSoundField = null;
            }
        }

        // Check Zoom Buttons click
        if (selectedTemplate != null) {
            int viewportX = left + 310;
            int viewportY = top + 42;
            int viewportW = panelW - 320;
            int viewportH = panelH - 80;
            int btnY = viewportY + viewportH - 14;
            int btnMinusX = viewportX + viewportW - 27;
            int btnPlusX = viewportX + viewportW - 14;

            if (button == 0) {
                if (mouseX >= btnMinusX && mouseX < btnMinusX + 12 && mouseY >= btnY && mouseY < btnY + 12) {
                    previewZoom = Math.max(0.2f, previewZoom - 0.1f);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                if (mouseX >= btnPlusX && mouseX < btnPlusX + 12 && mouseY >= btnY && mouseY < btnY + 12) {
                    previewZoom = Math.min(4.0f, previewZoom + 0.1f);
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }

        int listX = left + 10;
        int listY = top + 25;
        int listW = 90;
        int listH = panelH - 72;

        // Click sidebar templates list
        int itemY = listY + 5;
        for (MountData m : templatesList) {
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= itemY && mouseY <= itemY + 12) {
                saveTextFieldsToActiveTemplate(); // Save current before switching!
                selectedTemplate = m;
                previewZoom = 1.0f; // Reset zoom on template change
                updateFieldValues();
                updateWidgetsVisibility();
                updateDummyRiders();
                return true;
            }
            itemY += 12;
        }

        // Add Mount template click
        int addY = listY + listH + 4;
        if (mouseX >= listX && mouseX <= listX + 42 && mouseY >= addY && mouseY <= addY + 14) {
            saveTextFieldsToActiveTemplate(); // Save current before switching!
            String newId = "new_mount_" + (templatesList.size() + 1);
            MountData m = new MountData();
            m.id = newId;
            m.name = "Custom Mount";
            m.category = "GROUND";
            m.modelType = "vanilla";
            m.modelId = "minecraft:wolf";
            m.scale = 1.0f;
            m.stats.maxHealth = 20.0;
            m.stats.movementSpeed = 0.25;
            
            MountRegistry.loadedTemplates.put(newId, m);
            templatesList.add(m);
            selectedTemplate = m;
            updateFieldValues();
            updateWidgetsVisibility();
            updateDummyRiders();
            return true;
        }

        // Delete Mount template click
        if (mouseX >= listX + 48 && mouseX <= listX + listW && mouseY >= addY && mouseY <= addY + 14) {
            if (selectedTemplate != null) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUtf(selectedTemplate.id);
                NetworkManager.sendToServer(ModPackets.C2S_DELETE_TEMPLATE, buf);
                
                MountRegistry.loadedTemplates.remove(selectedTemplate.id);
                templatesList.remove(selectedTemplate);
                selectedTemplate = templatesList.isEmpty() ? null : templatesList.get(0);
                updateFieldValues();
                updateWidgetsVisibility();
                updateDummyRiders();
            }
            return true;
        }

        // Copy Mount template click
        int copyY = addY + 18;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= copyY && mouseY <= copyY + 14) {
            if (selectedTemplate != null) {
                saveTextFieldsToActiveTemplate();
                
                String baseId = selectedTemplate.id + "_copy";
                String newId = baseId;
                int counter = 1;
                while (MountRegistry.loadedTemplates.containsKey(newId)) {
                    newId = baseId + counter;
                    counter++;
                }
                
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String rawJson = gson.toJson(selectedTemplate);
                MountData cloned = gson.fromJson(rawJson, MountData.class);
                cloned.id = newId;
                cloned.name = selectedTemplate.name + " Copy";
                
                MountRegistry.loadedTemplates.put(newId, cloned);
                templatesList.add(cloned);
                selectedTemplate = cloned;
                
                String clonedJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(cloned);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUtf(clonedJson);
                NetworkManager.sendToServer(ModPackets.C2S_SAVE_TEMPLATE, buf);
                
                updateFieldValues();
                updateWidgetsVisibility();
                updateDummyRiders();
            }
            return true;
        }

        if (selectedTemplate != null) {
            // Horizontal Tabs Click
            List<TabBounds> tabBoundsList = calculateTabBounds(left, top);
            for (TabBounds tb : tabBoundsList) {
                if (mouseX >= tb.x && mouseX <= tb.x + tb.w && mouseY >= tb.y && mouseY <= tb.y + tb.h) {
                    saveTextFieldsToActiveTemplate();
                    activeTab = tb.id;
                    updateFieldValues();
                    updateWidgetsVisibility();
                    return true;
                }
            }

            int formX = left + 110;
            int formY = top + 42;
            int formW = (int) ((panelW - 120) * 0.6);

            // Handle Tab fields clicks
            if (activeTab.equals("General")) {
                // Category click cycle
                int catBtnX = formX + 8;
                int catBtnY = formY + 112;
                if (mouseX >= catBtnX && mouseX <= catBtnX + (formW - 16) && mouseY >= catBtnY && mouseY <= catBtnY + 14) {
                    String curr = selectedTemplate.category;
                    if (curr.equalsIgnoreCase("GROUND")) selectedTemplate.category = "AQUATIC";
                    else if (curr.equalsIgnoreCase("AQUATIC")) selectedTemplate.category = "SURFACE_WATER";
                    else if (curr.equalsIgnoreCase("SURFACE_WATER")) selectedTemplate.category = "FLYING";
                    else selectedTemplate.category = "GROUND";
                    return true;
                }

                // Enhancer Slots adjusts
                int slotsY = formY + 140;
                if (mouseY >= slotsY && mouseY <= slotsY + 12) {
                    if (mouseX >= formX + 8 && mouseX <= formX + 20) {
                        selectedTemplate.enhancerSlots = Math.max(1, selectedTemplate.enhancerSlots - 1);
                        return true;
                    } else if (mouseX >= formX + formW - 20 && mouseX <= formX + formW - 8) {
                        selectedTemplate.enhancerSlots = Math.min(8, selectedTemplate.enhancerSlots + 1);
                        return true;
                    }
                }

                // Rarity click cycle
                int rarityBtnX = formX + 8;
                int rarityBtnY = formY + 166;
                if (mouseX >= rarityBtnX && mouseX <= rarityBtnX + (formW - 16) && mouseY >= rarityBtnY && mouseY <= rarityBtnY + 14) {
                    String curr = selectedTemplate.rarity != null ? selectedTemplate.rarity.toUpperCase() : "COMMON";
                    String nextRarity;
                    switch (curr) {
                        case "COMMON": nextRarity = "UNCOMMON"; break;
                        case "UNCOMMON": nextRarity = "RARE"; break;
                        case "RARE": nextRarity = "EPIC"; break;
                        case "EPIC": nextRarity = "LEGENDARY"; break;
                        case "LEGENDARY":
                        default: nextRarity = "COMMON"; break;
                    }
                    selectedTemplate.rarity = nextRarity;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }

            } else if (activeTab.equals("Model & Anims")) {
                // Model Type click cycle
                int typeBtnX = formX + 8;
                int typeBtnY = formY + 18;
                if (mouseX >= typeBtnX && mouseX <= typeBtnX + 138 && mouseY >= typeBtnY && mouseY <= typeBtnY + 14) {
                    saveTextFieldsToActiveTemplate(); // Save current fields first!
                    
                    String type = selectedTemplate.modelType;
                    if (type.equalsIgnoreCase("vanilla")) {
                        selectedTemplate.modelType = "geckolib";
                        if (selectedTemplate.modelId.startsWith("minecraft:")) {
                            selectedTemplate.modelId = "custom_mount";
                        }
                    } else if (type.equalsIgnoreCase("geckolib")) {
                        selectedTemplate.modelType = "mcmodel";
                    } else if (type.equalsIgnoreCase("mcmodel")) {
                        selectedTemplate.modelType = "java";
                    } else {
                        selectedTemplate.modelType = "vanilla";
                        if (!selectedTemplate.modelId.startsWith("minecraft:")) {
                            selectedTemplate.modelId = "minecraft:horse";
                        }
                    }
                    selectedTemplate.resetDimensions();
                    updateFieldValues();
                    updateWidgetsVisibility();
                    return true;
                }

                // Model ID cycling (vanilla)
                if (selectedTemplate.modelType.equals("vanilla")) {
                    int modelBtnX = formX + 8;
                    int modelBtnY = formY + 46;
                    if (mouseX >= modelBtnX && mouseX <= modelBtnX + 138 && mouseY >= modelBtnY && mouseY <= modelBtnY + 14) {
                        int index = 0;
                        String[] vanillaList = getVanillaModels();
                        for (int i = 0; i < vanillaList.length; i++) {
                            if (vanillaList[i].equals(selectedTemplate.modelId)) {
                                index = i;
                                break;
                            }
                        }
                        index = (index + 1) % vanillaList.length;
                        selectedTemplate.modelId = vanillaList[index];
                        modelIdField.setValue(selectedTemplate.modelId); // Keep in sync!
                        selectedTemplate.resetDimensions();
                        return true;
                    }
                }

                if (!selectedTemplate.modelType.equals("mcmodel")) {
                    // Scale adjuster clicks
                    int scaleY = formY + 124;
                    if (mouseY >= scaleY && mouseY <= scaleY + 12) {
                        if (mouseX >= formX + 8 && mouseX <= formX + 20) {
                            selectedTemplate.scale = Math.max(0.1f, selectedTemplate.scale - 0.05f);
                            return true;
                        } else if (mouseX >= formX + 134 && mouseX <= formX + 146) {
                            selectedTemplate.scale = Math.min(6.0f, selectedTemplate.scale + 0.05f);
                            return true;
                        }
                    }

                    // Stamina Icon cycle clicks
                    int iconBtnX = formX + 8;
                    int iconBtnY = formY + 150;
                    if (mouseX >= iconBtnX && mouseX <= iconBtnX + 80 && mouseY >= iconBtnY && mouseY <= iconBtnY + 14) {
                        selectedTemplate.staminaIconType = (selectedTemplate.staminaIconType + 1) % 5;
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }

            } else if (activeTab.equals("Stats")) {
                int rowY = formY + 6;
                // Max HP
                if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 90 && mouseX <= formX + 180) {
                    if (mouseX <= formX + 102) selectedTemplate.stats.maxHealth = Math.max(5.0, selectedTemplate.stats.maxHealth - 5.0);
                    else if (mouseX >= formX + 168) selectedTemplate.stats.maxHealth = Math.min(1000.0, selectedTemplate.stats.maxHealth + 5.0);
                    return true;
                }
                // Speed
                rowY += 16;
                if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 90 && mouseX <= formX + 180) {
                    if (mouseX <= formX + 102) selectedTemplate.stats.movementSpeed = Math.max(0.02, selectedTemplate.stats.movementSpeed - 0.02);
                    else if (mouseX >= formX + 168) selectedTemplate.stats.movementSpeed = Math.min(1.5, selectedTemplate.stats.movementSpeed + 0.02);
                    return true;
                }
                // Swim Speed
                rowY += 16;
                if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 90 && mouseX <= formX + 180) {
                    if (mouseX <= formX + 102) selectedTemplate.stats.swimSpeed = Math.max(0.02, selectedTemplate.stats.swimSpeed - 0.02);
                    else if (mouseX >= formX + 168) selectedTemplate.stats.swimSpeed = Math.min(1.5, selectedTemplate.stats.swimSpeed + 0.02);
                    return true;
                }
                // Fly Speed
                rowY += 16;
                if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 90 && mouseX <= formX + 180) {
                    if (mouseX <= formX + 102) selectedTemplate.stats.flySpeed = Math.max(0.02, selectedTemplate.stats.flySpeed - 0.02);
                    else if (mouseX >= formX + 168) selectedTemplate.stats.flySpeed = Math.min(1.5, selectedTemplate.stats.flySpeed + 0.02);
                    return true;
                }
                // Jump Height
                rowY += 16;
                if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 90 && mouseX <= formX + 180) {
                    if (mouseX <= formX + 102) selectedTemplate.stats.jumpHeight = Math.max(0.1, selectedTemplate.stats.jumpHeight - 0.05);
                    else if (mouseX >= formX + 168) selectedTemplate.stats.jumpHeight = Math.min(3.0, selectedTemplate.stats.jumpHeight + 0.05);
                    return true;
                }
                // Max Stamina
                rowY += 16;
                if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 90 && mouseX <= formX + 180) {
                    if (mouseX <= formX + 102) selectedTemplate.stats.maxStamina = Math.max(10.0, selectedTemplate.stats.maxStamina - 10.0);
                    else if (mouseX >= formX + 168) selectedTemplate.stats.maxStamina = Math.min(500.0, selectedTemplate.stats.maxStamina + 10.0);
                    return true;
                }
                // Stamina Recovery
                rowY += 16;
                if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 90 && mouseX <= formX + 180) {
                    if (mouseX <= formX + 102) selectedTemplate.stats.staminaRecoveryRate = Math.max(1.0, selectedTemplate.stats.staminaRecoveryRate - 1.0);
                    else if (mouseX >= formX + 168) selectedTemplate.stats.staminaRecoveryRate = Math.min(50.0, selectedTemplate.stats.staminaRecoveryRate + 1.0);
                    return true;
                }

            } else if (activeTab.equals("Combat")) {
                int comBtnX = formX + 100;
                if (mouseX >= comBtnX && mouseX <= comBtnX + 50 && mouseY >= formY + 5 && mouseY <= formY + 15) {
                    selectedTemplate.combat.enableCombat = !selectedTemplate.combat.enableCombat;
                    updateWidgetsVisibility();
                    return true;
                }

                if (selectedTemplate.combat.enableCombat) {
                    int rowY = formY + 20;
                    // Strength and Attack Speed are edit boxes
                    // Combat AI click cycle
                    int aiBtnX = formX + 8;
                    int aiBtnY = formY + 20 + 26 + 26 + 10;
                    if (mouseX >= aiBtnX && mouseX <= aiBtnX + 138 && mouseY >= aiBtnY && mouseY <= aiBtnY + 14) {
                        String currentAi = selectedTemplate.combat.combatAi;
                        String nextAi;
                        if ("PASSIVE".equals(currentAi)) nextAi = "DEFENSIVE";
                        else if ("DEFENSIVE".equals(currentAi)) nextAi = "AGGRESSIVE";
                        else if ("AGGRESSIVE".equals(currentAi)) nextAi = "ASSIST_RIDER";
                        else nextAi = "PASSIVE";
                        selectedTemplate.combat.combatAi = nextAi;
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }

            } else if (activeTab.equals("Abilities")) {
                int listBoxY = formY + 20;
                int listBoxH = panelH - 80 - 28;

                List<MountData.AbilityData> passives = new ArrayList<>();
                List<MountData.AbilityData> actives = new ArrayList<>();
                for (MountData.AbilityData ab : MountRegistry.customAbilities.values()) {
                    if (ab.isPassive) {
                        passives.add(ab);
                    } else {
                        actives.add(ab);
                    }
                }
                passives.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                actives.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

                List<Object> items = new ArrayList<>();
                items.add(Component.translatable("gui.rpg_mounts.creator.abilities.passives"));
                items.addAll(passives);
                items.add(Component.translatable("gui.rpg_mounts.creator.abilities.actives"));
                items.addAll(actives);

                int visibleRows = 9;
                int rowHeight = 18;
                for (int i = 0; i < visibleRows; i++) {
                    int idx = i + abilityScrollOffset;
                    if (idx >= items.size()) break;
                    Object item = items.get(idx);
                    if (item instanceof MountData.AbilityData ab) {
                        int rowY = listBoxY + 2 + i * rowHeight;
                        if (mouseX >= formX + 8 && mouseX <= formX + formW - 16 && mouseY >= rowY && mouseY <= rowY + rowHeight) {
                            boolean hasAbility = selectedTemplate.availableAbilities.stream().anyMatch(a -> a.name.equalsIgnoreCase(ab.name));
                            if (hasAbility) {
                                selectedTemplate.availableAbilities.removeIf(a -> a.name.equalsIgnoreCase(ab.name));
                            } else {
                                selectedTemplate.availableAbilities.add(ab);
                            }
                            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            return true;
                        }
                    }
                }

            } else if (activeTab.equals("Seating & Rules")) {
                int countY = formY + 5;
                // Total Seats count
                if (mouseY >= countY && mouseY <= countY + 12 && mouseX >= formX + 100 && mouseX <= formX + 180) {
                    int size = selectedTemplate.seats.size();
                    if (mouseX <= formX + 112) {
                        if (size > 1) {
                            selectedTemplate.seats.remove(size - 1);
                            selectedSeatIndex = Math.min(selectedSeatIndex, selectedTemplate.seats.size() - 1);
                            updateDummyRiders();
                        }
                    } else if (mouseX >= formX + 168) {
                        if (size < 4) {
                            selectedTemplate.seats.add(new MountData.SeatOffset(0.0, 0.0, -0.5 * size));
                            selectedSeatIndex = selectedTemplate.seats.size() - 1;
                            updateDummyRiders();
                        }
                    }
                    return true;
                }

                if (!selectedTemplate.seats.isEmpty()) {
                    MountData.SeatOffset seat = selectedTemplate.seats.get(selectedSeatIndex);
                    int rowY = formY + 22;

                    // Seat Selector cycle
                    if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 100 && mouseX <= formX + 180) {
                        if (mouseX <= formX + 112) {
                            selectedSeatIndex = (selectedSeatIndex - 1 + selectedTemplate.seats.size()) % selectedTemplate.seats.size();
                        } else if (mouseX >= formX + 168) {
                            selectedSeatIndex = (selectedSeatIndex + 1) % selectedTemplate.seats.size();
                        }
                        return true;
                    }

                    // Seat X offset adjuster
                    rowY += 16;
                    if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 100 && mouseX <= formX + 180) {
                        if (mouseX <= formX + 112) seat.x -= 0.05;
                        else if (mouseX >= formX + 168) seat.x += 0.05;
                        updateDummyRiders();
                        return true;
                    }

                    // Seat Y offset adjuster
                    rowY += 16;
                    if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 100 && mouseX <= formX + 180) {
                        if (mouseX <= formX + 112) seat.y -= 0.05;
                        else if (mouseX >= formX + 168) seat.y += 0.05;
                        updateDummyRiders();
                        return true;
                    }

                    // Seat Z offset adjuster
                    rowY += 16;
                    if (mouseY >= rowY && mouseY <= rowY + 12 && mouseX >= formX + 100 && mouseX <= formX + 180) {
                        if (mouseX <= formX + 112) seat.z -= 0.05;
                        else if (mouseX >= formX + 168) seat.z += 0.05;
                        updateDummyRiders();
                        return true;
                    }
                }

                // Steve view toggle
                int toggleY = formY + 94;
                int tgX = formX + 100;
                if (mouseX >= tgX && mouseX <= tgX + 80 && mouseY >= toggleY && mouseY <= toggleY + 12) {
                    showSteve = !showSteve;
                    updateDummyRiders();
                    return true;
                }

                // Cargo click trigger modal item selector
                int cargoY = formY + 110;
                int cargoBtnX = formX + 100;
                if (mouseX >= cargoBtnX && mouseX <= cargoBtnX + 80 && mouseY >= cargoY && mouseY <= cargoY + 12) {
                    isItemSelectorOpen = true;
                    itemSelectorTarget = "cargo";
                    itemSearchBox.setValue("");
                    updateWidgetsVisibility();
                    return true;
                }
            }
        }

        // Save All Changes Click
        int saveX = left + 110;
        int saveY = top + panelH - 30;
        if (mouseX >= saveX && mouseX <= saveX + 140 && mouseY >= saveY && mouseY <= saveY + 18) {
            saveTextFieldsToActiveTemplate();
            if (selectedTemplate != null) {
                // Serializes template config as json string
                String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(selectedTemplate);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeUtf(json);
                NetworkManager.sendToServer(ModPackets.C2S_SAVE_TEMPLATE, buf);
            }
            return true;
        }

        // Discard & Exit Click
        int discardX = left + 270;
        if (mouseX >= discardX && mouseX <= discardX + 140 && mouseY >= saveY && mouseY <= saveY + 18) {
            this.onClose();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void saveTextFieldsToActiveTemplate() {
        if (selectedTemplate == null) return;
        String oldModelId = selectedTemplate.modelId;
        String oldModelType = selectedTemplate.modelType;
        selectedTemplate.name = nameField.getValue();
        selectedTemplate.description = descField.getValue();
        selectedTemplate.flightParticle = flightParticleField.getValue();
        selectedTemplate.groundParticle = groundParticleField.getValue();
        
        if (selectedTemplate.modelType.equals("vanilla")) {
            selectedTemplate.modelId = modelIdField.getValue();
            selectedTemplate.texturePath = textureField.getValue();
            selectedTemplate.animationPath = "";
        } else if (!selectedTemplate.modelType.equals("mcmodel")) {
            selectedTemplate.modelId = modelIdField.getValue();
            selectedTemplate.texturePath = textureField.getValue();
            selectedTemplate.animationPath = animField.getValue();
        }

        if (!selectedTemplate.modelId.equals(oldModelId) || !selectedTemplate.modelType.equals(oldModelType)) {
            selectedTemplate.resetDimensions();
        }

        if (selectedTemplate.rarity == null) {
            selectedTemplate.rarity = "COMMON";
        }

        // Combat Active Ability is managed via the custom abilities list selection, not textfields.

        // SFX
        if (selectedTemplate.sounds == null) {
            selectedTemplate.sounds = new MountData.SoundsData();
        }
        selectedTemplate.sounds.ambient = soundAmbientField.getValue();
        selectedTemplate.sounds.step = soundStepField.getValue();
        selectedTemplate.sounds.hurt = soundHurtField.getValue();
        selectedTemplate.sounds.death = soundDeathField.getValue();
        
        if (selectedTemplate.spawnEffects == null) {
            selectedTemplate.spawnEffects = new MountData.SpawnEffectsData();
        }
        selectedTemplate.spawnEffects.particle = spawnParticleField.getValue();
        selectedTemplate.spawnEffects.sound = spawnSoundField.getValue();
        
        if (selectedTemplate.combat == null) {
            selectedTemplate.combat = new MountData.CombatData();
        }
        try {
            selectedTemplate.combat.strength = Double.parseDouble(combatStrengthField.getValue());
        } catch (NumberFormatException ignored) {}
        try {
            selectedTemplate.combat.attackSpeed = Double.parseDouble(combatAttackSpeedField.getValue());
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;
        int formX = left + 110;
        int formW = (int) ((this.panelW - 120) * 0.6);
        int viewportX = formX + formW + 10;
        int viewportY = top + 42;
        int viewportW = this.panelW - 10 - (viewportX - left);
        int viewportH = this.panelH - 80;
        if (mouseX >= viewportX && mouseX <= viewportX + viewportW && mouseY >= viewportY && mouseY <= viewportY + viewportH) {
            previewRotation -= dragX * 2.5f;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int left = (this.width - this.panelW) / 2;
        int top = (this.height - this.panelH) / 2;
        int formX = left + 110;
        int formY = top + 42;
        int formW = (int) ((this.panelW - 120) * 0.6);
        if (showModelSuggestions && !activeSuggestions.isEmpty() && activeTab.equals("Model & Anims")) {
            int dropX = formX + 8;
            int dropY = formY + modelSuggestionYOffset;
            int rowH = 12;
            int maxVisibleRows = 5;
            int visibleRows = Math.min(maxVisibleRows, activeSuggestions.size());
            int dropH = visibleRows * rowH;
            
            if (modelSuggestionYOffset + dropH > 210) {
                dropY = formY + (modelSuggestionYOffset - 12) - dropH;
            }

            if (mouseX >= dropX && mouseX <= dropX + (formW - 16) && mouseY >= dropY && mouseY <= dropY + dropH) {
                int maxOffset = Math.max(0, activeSuggestions.size() - maxVisibleRows);
                if (amount > 0) {
                    suggestionsScrollOffset = Math.max(0, suggestionsScrollOffset - 1);
                } else if (amount < 0) {
                    suggestionsScrollOffset = Math.min(maxOffset, suggestionsScrollOffset + 1);
                }
                return true;
            }
        }
        if (showSoundSuggestions && !activeSuggestions.isEmpty() && activeTab.equals("Sounds & FX") && activeSoundField != null) {
            int dropX = formX + 8;
            int dropY = formY + soundSuggestionYOffset;
            int rowH = 12;
            int maxVisibleRows = 5;
            int visibleRows = Math.min(maxVisibleRows, activeSuggestions.size());
            int dropH = visibleRows * rowH;

            if (soundSuggestionYOffset + dropH > 210) {
                dropY = formY + (soundSuggestionYOffset - 12) - dropH;
            }

            if (mouseX >= dropX && mouseX <= dropX + (formW - 16) && mouseY >= dropY && mouseY <= dropY + dropH) {
                int maxOffset = Math.max(0, activeSuggestions.size() - maxVisibleRows);
                if (amount > 0) {
                    suggestionsScrollOffset = Math.max(0, suggestionsScrollOffset - 1);
                } else if (amount < 0) {
                    suggestionsScrollOffset = Math.min(maxOffset, suggestionsScrollOffset + 1);
                }
                return true;
            }
        }
        if (isItemSelectorOpen) {
            int maxOffset = Math.max(0, (filteredItems.size() / 8) - 5);
            if (amount > 0) {
                itemScrollOffset = Math.max(0, itemScrollOffset - 1);
            } else if (amount < 0) {
                itemScrollOffset = Math.min(maxOffset, itemScrollOffset + 1);
            }
            return true;
        }
        if (activeTab.equals("Abilities") && selectedTemplate != null) {
            int totalItems = MountRegistry.customAbilities.size() + 2;
            int maxOffset = Math.max(0, totalItems - 9);
            if (amount > 0) {
                abilityScrollOffset = Math.max(0, abilityScrollOffset - 1);
            } else if (amount < 0) {
                abilityScrollOffset = Math.min(maxOffset, abilityScrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private List<String> getModelSuggestions(String query) {
        if (cachedModelList == null) {
            List<String> list = new ArrayList<>();
            java.io.File baseDir = new java.io.File(dev.architectury.platform.Platform.getConfigFolder().toFile(), "RPG Mounts");
            java.io.File mountsFolder = new java.io.File(baseDir, "Mounts/Unpacked");
            if (mountsFolder.exists() && mountsFolder.isDirectory()) {
                java.io.File[] folders = mountsFolder.listFiles();
                if (folders != null) {
                    for (java.io.File folder : folders) {
                        if (folder.isDirectory()) {
                            list.add(folder.getName());
                        }
                    }
                }
            }
            for (String id : getVanillaModels()) {
                list.add(id);
            }
            cachedModelList = new ArrayList<>(new java.util.HashSet<>(list));
            cachedModelList.sort(String::compareToIgnoreCase);
        }

        if (query.isEmpty()) {
            return cachedModelList;
        }

        List<String> suggestions = new ArrayList<>();
        String queryLower = query.toLowerCase();
        for (String id : cachedModelList) {
            if (id.toLowerCase().contains(queryLower)) {
                suggestions.add(id);
            }
        }
        return suggestions;
    }

    private List<String> getSoundSuggestions(String query) {
        if (cachedSoundList == null) {
            List<String> list = new ArrayList<>();
            // 1. Add all registered sound events
            for (ResourceLocation loc : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet()) {
                list.add(loc.toString());
            }
            // 2. Add custom ogg files
            java.io.File soundsDir = ddraig.net.rpgmounts.data.MountRegistry.getSoundsFolder();
            if (soundsDir.exists() && soundsDir.isDirectory()) {
                addCustomOggSuggestions(soundsDir, "", "custom", "", list);
            }
            // 3. Add unpacked model folder sounds
            java.io.File unpackedDir = ddraig.net.rpgmounts.data.MountRegistry.getMountsFolder();
            if (unpackedDir.exists() && unpackedDir.isDirectory()) {
                java.io.File[] folders = unpackedDir.listFiles();
                if (folders != null) {
                    for (java.io.File folder : folders) {
                        if (folder.isDirectory()) {
                            String modelId = folder.getName();
                            addCustomOggSuggestions(folder, "", "unpacked." + modelId, "", list);
                        }
                    }
                }
            }
            cachedSoundList = new ArrayList<>(new java.util.HashSet<>(list));
            cachedSoundList.sort(String::compareToIgnoreCase);
        }

        if (query.isEmpty()) {
            return cachedSoundList;
        }

        List<String> suggestions = new ArrayList<>();
        String queryLower = query.toLowerCase();
        for (String id : cachedSoundList) {
            if (id.contains(queryLower)) {
                suggestions.add(id);
            }
        }
        return suggestions;
    }

    private List<String> getParticleSuggestions(String query) {
        if (cachedParticleList == null) {
            List<String> list = new ArrayList<>();
            for (ResourceLocation loc : net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.keySet()) {
                list.add(loc.toString());
            }
            cachedParticleList = new ArrayList<>(new java.util.HashSet<>(list));
            cachedParticleList.sort(String::compareToIgnoreCase);
        }

        if (query.isEmpty()) {
            return cachedParticleList;
        }

        List<String> suggestions = new ArrayList<>();
        String queryLower = query.toLowerCase(java.util.Locale.ROOT);
        for (String id : cachedParticleList) {
            if (id.contains(queryLower)) {
                suggestions.add(id);
            }
        }
        return suggestions;
    }

    private void addCustomOggSuggestions(java.io.File dir, String relativePath, String eventPrefix, String query, List<String> suggestions) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                String nextRel = relativePath.isEmpty() ? f.getName() : relativePath + "/" + f.getName();
                addCustomOggSuggestions(f, nextRel, eventPrefix, query, suggestions);
            } else if (f.isFile() && f.getName().toLowerCase().endsWith(".ogg")) {
                String nameNoExt = f.getName().substring(0, f.getName().length() - 4);
                String soundPath = relativePath.isEmpty() ? nameNoExt : relativePath + "/" + nameNoExt;

                String eventSuffix = soundPath.replace('/', '.');
                String eventKey = eventPrefix.isEmpty() ? eventSuffix : eventPrefix + "." + eventSuffix;

                String fullId = "rpg_mounts:" + eventKey;
                if (query.isEmpty() || fullId.toLowerCase().contains(query.toLowerCase())) {
                    suggestions.add(fullId);
                }

                if (eventPrefix.equals("custom")) {
                    String aliasId = "rpg_mounts:" + eventSuffix;
                    if (query.isEmpty() || aliasId.toLowerCase().contains(query.toLowerCase())) {
                        suggestions.add(aliasId);
                    }
                }
            }
        }
    }

    private String truncate(String text, int length) {
        if (text.length() <= length) return text;
        return text.substring(0, length - 2) + "..";
    }

    private void renderMountPreview(GuiGraphics graphics, int viewCenterX, int viewCenterY, int scaleFactor, RPGMountEntity preview) {
        // Render the mount itself
        InventoryScreen.renderEntityInInventory(
                graphics,
                viewCenterX,
                viewCenterY,
                scaleFactor,
                new org.joml.Quaternionf().rotationZ((float)Math.PI).rotateX(-20.0f * (float)(Math.PI / 180.0)).rotateY(previewRotation * (float)(Math.PI / 180.0)),
                null,
                preview
        );

        // Render passengers
        if (showSteve && selectedTemplate != null) {
            preview.positionRidersPublic();
            for (int i = 0; i < preview.getPassengers().size(); i++) {
                net.minecraft.world.entity.Entity passenger = preview.getPassengers().get(i);
                if (passenger instanceof net.minecraft.world.entity.LivingEntity livingPassenger) {
                    if (i < selectedTemplate.seats.size()) {
                        MountData.SeatOffset offset = selectedTemplate.seats.get(i);
                        
                        double yawRad = previewRotation * (Math.PI / 180.0);
                        double pitchRad = -20.0 * (Math.PI / 180.0);

                        double cosY = Math.cos(yawRad);
                        double sinY = Math.sin(yawRad);
                        double cosP = Math.cos(pitchRad);
                        double sinP = Math.sin(pitchRad);

                        double ox = -offset.x * selectedTemplate.scale;
                        double oy = (offset.y * selectedTemplate.scale) - livingPassenger.getMyRidingOffset();
                        double oz = offset.z * selectedTemplate.scale;

                        double x1 = ox * cosY - oz * sinY;
                        double z1 = ox * sinY + oz * cosY;
                        double y1 = oy;

                        double rx = x1;
                        double ry = y1 * cosP - z1 * sinP;

                        int riderX = viewCenterX + (int) (rx * scaleFactor);
                        int riderY = viewCenterY - (int) (ry * scaleFactor);

                        livingPassenger.setYRot(0.0f);
                        livingPassenger.setYHeadRot(0.0f);
                        livingPassenger.setXRot(0.0f);

                        InventoryScreen.renderEntityInInventory(
                                graphics,
                                riderX,
                                riderY,
                                scaleFactor,
                                new org.joml.Quaternionf().rotationZ((float)Math.PI).rotateX(-20.0f * (float)(Math.PI / 180.0)).rotateY(previewRotation * (float)(Math.PI / 180.0)),
                                null,
                                livingPassenger
                        );
                    }
                }
            }
        }
    }

    public MountData getSelectedTemplate() {
        return this.selectedTemplate;
    }

    private void drawPlaySoundButton(GuiGraphics graphics, int btnX, int btnY, int mouseX, int mouseY) {
        boolean hover = mouseX >= btnX && mouseX < btnX + 12 && mouseY >= btnY && mouseY < btnY + 12;
        int color = hover ? 0xFF55FF55 : 0xFF00AA00;
        graphics.fill(btnX, btnY, btnX + 12, btnY + 12, 0xFF333333);
        graphics.fill(btnX + 1, btnY + 1, btnX + 11, btnY + 11, color);
        String playSymbol = "▶";
        int textW = this.font.width(playSymbol);
        graphics.drawString(this.font, playSymbol, btnX + (12 - textW) / 2 + 1, btnY + 2, 0xFFFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
