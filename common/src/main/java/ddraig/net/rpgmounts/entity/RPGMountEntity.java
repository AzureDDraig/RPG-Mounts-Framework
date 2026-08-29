package ddraig.net.rpgmounts.entity;

import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.config.ModConfig;
import ddraig.net.rpgmounts.config.AnimationMappingConfig;
import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import dev.architectury.networking.NetworkManager;
import ddraig.net.rpgmounts.network.ModPackets;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * RPG Mount Entity class
 * Handles riding input controls, stamina, movement archetypes (ground, water, flying), 
 * multi-passenger offsets, culling, custom abilities, and bonding.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Replaced stub with full rideable mechanics, controls, custom flying/swimming inputs, culling tick suspension, and database integration.
 * - 2026-06-19: [Fix Compile Errors] - Use setMaxUpStep, check controlling passenger is not null instead of instanceof pattern to support Java 17 compile.
 * - 2026-06-19: [Feeding & Bonding] - Add mount feeding interactions to increase bonding scores.
 */
public class RPGMountEntity extends PathfinderMob implements software.bernie.geckolib.animatable.GeoEntity, PlayerRideableJumping {
    public static final EntityDataAccessor<String> TEMPLATE_ID = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Float> STAMINA = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Integer> BONDING = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> HAS_SADDLE = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<String> ARMOR_ITEM = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<String> CARGO_ITEM = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<String> ACTIVE_ANIMATION = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Integer> ABILITY_1_INDEX = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ABILITY_2_INDEX = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<String> OWNER_UUID = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Float> XP = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Boolean> IS_CHROMA = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<String> DISABLED_PASSIVES = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<String> INSTANCE_ID = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Integer> HOVER_TICKS = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> STEALTH_TICKS = SynchedEntityData.defineId(RPGMountEntity.class, EntityDataSerializers.INT);
    
    public float rollO = 0.0F;
    private final software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache geckolibCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    
    private static final UUID SPRINT_MODIFIER_UUID = UUID.fromString("662a6b8d-da3e-4c1d-8a6c-31a8bc51613b");
    private static final AttributeModifier SPRINT_MODIFIER = new AttributeModifier(SPRINT_MODIFIER_UUID, "Sprinting speed boost", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private net.minecraft.world.SimpleContainer inventory;
    private UUID ownerUuid;
    private boolean isSprinting = false;
    private int ability1Cooldown = 0;
    private int ability2Cooldown = 0;
    private int idleTicks = 0;
    private int activeAnimationTicks = 0;
    private int customStepSoundCooldown = 0;
    private int teleportImmunityTicks = 0;

    // Client-side animation tracking
    public String clientLastAnimation = "";
    public String clientLastLoopAnimation = "";
    public float clientAnimationStartTick = 0.0f;
    public String clientJumpState = ""; // "", "jump", "jump_in", "jump_out"
    public float clientJumpStartTick = 0.0f;
    public boolean clientLastOnGround = true;

    // Movement control states (modified via packets or ticks)
    public boolean inputFlyUp = false;
    public boolean inputFlyDown = false;
    public boolean inputSprint = false;

    private final java.util.Set<String> disabledPassives = new java.util.HashSet<>();
    private double jumpStartY = 0.0;
    private double maxJumpY = 0.0;
    private boolean isJumping = false;

    public boolean hasSpiderClimbActive = false;
    public boolean hasStepAssistActive = false;
    public boolean hasFireproofScalesActive = false;
    public boolean hasGillsOfTheDeepActive = false;
    public boolean hasTractionTreadActive = false;
    public boolean hasFeatherLightActive = false;
    public boolean hasRejuvenationAuraActive = false;
    public boolean hasDeepDiverActive = false;
    public boolean hasShadowCamouflageActive = false;
    public boolean hasBondingBoostActive = false;
    public boolean hasCargoCushionActive = false;
    public boolean hasThornGuardActive = false;
    public boolean hasPhotosynthesisActive = false;
    public boolean hasToxicSecretionsActive = false;
    public boolean hasGlacialAuraActive = false;
    public boolean hasMagnetosphereActive = false;
    public boolean hasNightEyesActive = false;
    public boolean hasReinforcedHideActive = false;

    // Cached stat modifiers from enhancers
    public double swimSpeedModifier = 0.0;
    public double flySpeedModifier = 0.0;
    public double strengthModifier = 0.0;
    public double attackSpeedModifier = 0.0;
    public double flatDamageReductionModifier = 0.0;

    public void updatePassiveCaches() {
        this.hasSpiderClimbActive = evaluatePassive("Spider Climb", "SpiderClimb", "Wall Climb", "WallClimb");
        this.hasStepAssistActive = evaluatePassive("Step Assist", "StepAssist", "Assistant Step", "Step Assistant", "Assistant Step Assist");
        this.hasFireproofScalesActive = evaluatePassive("Fireproof Scales", "FireproofScales", "Lava Walk", "LavaWalk");
        this.hasGillsOfTheDeepActive = evaluatePassive("Gills of the Deep", "GillsOfTheDeep", "Water Breathing", "WaterBreathing");
        this.hasTractionTreadActive = evaluatePassive("Traction Tread", "TractionTread");
        this.hasFeatherLightActive = evaluatePassive("Feather Light", "FeatherLight", "Slow Falling", "SlowFalling");
        this.hasRejuvenationAuraActive = evaluatePassive("Rejuvenation Aura", "RejuvenationAura", "Regeneration", "Regeneration Aura");
        this.hasDeepDiverActive = evaluatePassive("Deep Diver", "DeepDiver", "Swim Speed");
        this.hasShadowCamouflageActive = evaluatePassive("Shadow Camouflage", "ShadowCamouflage", "Invisibility");
        this.hasBondingBoostActive = evaluatePassive("Bonding Boost", "BondingBoost");
        this.hasCargoCushionActive = evaluatePassive("Cargo Cushion", "CargoCushion");
        this.hasThornGuardActive = evaluatePassive("Thorn Guard", "ThornGuard");
        this.hasPhotosynthesisActive = evaluatePassive("Photosynthesis");
        this.hasToxicSecretionsActive = evaluatePassive("Toxic Secretions", "ToxicSecretions");
        this.hasGlacialAuraActive = evaluatePassive("Glacial Aura", "GlacialAura");
        this.hasMagnetosphereActive = evaluatePassive("Magnetosphere");
        this.hasNightEyesActive = evaluatePassive("Night Eyes", "NightEyes", "Night Vision", "NightVision");
        this.hasReinforcedHideActive = evaluatePassive("Reinforced Hide", "ReinforcedHide", "Resistance");

        // Set step height based on Step Assist passive status
        this.setMaxUpStep(this.hasStepAssistActive ? 1.5f : 1.0f);
    }

    private boolean evaluatePassive(String passiveName) {
        return evaluatePassive(new String[]{passiveName});
    }

    private boolean evaluatePassive(String... alternateNames) {
        String tId = getTemplateId();
        if (tId == null || tId.isEmpty()) return false;
        MountData data = MountRegistry.getTemplate(tId);
        if (data == null) return false;
        for (String name : alternateNames) {
            boolean hasAbility = data.availableAbilities.stream()
                    .anyMatch(a -> a.name.equalsIgnoreCase(name) && a.isPassive);
            if (hasAbility && !disabledPassives.contains(name.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public boolean isPassiveActive(String passiveName) {
        return evaluatePassive(passiveName);
    }

    public void togglePassive(String passiveName, boolean enable) {
        String key = passiveName.toLowerCase();
        if (enable) {
            disabledPassives.remove(key);
        } else {
            disabledPassives.add(key);
        }
        this.entityData.set(DISABLED_PASSIVES, String.join(",", disabledPassives));
        updatePassiveCaches();
    }

    public RPGMountEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setMaxUpStep(1.0f); // Default step height
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MountFloatGoal(this));
        this.goalSelector.addGoal(2, new MountMeleeAttackGoal(this));
        this.goalSelector.addGoal(3, new MountStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 6.0F));
        this.goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
        
        this.targetSelector.addGoal(1, new MountOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new MountOwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new MountHurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new MountAggressiveTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TEMPLATE_ID, "horse");
        this.entityData.define(STAMINA, 100.0f);
        this.entityData.define(BONDING, 0);
        this.entityData.define(HAS_SADDLE, false);
        this.entityData.define(ARMOR_ITEM, "");
        this.entityData.define(CARGO_ITEM, "");
        this.entityData.define(ACTIVE_ANIMATION, "");
        this.entityData.define(ABILITY_1_INDEX, 0);
        this.entityData.define(ABILITY_2_INDEX, 1);
        this.entityData.define(OWNER_UUID, "");
        this.entityData.define(ROLL, 0.0F);
        this.entityData.define(LEVEL, 1);
        this.entityData.define(XP, 0.0F);
        this.entityData.define(IS_CHROMA, false);
        this.entityData.define(DISABLED_PASSIVES, "");
        this.entityData.define(INSTANCE_ID, "");
        this.entityData.define(HOVER_TICKS, 0);
        this.entityData.define(STEALTH_TICKS, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.JUMP_STRENGTH, 0.7)
                .add(Attributes.ARMOR, 0.0);
    }

    public String getTemplateId() {
        return this.entityData.get(TEMPLATE_ID);
    }

    public String getInstanceId() {
        String instId = this.entityData.get(INSTANCE_ID);
        if (instId.isEmpty()) {
            instId = java.util.UUID.randomUUID().toString();
            this.setInstanceId(instId);
        }
        return instId;
    }

    public void setInstanceId(String instanceId) {
        this.entityData.set(INSTANCE_ID, instanceId == null ? "" : instanceId);
    }

    public void setTemplateId(String templateId) {
        this.entityData.set(TEMPLATE_ID, templateId);
        recalculateStats();
        this.refreshDimensions();
        MountData data = MountRegistry.getTemplate(templateId);
        if (data != null) {
            this.setHealth((float) this.getAttributeValue(Attributes.MAX_HEALTH));
            this.entityData.set(STAMINA, (float) data.stats.maxStamina);
            if (!this.level().isClientSide && this.ownerUuid != null) {
                loadGearFromDatabase();
            }
        }
    }

    public float getStamina() {
        return this.entityData.get(STAMINA);
    }

    public void setStamina(float stamina) {
        this.entityData.set(STAMINA, stamina);
    }

    public int getBonding() {
        return this.entityData.get(BONDING);
    }

    public void setBonding(int bonding) {
        this.entityData.set(BONDING, bonding);
        recalculateStats();
    }

    public UUID getOwnerUuid() {
        if (this.ownerUuid == null) {
            String s = this.entityData.get(OWNER_UUID);
            if (!s.isEmpty()) {
                try {
                    this.ownerUuid = UUID.fromString(s);
                } catch (Exception e) {}
            }
        }
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        this.entityData.set(OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        if (!this.level().isClientSide && !this.getTemplateId().isEmpty()) {
            loadGearFromDatabase();
            loadStatsFromDatabase();
        }
    }

    public boolean hasSaddle() {
        return this.entityData.get(HAS_SADDLE);
    }

    public String getArmorItem() {
        return this.entityData.get(ARMOR_ITEM);
    }

    public String getCargoItem() {
        return this.entityData.get(CARGO_ITEM);
    }

    public String getActiveAnimation() {
        return this.entityData.get(ACTIVE_ANIMATION);
    }

    public void setActiveAnimation(String anim) {
        this.entityData.set(ACTIVE_ANIMATION, anim);
    }

    public boolean isChroma() {
        return this.entityData.get(IS_CHROMA);
    }

    public void setChroma(boolean value) {
        this.entityData.set(IS_CHROMA, value);
    }

    private boolean isSilhouette = false;

    public boolean isSilhouette() {
        return this.isSilhouette;
    }

    public void setSilhouette(boolean value) {
        this.isSilhouette = value;
    }

    public int getAbility1Index() {
        return this.entityData.get(ABILITY_1_INDEX);
    }

    public void setAbility1Index(int idx) {
        this.entityData.set(ABILITY_1_INDEX, idx);
    }

    public int getAbility2Index() {
        return this.entityData.get(ABILITY_2_INDEX);
    }

    public void setAbility2Index(int idx) {
        this.entityData.set(ABILITY_2_INDEX, idx);
    }

    public float getRoll() {
        return this.entityData.get(ROLL);
    }

    public void setRoll(float roll) {
        this.entityData.set(ROLL, roll);
    }

    public int getLevel() {
        return this.entityData.get(LEVEL);
    }

    public void setLevel(int level) {
        this.entityData.set(LEVEL, level);
    }

    public float getXp() {
        return this.entityData.get(XP);
    }

    public void setXp(float xp) {
        this.entityData.set(XP, xp);
    }

    public net.minecraft.world.SimpleContainer getInventory() {
        if (this.inventory == null) {
            this.inventory = new net.minecraft.world.SimpleContainer(120);
            this.inventory.addListener(container -> {
                if (!this.level().isClientSide) {
                    saveGearToDatabase();
                    updateSynchedData();
                    recalculateStats();
                }
            });
        }
        return this.inventory;
    }

    public void updateSynchedData() {
        ItemStack saddle = getInventory().getItem(0);
        this.entityData.set(HAS_SADDLE, !saddle.isEmpty());

        ItemStack armor = getInventory().getItem(1);
        this.entityData.set(ARMOR_ITEM, armor.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(armor.getItem()).toString());

        ItemStack cargo = getInventory().getItem(2);
        this.entityData.set(CARGO_ITEM, cargo.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cargo.getItem()).toString());
    }

    public void loadGearFromDatabase() {
        if (this.ownerUuid == null) return;
        Map<String, DatabaseManager.MountGearData> playerGear = DatabaseManager.mountGearCache.get(this.ownerUuid);
        if (playerGear != null) {
            DatabaseManager.MountGearData gear = playerGear.get(this.getTemplateId());
            if (gear != null && !gear.cargoNbt.isEmpty()) {
                try {
                    CompoundTag nbt = net.minecraft.nbt.TagParser.parseTag(gear.cargoNbt);
                    if (nbt.contains("Items")) {
                        loadContainerFromTag(this.getInventory(), nbt.getList("Items", 10));
                    }
                } catch (Exception e) {
                    RPGMounts.LOGGER.error("Failed to parse cargo NBT for mount " + this.getTemplateId(), e);
                }
            }
        }
        updateSynchedData();
    }
    public static void loadContainerFromTag(net.minecraft.world.SimpleContainer container, net.minecraft.nbt.ListTag listTag) {
        container.clearContent();
        for (int i = 0; i < listTag.size(); i++) {
            net.minecraft.nbt.CompoundTag compoundTag = listTag.getCompound(i);
            int slot = compoundTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < container.getContainerSize()) {
                container.setItem(slot, net.minecraft.world.item.ItemStack.of(compoundTag));
            }
        }
    }

    public static net.minecraft.nbt.ListTag saveContainerToTag(net.minecraft.world.SimpleContainer container) {
        net.minecraft.nbt.ListTag listTag = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putByte("Slot", (byte) i);
                stack.save(compoundTag);
                listTag.add(compoundTag);
            }
        }
        return listTag;
    }

    public void saveGearToDatabase() {
        if (this.ownerUuid == null) return;
        DatabaseManager.MountGearData gear = new DatabaseManager.MountGearData();

        ItemStack saddle = getInventory().getItem(0);
        gear.saddleItem = saddle.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(saddle.getItem()).toString();

        ItemStack armor = getInventory().getItem(1);
        gear.armorItem = armor.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(armor.getItem()).toString();

        ItemStack cargo = getInventory().getItem(2);
        gear.cargoItem = cargo.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cargo.getItem()).toString();

        CompoundTag nbt = new CompoundTag();
        nbt.put("Items", saveContainerToTag(getInventory()));
        gear.cargoNbt = nbt.toString();

        DatabaseManager.saveMountGearAsync(this.ownerUuid, this.getTemplateId(), gear);
    }

    public double getEnhancerModifier(String category, String type) {
        if (!ModConfig.get().combatAndEnhancers.enable_enhancers) return 0.0;
        double total = 0.0;
        int count = 0;
        int maxAllowed = 2;
        if (category.equalsIgnoreCase("defense")) maxAllowed = ModConfig.get().combatAndEnhancers.max_enhancers_defense;
        else if (category.equalsIgnoreCase("movement")) maxAllowed = ModConfig.get().combatAndEnhancers.max_enhancers_movement;
        else if (category.equalsIgnoreCase("damage")) maxAllowed = ModConfig.get().combatAndEnhancers.max_enhancers_damage;
        else if (category.equalsIgnoreCase("ability")) maxAllowed = ModConfig.get().combatAndEnhancers.max_enhancers_ability;

        // Loop enhancers slots 3-6
        for (int i = 3; i < 7; i++) {
            ItemStack stack = getInventory().getItem(i);
            if (!stack.isEmpty() && (
                stack.getItem() == ddraig.net.rpgmounts.registry.ModItems.DEFENSE_ENHANCER.get() ||
                stack.getItem() == ddraig.net.rpgmounts.registry.ModItems.MOVEMENT_ENHANCER.get() ||
                stack.getItem() == ddraig.net.rpgmounts.registry.ModItems.DAMAGE_ENHANCER.get() ||
                stack.getItem() == ddraig.net.rpgmounts.registry.ModItems.ABILITY_ENHANCER.get()
            )) {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("EnhancerCategory")) {
                    String c = tag.getString("EnhancerCategory");
                    String t = tag.getString("EnhancerType");
                    double val = tag.getDouble("EnhancerValue");
                    if (c.equalsIgnoreCase(category) && t.equalsIgnoreCase(type)) {
                        count++;
                        if (count <= maxAllowed) {
                            total += val;
                        }
                    }
                }
            }
        }
        return total;
    }

    public void recalculateStats() {
        MountData data = MountRegistry.getTemplate(getTemplateId());
        if (data == null) return;

        double baseHealth = data.stats.maxHealth;
        double baseSpeed = data.stats.movementSpeed;
        double baseJump = data.stats.jumpHeight;

        if (getBonding() >= 50 && ModConfig.get().bondingAndLeveling.enable_bonding_buffs) {
            baseHealth *= (1.0 + ModConfig.get().bondingAndLeveling.bonding_health_multiplier);
            baseSpeed *= (1.0 + ModConfig.get().bondingAndLeveling.bonding_speed_multiplier);
        }

        if (this.hasSaddle()) {
            baseSpeed *= (1.0 + ModConfig.get().stamina.saddle_speed_boost_multiplier);
        }

        double healthMod = getEnhancerModifier("defense", "max_health");
        double armorMod = getEnhancerModifier("defense", "armor");
        double speedMod = getEnhancerModifier("movement", "speed");
        double jumpMod = getEnhancerModifier("movement", "jump_strength");

        // Cache new modifiers
        this.swimSpeedModifier = getEnhancerModifier("movement", "swim_speed");
        this.flySpeedModifier = getEnhancerModifier("movement", "fly_speed");
        double jumpHeightMod = getEnhancerModifier("movement", "jump_height");
        this.strengthModifier = getEnhancerModifier("damage", "strength");
        this.attackSpeedModifier = getEnhancerModifier("damage", "attack_speed");
        this.flatDamageReductionModifier = getEnhancerModifier("defense", "flat_damage_reduction");

        double finalHealth = Math.max(ModConfig.get().stats.min_health_allowed, Math.min(ModConfig.get().stats.max_health_allowed, baseHealth + healthMod + getHealthGrowth()));
        double finalSpeed = Math.max(ModConfig.get().stats.min_speed_allowed, Math.min(ModConfig.get().stats.max_speed_allowed, baseSpeed + speedMod + getSpeedGrowth()));
        double finalJump = Math.max(0.1, baseJump + jumpMod + jumpHeightMod);

        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(finalHealth);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(finalSpeed);
        
        if (this.getAttribute(Attributes.JUMP_STRENGTH) != null) {
            this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(finalJump);
        }
        if (this.getAttribute(Attributes.ARMOR) != null) {
            this.getAttribute(Attributes.ARMOR).setBaseValue(armorMod);
        }
        this.updatePassiveCaches();
    }

    @Override
    public void tick() {
        this.rollO = this.getRoll();
        if (this.customStepSoundCooldown > 0) {
            this.customStepSoundCooldown--;
        }
        
        if (this.onGround()) {
            this.isJumping = false;
        } else if (this.isJumping) {
            this.maxJumpY = Math.max(this.maxJumpY, this.getY());
        }
        
        // Enforce step height on every tick to prevent other mods or ticks from overriding it
        this.setMaxUpStep(this.hasStepAssistActive ? 1.5f : 1.0f);
        
        // Performance culling: If dismounted and no player is within config block radius, suspend ticking
        if (!this.isVehicle() && !this.level().isClientSide) {
            int radius = ModConfig.get().general.culling_distance_blocks;
            boolean playerNear = this.level().hasNearbyAlivePlayer(this.getX(), this.getY(), this.getZ(), radius);
            if (!playerNear) {
                // Suspended ticking: Skip super.tick() logic to save TPS cycles
                this.idleTicks++;
                if (this.idleTicks > 200 && ModConfig.get().general.autoDespawnIdleSeconds > 0) {
                    if (this.idleTicks > ModConfig.get().general.autoDespawnIdleSeconds * 20) {
                        this.discard(); // Safely despawn idle mounts
                    }
                }
                return;
            }
        }
        this.idleTicks = 0;

        super.tick();

        if (this.teleportImmunityTicks > 0) {
            this.teleportImmunityTicks--;
        }

        if (!this.level().isClientSide && this.teleportImmunityTicks > 0 && this.tickCount % 5 == 0 && this.isInWall() && this.getControllingPassenger() != null) {
            net.minecraft.core.BlockPos pos = this.blockPosition();
            for (int dy = 0; dy < 4; dy++) {
                net.minecraft.core.BlockPos targetPos = pos.above(dy);
                if (this.level().getBlockState(targetPos).getCollisionShape(this.level(), targetPos).isEmpty() &&
                    this.level().getBlockState(targetPos.above()).getCollisionShape(this.level(), targetPos.above()).isEmpty()) {
                    this.teleportTo(this.getX(), targetPos.getY() + 0.01, this.getZ());
                    this.resetFallDistance();
                    break;
                }
            }
        }

        // Tick and handle custom synced states (Hover & Stealth)
        int hover = this.entityData.get(HOVER_TICKS);
        if (hover > 0) {
            this.entityData.set(HOVER_TICKS, hover - 1);
            // Suspend vertical movement / gravity
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x, 0.0D, motion.z);
            this.resetFallDistance();
        }

        int stealth = this.entityData.get(STEALTH_TICKS);
        if (stealth > 0) {
            this.entityData.set(STEALTH_TICKS, stealth - 1);
            this.setSilent(true);
            Entity passenger = this.getControllingPassenger();
            if (passenger != null) {
                passenger.setSilent(true);
            }
            if (stealth - 1 == 0) {
                this.setSilent(false);
                if (passenger != null) {
                    passenger.setSilent(false);
                }
            }
        }

        // Passive Abilities Ticking (Throttled & Staggered for performance)
        if (!this.level().isClientSide) {
            if (this.tickCount % 20 == 0) {
                Entity passenger = this.getControllingPassenger();
                if (passenger instanceof LivingEntity living) {
                    if (this.hasFireproofScalesActive) {
                        living.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
                    }
                    if (this.hasGillsOfTheDeepActive) {
                        living.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false, true));
                    }
                    if (this.hasNightEyesActive) {
                        living.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, false, false, true));
                    }
                    if (this.hasRejuvenationAuraActive && this.getStamina() > 50.0F) {
                        living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false, true));
                    }
                }
                
                if (this.hasFireproofScalesActive) {
                    this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
                }
                if (this.hasRejuvenationAuraActive) {
                    this.heal(0.5F);
                }
                if (this.hasGlacialAuraActive) {
                    double r = 4.0D;
                    for (LivingEntity livingTarget : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(r), e -> e != this && !(e instanceof Player))) {
                        livingTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
                    }
                }
                if (this.hasMagnetosphereActive) {
                    double r = 5.0D;
                    if (passenger instanceof Player player) {
                        for (net.minecraft.world.entity.item.ItemEntity item : this.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, this.getBoundingBox().inflate(r))) {
                            if (item.isAlive()) {
                                item.setNoPickUpDelay();
                                Vec3 pullVec = player.position().subtract(item.position()).normalize().scale(0.3D);
                                item.setDeltaMovement(item.getDeltaMovement().add(pullVec));
                            }
                        }
                    }
                }
            }

            if (this.tickCount % 10 == 0 && this.hasPhotosynthesisActive) {
                if (this.level().isDay() && this.level().canSeeSky(this.blockPosition())) {
                    MountData data = MountRegistry.getTemplate(getTemplateId());
                    if (data != null) {
                        float maxStam = (float) data.stats.maxStamina;
                        float extra = (float) (data.stats.staminaRecoveryRate * 0.3 * 0.5);
                        this.setStamina(Math.min(maxStam, getStamina() + extra));
                    }
                }
            }
        }

        // Active animation tick counter and distance/XP tracking
        if (!this.level().isClientSide) {
            if (this.isVehicle()) {
                Entity passenger = this.getControllingPassenger();
                if (passenger instanceof Player) {
                    double dist = this.getDeltaMovement().horizontalDistance();
                    DatabaseManager.UnlockedMountData uData = getUnlockedData();
                    if (uData != null) {
                        uData.distanceTravelled += dist;
                        uData.dirty = true;
                        if (ModConfig.get().bondingAndLeveling.enableMountLevelling) {
                            double ridingXp = ModConfig.get().bondingAndLeveling.ridingXpPerSecond / 20.0;
                            addXp(ridingXp);
                        }
                    }
                }
            }

            if (activeAnimationTicks > 0) {
                activeAnimationTicks--;
                if (activeAnimationTicks == 0) {
                    setActiveAnimation("");
                }
            }

            if (this.tickCount % 40 == 0 && !this.getPassengers().isEmpty()) {
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.getChunkSource().broadcast(this, new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(this));
                }
            }
        }

        // Flight Banking
        if (!this.level().isClientSide) {
            MountData data = MountRegistry.getTemplate(getTemplateId());
            if (data != null) {
                boolean canBank = (data.category.equalsIgnoreCase("FLYING") && !this.onGround()) || data.category.equalsIgnoreCase("AQUATIC");
                float targetRoll = 0.0F;
                if (canBank) {
                    float yawDiff = Mth.wrapDegrees(this.getYRot() - this.yRotO);
                    targetRoll = -yawDiff * 2.5F;
                    targetRoll = Mth.clamp(targetRoll, -25.0F, 25.0F);
                }
                float newRoll = Mth.lerp(0.2F, this.getRoll(), targetRoll);
                if (Math.abs(newRoll) < 0.01F) newRoll = 0.0F;
                this.setRoll(newRoll);
            }
        }

        // Regenerate stamina and tick cooldowns
        if (!this.level().isClientSide) {
            MountData data = MountRegistry.getTemplate(getTemplateId());
            if (data != null) {
                float maxStamina = (float) data.stats.maxStamina;
                float current = getStamina();
                
                BlockPos below = this.blockPosition().below();
                BlockState stateBelow = this.level().getBlockState(below);
                boolean isAirOrLiquidBelow = stateBelow.isAir() || !stateBelow.getFluidState().isEmpty();
                boolean isFlying = data.category.equalsIgnoreCase("FLYING") && (isAirOrLiquidBelow || !this.onGround());
                
                double staminaReduction = getEnhancerModifier("ability", "stamina_cost_reduction");
                double cargoPenalty = 0.0;
                if (!this.getInventory().getItem(2).isEmpty()) {
                    cargoPenalty = 0.30; // 30% baseline penalty for carrying cargo
                    if (this.hasCargoCushionActive) {
                        cargoPenalty *= 0.5; // reduces penalty by 50%
                    }
                }
                boolean isAquaticInFluid = data.category.equalsIgnoreCase("AQUATIC") && (this.isInWater() || this.isInLava());
                if (isAquaticInFluid) {
                    boolean hasInput = false;
                    LivingEntity driver = this.getControllingPassenger();
                    if (driver != null) {
                        hasInput = (driver.xxa != 0.0F || driver.zza != 0.0F || this.inputFlyUp || this.inputFlyDown);
                    }
                    if (hasInput) {
                        double cost = 6.0 * (1.0 - staminaReduction) * (1.0 + cargoPenalty);
                        current = (float) Math.max(0.0, current - (cost / 20.0));
                    } else {
                        float recovery = (float) data.stats.staminaRecoveryRate / 20.0f;
                        if (getBonding() >= 50) recovery *= 1.25f;
                        current = Math.min(maxStamina, current + recovery);
                    }
                } else if ((isSprinting || this.inputSprint) && this.isVehicle()) {
                    double cost = ModConfig.get().stamina.sprint_stamina_cost_per_second * (1.0 - staminaReduction) * (1.0 + cargoPenalty);
                    current = (float) Math.max(0.0, current - (cost / 20.0));
                    if (current <= 0) {
                        isSprinting = false;
                    }
                } else if (isFlying && this.isVehicle()) {
                    if (this.inputFlyDown) {
                        float recovery = (float) data.stats.staminaRecoveryRate / 20.0f;
                        if (getBonding() >= 50) recovery *= 1.25f;
                        double ratio = ModConfig.get().stamina.flight_descent_stamina_regenerate_ratio;
                        current = (float) Math.min(maxStamina, current + (recovery * ratio));
                    } else {
                        double cost = ModConfig.get().stamina.flight_stamina_cost_per_second * (1.0 - staminaReduction) * (1.0 + cargoPenalty);
                        current = (float) Math.max(0.0, current - (cost / 20.0));
                    }
                } else {
                    float recovery = (float) data.stats.staminaRecoveryRate / 20.0f;
                    if (getBonding() >= 50) recovery *= 1.25f; // bonding perk: +25% recovery
                    current = Math.min(maxStamina, current + recovery);
                }
                setStamina(current);

                if (ability1Cooldown > 0) ability1Cooldown--;
                if (ability2Cooldown > 0) ability2Cooldown--;

                // Aquatic breathing check (throttled to every 10 ticks to avoid effect object allocation spam)
                if (data.category.equalsIgnoreCase("AQUATIC") && this.isVehicle() && this.tickCount % 10 == 0) {
                    for (Entity rider : this.getPassengers()) {
                        if (rider instanceof LivingEntity living) {
                            MobEffectInstance active = living.getEffect(MobEffects.WATER_BREATHING);
                            if (active == null || active.getDuration() < 30) {
                                living.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, false, false, true));
                            }
                        }
                    }
                }
            }
        }

        // Speed-Based Particle Trails
        if (this.level().isClientSide) {
            MountData data = MountRegistry.getTemplate(getTemplateId());
            if (data != null) {
                // Client-side passive particles
                if (this.hasRejuvenationAuraActive && this.tickCount % 40 == 0) {
                    this.level().addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, 
                        this.getRandomX(0.8), this.getRandomY() + 0.2, this.getRandomZ(0.8), 
                        0.0, 0.0, 0.0);
                }
                if (this.hasFireproofScalesActive && this.isInLava() && this.tickCount % 5 == 0) {
                    this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, 
                        this.getRandomX(0.8), this.getRandomY(), this.getRandomZ(0.8), 
                        0.0, 0.02, 0.0);
                }
                if (this.hasShadowCamouflageActive && this.tickCount % 10 == 0) {
                    if (this.level().getMaxLocalRawBrightness(this.blockPosition()) < 5) {
                        this.level().addParticle(net.minecraft.core.particles.ParticleTypes.ASH, 
                            this.getRandomX(0.8), this.getRandomY() + 0.2, this.getRandomZ(0.8), 
                            0.0, 0.0, 0.0);
                    }
                }
                if (this.hasSpiderClimbActive && this.horizontalCollision && this.tickCount % 5 == 0) {
                    this.level().addParticle(net.minecraft.core.particles.ParticleTypes.CRIT, 
                        this.getRandomX(0.5), this.getY() + 0.1, this.getRandomZ(0.5), 
                        0.0, 0.0, 0.0);
                }

                double posDiffSq = (this.getX() - this.xo) * (this.getX() - this.xo) + (this.getZ() - this.zo) * (this.getZ() - this.zo);
                double speedSq = Math.max(this.getDeltaMovement().horizontalDistanceSqr(), posDiffSq);
                boolean isMovingFast = speedSq > 0.008 || this.isSprinting();
                double vx = Math.abs(this.getDeltaMovement().x) > 0.001 ? this.getDeltaMovement().x : (this.getX() - this.xo);
                double vz = Math.abs(this.getDeltaMovement().z) > 0.001 ? this.getDeltaMovement().z : (this.getZ() - this.zo);
                if (isMovingFast) {
                    if (data.category.equalsIgnoreCase("GROUND") && this.onGround()) {
                        String pName = data.groundParticle;
                        net.minecraft.core.particles.ParticleOptions part = getParticleOption(pName != null && !pName.isEmpty() ? pName : "minecraft:crit");
                        if (part != null) {
                            this.level().addParticle(part, 
                                this.getRandomX(0.5), this.getY(), this.getRandomZ(0.5), 
                                0.0, 0.0, 0.0);
                        }
                    } else if (data.category.equalsIgnoreCase("FLYING") && !this.onGround()) {
                        String pName = data.flightParticle;
                        net.minecraft.core.particles.ParticleOptions part = getParticleOption(pName != null && !pName.isEmpty() ? pName : "minecraft:cloud");
                        if (part != null) {
                            float scale = data.scale;
                            double yawRad = this.getYRot() * Mth.DEG_TO_RAD;
                            double leftX = this.getX() + (1.5 * scale * Math.cos(yawRad) - 0.5 * scale * Math.sin(yawRad));
                            double leftZ = this.getZ() + (1.5 * scale * Math.sin(yawRad) + 0.5 * scale * Math.cos(yawRad));
                            double rightX = this.getX() + (-1.5 * scale * Math.cos(yawRad) - 0.5 * scale * Math.sin(yawRad));
                            double rightZ = this.getZ() + (-1.5 * scale * Math.sin(yawRad) + 0.5 * scale * Math.cos(yawRad));
                            
                            this.level().addParticle(part, 
                                leftX, this.getY() + 0.5 * scale, leftZ, 
                                -vx * 0.2, 0.0, -vz * 0.2);
                            this.level().addParticle(part, 
                                rightX, this.getY() + 0.5 * scale, rightZ, 
                                -vx * 0.2, 0.0, -vz * 0.2);
                        }
                    } else if ((data.category.equalsIgnoreCase("AQUATIC") || data.category.equalsIgnoreCase("SURFACE_WATER")) && this.isInWater()) {
                        float scale = data.scale;
                        double yawRad = this.getYRot() * Mth.DEG_TO_RAD;
                        double backX = this.getX() - 1.2 * scale * Math.sin(yawRad);
                        double backZ = this.getZ() + 1.2 * scale * Math.cos(yawRad);
                        String pName = data.groundParticle;
                        net.minecraft.core.particles.ParticleOptions part = getParticleOption(pName != null && !pName.isEmpty() ? pName : "minecraft:bubble");
                        if (part != null) {
                            this.level().addParticle(part, 
                                backX, this.getY() + 0.5 * scale, backZ, 
                                -vx * 0.5, 0.0, -vz * 0.5);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        super.teleportTo(x, y, z);
        this.teleportImmunityTicks = 40;
        this.resetFallDistance();
        for (Entity passenger : this.getPassengers()) {
            passenger.resetFallDistance();
        }
    }

    @Override
    public Entity changeDimension(net.minecraft.server.level.ServerLevel destination) {
        Entity entity = super.changeDimension(destination);
        if (entity instanceof RPGMountEntity mount) {
            mount.teleportImmunityTicks = 40;
            mount.resetFallDistance();
            for (Entity passenger : mount.getPassengers()) {
                passenger.resetFallDistance();
            }
        }
        return entity;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.getControllingPassenger() != null) {
            if (!this.getNavigation().isDone()) {
                this.getNavigation().stop();
            }
            this.setTarget(null);
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (!this.isAlive()) return;

        LivingEntity driver = this.getControllingPassenger();
        if (this.isVehicle() && driver != null) {
            MountData data = MountRegistry.getTemplate(getTemplateId());
            boolean isFlyingOrAquatic = data != null && (data.category.equalsIgnoreCase("FLYING") || data.category.equalsIgnoreCase("AQUATIC"));
            if (!isFlyingOrAquatic) {
                this.setYRot(driver.getYRot());
            }
            
            // Calculate pitch based on vertical velocity for flying/aquatic mounts
            float targetXRot = 0.0F;
            if (data != null && (data.category.equalsIgnoreCase("FLYING") || data.category.equalsIgnoreCase("AQUATIC"))) {
                double verticalSpeed = this.getDeltaMovement().y;
                targetXRot = (float) (-verticalSpeed * 50.0F);
                targetXRot = Mth.clamp(targetXRot, -30.0F, 30.0F);
            } else {
                targetXRot = driver.getXRot() * 0.5F;
            }
            this.setXRot(Mth.rotLerp(0.15F, this.getXRot(), targetXRot));
            
            this.setRot(this.getYRot(), this.getXRot());
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.yBodyRot;

            float strafe = driver.xxa * 0.5F;
            float forward = driver.zza;

            if (forward <= 0.0F) {
                if (this.hasTractionTreadActive) {
                    forward *= 0.75F;
                } else {
                    forward *= 0.25F;
                }
            }

            if (this.hasDeepDiverActive && this.isInWater() && data != null && !data.category.equalsIgnoreCase("AQUATIC")) {
                strafe *= 1.4F;
                forward *= 1.4F;
            }

            // Sprinting
            boolean allowSprinting = ModConfig.get().stamina.allow_saddleless_sprinting || this.hasSaddle();
            boolean isSprintingInput = isFlyingOrAquatic ? this.inputSprint : (this.inputSprint || driver.isSprinting());
            if (allowSprinting && isSprintingInput && getStamina() > 5.0f) {
                if (!isSprinting) {
                    isSprinting = true;
                    this.setSprinting(true);
                    var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speedAttr != null && !speedAttr.hasModifier(SPRINT_MODIFIER)) {
                        speedAttr.addTransientModifier(SPRINT_MODIFIER);
                    }
                }
                forward *= 1.3f;
            } else {
                if (isSprinting) {
                    isSprinting = false;
                    this.setSprinting(false);
                    var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speedAttr != null && speedAttr.hasModifier(SPRINT_MODIFIER)) {
                        speedAttr.removeModifier(SPRINT_MODIFIER);
                    }
                }
            }

            if (data != null) {
                // Handle categories
                if (data.category.equalsIgnoreCase("FLYING")) {
                    double activeSpeed;
                    if (this.onGround() && !inputFlyUp) {
                        activeSpeed = this.getAttributeValue(Attributes.MOVEMENT_SPEED);
                    } else {
                        double flySpeed = data.stats.flySpeed + getSpeedGrowth() * 0.5 + this.flySpeedModifier;
                        if (isSprinting) {
                            flySpeed *= 1.3;
                        }
                        activeSpeed = flySpeed;
                    }
                    
                    if (ModConfig.get().general.enableSpeedPulsing && (forward != 0.0F || strafe != 0.0F)) {
                        double pulse = 0.85 + 0.3 * Math.sin(this.tickCount * 0.20) * Math.sin(this.tickCount * 0.20);
                        activeSpeed *= pulse;
                    }
                    double verticalSpeed = 0.0;

                    // Stamina depletion forces glide
                    if (getStamina() <= 0.0f) {
                        if (inputFlyDown) {
                            verticalSpeed = -0.4; // Fast forced descent
                        } else {
                            verticalSpeed = -0.2; // Decent pace descent
                        }
                    } else {
                        if (inputFlyUp) verticalSpeed = 0.25;
                        else if (inputFlyDown) verticalSpeed = -0.25;
                    }

                    // Calculate movement relative to driver's rotation
                    Vec3 dir = new Vec3(strafe, verticalSpeed, forward).yRot(-driver.getYRot() * Mth.DEG_TO_RAD);
                    this.setDeltaMovement(dir.x * activeSpeed * 4, dir.y, dir.z * activeSpeed * 4);

                    // Interpolate yaw based on movement
                    float targetYaw;
                    double dx = dir.x;
                    double dz = dir.z;
                    if (dx * dx + dz * dz > 0.001) {
                        targetYaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
                    } else {
                        targetYaw = driver.getYRot();
                    }
                    float interpolatedYaw = Mth.rotLerp(0.1F, this.getYRot(), targetYaw);
                    this.setYRot(interpolatedYaw);

                    this.setRot(this.getYRot(), this.getXRot());
                    this.yBodyRot = this.getYRot();
                    this.yHeadRot = this.yBodyRot;

                    this.resetFallDistance();

                    this.move(MoverType.SELF, this.getDeltaMovement());
                    return;
                } else if (data.category.equalsIgnoreCase("AQUATIC")) {
                    if (this.isInWater() || this.isInLava()) {
                        double swimSpeed = data.stats.swimSpeed + getSpeedGrowth() * 0.5 + this.swimSpeedModifier;
                        if (this.hasDeepDiverActive) {
                            swimSpeed *= 1.4;
                        }
                        if (isSprinting) {
                            swimSpeed *= 1.3;
                        }
                        if (ModConfig.get().general.enableSpeedPulsing && (forward != 0.0F || strafe != 0.0F)) {
                            double pulse = 0.8 + 0.4 * Math.sin(this.tickCount * 0.30) * Math.sin(this.tickCount * 0.30);
                            swimSpeed *= pulse;
                        }
                        if (this.getStamina() <= 0.0f) {
                            swimSpeed *= 0.3; // moves much slower when out of stamina
                        }
                        double verticalSpeed = 0.0;
                        if (inputFlyUp) verticalSpeed = 0.25;
                        else if (inputFlyDown) verticalSpeed = -0.25;

                        // Calculate movement relative to driver's rotation
                        Vec3 dir = new Vec3(strafe, verticalSpeed, forward).yRot(-driver.getYRot() * Mth.DEG_TO_RAD);
                        this.setDeltaMovement(dir.x * swimSpeed * 3, dir.y, dir.z * swimSpeed * 3);

                        // Interpolate yaw based on movement
                        float targetYaw;
                        double dx = dir.x;
                        double dz = dir.z;
                        if (dx * dx + dz * dz > 0.001) {
                            targetYaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
                        } else {
                            targetYaw = driver.getYRot();
                        }
                        float interpolatedYaw = Mth.rotLerp(0.1F, this.getYRot(), targetYaw);
                        this.setYRot(interpolatedYaw);

                        this.setRot(this.getYRot(), this.getXRot());
                        this.yBodyRot = this.getYRot();
                        this.yHeadRot = this.yBodyRot;

                        this.move(MoverType.SELF, this.getDeltaMovement());
                        return;
                    } else {
                        // Out of water/lava: no movement, just gravity/fall
                        Vec3 currentMovement = this.getDeltaMovement();
                        double gravity = -0.08;
                        this.setDeltaMovement(0.0, currentMovement.y + gravity, 0.0);
                        this.move(MoverType.SELF, this.getDeltaMovement());
                        return;
                    }
                } else if (data.category.equalsIgnoreCase("SURFACE_WATER")) {
                    if (this.isInWater() || this.isInLava()) {
                        double swimSpeed = data.stats.swimSpeed + getSpeedGrowth() * 0.5 + this.swimSpeedModifier;
                        if (isSprinting) {
                            swimSpeed *= 1.3;
                        }
                        if (ModConfig.get().general.enableSpeedPulsing && (forward != 0.0F || strafe != 0.0F)) {
                            double pulse = 0.8 + 0.4 * Math.sin(this.tickCount * 0.30) * Math.sin(this.tickCount * 0.30);
                            swimSpeed *= pulse;
                        }
                        if (this.getStamina() <= 0.0f) {
                            swimSpeed *= 0.3;
                        }

                        // Buoyancy: if submerged, float up; if at surface, stay at surface (y = 0.0)
                        double verticalSpeed = 0.0;
                        net.minecraft.world.level.material.FluidState fluidAbove = this.level().getFluidState(this.blockPosition().above());
                        if (fluidAbove.is(net.minecraft.tags.FluidTags.WATER) || fluidAbove.is(net.minecraft.tags.FluidTags.LAVA)) {
                            verticalSpeed = 0.15; // float to surface
                        } else {
                            // Align slightly to surface: if entity origin is too deep, apply minor correction
                            double waterHeight = this.level().getFluidState(this.blockPosition()).getHeight(this.level(), this.blockPosition());
                            double surfaceY = this.blockPosition().getY() + waterHeight;
                            if (this.getY() < surfaceY - 0.1) {
                                verticalSpeed = 0.08;
                            }
                        }

                        Vec3 dir = new Vec3(strafe, verticalSpeed, forward).yRot(-driver.getYRot() * Mth.DEG_TO_RAD);
                        this.setDeltaMovement(dir.x * swimSpeed * 3, dir.y, dir.z * swimSpeed * 3);

                        float targetYaw;
                        double dx = dir.x;
                        double dz = dir.z;
                        if (dx * dx + dz * dz > 0.001) {
                            targetYaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
                        } else {
                            targetYaw = driver.getYRot();
                        }
                        float interpolatedYaw = Mth.rotLerp(0.1F, this.getYRot(), targetYaw);
                        this.setYRot(interpolatedYaw);

                        this.setRot(this.getYRot(), this.getXRot());
                        this.yBodyRot = this.getYRot();
                        this.yHeadRot = this.yBodyRot;

                        this.move(MoverType.SELF, this.getDeltaMovement());
                        return;
                    }
                }
            }

            double yMove = travelVector.y;
            if (this.hasSpiderClimbActive && this.horizontalCollision && forward > 0.0F) {
                yMove = 0.2D;
            }
            float finalSpeed = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
            if (data != null && data.category.equalsIgnoreCase("SURFACE_WATER")) {
                finalSpeed *= 0.3F;
            }
            this.setSpeed(finalSpeed);
            super.travel(new Vec3(strafe, yMove, forward));
        } else {
            if (isSprinting) {
                isSprinting = false;
                this.setSprinting(false);
                var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null && speedAttr.hasModifier(SPRINT_MODIFIER)) {
                    speedAttr.removeModifier(SPRINT_MODIFIER);
                }
            }
            double yMove = travelVector.y;
            if (this.hasSpiderClimbActive && this.horizontalCollision && travelVector.z > 0.0D) {
                yMove = 0.2D;
            }
            super.travel(new Vec3(travelVector.x, yMove, travelVector.z));
        }
    }

    private static Vec3 zRot(Vec3 vec, float roll) {
        float f = Mth.cos(roll);
        float f1 = Mth.sin(roll);
        double d0 = vec.x * (double)f - vec.y * (double)f1;
        double d1 = vec.y * (double)f + vec.x * (double)f1;
        double d2 = vec.z;
        return new Vec3(d0, d1, d2);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        MountData data = null;
        if (this.level().isClientSide) {
            net.minecraft.client.gui.screens.Screen currentScreen = net.minecraft.client.Minecraft.getInstance().screen;
            if (currentScreen instanceof ddraig.net.rpgmounts.client.gui.MountCreatorScreen creatorScreen) {
                data = creatorScreen.getSelectedTemplate();
            }
        }
        if (data == null) {
            data = MountRegistry.getTemplate(getTemplateId());
        }
        if (data != null && !data.seats.isEmpty()) {
            int index = this.getPassengers().indexOf(passenger);
            if (index >= 0 && index < data.seats.size()) {
                MountData.SeatOffset offset = data.seats.get(index);
                Vec3 vec = new Vec3(offset.x, offset.y, offset.z);
                
                // Scale seat offsets dynamically with mount scale
                vec = vec.scale(data.scale);
                
                // Apply 3D rotation: Pitch (around X), Roll (around Z), Yaw (around Y)
                float pitch = this.getXRot();
                float roll = this.getRoll();
                float yaw = this.getYRot();
                
                vec = vec.xRot(pitch * Mth.DEG_TO_RAD);
                vec = zRot(vec, roll * Mth.DEG_TO_RAD);
                vec = vec.yRot(-yaw * Mth.DEG_TO_RAD);
                
                moveFunction.accept(passenger, this.getX() + vec.x, this.getY() + vec.y, this.getZ() + vec.z);
            }
        }
    }

    public static Vec3 getClientBoneOffset(RPGMountEntity mount, String boneName) {
        try {
            Object dispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
            Object renderer = ((net.minecraft.client.renderer.entity.EntityRenderDispatcher) dispatcher).getRenderer(mount);
            if (renderer == null) {
                return null;
            }
            Object geoRenderer = null;
            if (renderer instanceof ddraig.net.rpgmounts.client.renderer.RPGMountRenderer rpgRenderer) {
                geoRenderer = rpgRenderer.getGeckoLibRenderer();
            }
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
                        if (rootBones instanceof java.util.Collection) {
                            for (Object rootObj : (java.util.Collection<?>) rootBones) {
                                bone = findBoneRecursive(rootObj, boneName);
                                if (bone != null) {
                                    break;
                                }
                            }
                        }
                        if (bone != null) {
                            return computeBoneOffset(bone);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
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

    private static Vec3 computeBoneOffset(Object bone) throws Exception {
        Class<?> boneClass = bone.getClass();
        java.lang.reflect.Method getModelSpaceMatrix = boneClass.getMethod("getModelSpaceMatrix");
        Object matrixObj = getModelSpaceMatrix.invoke(bone);
        if (matrixObj instanceof org.joml.Matrix4f matrix) {
            org.joml.Vector3f translation = matrix.getTranslation(new org.joml.Vector3f());
            return new Vec3(translation.x / 16.0, translation.y / 16.0, translation.z / 16.0);
        }
        return null;
    }

    public void positionRidersPublic() {
        for (Entity passenger : this.getPassengers()) {
            this.positionRider(passenger);
        }
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity firstPassenger = this.getFirstPassenger();
        return firstPassenger instanceof LivingEntity ? (LivingEntity) firstPassenger : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        MountData data = MountRegistry.getTemplate(getTemplateId());
        if (data != null && !data.seats.isEmpty()) {
            return this.getPassengers().size() < data.seats.size();
        }
        return this.getPassengers().size() < 1;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getVehicle() == this) {
            player.stopRiding();
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (!this.level().isClientSide) {

            ItemStack held = player.getItemInHand(hand);
            MountData data = MountRegistry.getTemplate(getTemplateId());
            if (data != null) {
                String heldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                // 1. Feeding
                if (data.allowed_food_map.containsKey(heldId)) {
                    if (this.getBonding() < 100) {
                        int add = data.allowed_food_map.get(heldId);
                        if (this.hasBondingBoostActive) {
                            add = (int) (add * 1.5);
                        }
                        int newBonding = Math.min(100, this.getBonding() + add);
                        this.setBonding(newBonding);
                        if (!player.getAbilities().instabuild) {
                            held.shrink(1);
                        }
                        this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0f, 1.0f);
                        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, this.getX(), this.getY() + 0.5, this.getZ(), 7, 0.3, 0.3, 0.3, 0.1);
                        }
                        if (this.ownerUuid != null) {
                            DatabaseManager.UnlockedMountData existingData = getUnlockedData();
                            String saveInstanceId = (existingData != null) ? existingData.instanceId : this.getInstanceId();
                            this.setInstanceId(saveInstanceId);
                            DatabaseManager.saveUnlockedMountAsync(this.ownerUuid, saveInstanceId, this.getTemplateId(), newBonding, this.getCustomName() != null ? this.getCustomName().getString() : "");
                            if (player instanceof ServerPlayer serverPlayer) {
                                ModPackets.syncUnlockedMounts(serverPlayer);
                            }
                        }
                        return InteractionResult.SUCCESS;
                    } else {
                        player.sendSystemMessage(Component.literal("Your mount is already fully bonded!"));
                        return InteractionResult.SUCCESS;
                    }
                }

                // 2. Direct cargo attachment in-world
                if (data.allowed_cargo_map.containsKey(heldId)) {
                    ItemStack currentCargo = getInventory().getItem(2);
                    if (currentCargo.isEmpty()) {
                        ItemStack cargoStack = held.copy();
                        cargoStack.setCount(1);
                        getInventory().setItem(2, cargoStack);
                        if (!player.getAbilities().instabuild) {
                            held.shrink(1);
                        }
                        this.level().playSound(null, this.blockPosition(), SoundEvents.DONKEY_CHEST, SoundSource.NEUTRAL, 1.0f, 1.0f);
                        return InteractionResult.SUCCESS;
                    }
                }
            }

            // Open custom Mount Gear screen on sneak-right-click
            if (player.isSecondaryUseActive()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                    buf.writeInt(this.getId());
                    NetworkManager.sendToPlayer(serverPlayer, ModPackets.S2C_OPEN_GEAR, buf);
                }
                return InteractionResult.SUCCESS;
            }

            // Ride mount
            int maxSeats = 1;
            if (data != null && !data.seats.isEmpty()) {
                maxSeats = data.seats.size();
            }
            if (this.getPassengers().size() < maxSeats) {
                player.startRiding(this);
                if (data != null && data.category.equalsIgnoreCase("FLYING")) {
                    player.displayClientMessage(Component.literal("§aFlying Mount: Right-click mount to dismount. Space/Ctrl to Fly Up/Down."), true);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    public List<MountData.AbilityData> getAvailableAbilities() {
        List<MountData.AbilityData> list = new java.util.ArrayList<>();
        MountData data = MountRegistry.getTemplate(getTemplateId());
        if (data != null) {
            list.addAll(data.availableAbilities);
        }
        // Add enhancer-granted abilities
        for (int i = 3; i < 7; i++) {
            ItemStack stack = getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == ddraig.net.rpgmounts.registry.ModItems.ABILITY_ENHANCER.get()) {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("EnhancerType") && tag.getString("EnhancerType").equalsIgnoreCase("grant_ability")) {
                    if (tag.contains("EnhancerAbility")) {
                        String name = tag.getString("EnhancerAbility");
                        MountData.AbilityData ab = MountRegistry.customAbilities.get(name);
                        if (ab != null && !list.contains(ab)) {
                            list.add(ab);
                        }
                    }
                }
            }
        }
        return list;
    }

    public void triggerAbility(int slot) {
        if (this.level().isClientSide) return;
        MountData data = MountRegistry.getTemplate(getTemplateId());
        if (data == null || !data.combat.enableCombat || !ModConfig.get().combatAndEnhancers.enable_combat_abilities) return;

        List<MountData.AbilityData> abilities = getAvailableAbilities();
        int idx = (slot == 1) ? getAbility1Index() : getAbility2Index();
        if (idx < 0 || idx >= abilities.size()) return;
        MountData.AbilityData ability = abilities.get(idx);
        int currentCooldown = (slot == 1) ? ability1Cooldown : ability2Cooldown;

        if (currentCooldown <= 0 && getStamina() >= ability.staminaCost) {
            setStamina((float) (getStamina() - ability.staminaCost));
            
            // Apply enhancers ability cooldown reduction
            double cdReduction = getEnhancerModifier("ability", "cooldown_reduction");
            int finalCooldown = (int) Math.max(20, ability.cooldownTicks * (1.0 - cdReduction));
            
            if (slot == 1) ability1Cooldown = finalCooldown;
            else ability2Cooldown = finalCooldown;

            double dmgBoost = getEnhancerModifier("damage", "damage_boost") + getPowerGrowth();
            double finalDamage = ability.damage + dmgBoost;
            double finalRange = ability.range;

            // Break stealth on attacking/using an ability
            if (this.entityData.get(STEALTH_TICKS) > 0) {
                this.entityData.set(STEALTH_TICKS, 0);
                this.setSilent(false);
                this.removeEffect(MobEffects.INVISIBILITY);
                Entity passenger = this.getControllingPassenger();
                if (passenger instanceof LivingEntity living) {
                    living.setSilent(false);
                    living.removeEffect(MobEffects.INVISIBILITY);
                }
            }

            boolean handled = false;
            if (ability.name.equalsIgnoreCase("Flame Breath")) {
                Vec3 look = this.getLookAngle();
                Vec3 headPos = this.getEyePosition();
                double range = finalRange;
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range, range, range));
                double totalDmg = 0.0;
                for (LivingEntity target : targets) {
                    if (target != this && !this.getPassengers().contains(target)) {
                        Vec3 toTarget = target.position().subtract(headPos).normalize();
                        double dot = look.dot(toTarget);
                        if (dot > 0.707) { // ~45 degree angle cone
                            if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                                target.setSecondsOnFire(5);
                                totalDmg += finalDamage;
                            }
                        }
                    }
                }
                if (totalDmg > 0) awardDamageDealtXp(totalDmg);
                
                // Spawn cone of flame particles
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 80; i++) {
                        double vx = look.x * 0.5 + (this.random.nextDouble() - 0.5) * 0.2;
                        double vy = look.y * 0.5 + (this.random.nextDouble() - 0.5) * 0.2;
                        double vz = look.z * 0.5 + (this.random.nextDouble() - 0.5) * 0.2;
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, headPos.x, headPos.y, headPos.z, 1, vx, vy, vz, 0.1);
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Tail Sweep")) {
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, 2.0, finalRange));
                double totalDmg = 0.0;
                for (LivingEntity target : targets) {
                    if (target != this && !this.getPassengers().contains(target)) {
                        if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                            double dx = target.getX() - this.getX();
                            double dz = target.getZ() - this.getZ();
                            target.knockback(1.5D, -dx, -dz);
                            totalDmg += finalDamage;
                        }
                    }
                }
                if (totalDmg > 0) awardDamageDealtXp(totalDmg);
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int angle = 0; angle < 360; angle += 6) {
                        double rad = Math.toRadians(angle);
                        double px = this.getX() + Math.cos(rad) * 2.5;
                        double pz = this.getZ() + Math.sin(rad) * 2.5;
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, px, this.getY() + 0.2, pz, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Venomous Bite")) {
                LivingEntity target = getNearestTargetInCone(3.0D, 0.707);
                if (target != null) {
                    if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                        target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1)); // Poison II, 5s
                        awardDamageDealtXp(finalDamage);
                        if (this.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR, target.getX(), target.getY(0.5), target.getZ(), 35, 0.3, 0.3, 0.3, 0.05);
                        }
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Sonic Screech")) {
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, finalRange, finalRange));
                double totalDmg = 0.0;
                for (LivingEntity target : targets) {
                    if (target != this && !this.getPassengers().contains(target)) {
                        if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1)); // Slowness II, 6s
                            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0)); // Weakness I, 6s
                            totalDmg += finalDamage;
                        }
                    }
                }
                if (totalDmg > 0) awardDamageDealtXp(totalDmg);
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, this.getX(), this.getY() + 1.0, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                    for (int angle = 0; angle < 360; angle += 20) {
                        double rad = Math.toRadians(angle);
                        double px = this.getX() + Math.cos(rad) * 4.0;
                        double pz = this.getZ() + Math.sin(rad) * 4.0;
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, px, this.getY() + 0.5, pz, 3, 0.1, 0.1, 0.1, 0.05);
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Thunder Stomp")) {
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, finalRange, finalRange));
                double totalDmg = 0.0;
                for (LivingEntity target : targets) {
                    if (target != this && !this.getPassengers().contains(target)) {
                        if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 9)); // Slowness X stun, 5s
                            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 4));
                            totalDmg += finalDamage;
                        }
                    }
                }
                if (totalDmg > 0) awardDamageDealtXp(totalDmg);
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Frost Nova")) {
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, finalRange, finalRange));
                double totalDmg = 0.0;
                for (LivingEntity target : targets) {
                    if (target != this && !this.getPassengers().contains(target)) {
                        if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 9)); // Lock in ice, 3s
                            totalDmg += finalDamage;
                        }
                    }
                }
                if (totalDmg > 0) awardDamageDealtXp(totalDmg);
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int r = 1; r <= 5; r++) {
                        for (int angle = 0; angle < 360; angle += 15) {
                            double rad = Math.toRadians(angle);
                            double px = this.getX() + Math.cos(rad) * r;
                            double pz = this.getZ() + Math.sin(rad) * r;
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, px, this.getY() + 0.2, pz, 1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Blight Spit")) {
                Vec3 look = this.getLookAngle();
                double spawnDist = this.getBbWidth() * 0.5D + 0.5D;
                Vec3 start = this.getEyePosition().add(look.scale(spawnDist));
                net.minecraft.world.entity.projectile.LlamaSpit spit = new net.minecraft.world.entity.projectile.LlamaSpit(net.minecraft.world.entity.EntityType.LLAMA_SPIT, this.level());
                spit.setOwner(this);
                spit.setPos(start.x, start.y, start.z);
                spit.shoot(look.x, look.y, look.z, 1.5F, 1.0F);
                this.level().addFreshEntity(spit);
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Fireball")) {
                Vec3 look = this.getLookAngle();
                double spawnDist = this.getBbWidth() * 0.5D + 0.5D;
                Vec3 start = this.getEyePosition().add(look.scale(spawnDist));
                net.minecraft.world.entity.projectile.SmallFireball fireball = new net.minecraft.world.entity.projectile.SmallFireball(this.level(), this, look.x, look.y, look.z);
                fireball.setPos(start.x, start.y, start.z);
                this.level().addFreshEntity(fireball);
                handled = true;
            } else if (ability.name.equalsIgnoreCase("High Jump")) {
                Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x, 0.95D, motion.z);
                this.hasImpulse = true;
                this.setActiveAnimation("jump");
                this.activeAnimationTicks = 40;
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Feather Hover")) {
                this.entityData.set(HOVER_TICKS, 100);
                this.resetFallDistance();
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Wind Glide")) {
                this.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0));
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Frightening Roar")) {
                List<PathfinderMob> hostileMobs = this.level().getEntitiesOfClass(PathfinderMob.class, this.getBoundingBox().inflate(finalRange), e -> e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof net.minecraft.world.entity.monster.Monster);
                for (PathfinderMob mob : hostileMobs) {
                    mob.setTarget(null);
                    Vec3 away = mob.position().subtract(this.position()).normalize().scale(12.0);
                    Vec3 targetPos = mob.position().add(away);
                    mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.5D);
                }
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 1.5, this.getZ(), 35, 0.5, 0.5, 0.5, 0.1);
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Healing Touch")) {
                this.heal(10.0F);
                Entity passenger = this.getControllingPassenger();
                if (passenger instanceof LivingEntity living) {
                    living.heal(4.0F);
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Life Steal Bite")) {
                LivingEntity target = getNearestTargetInCone(3.0D, 0.707);
                if (target != null) {
                    if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                        this.heal((float) (finalDamage * 0.5));
                        awardDamageDealtXp(finalDamage);
                        if (this.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.5), target.getZ(), 35, 0.2, 0.2, 0.2, 0.1);
                        }
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Lightning Strike")) {
                Vec3 look = this.getLookAngle();
                Vec3 start = this.getEyePosition();
                Vec3 end = start.add(look.scale(finalRange));
                net.minecraft.world.phys.HitResult hitResult = this.level().clip(new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
                Vec3 targetPos = hitResult.getLocation();
                
                net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(this.level(), this, start, end, this.getBoundingBox().expandTowards(look.scale(finalRange)).inflate(1.0), e -> e instanceof LivingEntity && e != this);
                if (entityHit != null) {
                    targetPos = entityHit.getEntity().position();
                    LivingEntity target = (LivingEntity) entityHit.getEntity();
                    if (target.hurt(this.damageSources().magic(), (float) finalDamage)) {
                        awardDamageDealtXp(finalDamage);
                    }
                }
                
                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(this.level());
                if (bolt != null) {
                    bolt.moveTo(targetPos);
                    this.level().addFreshEntity(bolt);
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Spore Blast")) {
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, finalRange, finalRange));
                double totalDmg = 0.0;
                for (LivingEntity target : targets) {
                    if (target != this && !this.getPassengers().contains(target)) {
                        if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0)); // Blindness, 4s
                            target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0)); // Poison, 4s
                            totalDmg += finalDamage;
                        }
                    }
                }
                if (totalDmg > 0) awardDamageDealtXp(totalDmg);
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 90; i++) {
                        double rx = this.getX() + (this.random.nextDouble() - 0.5) * finalRange;
                        double ry = this.getY() + 0.5 + (this.random.nextDouble() - 0.5) * 1.5;
                        double rz = this.getZ() + (this.random.nextDouble() - 0.5) * finalRange;
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR, rx, ry, rz, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Iron Wall")) {
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 2)); // Resistance III
                Entity passenger = this.getControllingPassenger();
                if (passenger instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 2));
                }
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 45; i++) {
                        double px = this.getX() + (this.random.nextDouble() - 0.5) * 1.5;
                        double py = this.getY() + 0.5 + this.random.nextDouble() * 1.0;
                        double pz = this.getZ() + (this.random.nextDouble() - 0.5) * 1.5;
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Iron Skin")) {
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ability.durationTicks, 1)); // Resistance II
                Entity passenger = this.getControllingPassenger();
                if (passenger instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ability.durationTicks, 1));
                }
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 45; i++) {
                        double px = this.getX() + (this.random.nextDouble() - 0.5) * 1.5;
                        double py = this.getY() + 0.5 + this.random.nextDouble() * 1.0;
                        double pz = this.getZ() + (this.random.nextDouble() - 0.5) * 1.5;
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Infernal Charge")) {
                Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot()).scale(ability.power * 1.5);
                this.setDeltaMovement(forward.x, 0.2, forward.z);
                this.hasImpulse = true;
                
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, 1.0, finalRange));
                double totalDmg = 0.0;
                for (LivingEntity target : targets) {
                    if (target != this && !this.getPassengers().contains(target)) {
                        if (target.hurt(this.damageSources().inFire(), (float) finalDamage)) {
                            target.setSecondsOnFire(4);
                            totalDmg += finalDamage;
                        }
                    }
                }
                if (totalDmg > 0) awardDamageDealtXp(totalDmg);
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 75; i++) {
                        double px = this.getX() + (this.random.nextDouble() - 0.5) * 1.5;
                        double py = this.getY() + this.random.nextDouble() * 1.0;
                        double pz = this.getZ() + (this.random.nextDouble() - 0.5) * 1.5;
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, px, py, pz, 1, 0.0, 0.05, 0.0, 0.02);
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, px, py, pz, 1, 0.0, 0.02, 0.0, 0.01);
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Sonic Dash")) {
                Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot()).scale(ability.power);
                this.setDeltaMovement(forward.x, 0.2, forward.z);
                this.hasImpulse = true;
                
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, 1.0, finalRange));
                double totalDmg = 0.0;
                for (LivingEntity target : targets) {
                    if (target != this && !this.getPassengers().contains(target)) {
                        if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                            totalDmg += finalDamage;
                        }
                    }
                }
                if (totalDmg > 0) awardDamageDealtXp(totalDmg);
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (double d = 0; d < ability.power; d += 1.0) {
                        double px = this.getX() + forward.x * (d / ability.power);
                        double pz = this.getZ() + forward.z * (d / ability.power);
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, px, this.getY() + 0.8, pz, 1, 0.0, 0.0, 0.0, 0.0);
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, px, this.getY() + 0.5, pz, 3, 0.1, 0.1, 0.1, 0.01);
                    }
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Teleport Dash")) {
                Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot()).scale(ability.power);
                double targetX = this.getX() + forward.x;
                double targetY = this.getY();
                double targetZ = this.getZ() + forward.z;
                
                this.teleportTo(targetX, targetY, targetZ);
                this.hasImpulse = true;
                this.resetFallDistance();
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, this.getX(), this.getY() + 0.5, this.getZ(), 35, 0.5, 0.5, 0.5, 0.1);
                }
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Aqua Propulsion") && data != null && data.category.equalsIgnoreCase("AQUATIC")) {
                this.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100, 2)); // Dolphins Grace III
                this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 2)); // Speed III
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Trample")) {
                Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot()).scale(ability.power);
                this.setDeltaMovement(forward.x, 0.2, forward.z);
                this.hasImpulse = true;
                
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, 1.0, finalRange));
                double totalDmg = 0.0;
                for (LivingEntity target : targets) {
                    if (target != this && !this.getPassengers().contains(target)) {
                        if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1)); // Slowness II, 3s
                            totalDmg += finalDamage;
                        }
                    }
                }
                if (totalDmg > 0) awardDamageDealtXp(totalDmg);
                handled = true;
            } else if (ability.name.equalsIgnoreCase("Abyssal Stealth")) {
                this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ability.durationTicks, 0));
                this.entityData.set(STEALTH_TICKS, ability.durationTicks);
                Entity passenger = this.getControllingPassenger();
                if (passenger instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ability.durationTicks, 0));
                }
                handled = true;
            }

            if (!handled) {
                // Execute custom action types
                if (ability.type.equalsIgnoreCase("DASH")) {
                    Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot()).scale(ability.power);
                    this.setDeltaMovement(forward.x, 0.3, forward.z);
                    this.hasImpulse = true;
                    
                    // Deal damage to entities in path
                    List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, 1.0, finalRange));
                    double totalDmg = 0.0;
                    for (LivingEntity target : targets) {
                        if (target != this && !this.getPassengers().contains(target)) {
                            if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                                totalDmg += finalDamage;
                            }
                        }
                    }
                    if (totalDmg > 0) {
                        awardDamageDealtXp(totalDmg);
                    }
                } else if (ability.type.equalsIgnoreCase("PROJECTILE")) {
                    // Trace line raycast and deal damage
                    Vec3 look = this.getLookAngle();
                    Vec3 start = this.getEyePosition();
                    Vec3 end = start.add(look.scale(finalRange));
                    
                    net.minecraft.world.phys.EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(this.level(), this, start, end, this.getBoundingBox().expandTowards(look.scale(finalRange)).inflate(1.0), e -> e instanceof LivingEntity && e != this);
                    if (entityHit != null) {
                        LivingEntity target = (LivingEntity) entityHit.getEntity();
                        if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                            awardDamageDealtXp(finalDamage);
                        }
                    }
                } else if (ability.type.equalsIgnoreCase("AOE")) {
                    List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(finalRange, finalRange, finalRange));
                    double totalDmg = 0.0;
                    for (LivingEntity target : targets) {
                        if (target != this && !this.getPassengers().contains(target)) {
                            if (target.hurt(this.damageSources().mobAttack(this), (float) finalDamage)) {
                                totalDmg += finalDamage;
                            }
                        }
                    }
                    if (totalDmg > 0) {
                        awardDamageDealtXp(totalDmg);
                    }
                } else if (ability.type.equalsIgnoreCase("BUFF")) {
                    this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ability.durationTicks, 1));
                    if (this.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, this.getX(), this.getY() + 1.0, this.getZ(), 35, 0.3, 0.3, 0.3, 0.1);
                    }
                } else if (ability.type.equalsIgnoreCase("STEALTH")) {
                    this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ability.durationTicks, 0));
                }
            }

            // Play sound and particles
            if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                if (!ability.particle.isEmpty() && !handled) {
                    try {
                        net.minecraft.core.particles.ParticleOptions part = (net.minecraft.core.particles.ParticleOptions) net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(new net.minecraft.resources.ResourceLocation(ability.particle));
                        if (part != null) {
                            serverLevel.sendParticles(part, this.getX(), this.getY() + 0.5, this.getZ(), ability.particleCount, 0.5, 0.5, 0.5, 0.1);
                        }
                    } catch (Exception e) {}
                }
                if (!ability.sound.isEmpty()) {
                    this.playMountSound(ability.sound, 1.0f, 1.0f);
                } else {
                    this.playMountSound("minecraft:entity.ender_dragon.growl", 1.0f, 1.2f);
                }
            }

            // Trigger animation sync
            String anim = ability.vanillaAnimation.equalsIgnoreCase("NONE") ? ability.animationName : ability.vanillaAnimation;
            if (anim.isEmpty() || anim.equalsIgnoreCase("NONE")) {
                anim = "attack";
            }
            setActiveAnimation(anim);
            this.activeAnimationTicks = ability.durationTicks > 0 ? ability.durationTicks : 20;
        }
    }

    private LivingEntity getNearestTargetInCone(double range, double dotThreshold) {
        Vec3 look = this.getLookAngle();
        Vec3 start = this.getEyePosition();
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range, range, range));
        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (LivingEntity target : targets) {
            if (target != this && !this.getPassengers().contains(target)) {
                Vec3 toTarget = target.position().subtract(start).normalize();
                double dot = look.dot(toTarget);
                if (dot > dotThreshold) {
                    double distSq = this.distanceToSqr(target);
                    if (distSq < nearestDistSq) {
                        nearest = target;
                        nearestDistSq = distSq;
                    }
                }
            }
        }
        return nearest;
    }

    public int getStealthTicks() {
        return this.entityData.get(STEALTH_TICKS);
    }

    public void setStealthTicks(int ticks) {
        this.entityData.set(STEALTH_TICKS, ticks);
    }

    public int getHoverTicks() {
        return this.entityData.get(HOVER_TICKS);
    }

    public void setHoverTicks(int ticks) {
        this.entityData.set(HOVER_TICKS, ticks);
    }

    private SoundEvent getSoundEvent(String soundName) {
        if (soundName == null || soundName.isEmpty()) return null;
        try {
            String sName = soundName.toLowerCase(java.util.Locale.ROOT);
            if (!sName.contains(":")) {
                sName = "rpg_mounts:" + sName;
            }
            ResourceLocation resLoc = ResourceLocation.tryParse(sName);
            if (resLoc == null) return null;
            return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(resLoc)
                    .orElseGet(() -> SoundEvent.createVariableRangeEvent(resLoc));
        } catch (Exception e) {
            RPGMounts.LOGGER.warn("[RPG_MOUNTS] Failed to parse sound: " + soundName, e);
            return null;
        }
    }

    public void playMountSound(String soundName, float volume, float pitch) {
        if (soundName == null || soundName.isEmpty()) return;
        if (this.level().isClientSide) {
            try {
                String sName = soundName.toLowerCase(java.util.Locale.ROOT);
                if (!sName.contains(":")) {
                    sName = "rpg_mounts:" + sName;
                }
                ResourceLocation resLoc = ResourceLocation.tryParse(sName);
                if (resLoc != null) {
                    SoundEvent snd = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(resLoc)
                            .orElseGet(() -> SoundEvent.createVariableRangeEvent(resLoc));
                    if (snd != null) {
                        this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), snd, net.minecraft.sounds.SoundSource.NEUTRAL, volume, pitch, false);
                    }
                }
            } catch (Exception e) {}
        } else {
            ddraig.net.rpgmounts.network.ModPackets.sendPlaySoundPacketToTrackers(this, soundName, volume, pitch);
        }
    }

    @Override
    public void playAmbientSound() {
        String tId = this.getTemplateId();
        if (tId != null && !tId.isEmpty()) {
            MountData data = MountRegistry.getTemplate(tId);
            if (data != null && data.sounds != null && data.sounds.ambient != null && !data.sounds.ambient.isEmpty()) {
                playMountSound(data.sounds.ambient, this.getSoundVolume(), this.getVoicePitch());
                return;
            }
        }
        super.playAmbientSound();
    }

    @Override
    protected void playHurtSound(DamageSource damageSource) {
        String tId = this.getTemplateId();
        if (tId != null && !tId.isEmpty()) {
            MountData data = MountRegistry.getTemplate(tId);
            if (data != null && data.sounds != null && data.sounds.hurt != null && !data.sounds.hurt.isEmpty()) {
                playMountSound(data.sounds.hurt, this.getSoundVolume(), this.getVoicePitch());
                return;
            }
        }
        super.playHurtSound(damageSource);
    }



    @org.jetbrains.annotations.Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        String tId = this.getTemplateId();
        if (tId != null && !tId.isEmpty()) {
            MountData data = MountRegistry.getTemplate(tId);
            if (data != null && data.sounds != null && data.sounds.ambient != null && !data.sounds.ambient.isEmpty()) {
                return null; // Handled in playAmbientSound
            }
        }
        return super.getAmbientSound();
    }

    @org.jetbrains.annotations.Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        String tId = this.getTemplateId();
        if (tId != null && !tId.isEmpty()) {
            MountData data = MountRegistry.getTemplate(tId);
            if (data != null && data.sounds != null && data.sounds.hurt != null && !data.sounds.hurt.isEmpty()) {
                return null; // Handled in playHurtSound
            }
        }
        return super.getHurtSound(damageSource);
    }

    @org.jetbrains.annotations.Nullable
    @Override
    protected SoundEvent getDeathSound() {
        String tId = this.getTemplateId();
        if (tId != null && !tId.isEmpty()) {
            MountData data = MountRegistry.getTemplate(tId);
            if (data != null && data.sounds != null && data.sounds.death != null && !data.sounds.death.isEmpty()) {
                return null; // Handled in playDeathSound
            }
        }
        return super.getDeathSound();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (this.customStepSoundCooldown > 0) {
            return;
        }
        String tId = this.getTemplateId();
        if (tId != null && !tId.isEmpty()) {
            MountData data = MountRegistry.getTemplate(tId);
            if (data != null && data.sounds != null && data.sounds.step != null && !data.sounds.step.isEmpty()) {
                try {
                    playMountSound(data.sounds.step, 0.15F, 1.0F);
                    int duration = getSoundDurationTicks(data.sounds.step);
                    this.customStepSoundCooldown = duration;
                    return;
                } catch (Exception e) {
                    RPGMounts.LOGGER.warn("[RPG_MOUNTS] Failed to parse custom step sound: " + data.sounds.step);
                }
            }
        }
        super.playStepSound(pos, state);
    }

    private static final Map<String, Integer> customSoundDurationCache = new java.util.concurrent.ConcurrentHashMap<>();

    private java.io.File getSoundFile(String soundName) {
        if (soundName == null || soundName.isEmpty()) return null;
        String path = soundName;
        if (path.contains(":")) {
            String[] parts = path.split(":", 2);
            if (!parts[0].equals("rpg_mounts")) {
                return null;
            }
            path = parts[1];
        }
        if (path.startsWith("unpacked.")) {
            String remaining = path.substring(9);
            int dotIdx = remaining.indexOf('.');
            if (dotIdx > 0) {
                String mountId = remaining.substring(0, dotIdx);
                String soundPath = remaining.substring(dotIdx + 1).replace('.', '/');
                java.io.File mountFolder = new java.io.File(MountRegistry.getMountsFolder(), mountId);
                java.io.File f = new java.io.File(mountFolder, soundPath + ".ogg");
                if (f.exists()) return f;
            }
        } else {
            String customPath = path;
            if (customPath.startsWith("custom.")) {
                customPath = customPath.substring(7);
            }
            customPath = customPath.replace('.', '/');
            java.io.File f = new java.io.File(MountRegistry.getSoundsFolder(), customPath + ".ogg");
            if (f.exists()) return f;
        }
        return null;
    }

    private int getSoundDurationTicks(String soundName) {
        if (soundName == null || soundName.isEmpty()) return 0;
        return customSoundDurationCache.computeIfAbsent(soundName, name -> {
            try {
                java.io.File f = getSoundFile(name);
                if (f != null && f.exists()) {
                    double durationSeconds = getOggDurationSeconds(f);
                    if (durationSeconds > 0.0) {
                        return (int) Math.ceil(durationSeconds * 20.0);
                    }
                }
            } catch (Exception e) {
                RPGMounts.LOGGER.warn("[RPG_MOUNTS] Failed to parse duration for sound: " + name, e);
            }
            return 20; // Default fallback to 1 second (20 ticks) if not found/failed
        });
    }

    private double getOggDurationSeconds(java.io.File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return 0.0;
        }
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (fileLength < 30) return 0.0;

            // 1. Read first page to get sample rate
            byte[] buffer = new byte[Math.min(2048, (int) fileLength)];
            raf.seek(0);
            raf.readFully(buffer);
            
            int sampleRate = -1;
            for (int i = 0; i < buffer.length - 30; i++) {
                if (buffer[i] == 0x01 && 
                    buffer[i+1] == 0x76 && // 'v'
                    buffer[i+2] == 0x6F && // 'o'
                    buffer[i+3] == 0x72 && // 'r'
                    buffer[i+4] == 0x62 && // 'b'
                    buffer[i+5] == 0x69 && // 'i'
                    buffer[i+6] == 0x73)   // 's'
                {
                    int rateOffset = i + 12;
                    if (rateOffset + 4 <= buffer.length) {
                        sampleRate = (buffer[rateOffset] & 0xFF) |
                                     ((buffer[rateOffset+1] & 0xFF) << 8) |
                                     ((buffer[rateOffset+2] & 0xFF) << 16) |
                                     ((buffer[rateOffset+3] & 0xFF) << 24);
                        break;
                    }
                }
            }

            if (sampleRate <= 0) {
                sampleRate = 44100;
            }

            // 2. Read from end of file backwards to find the last OggS signature
            long seekStart = Math.max(0, fileLength - 65536);
            raf.seek(seekStart);
            int readLen = (int) (fileLength - seekStart);
            byte[] endBuffer = new byte[readLen];
            raf.readFully(endBuffer);

            long lastGranulePosition = -1;
            for (int i = endBuffer.length - 4; i >= 0; i--) {
                if (endBuffer[i] == 0x4F &&     // 'O'
                    endBuffer[i+1] == 0x67 &&   // 'g'
                    endBuffer[i+2] == 0x67 &&   // 'g'
                    endBuffer[i+3] == 0x53)     // 'S'
                {
                    int granuleOffset = i + 6;
                    if (granuleOffset + 8 <= endBuffer.length) {
                        long granule = 0;
                        for (int b = 0; b < 8; b++) {
                            granule |= ((long) (endBuffer[granuleOffset + b] & 0xFF)) << (b * 8);
                        }
                        if (granule > 0 && granule != -1) {
                            lastGranulePosition = granule;
                            break;
                        }
                    }
                }
            }

            if (lastGranulePosition > 0) {
                return (double) lastGranulePosition / sampleRate;
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0.0;
    }

    private static final Map<String, net.minecraft.core.particles.ParticleOptions> cachedParticles = new java.util.concurrent.ConcurrentHashMap<>();

    private net.minecraft.core.particles.ParticleOptions getParticleOption(String name) {
        if (name == null || name.isEmpty()) return null;
        return cachedParticles.computeIfAbsent(name, n -> {
            try {
                return (net.minecraft.core.particles.ParticleOptions) net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(n));
            } catch (Exception e) {
                return null;
            }
        });
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.teleportImmunityTicks > 0 && (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.FALL))) {
            return false;
        }
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            return false;
        }
        if (this.hasReinforcedHideActive) {
            amount = Math.max(0.0F, amount - 1.5F);
        }
        if (this.flatDamageReductionModifier > 0.0) {
            amount = Math.max(0.0F, (float) (amount - this.flatDamageReductionModifier));
        }
        if (amount <= 0.0f) {
            return false;
        }
        if (!this.level().isClientSide) {
            DatabaseManager.UnlockedMountData data = getUnlockedData();
            if (data != null && ModConfig.get().bondingAndLeveling.enableMountLevelling) {
                data.damageTaken += amount;
                data.dirty = true;
                double combatXp = amount * ModConfig.get().bondingAndLeveling.combatXpTakenRatio;
                addXp(combatXp);
            }
            if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker && attacker != this) {
                if (this.hasThornGuardActive) {
                    attacker.hurt(this.damageSources().thorns(this), amount * 0.2F);
                }
                if (this.hasToxicSecretionsActive && this.random.nextFloat() < 0.3F) {
                    attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 80, 0));
                    if (this.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EFFECT, attacker.getX(), attacker.getY(0.5D), attacker.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.05D);
                    }
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        String tId = this.getTemplateId();
        if (tId != null && !tId.isEmpty()) {
            MountData data = MountRegistry.getTemplate(tId);
            if (data != null && data.sounds != null && data.sounds.death != null && !data.sounds.death.isEmpty()) {
                playMountSound(data.sounds.death, this.getSoundVolume(), this.getVoicePitch());
            }
        }
        if (!this.level().isClientSide) {
            this.ejectPassengers();
            
            // Drop equipped items and cargo inventory on death
            for (int i = 0; i < getInventory().getContainerSize(); i++) {
                ItemStack stack = getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(stack);
                }
            }
            // Remove gear record from database
            if (this.ownerUuid != null) {
                DatabaseManager.deleteMountGearAsync(this.ownerUuid, this.getInstanceId());
            }

            // Handle mortality based on server config
            if (ownerUuid != null) {
                String mode = ModConfig.get().mortalityAndSafety.mounts_mortality;
                DatabaseManager.ActiveMountData active = new DatabaseManager.ActiveMountData();
                active.activeMountUuid = "";
                
                if (mode.equalsIgnoreCase("Permadeath")) {
                    DatabaseManager.removeUnlockedMountAsync(ownerUuid, getInstanceId());
                } else if (mode.equalsIgnoreCase("Timer")) {
                    active.status = "INJURED";
                    active.timerRemaining = ModConfig.get().mortalityAndSafety.mounts_mortality_cooldown_ticks;
                    DatabaseManager.saveActiveMountAsync(ownerUuid, active);
                } else {
                    active.status = "REVIVAL_REQUIRED";
                    DatabaseManager.saveActiveMountAsync(ownerUuid, active);
                }

                DatabaseManager.UnlockedMountData data = getUnlockedData();
                if (data != null) {
                    data.hpZeroCount++;
                    data.dirty = true;
                    if (this.level().getServer() != null) {
                        ServerPlayer owner = this.level().getServer().getPlayerList().getPlayer(this.ownerUuid);
                        if (owner != null) {
                            ModPackets.syncUnlockedMounts(owner);
                        }
                    }
                }
            }
        }
        super.die(source);
    }

    @Override
    public boolean canBreatheUnderwater() {
        MountData data = MountRegistry.getTemplate(getTemplateId());
        return (data != null && (data.category.equalsIgnoreCase("AQUATIC") || data.category.equalsIgnoreCase("SURFACE_WATER"))) || super.canBreatheUnderwater();
    }



    @Override
    public boolean isPushedByFluid() {
        MountData data = MountRegistry.getTemplate(getTemplateId());
        if (data != null && data.category.equalsIgnoreCase("AQUATIC") && (this.isInWater() || this.isInLava())) {
            return false;
        }
        return super.isPushedByFluid();
    }

    public void loadStatsFromDatabase() {
        if (this.ownerUuid == null || this.getTemplateId().isEmpty()) return;
        DatabaseManager.UnlockedMountData data = getUnlockedData();
        if (data != null) {
            this.setInstanceId(data.instanceId);
            this.setBonding(data.bondingScore);
            this.setLevel(data.level);
            this.setXp((float) data.xp);
            this.setChroma(data.isChroma);
            if (data.customName != null && !data.customName.isEmpty()) {
                this.setCustomName(Component.literal(data.customName));
            }
        }
    }

    public DatabaseManager.UnlockedMountData getUnlockedData() {
        if (this.ownerUuid == null || this.getTemplateId().isEmpty()) return null;
        Map<String, DatabaseManager.UnlockedMountData> map = DatabaseManager.unlockedMountsCache.get(this.ownerUuid);
        if (map == null || map.isEmpty()) return null;
        String inst = this.entityData.get(INSTANCE_ID);
        if (inst != null && !inst.isEmpty() && map.containsKey(inst)) {
            return map.get(inst);
        }
        for (DatabaseManager.UnlockedMountData d : map.values()) {
            if (DatabaseManager.isSameTemplate(d.mountId, this.getTemplateId())) {
                return d;
            }
        }
        return null;
    }

    public double getHealthGrowth() {
        if (!ModConfig.get().bondingAndLeveling.enableMountLevelling) return 0.0;
        DatabaseManager.UnlockedMountData data = getUnlockedData();
        if (data == null) return 0.0;
        double res = data.damageTaken + data.hpZeroCount * 10.0;
        double pow = data.damageDealt;
        double agi = data.distanceTravelled / 100.0;
        double total = res + pow + agi;
        double ratio = (total > 0.0) ? (res / total) : 0.33;
        return (data.level - 1) * 2.0 * ratio;
    }

    public double getSpeedGrowth() {
        if (!ModConfig.get().bondingAndLeveling.enableMountLevelling) return 0.0;
        DatabaseManager.UnlockedMountData data = getUnlockedData();
        if (data == null) return 0.0;
        double res = data.damageTaken + data.hpZeroCount * 10.0;
        double pow = data.damageDealt;
        double agi = data.distanceTravelled / 100.0;
        double total = res + pow + agi;
        double ratio = (total > 0.0) ? (agi / total) : 0.33;
        return (data.level - 1) * 0.02 * ratio;
    }

    public double getPowerGrowth() {
        if (!ModConfig.get().bondingAndLeveling.enableMountLevelling) return 0.0;
        DatabaseManager.UnlockedMountData data = getUnlockedData();
        if (data == null) return 0.0;
        double res = data.damageTaken + data.hpZeroCount * 10.0;
        double pow = data.damageDealt;
        double agi = data.distanceTravelled / 100.0;
        double total = res + pow + agi;
        double ratio = (total > 0.0) ? (pow / total) : 0.33;
        return (data.level - 1) * 1.5 * ratio;
    }

    public void awardDamageDealtXp(double damage) {
        if (!ModConfig.get().bondingAndLeveling.enableMountLevelling) return;
        DatabaseManager.UnlockedMountData data = getUnlockedData();
        if (data != null) {
            data.damageDealt += damage;
            data.dirty = true;
            double combatXp = damage * ModConfig.get().bondingAndLeveling.combatXpDealtRatio;
            addXp(combatXp);
        }
    }

    public void addXp(double amount) {
        if (!ModConfig.get().bondingAndLeveling.enableMountLevelling) return;
        DatabaseManager.UnlockedMountData data = getUnlockedData();
        if (data == null) return;

        double currentXp = data.xp + amount;
        int currentLevel = data.level;
        double xpReq = ModConfig.get().bondingAndLeveling.baseXpRequirement * Math.pow(currentLevel, ModConfig.get().bondingAndLeveling.xpExponent);

        boolean leveledUp = false;
        while (currentXp >= xpReq) {
            currentXp -= xpReq;
            currentLevel++;
            leveledUp = true;
            triggerLevelUp(data, currentLevel);
            xpReq = ModConfig.get().bondingAndLeveling.baseXpRequirement * Math.pow(currentLevel, ModConfig.get().bondingAndLeveling.xpExponent);
        }

        data.xp = currentXp;
        data.level = currentLevel;
        data.dirty = true;

        this.setXp((float) currentXp);
        this.setLevel(currentLevel);

        if (leveledUp) {
            recalculateStats();
        }

        if (this.ownerUuid != null && this.level().getServer() != null) {
            ServerPlayer owner = this.level().getServer().getPlayerList().getPlayer(this.ownerUuid);
            if (owner != null) {
                ModPackets.syncUnlockedMounts(owner);
            }
        }
    }

    private void triggerLevelUp(DatabaseManager.UnlockedMountData data, int newLevel) {
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0f, 1.0f);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + 1.0, this.getZ(), 20, 0.5, 0.5, 0.5, 0.15);
            Entity owner = serverLevel.getEntity(data.playerUuid);
            if (owner instanceof Player player) {
                player.sendSystemMessage(Component.literal("§6★ Your mount leveled up to Level " + newLevel + "! ★"));
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("TemplateId")) setTemplateId(compound.getString("TemplateId"));
        if (compound.contains("InstanceId")) setInstanceId(compound.getString("InstanceId"));
        if (compound.contains("Stamina")) setStamina(compound.getFloat("Stamina"));
        if (compound.contains("Bonding")) setBonding(compound.getInt("Bonding"));
        if (compound.hasUUID("Owner")) setOwnerUuid(compound.getUUID("Owner"));
        if (compound.contains("Ability1Index")) setAbility1Index(compound.getInt("Ability1Index"));
        if (compound.contains("Ability2Index")) setAbility2Index(compound.getInt("Ability2Index"));
        if (compound.contains("IsChroma")) setChroma(compound.getBoolean("IsChroma"));
        if (compound.contains("HoverTicks")) this.entityData.set(HOVER_TICKS, compound.getInt("HoverTicks"));
        if (compound.contains("StealthTicks")) this.entityData.set(STEALTH_TICKS, compound.getInt("StealthTicks"));
        if (compound.contains("DisabledPassives", 9)) {
            net.minecraft.nbt.ListTag passivesList = compound.getList("DisabledPassives", 8);
            disabledPassives.clear();
            for (int i = 0; i < passivesList.size(); i++) {
                disabledPassives.add(passivesList.getString(i).toLowerCase());
            }
            this.entityData.set(DISABLED_PASSIVES, String.join(",", disabledPassives));
            updatePassiveCaches();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("TemplateId", getTemplateId());
        compound.putString("InstanceId", getInstanceId());
        compound.putFloat("Stamina", getStamina());
        compound.putInt("Bonding", getBonding());
        if (ownerUuid != null) compound.putUUID("Owner", ownerUuid);
        compound.putInt("Ability1Index", getAbility1Index());
        compound.putInt("Ability2Index", getAbility2Index());
        compound.putBoolean("IsChroma", isChroma());
        compound.putInt("HoverTicks", this.entityData.get(HOVER_TICKS));
        compound.putInt("StealthTicks", this.entityData.get(STEALTH_TICKS));
        net.minecraft.nbt.ListTag passivesList = new net.minecraft.nbt.ListTag();
        for (String p : disabledPassives) {
            passivesList.add(net.minecraft.nbt.StringTag.valueOf(p));
        }
        compound.put("DisabledPassives", passivesList);
    }

    public static String getAnimationNameCaseInsensitive(software.bernie.geckolib.loading.object.BakedAnimations baked, String target) {
        if (baked == null || target == null || target.isEmpty()) return null;
        for (String key : baked.animations().keySet()) {
            if (key.equalsIgnoreCase(target)) return key;
        }
        return null;
    }

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "controller", 5, event -> {
            RPGMountEntity mount = event.getAnimatable();
            
            // Multi-factor client/server movement check including position changes (for vertical and client tracking interpolation)
            double dx = mount.getX() - mount.xo;
            double dy = mount.getY() - mount.yo;
            double dz = mount.getZ() - mount.zo;
            boolean isMoving = (dx * dx + dy * dy + dz * dz) > 1E-4 || mount.getDeltaMovement().horizontalDistanceSqr() > 1E-4 || mount.walkAnimation.isMoving();
            
            String activeAnimName = mount.getActiveAnimation();

            if (mount.level().isClientSide) {
                boolean onGround = mount.onGround();
                
                // 1. Resolve animation availability
                MountData data = MountRegistry.getTemplate(mount.getTemplateId());
                boolean isFlyingType = data != null && data.category.equalsIgnoreCase("FLYING");

                String animPath = (data != null && data.animationPath != null && !data.animationPath.isEmpty()) ? data.animationPath : mount.getTemplateId();
                if (!animPath.endsWith(".animation.json")) {
                    animPath = animPath + ".animation.json";
                }
                ResourceLocation animLoc = new ResourceLocation("rpg_mounts", "animations/" + animPath);
                
                software.bernie.geckolib.loading.object.BakedAnimations baked = getOrLoadBakedAnimations(animLoc, mount.getTemplateId());
                
                AnimationMappingConfig.AnimationNames cfgAnims = AnimationMappingConfig.get().getMappingFor(mount.getTemplateId());
                
                String resolvedWalk = getAnimationNameCaseInsensitive(baked, cfgAnims.walk);
                if (resolvedWalk == null) resolvedWalk = getAnimationNameCaseInsensitive(baked, "walk");
                if (resolvedWalk == null) resolvedWalk = getAnimationNameCaseInsensitive(baked, "walking");
                if (resolvedWalk == null) resolvedWalk = getAnimationNameCaseInsensitive(baked, "crawl");
                if (resolvedWalk == null) resolvedWalk = getAnimationNameCaseInsensitive(baked, "crawling");
                
                String resolvedRun = getAnimationNameCaseInsensitive(baked, cfgAnims.run);
                if (resolvedRun == null) resolvedRun = getAnimationNameCaseInsensitive(baked, "run");
                if (resolvedRun == null) resolvedRun = getAnimationNameCaseInsensitive(baked, "running");
                if (resolvedRun == null) resolvedRun = resolvedWalk;
                
                String resolvedFly = getAnimationNameCaseInsensitive(baked, cfgAnims.fly);
                if (resolvedFly == null) resolvedFly = getAnimationNameCaseInsensitive(baked, "fly");
                if (resolvedFly == null) resolvedFly = getAnimationNameCaseInsensitive(baked, "flying");
                
                String resolvedHover = getAnimationNameCaseInsensitive(baked, cfgAnims.hover);
                if (resolvedHover == null) resolvedHover = getAnimationNameCaseInsensitive(baked, "hover");
                if (resolvedHover == null) resolvedHover = getAnimationNameCaseInsensitive(baked, "hovering");
                if (resolvedHover == null) resolvedHover = getAnimationNameCaseInsensitive(baked, "fly_idle");
                
                String resolvedIdle = getAnimationNameCaseInsensitive(baked, cfgAnims.idle);
                if (resolvedIdle == null) resolvedIdle = getAnimationNameCaseInsensitive(baked, "idle");
                
                String resolvedJump = getAnimationNameCaseInsensitive(baked, cfgAnims.jump);
                if (resolvedJump == null) resolvedJump = getAnimationNameCaseInsensitive(baked, "jump");
                
                String resolvedJumpIn = getAnimationNameCaseInsensitive(baked, "jump_in");
                if (resolvedJumpIn == null && cfgAnims.jump != null) {
                    resolvedJumpIn = getAnimationNameCaseInsensitive(baked, cfgAnims.jump + "_in");
                }
                String resolvedJumpOut = getAnimationNameCaseInsensitive(baked, "jump_out");
                if (resolvedJumpOut == null && cfgAnims.jump != null) {
                    resolvedJumpOut = getAnimationNameCaseInsensitive(baked, cfgAnims.jump + "_out");
                }
                
                String resolvedSwim = getAnimationNameCaseInsensitive(baked, cfgAnims.swim);
                if (resolvedSwim == null) resolvedSwim = getAnimationNameCaseInsensitive(baked, "swim");
                if (resolvedSwim == null) resolvedSwim = getAnimationNameCaseInsensitive(baked, "swimming");
                
                String resolvedActiveAnim = getAnimationNameCaseInsensitive(baked, activeAnimName);
                if (resolvedActiveAnim == null && activeAnimName.equalsIgnoreCase("attack") && cfgAnims.attack != null) {
                    resolvedActiveAnim = getAnimationNameCaseInsensitive(baked, cfgAnims.attack);
                }
                boolean hasActiveAnim = resolvedActiveAnim != null;
                
                // 2. State machine logic
                if (activeAnimName.equalsIgnoreCase("jump")) {
                    if (mount.clientJumpState.isEmpty() || mount.clientJumpState.equalsIgnoreCase("idle") || mount.clientJumpState.equalsIgnoreCase("walk")) {
                        if (resolvedJumpIn != null) {
                            mount.clientJumpState = resolvedJumpIn;
                            mount.clientJumpStartTick = mount.tickCount;
                        } else if (resolvedJump != null) {
                            mount.clientJumpState = resolvedJump;
                            mount.clientJumpStartTick = mount.tickCount;
                        }
                    }
                }
                
                // Check landing
                if (onGround && !mount.clientLastOnGround) {
                    if (resolvedJumpIn != null && mount.clientJumpState.equalsIgnoreCase(resolvedJumpIn)) {
                        if (resolvedJumpOut != null) {
                            mount.clientJumpState = resolvedJumpOut;
                            mount.clientJumpStartTick = mount.tickCount;
                        } else {
                            mount.clientJumpState = "";
                        }
                    } else if (resolvedJump != null && mount.clientJumpState.equalsIgnoreCase(resolvedJump)) {
                        mount.clientJumpState = "";
                    }
                }
                mount.clientLastOnGround = onGround;
                
                // Check if jump_out finished
                if (resolvedJumpOut != null && mount.clientJumpState.equalsIgnoreCase(resolvedJumpOut)) {
                    float elapsed = mount.tickCount - mount.clientJumpStartTick;
                    double jumpOutLength = 0.75;
                    if (baked != null && baked.getAnimation(resolvedJumpOut) != null) {
                        jumpOutLength = baked.getAnimation(resolvedJumpOut).length();
                    }
                    if (elapsed >= jumpOutLength * 20.0f) {
                        mount.clientJumpState = "";
                    }
                }
                
                // Override active animation to play
                String animToPlay = "";
                if (!mount.clientJumpState.isEmpty()) {
                    animToPlay = mount.clientJumpState;
                } else if (hasActiveAnim) {
                    animToPlay = resolvedActiveAnim;
                }

                // Resolve loops based on state and category
                String moveAnim = resolvedWalk != null ? resolvedWalk : "walk";
                if (isMoving) {
                    boolean isSprinting = mount.isSprinting();
                    boolean isInWaterOrLava = mount.isInWater() || mount.isInLava();
                    
                    if (isInWaterOrLava && resolvedSwim != null) {
                        moveAnim = resolvedSwim;
                    } else if (isFlyingType) {
                        if (onGround) {
                            if (isSprinting && resolvedRun != null) moveAnim = resolvedRun;
                            else if (resolvedWalk != null) moveAnim = resolvedWalk;
                            else if (resolvedFly != null) moveAnim = resolvedFly;
                        } else {
                            if (resolvedFly != null) moveAnim = resolvedFly;
                            else if (resolvedWalk != null) moveAnim = resolvedWalk;
                        }
                    } else {
                        if (isSprinting && resolvedRun != null) moveAnim = resolvedRun;
                        else if (resolvedWalk != null) moveAnim = resolvedWalk;
                    }
                }

                String idleAnim = resolvedIdle != null ? resolvedIdle : "idle";
                if (!isMoving) {
                    if (isFlyingType && !onGround) {
                        if (resolvedHover != null) idleAnim = resolvedHover;
                        else if (resolvedIdle != null) idleAnim = resolvedIdle;
                    } else {
                        if (resolvedIdle != null) idleAnim = resolvedIdle;
                    }
                }

                // Track animation changes and set animation on controller
                if (!animToPlay.equals(mount.clientLastAnimation)) {
                    mount.clientLastAnimation = animToPlay;
                    if (!animToPlay.isEmpty()) {
                        event.getController().setAnimation(software.bernie.geckolib.core.animation.RawAnimation.begin().thenPlay(animToPlay));
                    } else {
                        String currentLoop = isMoving ? moveAnim : idleAnim;
                        mount.clientLastLoopAnimation = currentLoop;
                        event.getController().setAnimation(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop(currentLoop));
                    }
                } else if (animToPlay.isEmpty()) {
                    // If no active animation override, handle walking vs idling loop transitions
                    String currentLoop = isMoving ? moveAnim : idleAnim;
                    if (!currentLoop.equals(mount.clientLastLoopAnimation)) {
                        mount.clientLastLoopAnimation = currentLoop;
                        event.getController().setAnimation(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop(currentLoop));
                    }
                }
            }
            return software.bernie.geckolib.core.object.PlayState.CONTINUE;
        }));
    }

    public static software.bernie.geckolib.loading.object.BakedAnimations getOrLoadBakedAnimations(ResourceLocation location, String templateId) {
        software.bernie.geckolib.loading.object.BakedAnimations baked = software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations().get(location);
        if (baked == null) {
            try {
                String path = location.getPath();
                if (path.startsWith("animations/") && path.endsWith(".animation.json")) {
                    MountData data = MountRegistry.getTemplate(templateId);
                    String modelId = (data != null && data.modelId != null && !data.modelId.isEmpty()) ? data.modelId : templateId;
                    
                    java.io.File configFolder = MountRegistry.getMountsFolder();
                    java.io.File unpackedFolder = new java.io.File(configFolder, modelId);
                    if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
                        java.io.File[] files = unpackedFolder.listFiles();
                        if (files != null) {
                            for (java.io.File f : files) {
                                if (f.getName().toLowerCase().endsWith(".animation.json")) {
                                    String content = java.nio.file.Files.readString(f.toPath());
                                    com.google.gson.JsonObject json = net.minecraft.util.GsonHelper.fromJson(software.bernie.geckolib.util.JsonUtil.GEO_GSON, content, com.google.gson.JsonObject.class);
                                    baked = software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(json.getAsJsonObject("animations"), software.bernie.geckolib.loading.object.BakedAnimations.class);
                                    if (baked != null) {
                                        ((java.util.Map) software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations()).put(location, baked);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                RPGMounts.LOGGER.error("Failed to dynamically load/bake GeckoLib animations in common: " + location, e);
            }
        }
        return baked;
    }

    // PlayerRideableJumping Implementation
    @Override
    public boolean canJump() {
        MountData data = MountRegistry.getTemplate(getTemplateId());
        if (data == null || !data.category.equalsIgnoreCase("GROUND") || !this.onGround()) {
            return false;
        }
        if (!ModConfig.get().stamina.allow_saddleless_jumping && !this.hasSaddle()) {
            return false;
        }
        return true;
    }

    @Override
    public void handleStartJump(int jumpPower) {
        this.level().playSound(null, this.blockPosition(), SoundEvents.HORSE_JUMP, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    public void onPlayerJump(int jumpPower) {
        if (jumpPower < 0) jumpPower = 0;
        double baseStrength = this.getAttributeValue(Attributes.JUMP_STRENGTH);
        double jumpStrength = baseStrength * (jumpPower / 100.0);
        
        this.jumpStartY = this.getY();
        this.maxJumpY = this.getY();
        this.isJumping = true;
        
        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(movement.x, jumpStrength, movement.z);
        this.hasImpulse = true;
        
        // Trigger jump animation state
        if (!this.level().isClientSide) {
            this.setActiveAnimation("jump");
            this.activeAnimationTicks = 40; // Play it for up to 2 seconds or until landing
        }
    }

    @Override
    public int getJumpCooldown() {
        return 0;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        String templateId = this.getTemplateId();
        MountData data = MountRegistry.getTemplate(templateId);
        if (data != null) {
            float width = data.getModelWidth();
            float height = data.getModelHeight();
            float scale = data.scale;
            return EntityDimensions.scalable(width * scale, height * scale);
        }
        return super.getDimensions(pose);
    }

    @Override
    public boolean onGround() {
        if (super.onGround()) {
            return true;
        }
        // Fallback check to prevent getting stuck on snow layers/slabs/block edges due to collision box jitter
        double checkY = this.getY() - 0.15;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Mth.floor(this.getX()), Mth.floor(checkY), Mth.floor(this.getZ()));
        BlockState state = this.level().getBlockState(pos);
        if (!state.isAir() && !state.getCollisionShape(this.level(), pos).isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TEMPLATE_ID.equals(key)) {
            this.refreshDimensions();
            this.updatePassiveCaches();
        }
        if (DISABLED_PASSIVES.equals(key)) {
            String str = this.entityData.get(DISABLED_PASSIVES);
            this.disabledPassives.clear();
            if (str != null && !str.isEmpty()) {
                for (String s : str.split(",")) {
                    this.disabledPassives.add(s.trim().toLowerCase());
                }
            }
            this.updatePassiveCaches();
        }
    }

    @Override
    public software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geckolibCache;
    }

    @Override
    public boolean onClimbable() {
        return super.onClimbable() || (this.hasSpiderClimbActive && this.horizontalCollision);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        MountData data = MountRegistry.getTemplate(getTemplateId());
        if (data != null && data.category.equalsIgnoreCase("FLYING")) {
            return false;
        }
        if (data != null) {
            if (this.isJumping) {
                float actualJumpHeight = (float) Math.max(0.0, this.maxJumpY - this.jumpStartY);
                fallDistance = Math.max(0.0f, fallDistance - actualJumpHeight);
            } else {
                double jumpStrength = this.getAttributeValue(Attributes.JUMP_STRENGTH);
                float jumpHeightBlocks = (float) (jumpStrength * 4.0);
                fallDistance = Math.max(0.0f, fallDistance - jumpHeightBlocks);
            }
        }
        if (this.hasFeatherLightActive) {
            damageMultiplier *= 0.1F;
        }
        return super.causeFallDamage(fallDistance, damageMultiplier, damageSource);
    }

    @Override
    protected float getBlockSpeedFactor() {
        if (this.hasTractionTreadActive) {
            return 1.0F;
        }
        return super.getBlockSpeedFactor();
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 speedMultiplier) {
        if (this.hasTractionTreadActive) {
            this.resetFallDistance();
        } else {
            super.makeStuckInBlock(state, speedMultiplier);
        }
    }

    public LivingEntity getOwnerEntity() {
        UUID uuid = getOwnerUuid();
        if (uuid == null) return null;
        return this.level().getPlayerByUUID(uuid);
    }

    public static class MountMeleeAttackGoal extends net.minecraft.world.entity.ai.goal.Goal {
        protected final RPGMountEntity mount;
        private int attackInterval = 20;
        private int ticksUntilNextAttack = 0;

        public MountMeleeAttackGoal(RPGMountEntity mount) {
            this.mount = mount;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.mount.getControllingPassenger() != null) return false;
            String tId = this.mount.getTemplateId();
            if (tId == null || tId.isEmpty()) return false;
            MountData data = MountRegistry.getTemplate(tId);
            if (data == null || data.combat == null || !data.combat.enableCombat) return false;
            
            LivingEntity target = this.mount.getTarget();
            return target != null && target.isAlive() && !this.mount.getPassengers().contains(target);
        }

        @Override
        public void start() {
            this.mount.getNavigation().moveTo(this.mount.getTarget(), 1.25D);
            this.ticksUntilNextAttack = 0;
        }

        @Override
        public void stop() {
            this.mount.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.mount.getTarget();
            if (target == null) return;
            
            this.mount.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distanceSq = this.mount.distanceToSqr(target.getX(), target.getY(), target.getZ());
            
            if (this.mount.getRandom().nextInt(10) == 0) {
                this.mount.getNavigation().moveTo(target, 1.25D);
            }
            
            if (this.ticksUntilNextAttack > 0) {
                this.ticksUntilNextAttack--;
            }
            
            double reach = (double)(this.mount.getBbWidth() * 2.0F * this.mount.getBbWidth() * 2.0F + target.getBbWidth());
            if (distanceSq <= reach && this.ticksUntilNextAttack <= 0) {
                String tId = this.mount.getTemplateId();
                MountData data = MountRegistry.getTemplate(tId);
                double baseDamage = data != null && data.combat != null ? data.combat.strength : 2.0;
                double speedMult = (data != null && data.combat != null ? data.combat.attackSpeed : 1.0) + this.mount.attackSpeedModifier;
                
                double dmgBoost = this.mount.getEnhancerModifier("damage", "damage_boost") + this.mount.getPowerGrowth();
                double strengthMod = this.mount.strengthModifier;
                double finalDamage = baseDamage + dmgBoost + strengthMod;
                
                this.attackInterval = (int) Math.max(5, 20.0 / speedMult);
                this.ticksUntilNextAttack = this.attackInterval;
                
                if (target.hurt(this.mount.damageSources().mobAttack(this.mount), (float) finalDamage)) {
                    this.mount.awardDamageDealtXp(finalDamage);
                    String anim = "attack";
                    if (data != null && data.combat != null && data.combat.ability1 != null) {
                        String combatAnim = data.combat.ability1.vanillaAnimation.equalsIgnoreCase("NONE") ? data.combat.ability1.animationName : data.combat.ability1.vanillaAnimation;
                        if (combatAnim != null && !combatAnim.isEmpty() && !combatAnim.equalsIgnoreCase("NONE")) {
                            anim = combatAnim.toLowerCase();
                        }
                    }
                    this.mount.entityData.set(ACTIVE_ANIMATION, anim);
                    this.mount.activeAnimationTicks = 15;
                    
                    this.mount.level().playSound(null, this.mount.blockPosition(), net.minecraft.sounds.SoundEvents.WOLF_GROWL, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                }
            }
        }
    }

    public static class MountOwnerHurtByTargetGoal extends net.minecraft.world.entity.ai.goal.target.TargetGoal {
        protected final RPGMountEntity mount;
        protected LivingEntity attacker;
        protected int timestamp;

        public MountOwnerHurtByTargetGoal(RPGMountEntity mount) {
            super(mount, false);
            this.mount = mount;
            this.setFlags(java.util.EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.mount.getControllingPassenger() != null) return false;
            String tId = this.mount.getTemplateId();
            if (tId == null || tId.isEmpty()) return false;
            MountData data = MountRegistry.getTemplate(tId);
            if (data == null || data.combat == null || !data.combat.enableCombat) return false;
            
            String ai = data.combat.combatAi;
            if (!"ASSIST_RIDER".equalsIgnoreCase(ai) && !"DEFENSIVE".equalsIgnoreCase(ai)) return false;
            
            LivingEntity owner = this.mount.getOwnerEntity();
            if (owner == null) return false;
            
            this.attacker = owner.getLastHurtByMob();
            int i = owner.getLastHurtByMobTimestamp();
            return i != this.timestamp && this.canAttack(this.attacker, net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT) && !this.mount.getPassengers().contains(this.attacker);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.attacker);
            LivingEntity owner = this.mount.getOwnerEntity();
            if (owner != null) {
                this.timestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    public static class MountOwnerHurtTargetGoal extends net.minecraft.world.entity.ai.goal.target.TargetGoal {
        protected final RPGMountEntity mount;
        protected LivingEntity target;
        protected int timestamp;

        public MountOwnerHurtTargetGoal(RPGMountEntity mount) {
            super(mount, false);
            this.mount = mount;
            this.setFlags(java.util.EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.mount.getControllingPassenger() != null) return false;
            String tId = this.mount.getTemplateId();
            if (tId == null || tId.isEmpty()) return false;
            MountData data = MountRegistry.getTemplate(tId);
            if (data == null || data.combat == null || !data.combat.enableCombat) return false;
            
            String ai = data.combat.combatAi;
            if (!"ASSIST_RIDER".equalsIgnoreCase(ai)) return false;
            
            LivingEntity owner = this.mount.getOwnerEntity();
            if (owner == null) return false;
            
            this.target = owner.getLastHurtMob();
            int i = owner.getLastHurtMobTimestamp();
            return i != this.timestamp && this.canAttack(this.target, net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT) && !this.mount.getPassengers().contains(this.target);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.target);
            LivingEntity owner = this.mount.getOwnerEntity();
            if (owner != null) {
                this.timestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    public static class MountHurtByTargetGoal extends net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal {
        protected final RPGMountEntity mount;

        public MountHurtByTargetGoal(RPGMountEntity mount) {
            super(mount);
            this.mount = mount;
        }

        @Override
        public boolean canUse() {
            if (this.mount.getControllingPassenger() != null) return false;
            String tId = this.mount.getTemplateId();
            if (tId == null || tId.isEmpty()) return false;
            MountData data = MountRegistry.getTemplate(tId);
            if (data == null || data.combat == null || !data.combat.enableCombat) return false;
            
            String ai = data.combat.combatAi;
            if ("PASSIVE".equalsIgnoreCase(ai)) return false;
            
            return super.canUse();
        }
    }

    public static class MountAggressiveTargetGoal extends net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<net.minecraft.world.entity.Mob> {
        protected final RPGMountEntity mount;

        public MountAggressiveTargetGoal(RPGMountEntity mount) {
            super(mount, net.minecraft.world.entity.Mob.class, 10, true, false, entity -> 
                entity instanceof net.minecraft.world.entity.monster.Enemy || 
                entity instanceof net.minecraft.world.entity.monster.Monster);
            this.mount = mount;
        }

        @Override
        public boolean canUse() {
            if (this.mount.getControllingPassenger() != null) return false;
            String tId = this.mount.getTemplateId();
            if (tId == null || tId.isEmpty()) return false;
            MountData data = MountRegistry.getTemplate(tId);
            if (data == null || data.combat == null || !data.combat.enableCombat) return false;
            
            String ai = data.combat.combatAi;
            if (!"AGGRESSIVE".equalsIgnoreCase(ai)) return false;
            
            return super.canUse();
        }
    }

    public static class MountFloatGoal extends net.minecraft.world.entity.ai.goal.FloatGoal {
        private final RPGMountEntity mount;

        public MountFloatGoal(RPGMountEntity mount) {
            super(mount);
            this.mount = mount;
        }

        @Override
        public boolean canUse() {
            MountData data = MountRegistry.getTemplate(this.mount.getTemplateId());
            if (data != null && data.category.equalsIgnoreCase("AQUATIC")) {
                return false;
            }
            return super.canUse();
        }
    }

    public static class MountStrollGoal extends net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal {
        private final RPGMountEntity mount;

        public MountStrollGoal(RPGMountEntity mount, double speedModifier) {
            super(mount, speedModifier);
            this.mount = mount;
        }

        @Override
        public boolean canUse() {
            MountData data = MountRegistry.getTemplate(this.mount.getTemplateId());
            if (data != null && data.category.equalsIgnoreCase("AQUATIC")) {
                if (this.mount.isInWater()) {
                    return false;
                }
            }
            return super.canUse();
        }
    }
}
