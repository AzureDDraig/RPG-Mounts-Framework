package ddraig.net.rpgmounts.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.config.AnimationMappingConfig;
import ddraig.net.rpgmounts.data.MountData;
import ddraig.net.rpgmounts.data.MountRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;

/**
 * RPG Mount Entity Renderer class
 * Implements a dynamic rendering pipeline that delegates visual models 
 * to vanilla entity renderers based on the loaded configuration template.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented base structures.
 * - 2026-06-19: [Dynamic Models Delegation] - Overrode render() and getTextureLocation() to dynamically instantiate, position sync, scale, and render appropriate vanilla entities.
 */
public class RPGMountRenderer extends MobRenderer<RPGMountEntity, CowModel<RPGMountEntity>> {
    public static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("minecraft", "textures/entity/cow/cow.png");
    private final java.util.Map<RPGMountEntity, Map<String, Entity>> dummyEntities = new java.util.WeakHashMap<>();
    private final IGeckoLibRenderer geckolibRenderer;
    private static final Map<ResourceLocation, ResourceLocation> CHROMA_CACHE = new HashMap<>();

    public static ResourceLocation getOrCreateChromaTexture(ResourceLocation originalLoc) {
        return CHROMA_CACHE.computeIfAbsent(originalLoc, loc -> {
            try {
                var resourceManager = Minecraft.getInstance().getResourceManager();
                var resource = resourceManager.getResource(loc).orElse(null);
                if (resource != null) {
                    try (InputStream is = resource.open()) {
                        NativeImage originalImage = NativeImage.read(is);
                        int width = originalImage.getWidth();
                        int height = originalImage.getHeight();
                        NativeImage chromaImage = new NativeImage(width, height, true);
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int pixel = originalImage.getPixelRGBA(x, y);
                                int alpha = (pixel >> 24) & 0xFF;
                                int blue = (pixel >> 16) & 0xFF;
                                int green = (pixel >> 8) & 0xFF;
                                int red = pixel & 0xFF;

                                // Invert RGB channels
                                int invRed = 255 - red;
                                int invGreen = 255 - green;
                                int invBlue = 255 - blue;

                                int newPixel = (alpha << 24) | (invBlue << 16) | (invGreen << 8) | invRed;
                                chromaImage.setPixelRGBA(x, y, newPixel);
                            }
                        }
                        DynamicTexture dynamicTexture = new DynamicTexture(chromaImage);
                        ResourceLocation chromaLoc = new ResourceLocation(loc.getNamespace(), loc.getPath().replace(".png", "") + "_chroma");
                        Minecraft.getInstance().getTextureManager().register(chromaLoc, dynamicTexture);
                        originalImage.close(); // Clean up NativeImage
                        return chromaLoc;
                    }
                }
            } catch (Exception e) {
                ddraig.net.rpgmounts.RPGMounts.LOGGER.error("Failed to generate chroma texture for " + loc, e);
            }
            return loc;
        });
    }

    public RPGMountRenderer(EntityRendererProvider.Context context) {
        super(context, new CowModel<>(context.bakeLayer(ModelLayers.COW)), 0.7f);
        this.geckolibRenderer = GeckoLibRendererBridge.createRenderer(context);
    }

    public IGeckoLibRenderer getGeckoLibRenderer() {
        return this.geckolibRenderer;
    }

    private Entity getOrCreateDummy(RPGMountEntity mount, String modelId) {
        Map<String, Entity> mountDummies = dummyEntities.computeIfAbsent(mount, m -> new HashMap<>());
        return mountDummies.computeIfAbsent(modelId, id -> {
            try {
                ResourceLocation loc = new ResourceLocation(id);
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(loc);
                if (type != null) {
                    Entity dummy = type.create(mount.level());
                    if (dummy != null) {
                        if (dummy instanceof net.minecraft.world.entity.Mob mob) {
                            mob.setNoAi(true);
                        }
                        return dummy;
                    }
                }
            } catch (Exception e) {
                ddraig.net.rpgmounts.RPGMounts.LOGGER.error("Failed to create dummy entity for ID: " + id, e);
            }
            return null;
        });
    }

    @Override
    public void render(RPGMountEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        String templateId = entity.getTemplateId();
        MountData data = MountRegistry.getTemplate(templateId);

        if (data != null) {
            if (data.modelType.equalsIgnoreCase("java")) {
                HierarchicalModel<RPGMountEntity> modelInstance = JavaModelLoader.getModel(data.modelId);
                if (modelInstance != null) {
                    ResourceLocation tex = JavaModelLoader.getTexture(data.id, data.modelId, data.texturePath);
                    if (entity.isChroma()) {
                        tex = getOrCreateChromaTexture(tex);
                    }

                    poseStack.pushPose();

                    // Calculate rotations
                    float f = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
                    float f1 = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
                    float f2 = f1 - f;
                    float f6 = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

                    // Align to Minecraft orientation
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - f));

                    // Apply roll banking
                    float roll = Mth.lerp(partialTicks, entity.rollO, entity.getRoll());
                    if (roll != 0.0F) {
                        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll));
                    }

                    // Apply body pitch for flying/aquatic mounts
                    if (data.category.equalsIgnoreCase("FLYING") || data.category.equalsIgnoreCase("AQUATIC")) {
                        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(f6));
                    }

                    // Scale (Minecraft models are negatively scaled on X/Y by default)
                    float scale = data.scale;
                    poseStack.scale(-scale, -scale, scale);
                    poseStack.translate(0.0D, -1.501D, 0.0D);

                    // Reset pose first to avoid deformation
                    modelInstance.root().getAllParts().forEach(ModelPart::resetPose);

                    Map<String, AnimationDefinition> anims = JavaModelLoader.getAnimations(data.modelId);
                    String activeAnimName = entity.getActiveAnimation();
                    AnimationMappingConfig.AnimationNames cfgAnims = AnimationMappingConfig.get().getMappingFor(entity.getTemplateId());
                    
                    String resolvedActiveAnim = getJavaAnimationCaseInsensitive(anims, activeAnimName);
                    if (resolvedActiveAnim == null && activeAnimName.equalsIgnoreCase("attack") && cfgAnims.attack != null) {
                        resolvedActiveAnim = getJavaAnimationCaseInsensitive(anims, cfgAnims.attack);
                    }
                    
                    String resolvedWalk = getJavaAnimationCaseInsensitive(anims, cfgAnims.walk);
                    if (resolvedWalk == null) resolvedWalk = getJavaAnimationCaseInsensitive(anims, "walk");
                    if (resolvedWalk == null) resolvedWalk = getJavaAnimationCaseInsensitive(anims, "walking");
                    if (resolvedWalk == null) resolvedWalk = getJavaAnimationCaseInsensitive(anims, "crawl");
                    if (resolvedWalk == null) resolvedWalk = getJavaAnimationCaseInsensitive(anims, "crawling");
                    
                    String resolvedRun = getJavaAnimationCaseInsensitive(anims, cfgAnims.run);
                    if (resolvedRun == null) resolvedRun = getJavaAnimationCaseInsensitive(anims, "run");
                    if (resolvedRun == null) resolvedRun = getJavaAnimationCaseInsensitive(anims, "running");
                    if (resolvedRun == null) resolvedRun = resolvedWalk;
                    
                    String resolvedFly = getJavaAnimationCaseInsensitive(anims, cfgAnims.fly);
                    if (resolvedFly == null) resolvedFly = getJavaAnimationCaseInsensitive(anims, "fly");
                    if (resolvedFly == null) resolvedFly = getJavaAnimationCaseInsensitive(anims, "flying");
                    
                    String resolvedHover = getJavaAnimationCaseInsensitive(anims, cfgAnims.hover);
                    if (resolvedHover == null) resolvedHover = getJavaAnimationCaseInsensitive(anims, "hover");
                    if (resolvedHover == null) resolvedHover = getJavaAnimationCaseInsensitive(anims, "hovering");
                    if (resolvedHover == null) resolvedHover = getJavaAnimationCaseInsensitive(anims, "fly_idle");
                    
                    String resolvedIdle = getJavaAnimationCaseInsensitive(anims, cfgAnims.idle);
                    if (resolvedIdle == null) resolvedIdle = getJavaAnimationCaseInsensitive(anims, "idle");
                    
                    String resolvedJump = getJavaAnimationCaseInsensitive(anims, cfgAnims.jump);
                    if (resolvedJump == null) resolvedJump = getJavaAnimationCaseInsensitive(anims, "jump");
                    
                    String resolvedJumpIn = getJavaAnimationCaseInsensitive(anims, "jump_in");
                    if (resolvedJumpIn == null && cfgAnims.jump != null) {
                        resolvedJumpIn = getJavaAnimationCaseInsensitive(anims, cfgAnims.jump + "_in");
                    }
                    String resolvedJumpOut = getJavaAnimationCaseInsensitive(anims, "jump_out");
                    if (resolvedJumpOut == null && cfgAnims.jump != null) {
                        resolvedJumpOut = getJavaAnimationCaseInsensitive(anims, cfgAnims.jump + "_out");
                    }
                    
                    String resolvedSwim = getJavaAnimationCaseInsensitive(anims, cfgAnims.swim);
                    if (resolvedSwim == null) resolvedSwim = getJavaAnimationCaseInsensitive(anims, "swim");
                    if (resolvedSwim == null) resolvedSwim = getJavaAnimationCaseInsensitive(anims, "swimming");

                    // Multi-factor client/server movement check including position changes (for vertical and client tracking interpolation)
                    double dx = entity.getX() - entity.xo;
                    double dy = entity.getY() - entity.yo;
                    double dz = entity.getZ() - entity.zo;
                    boolean isMoving = (dx * dx + dy * dy + dz * dz) > 1E-4 || entity.getDeltaMovement().horizontalDistanceSqr() > 1E-4 || entity.walkAnimation.isMoving();
                    boolean isFlyingType = data.category.equalsIgnoreCase("FLYING");
                    boolean onGround = entity.onGround();
                    
                    String activeAnim = "";
                    if (resolvedActiveAnim != null) {
                        activeAnim = resolvedActiveAnim;
                    }

                    // Fallback to walk/run/idle/fly if no active animation is set or if the animation is missing from the model
                    if (activeAnim.isEmpty()) {
                        boolean isInWaterOrLava = entity.isInWater() || entity.isInLava();
                        boolean isSprinting = entity.isSprinting();
                        
                        if (isMoving) {
                            if (isInWaterOrLava && resolvedSwim != null) {
                                activeAnim = resolvedSwim;
                            } else if (isFlyingType) {
                                if (onGround) {
                                    if (isSprinting && resolvedRun != null) activeAnim = resolvedRun;
                                    else if (resolvedWalk != null) activeAnim = resolvedWalk;
                                    else activeAnim = resolvedIdle != null ? resolvedIdle : "idle";
                                } else {
                                    if (resolvedFly != null) activeAnim = resolvedFly;
                                    else if (resolvedWalk != null) activeAnim = resolvedWalk;
                                    else activeAnim = resolvedIdle != null ? resolvedIdle : "idle";
                                }
                            } else {
                                if (isSprinting && resolvedRun != null) activeAnim = resolvedRun;
                                else if (resolvedWalk != null) activeAnim = resolvedWalk;
                                else activeAnim = resolvedIdle != null ? resolvedIdle : "idle";
                            }
                        } else {
                            if (isFlyingType && !onGround) {
                                if (resolvedHover != null) activeAnim = resolvedHover;
                                else activeAnim = resolvedIdle != null ? resolvedIdle : "idle";
                            } else {
                                activeAnim = resolvedIdle != null ? resolvedIdle : "idle";
                            }
                        }
                    }

                    // Client-side jump animation state machine for Java models
                    float currentTick = entity.tickCount + partialTicks;

                    if (activeAnimName.equalsIgnoreCase("jump")) {
                        if (entity.clientJumpState.equals("") || entity.clientJumpState.equalsIgnoreCase("idle") || entity.clientJumpState.equalsIgnoreCase("walk")) {
                            if (resolvedJumpIn != null) {
                                entity.clientJumpState = resolvedJumpIn;
                                entity.clientJumpStartTick = currentTick;
                            } else if (resolvedJump != null) {
                                entity.clientJumpState = resolvedJump;
                                entity.clientJumpStartTick = currentTick;
                            }
                        }
                    }

                    // Check landing
                    if (onGround && !entity.clientLastOnGround) {
                        if (resolvedJumpIn != null && entity.clientJumpState.equalsIgnoreCase(resolvedJumpIn)) {
                            if (resolvedJumpOut != null) {
                                entity.clientJumpState = resolvedJumpOut;
                                entity.clientJumpStartTick = currentTick;
                            } else {
                                entity.clientJumpState = "";
                            }
                        } else if (resolvedJump != null && entity.clientJumpState.equalsIgnoreCase(resolvedJump)) {
                            entity.clientJumpState = "";
                        }
                    }
                    entity.clientLastOnGround = onGround;

                    // Check if jump_out finished
                    if (resolvedJumpOut != null && entity.clientJumpState.equalsIgnoreCase(resolvedJumpOut)) {
                        AnimationDefinition outDef = anims.get(resolvedJumpOut);
                        if (outDef != null) {
                            float elapsed = currentTick - entity.clientJumpStartTick;
                            if (elapsed >= outDef.lengthInSeconds() * 20.0f) {
                                entity.clientJumpState = "";
                            }
                        } else {
                            entity.clientJumpState = "";
                        }
                    }

                    // Override active animation to play
                    if (!entity.clientJumpState.isEmpty()) {
                        activeAnim = entity.clientJumpState;
                    }

                    // Track animation start time to play from beginning
                    if (!activeAnim.equals(entity.clientLastAnimation)) {
                        entity.clientLastAnimation = activeAnim;
                        if (activeAnim.equals(entity.clientJumpState)) {
                            entity.clientAnimationStartTick = entity.clientJumpStartTick;
                        } else {
                            entity.clientAnimationStartTick = currentTick;
                        }
                    }

                    AnimationDefinition animDef = anims.get(activeAnim);
                    if (animDef != null) {
                        float elapsedTicks = currentTick - entity.clientAnimationStartTick;
                        long elapsedMillis = (long) (elapsedTicks * 50.0F);
                        net.minecraft.client.animation.KeyframeAnimations.animate(modelInstance, animDef, elapsedMillis, 1.0F, new org.joml.Vector3f());
                    }

                    // Dynamically apply look-target angles on the head ModelPart if found recursively
                    try {
                        ModelPart headPart = getPartRecursive(modelInstance.root(), "head");
                        if (headPart != null) {
                            if (data.category.equalsIgnoreCase("FLYING") || data.category.equalsIgnoreCase("AQUATIC")) {
                                headPart.xRot = 0.0F; // Aligned with pitched body
                            } else {
                                headPart.xRot = f6 * Mth.DEG_TO_RAD;
                            }
                            headPart.yRot = f2 * Mth.DEG_TO_RAD;
                        }
                    } catch (Exception ignored) {}

                    // Draw the model buffer
                    com.mojang.blaze3d.vertex.VertexConsumer vc = buffer.getBuffer(modelInstance.renderType(tex));
                    float r = entity.isSilhouette() ? 0.0F : 1.0F;
                    float g = entity.isSilhouette() ? 0.0F : 1.0F;
                    float b = entity.isSilhouette() ? 0.0F : 1.0F;
                    modelInstance.renderToBuffer(poseStack, vc, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, r, g, b, 1.0F);

                    poseStack.popPose();
                    return;
                }
            } else if (data.modelType.equalsIgnoreCase("vanilla")) {
                Entity dummy = getOrCreateDummy(entity, data.modelId);
                if (dummy != null) {
                    dummy.getTags().removeIf(tag -> tag.startsWith("rpg_texture:"));
                    dummy.getTags().remove("rpg_silhouette");
                    ResourceLocation tex = null;
                    if (data.texturePath != null && !data.texturePath.isEmpty()) {
                        tex = JavaModelLoader.getTexture(data.id, data.modelId, data.texturePath);
                    } else {
                        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(dummy);
                        if (renderer != null) {
                            tex = renderer.getTextureLocation(dummy);
                        }
                    }

                    if (tex != null) {
                        if (entity.isChroma()) {
                            tex = getOrCreateChromaTexture(tex);
                        }
                        dummy.addTag("rpg_texture:" + tex.toString());
                    }
                    if (entity.isSilhouette()) {
                        dummy.addTag("rpg_silhouette");
                    }
                    // Synchronize positions, rotation interpolation, and animation ticks
                    dummy.setPos(entity.getX(), entity.getY(), entity.getZ());
                    if (data.category.equalsIgnoreCase("FLYING") || data.category.equalsIgnoreCase("AQUATIC")) {
                        dummy.setXRot(0.0F); // Aligned with pitched body
                    } else {
                        dummy.setXRot(entity.getXRot());
                    }
                    if (dummy instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
                        dummy.setYRot(entity.getYRot() + 180.0F);
                        dummy.yRotO = entity.yRotO + 180.0F;
                    } else {
                        dummy.setYRot(entity.getYRot());
                        dummy.yRotO = entity.yRotO;
                    }
                    dummy.xRotO = entity.xRotO;
                    boolean isNewTick = dummy.tickCount != entity.tickCount;
                    dummy.tickCount = entity.tickCount;
 
                    if (dummy instanceof LivingEntity livingDummy) {
                        livingDummy.yBodyRot = entity.yBodyRot;
                        livingDummy.yBodyRotO = entity.yBodyRotO;
                        livingDummy.yHeadRot = entity.yHeadRot;
                        livingDummy.yHeadRotO = entity.yHeadRotO;
 
                        // Sync walking animations using accessor mixin
                        if (livingDummy.walkAnimation instanceof ddraig.net.rpgmounts.mixin.WalkAnimationStateAccessor dummyWalk &&
                            entity.walkAnimation instanceof ddraig.net.rpgmounts.mixin.WalkAnimationStateAccessor entityWalk) {
                            dummyWalk.setSpeed(entityWalk.getSpeed());
                            dummyWalk.setSpeedOld(entityWalk.getSpeedOld());
                            dummyWalk.setPosition(entityWalk.getPosition());
                        }
 
                        if (livingDummy instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
                            float flapSpeed = 0.05f;
                            String activeAnim = entity.getActiveAnimation();
                            if (activeAnim.equalsIgnoreCase("DRAGON_FLAP")) {
                                flapSpeed = 0.2f;
                            }
                            dragon.oFlapTime = (entity.tickCount - 1) * flapSpeed;
                            dragon.flapTime = entity.tickCount * flapSpeed;
 
                            // Synchronize positions history for segment rotations
                            if (dragon.posPointer < 0) {
                                dragon.posPointer = 0;
                                for (int i = 0; i < 64; i++) {
                                    dragon.positions[i][0] = entity.getYRot() + 180.0F;
                                    dragon.positions[i][1] = entity.getY();
                                    dragon.positions[i][2] = 0.0D;
                                }
                            }
 
                            if (isNewTick) {
                                dragon.posPointer = (dragon.posPointer + 1) & 63;
                                dragon.positions[dragon.posPointer][0] = entity.getYRot() + 180.0F;
                                dragon.positions[dragon.posPointer][1] = entity.getY();
                                dragon.positions[dragon.posPointer][2] = 0.0D;
                            }
                        }
                    }

                    // Synchronize gear visual rendering flags
                    if (dummy instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horseDummy) {
                        horseDummy.getSlot(400).set(entity.hasSaddle() ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE) : net.minecraft.world.item.ItemStack.EMPTY);

                        String armor = entity.getArmorItem();
                        if (armor.isEmpty()) {
                            horseDummy.getSlot(401).set(net.minecraft.world.item.ItemStack.EMPTY);
                        } else {
                            net.minecraft.resources.ResourceLocation loc = new net.minecraft.resources.ResourceLocation(armor);
                            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc);
                            if (item != null) {
                                horseDummy.getSlot(401).set(new net.minecraft.world.item.ItemStack(item));
                            }
                        }

                        if (horseDummy instanceof net.minecraft.world.entity.animal.horse.AbstractChestedHorse chestedDummy) {
                            chestedDummy.setChest(!entity.getCargoItem().isEmpty());
                        }
                    }

                    // Play animation triggers on dummy entities
                    String activeAnim = entity.getActiveAnimation();
                    if (!activeAnim.isEmpty()) {
                        if (activeAnim.equalsIgnoreCase("HORSE_REAR") && dummy instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) {
                            horse.setStanding(true);
                        } else if (activeAnim.equalsIgnoreCase("WOLF_BITE") && dummy instanceof net.minecraft.world.entity.animal.Wolf wolf) {
                            wolf.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        } else if (dummy instanceof net.minecraft.world.entity.LivingEntity livingDummy) {
                            livingDummy.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        }
                    } else {
                        if (dummy instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) {
                            horse.setStanding(false);
                        }
                    }

                    // Apply custom scale
                    poseStack.pushPose();
                    
                    // Apply roll banking
                    float roll = Mth.lerp(partialTicks, entity.rollO, entity.getRoll());
                    if (roll != 0.0F) {
                        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll));
                    }

                    // Apply body pitch for flying/aquatic mounts
                    float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
                    if (data.category.equalsIgnoreCase("FLYING") || data.category.equalsIgnoreCase("AQUATIC")) {
                        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
                    }

                    float scale = data.scale;
                    poseStack.scale(scale, scale, scale);

                    // Fetch delegate renderer
                    EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                    EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(dummy);
                    if (renderer != null) {
                        renderer.render(dummy, entityYaw, partialTicks, poseStack, buffer, packedLight);
                    }

                    poseStack.popPose();
                    return;
                }
            } else if (data.modelType.equalsIgnoreCase("geckolib") && this.geckolibRenderer != null) {
                String modelId = (data.modelId != null && !data.modelId.isEmpty()) ? data.modelId : templateId;
                boolean modelExists = modelFileExists(modelId);
                if (!modelExists) {
                    super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
                    return;
                }

                int savedDepth = 0;
                java.lang.reflect.Field depthField = null;
                try {
                    try {
                        depthField = PoseStack.class.getDeclaredField("lastIndex");
                    } catch (NoSuchFieldException e) {
                        for (java.lang.reflect.Field field : PoseStack.class.getDeclaredFields()) {
                            if (field.getType() == int.class) {
                                depthField = field;
                                break;
                            }
                        }
                    }
                    if (depthField != null) {
                        depthField.setAccessible(true);
                        savedDepth = depthField.getInt(poseStack);
                    }
                } catch (Exception ignored) {}

                poseStack.pushPose();

                // Apply roll banking
                float roll = Mth.lerp(partialTicks, entity.rollO, entity.getRoll());
                if (roll != 0.0F) {
                    poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll));
                }

                // Apply body pitch for flying/aquatic mounts
                float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
                if (data.category.equalsIgnoreCase("FLYING") || data.category.equalsIgnoreCase("AQUATIC")) {
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
                }

                // Apply custom scale
                float scale = data.scale;
                poseStack.scale(scale, scale, scale);

                // Save original XRot/xRotO
                float originalXRot = entity.getXRot();
                float originalXRotO = entity.xRotO;

                // If body pitched, zero out entity xRot during GeckoLib render to prevent head double-pitching/counter-rotation
                if (data.category.equalsIgnoreCase("FLYING") || data.category.equalsIgnoreCase("AQUATIC")) {
                    entity.setXRot(0.0F);
                    entity.xRotO = 0.0F;
                }

                try {
                    // Render via GeckoLib renderer
                    this.geckolibRenderer.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
                } catch (Exception e) {
                    ddraig.net.rpgmounts.RPGMounts.LOGGER.error("Failed to render GeckoLib model for template: " + templateId, e);
                    if (depthField != null) {
                        try {
                            while (depthField.getInt(poseStack) > savedDepth) {
                                poseStack.popPose();
                            }
                        } catch (Exception ignored) {}
                    } else {
                        poseStack.popPose();
                    }
                    // Fallback to cow model if custom model fails to render
                    super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
                    return;
                } finally {
                    // Restore original XRot/xRotO
                    entity.setXRot(originalXRot);
                    entity.xRotO = originalXRotO;
                }

                poseStack.popPose();
                return;
            }
        }

        // Fallback to cow model if custom model is not configured or fails to render
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RPGMountEntity entity) {
        String templateId = entity.getTemplateId();
        MountData data = MountRegistry.getTemplate(templateId);

        if (data != null) {
            ResourceLocation tex = null;
            if (data.modelType.equalsIgnoreCase("vanilla")) {
                Entity dummy = getOrCreateDummy(entity, data.modelId);
                if (dummy != null) {
                    if (data.texturePath != null && !data.texturePath.isEmpty()) {
                        tex = JavaModelLoader.getTexture(data.id, data.modelId, data.texturePath);
                    } else {
                        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(dummy);
                        if (renderer != null) {
                            tex = renderer.getTextureLocation(dummy);
                        }
                    }
                }
            } else if (data.modelType.equalsIgnoreCase("java")) {
                tex = JavaModelLoader.getTexture(data.id, data.modelId, data.texturePath);
            } else if (data.modelType.equalsIgnoreCase("geckolib") && this.geckolibRenderer != null) {
                try {
                    tex = this.geckolibRenderer.getTextureLocation(entity);
                } catch (Exception e) {
                    ddraig.net.rpgmounts.RPGMounts.LOGGER.error("Failed to get GeckoLib texture for template: " + templateId, e);
                }
            }
            if (tex != null) {
                if (entity.isChroma()) {
                    return getOrCreateChromaTexture(tex);
                }
                return tex;
            }
        }
        return DEFAULT_TEXTURE;
    }

    private boolean modelFileExists(String modelId) {
        java.io.File configFolder = ddraig.net.rpgmounts.data.MountRegistry.getMountsFolder();
        java.io.File unpackedFolder = new java.io.File(configFolder, modelId);
        if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
            java.io.File[] files = unpackedFolder.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    if (f.getName().toLowerCase().endsWith(".geo.json")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static String getJavaAnimationCaseInsensitive(Map<String, AnimationDefinition> anims, String target) {
        if (anims == null || target == null || target.isEmpty()) return null;
        for (String key : anims.keySet()) {
            if (key.equalsIgnoreCase(target)) return key;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ModelPart getPartRecursive(ModelPart parent, String name) {
        if (parent == null) return null;
        try {
            java.lang.reflect.Field childrenField = null;
            try {
                childrenField = ModelPart.class.getDeclaredField("children");
            } catch (NoSuchFieldException e) {
                for (java.lang.reflect.Field field : ModelPart.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        childrenField = field;
                        break;
                    }
                }
            }
            if (childrenField != null) {
                childrenField.setAccessible(true);
                Map<String, ModelPart> children = (Map<String, ModelPart>) childrenField.get(parent);
                if (children != null) {
                    if (children.containsKey(name)) {
                        return children.get(name);
                    }
                    for (ModelPart child : children.values()) {
                        ModelPart found = getPartRecursive(child, name);
                        if (found != null) return found;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
