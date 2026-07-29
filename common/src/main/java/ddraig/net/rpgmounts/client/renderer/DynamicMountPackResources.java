package ddraig.net.rpgmounts.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import ddraig.net.rpgmounts.data.MountRegistry;

public class DynamicMountPackResources implements PackResources {

    private static String sanitizePath(String path) {
        if (path == null) return "";
        return path.toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_.-/]", "");
    }

    private File findFileCaseInsensitive(File parentDir, String relativePath) {
        if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory()) return null;
        File exact = new File(parentDir, relativePath);
        if (exact.exists()) return exact;
        
        String[] parts = relativePath.split("/");
        File current = parentDir;
        for (String part : parts) {
            if (!current.exists() || !current.isDirectory()) return null;
            File[] children = current.listFiles();
            if (children == null) return null;
            File match = null;
            for (File child : children) {
                if (child.getName().equalsIgnoreCase(part) || sanitizePath(child.getName()).equalsIgnoreCase(sanitizePath(part))) {
                    match = child;
                    break;
                }
            }
            if (match == null) return null;
            current = match;
        }
        return current.exists() ? current : null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type == PackType.CLIENT_RESOURCES && location.getNamespace().equals("rpg_mounts")) {
            String path = location.getPath();
            if (path.startsWith("geo/") && path.endsWith(".geo.json")) {
                String modelId = path.substring(4, path.length() - 9);
                File file = findFileInUnpacked(modelId, ".geo.json");
                if (file != null && file.exists()) {
                    return () -> new FileInputStream(file);
                }
            } else if (path.startsWith("animations/") && path.endsWith(".animation.json")) {
                String modelId = path.substring(11, path.length() - 15);
                File file = findFileInUnpacked(modelId, ".animation.json");
                if (file != null && file.exists()) {
                    return () -> new FileInputStream(file);
                }
            } else if (path.equals("sounds.json")) {
                String json = generateSoundsJson();
                byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return () -> new java.io.ByteArrayInputStream(bytes);
            } else if (path.startsWith("sounds/") && path.endsWith(".ogg")) {
                if (path.startsWith("sounds/unpacked/")) {
                    String unpackedPath = path.substring(16); // e.g. "modelId/idle.ogg"
                    int slashIdx = unpackedPath.indexOf('/');
                    if (slashIdx > 0) {
                        String modelId = unpackedPath.substring(0, slashIdx);
                        String relativeSoundPath = unpackedPath.substring(slashIdx + 1);
                        File unpackedFolder = findFileCaseInsensitive(MountRegistry.getMountsFolder(), modelId);
                        if (unpackedFolder != null) {
                            File file = findFileCaseInsensitive(unpackedFolder, relativeSoundPath);
                            if (file != null && file.exists()) {
                                return () -> new FileInputStream(file);
                            }
                        }
                    }
                } else if (path.startsWith("sounds/custom/")) {
                    String customPath = path.substring(14); // e.g. "idle.ogg" or "subdir/idle.ogg"
                    File file = findFileCaseInsensitive(MountRegistry.getSoundsFolder(), customPath);
                    if (file != null && file.exists()) {
                        return () -> new FileInputStream(file);
                    }
                } else {
                    String customPath = path.substring(7); // e.g. "idle.ogg"
                    File file = findFileCaseInsensitive(MountRegistry.getSoundsFolder(), customPath);
                    if (file != null && file.exists()) {
                        return () -> new FileInputStream(file);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals("rpg_mounts")) {
            return;
        }

        if (path.equals("geo")) {
            File configFolder = MountRegistry.getMountsFolder();
            File[] folders = configFolder.listFiles();
            if (folders != null) {
                for (File folder : folders) {
                    if (folder.isDirectory()) {
                        String modelId = folder.getName();
                        File modelFile = findFileInUnpacked(modelId, ".geo.json");
                        if (modelFile != null && modelFile.exists()) {
                            try {
                                String cleanId = sanitizePath(modelId);
                                ResourceLocation loc = ResourceLocation.tryParse("rpg_mounts:geo/" + cleanId + ".geo.json");
                                if (loc != null) {
                                    output.accept(loc, IoSupplier.create(modelFile.toPath()));
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } else if (path.equals("animations")) {
            File configFolder = MountRegistry.getMountsFolder();
            File[] folders = configFolder.listFiles();
            if (folders != null) {
                for (File folder : folders) {
                    if (folder.isDirectory()) {
                        String modelId = folder.getName();
                        File animFile = findFileInUnpacked(modelId, ".animation.json");
                        if (animFile != null && animFile.exists()) {
                            try {
                                String cleanId = sanitizePath(modelId);
                                ResourceLocation loc = ResourceLocation.tryParse("rpg_mounts:animations/" + cleanId + ".animation.json");
                                if (loc != null) {
                                    output.accept(loc, IoSupplier.create(animFile.toPath()));
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } else if (path.equals("sounds")) {
            // List custom sounds
            File soundsDir = MountRegistry.getSoundsFolder();
            if (soundsDir.exists() && soundsDir.isDirectory()) {
                listOggResourcesRecursive(soundsDir, "", "custom", output);
            }
            // List unpacked sounds
            File unpackedDir = MountRegistry.getMountsFolder();
            if (unpackedDir.exists() && unpackedDir.isDirectory()) {
                File[] folders = unpackedDir.listFiles();
                if (folders != null) {
                    for (File folder : folders) {
                        if (folder.isDirectory()) {
                            listOggResourcesRecursive(folder, "", "unpacked/" + sanitizePath(folder.getName()), output);
                        }
                    }
                }
            }
        }
    }

    private void listOggResourcesRecursive(File dir, String relativePath, String prefix, PackResources.ResourceOutput output) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String nextRel = relativePath.isEmpty() ? f.getName() : relativePath + "/" + f.getName();
                listOggResourcesRecursive(f, nextRel, prefix, output);
            } else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                try {
                    String rawRel = relativePath.isEmpty() ? f.getName() : relativePath + "/" + f.getName();
                    String cleanRel = sanitizePath(rawRel);
                    String resPath = "sounds/" + prefix + "/" + cleanRel;
                    ResourceLocation loc = ResourceLocation.tryParse("rpg_mounts:" + resPath);
                    if (loc != null) {
                        output.accept(loc, IoSupplier.create(f.toPath()));
                    }
                } catch (Exception ignored) {
                    // Prevent any invalid filename from crashing game loading
                }
            }
        }
    }

    private String generateSoundsJson() {
        java.util.Map<String, Object> root = new java.util.HashMap<>();

        // 1. Scan dedicated Sounds folder
        File soundsDir = MountRegistry.getSoundsFolder();
        if (soundsDir.exists() && soundsDir.isDirectory()) {
            scanOggFolder(soundsDir, "", "custom", root);
        }

        // 2. Scan Unpacked mount folders
        File unpackedDir = MountRegistry.getMountsFolder();
        if (unpackedDir.exists() && unpackedDir.isDirectory()) {
            File[] folders = unpackedDir.listFiles();
            if (folders != null) {
                for (File folder : folders) {
                    if (folder.isDirectory()) {
                        String modelId = folder.getName();
                        scanOggFolder(folder, "", "unpacked." + sanitizePath(modelId), root);
                    }
                }
            }
        }

        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private void scanOggFolder(File dir, String relativePath, String eventPrefix, java.util.Map<String, Object> root) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String nextRel = relativePath.isEmpty() ? f.getName() : relativePath + "/" + f.getName();
                scanOggFolder(f, nextRel, eventPrefix, root);
            } else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                String nameNoExt = f.getName().substring(0, f.getName().length() - 4);
                String soundPath = relativePath.isEmpty() ? nameNoExt : relativePath + "/" + nameNoExt;
                String cleanSoundPath = sanitizePath(soundPath);
                
                String eventSuffix = cleanSoundPath.replace('/', '.');
                String eventKey = eventPrefix.isEmpty() ? eventSuffix : eventPrefix + "." + eventSuffix;
                
                java.util.Map<String, Object> entry = new java.util.HashMap<>();
                entry.put("category", "neutral");
                
                java.util.List<Object> soundList = new java.util.ArrayList<>();
                java.util.Map<String, Object> soundObj = new java.util.HashMap<>();
                
                String targetResourcePath;
                if (eventPrefix.startsWith("unpacked.")) {
                    String modelId = eventPrefix.substring(9);
                    targetResourcePath = "rpg_mounts:unpacked/" + modelId + "/" + cleanSoundPath;
                } else {
                    targetResourcePath = "rpg_mounts:custom/" + cleanSoundPath;
                }
                
                soundObj.put("name", targetResourcePath);
                soundObj.put("stream", true);
                soundList.add(soundObj);
                
                entry.put("sounds", soundList);
                root.put(eventKey, entry);
                
                if (eventPrefix.equals("custom")) {
                    String aliasKey = eventSuffix;
                    if (!root.containsKey(aliasKey)) {
                        root.put(aliasKey, entry);
                    }
                }
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type == PackType.CLIENT_RESOURCES) {
            return Set.of("rpg_mounts");
        }
        return Collections.emptySet();
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
        if (serializer.getMetadataSectionName().equals("pack")) {
            net.minecraft.server.packs.metadata.pack.PackMetadataSection section = new net.minecraft.server.packs.metadata.pack.PackMetadataSection(
                net.minecraft.network.chat.Component.literal("RPG Mounts Dynamic Resources"),
                15 // 1.20.1 Client Resource Pack format is 15
            );
            return (T) section;
        }
        return null;
    }

    @Override
    public String packId() {
        return "rpg_mounts_dynamic";
    }

    @Override
    public void close() {
    }

    private File findFileInUnpacked(String mountOrModelId, String suffix) {
        File configFolder = MountRegistry.getMountsFolder();
        File unpackedFolder = findFileCaseInsensitive(configFolder, mountOrModelId);
        if (unpackedFolder != null && unpackedFolder.isDirectory()) {
            File[] files = unpackedFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().toLowerCase(Locale.ROOT).endsWith(suffix)) {
                        return f;
                    }
                }
            }
        }
        return null;
    }
}
