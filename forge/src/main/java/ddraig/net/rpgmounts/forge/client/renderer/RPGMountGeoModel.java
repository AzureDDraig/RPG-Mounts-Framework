package ddraig.net.rpgmounts.forge.client.renderer;

import com.google.gson.JsonObject;
import ddraig.net.rpgmounts.client.renderer.JavaModelLoader;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.model.GeoModel;

import java.io.File;

public class RPGMountGeoModel extends GeoModel<RPGMountEntity> {
    @Override
    public void setCustomAnimations(RPGMountEntity animatable, long instanceId, software.bernie.geckolib.core.animation.AnimationState<RPGMountEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        getBone("hitbox").ifPresent(bone -> bone.setHidden(true));
        getBone("Hitbox").ifPresent(bone -> bone.setHidden(true));
    }

    @Override
    public ResourceLocation getModelResource(RPGMountEntity animatable) {
        ddraig.net.rpgmounts.data.MountData data = ddraig.net.rpgmounts.data.MountRegistry.getTemplate(animatable.getTemplateId());
        String modelId = (data != null && data.modelId != null && !data.modelId.isEmpty()) ? data.modelId : animatable.getTemplateId();
        return new ResourceLocation("rpg_mounts", "geo/" + modelId + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RPGMountEntity animatable) {
        ddraig.net.rpgmounts.data.MountData data = ddraig.net.rpgmounts.data.MountRegistry.getTemplate(animatable.getTemplateId());
        String texPath = (data != null && data.texturePath != null) ? data.texturePath : "";
        String modelId = (data != null && data.modelId != null && !data.modelId.isEmpty()) ? data.modelId : animatable.getTemplateId();
        return JavaModelLoader.getTexture(animatable.getTemplateId(), modelId, texPath);
    }

    @Override
    public ResourceLocation getAnimationResource(RPGMountEntity animatable) {
        ddraig.net.rpgmounts.data.MountData data = ddraig.net.rpgmounts.data.MountRegistry.getTemplate(animatable.getTemplateId());
        String animPath = (data != null && data.animationPath != null && !data.animationPath.isEmpty()) ? data.animationPath : animatable.getTemplateId();
        if (!animPath.endsWith(".animation.json")) {
            animPath = animPath + ".animation.json";
        }
        return new ResourceLocation("rpg_mounts", "animations/" + animPath);
    }

    @Override
    public BakedGeoModel getBakedModel(ResourceLocation location) {
        BakedGeoModel model = software.bernie.geckolib.cache.GeckoLibCache.getBakedModels().get(location);
        if (model == null) {
            try {
                String path = location.getPath();
                if (path.startsWith("geo/") && path.endsWith(".geo.json")) {
                    String modelId = path.substring(4, path.length() - 9);
                    File file = findFileInUnpacked(modelId, ".geo.json");
                    if (file != null && file.exists()) {
                        String content = java.nio.file.Files.readString(file.toPath());
                        JsonObject json = GsonHelper.fromJson(software.bernie.geckolib.util.JsonUtil.GEO_GSON, content, JsonObject.class);
                        Model rawModel = software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(json, Model.class);
                        BakedGeoModel bakedModel = BakedModelFactory.getForNamespace("rpg_mounts").constructGeoModel(GeometryTree.fromModel(rawModel));
                        if (bakedModel != null) {
                            software.bernie.geckolib.cache.GeckoLibCache.getBakedModels().put(location, bakedModel);
                        }
                    }
                }
            } catch (Exception e) {
                ddraig.net.rpgmounts.RPGMounts.LOGGER.error("Failed to dynamically load/bake GeckoLib model: " + location, e);
            }
        }
        BakedGeoModel baked = super.getBakedModel(location);
        if (baked != null) {
            try {
                String path = location.getPath();
                if (path.startsWith("geo/") && path.endsWith(".geo.json")) {
                    String modelId = path.substring(4, path.length() - 9);
                    injectProgrammaticSeatBones(baked, modelId);
                }
            } catch (Exception e) {
                ddraig.net.rpgmounts.RPGMounts.LOGGER.error("Failed to inject programmatic seat bones into model: " + location, e);
            }
        }
        return baked;
    }

    private void injectProgrammaticSeatBones(BakedGeoModel model, String modelId) {
        java.util.List<ddraig.net.rpgmounts.data.MountData> sharingTemplates = new java.util.ArrayList<>();
        for (ddraig.net.rpgmounts.data.MountData data : ddraig.net.rpgmounts.data.MountRegistry.loadedTemplates.values()) {
            String mId = (data.modelId != null && !data.modelId.isEmpty()) ? data.modelId : data.id;
            if (mId.equalsIgnoreCase(modelId)) {
                sharingTemplates.add(data);
            }
        }

        if (sharingTemplates.isEmpty()) {
            return;
        }

        java.util.Map<GeoBone, org.joml.Vector3f> absolutePivots = null;

        for (ddraig.net.rpgmounts.data.MountData data : sharingTemplates) {
            boolean alreadyAdded = false;
            for (Object rootObj : model.getBones()) {
                if (hasSpecificTemplateSeat(rootObj, data.id)) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (alreadyAdded) {
                continue;
            }

            if (absolutePivots == null) {
                absolutePivots = new java.util.HashMap<>();
                for (Object rootObj : model.getBones()) {
                    computeAbsolutePivots(rootObj, 0.0f, 0.0f, 0.0f, absolutePivots);
                }
            }

            for (int i = 0; i < data.seats.size(); i++) {
                ddraig.net.rpgmounts.data.MountData.SeatOffset offset = data.seats.get(i);
                float sx = (float) offset.x * 16.0f;
                float sy = (float) offset.y * 16.0f;
                float sz = (float) offset.z * 16.0f;

                GeoBone nearest = null;
                float minDstSq = Float.MAX_VALUE;
                for (java.util.Map.Entry<GeoBone, org.joml.Vector3f> entry : absolutePivots.entrySet()) {
                    GeoBone b = entry.getKey();
                    String name = b.getName().toLowerCase();
                    if (name.contains("body") || name.contains("torso") || name.contains("spine") || name.contains("root") || name.contains("chest") || name.contains("waist") || name.contains("pelvis")) {
                        org.joml.Vector3f pivot = entry.getValue();
                        float dx = sx - pivot.x;
                        float dy = sy - pivot.y;
                        float dz = sz - pivot.z;
                        float dstSq = dx * dx + dy * dy + dz * dz;
                        if (dstSq < minDstSq) {
                            minDstSq = dstSq;
                            nearest = b;
                        }
                    }
                }

                if (nearest == null) {
                    minDstSq = Float.MAX_VALUE;
                    for (java.util.Map.Entry<GeoBone, org.joml.Vector3f> entry : absolutePivots.entrySet()) {
                        GeoBone b = entry.getKey();
                        String name = b.getName().toLowerCase();
                        if (name.contains("seat") || name.contains("hitbox") || name.contains("leg") || name.contains("foot") ||
                            name.contains("feet") || name.contains("arm") || name.contains("hand") || name.contains("finger") ||
                            name.contains("toe") || name.contains("wing") || name.contains("tail") || name.contains("head") ||
                            name.contains("neck") || name.contains("jaw") || name.contains("ear") || name.contains("horn") ||
                            name.contains("shoulder") || name.contains("thigh") || name.contains("calf") || name.contains("paw") ||
                            name.contains("fin") || name.contains("wheel") || name.contains("claw") || name.contains("comb") ||
                            name.contains("mane")) {
                            continue;
                        }
                        org.joml.Vector3f pivot = entry.getValue();
                        float dx = sx - pivot.x;
                        float dy = sy - pivot.y;
                        float dz = sz - pivot.z;
                        float dstSq = dx * dx + dy * dy + dz * dz;
                        if (dstSq < minDstSq) {
                            minDstSq = dstSq;
                            nearest = b;
                        }
                    }
                }

                if (nearest == null) {
                    minDstSq = Float.MAX_VALUE;
                    for (java.util.Map.Entry<GeoBone, org.joml.Vector3f> entry : absolutePivots.entrySet()) {
                        GeoBone b = entry.getKey();
                        String name = b.getName().toLowerCase();
                        if (name.contains("seat") || name.contains("hitbox")) {
                            continue;
                        }
                        org.joml.Vector3f pivot = entry.getValue();
                        float dx = sx - pivot.x;
                        float dy = sy - pivot.y;
                        float dz = sz - pivot.z;
                        float dstSq = dx * dx + dy * dy + dz * dz;
                        if (dstSq < minDstSq) {
                            minDstSq = dstSq;
                            nearest = b;
                        }
                    }
                }

                if (nearest != null) {
                    org.joml.Vector3f parentPivot = absolutePivots.get(nearest);
                    GeoBone seatBone = new GeoBone(nearest, "programmatic_seat_" + data.id + "_" + i, false, 0.0, false, false);
                    seatBone.setPosX(sx - parentPivot.x);
                    seatBone.setPosY(sy - parentPivot.y);
                    seatBone.setPosZ(sz - parentPivot.z);
                    seatBone.setTrackingMatrices(true);
                    seatBone.saveInitialSnapshot();
                    nearest.getChildBones().add(seatBone);

                    absolutePivots.put(seatBone, new org.joml.Vector3f(sx, sy, sz));
                }
            }
        }
    }

    private boolean hasSpecificTemplateSeat(Object boneObj, String templateId) {
        GeoBone bone = (GeoBone) boneObj;
        if (bone.getName().startsWith("programmatic_seat_" + templateId + "_")) {
            return true;
        }
        for (Object child : bone.getChildBones()) {
            if (hasSpecificTemplateSeat(child, templateId)) {
                return true;
            }
        }
        return false;
    }

    private void computeAbsolutePivots(Object boneObj, float parentX, float parentY, float parentZ, java.util.Map<GeoBone, org.joml.Vector3f> absolutePivots) {
        GeoBone bone = (GeoBone) boneObj;
        float absX = parentX + bone.getPivotX();
        float absY = parentY + bone.getPivotY();
        float absZ = parentZ + bone.getPivotZ();
        absolutePivots.put(bone, new org.joml.Vector3f(absX, absY, absZ));
        for (Object child : bone.getChildBones()) {
            computeAbsolutePivots(child, absX, absY, absZ, absolutePivots);
        }
    }

    @Override
    public Animation getAnimation(RPGMountEntity animatable, String name) {
        ResourceLocation location = getAnimationResource(animatable);
        BakedAnimations bakedAnimations = software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations().get(location);
        if (bakedAnimations == null) {
            try {
                String path = location.getPath();
                if (path.startsWith("animations/") && path.endsWith(".animation.json")) {
                    String modelId = path.substring(11, path.length() - 15);
                    File file = findFileInUnpacked(modelId, ".animation.json");
                    if (file != null && file.exists()) {
                        String content = java.nio.file.Files.readString(file.toPath());
                        JsonObject json = GsonHelper.fromJson(software.bernie.geckolib.util.JsonUtil.GEO_GSON, content, JsonObject.class);
                        bakedAnimations = software.bernie.geckolib.util.JsonUtil.GEO_GSON.fromJson(json.getAsJsonObject("animations"), BakedAnimations.class);
                        if (bakedAnimations != null) {
                            software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations().put(location, bakedAnimations);
                        }
                    }
                }
            } catch (Exception e) {
                ddraig.net.rpgmounts.RPGMounts.LOGGER.error("Failed to dynamically load/bake GeckoLib animations: " + location, e);
            }
        }
        return super.getAnimation(animatable, name);
    }

    private File findFileInUnpacked(String mountOrModelId, String suffix) {
        File configFolder = ddraig.net.rpgmounts.data.MountRegistry.getMountsFolder();
        File unpackedFolder = new File(configFolder, mountOrModelId);
        if (unpackedFolder.exists() && unpackedFolder.isDirectory()) {
            File[] files = unpackedFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().toLowerCase().endsWith(suffix)) {
                        return f;
                    }
                }
            }
        }
        return null;
    }
}
