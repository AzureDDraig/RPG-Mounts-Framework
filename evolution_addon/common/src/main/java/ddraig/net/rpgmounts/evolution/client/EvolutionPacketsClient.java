package ddraig.net.rpgmounts.evolution.client;

import com.google.gson.Gson;
import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager;
import ddraig.net.rpgmounts.evolution.network.EvolutionPackets;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Handles client-side network receivers and packet reassembly for the Evolution Framework.
 * Separated to prevent server classloading crashes.
 */
public class EvolutionPacketsClient {
    // Client-side chunk reassembly buffer (Key: transactionId -> Value: (Map of chunkIndex -> byteData))
    private static final Map<Long, Map<Integer, byte[]>> assemblyBuffer = new ConcurrentHashMap<>();

    /**
     * Initializes client-side receivers. Must be called in client environment.
     */
    public static void initClient() {
        // S2C Sync Evolution Data (Handles chunk reassembly & decompression)
        NetworkManager.registerReceiver(NetworkManager.s2c(), EvolutionPackets.S2C_SYNC_EVOLUTION_DATA, (buf, context) -> {
            long transactionId = buf.readLong();
            int chunkIndex = buf.readInt();
            int totalChunks = buf.readInt();
            int chunkLength = buf.readInt();
            byte[] chunkData = buf.readByteArray(chunkLength);

            context.queue(() -> {
                Map<Integer, byte[]> chunks = assemblyBuffer.computeIfAbsent(transactionId, k -> new ConcurrentHashMap<>());
                chunks.put(chunkIndex, chunkData);

                if (chunks.size() == totalChunks) {
                    assemblyBuffer.remove(transactionId);
                    try {
                        // Reassemble raw bytes
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        for (int i = 0; i < totalChunks; i++) {
                            byte[] data = chunks.get(i);
                            if (data != null) {
                                bos.write(data);
                            }
                        }
                        byte[] compressedBytes = bos.toByteArray();

                        // Decompress GZIP
                        String jsonPayload;
                        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressedBytes));
                             InputStreamReader isr = new InputStreamReader(gis, StandardCharsets.UTF_8);
                             BufferedReader br = new BufferedReader(isr)) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                            jsonPayload = sb.toString();
                        }

                        // Parse and load into client tree configurations
                        Gson gson = new Gson();
                        EvolutionTreeManager.EvolutionTree[] trees = gson.fromJson(jsonPayload, EvolutionTreeManager.EvolutionTree[].class);
                        EvolutionTreeManager.loadedTrees.clear();
                        for (EvolutionTreeManager.EvolutionTree tree : trees) {
                            EvolutionTreeManager.loadedTrees.put(tree.mountId, tree);
                        }
                        
                        // Force client screens refresh if active
                        refreshActiveScreens();
                    } catch (Exception e) {
                        RPGMounts.LOGGER.error("Failed to reassemble or decompress fragmented evolution tree data", e);
                    }
                }
            });
        });

        // S2C Open Evolution Editor Screen
        NetworkManager.registerReceiver(NetworkManager.s2c(), EvolutionPackets.S2C_OPEN_EVOLUTION_EDITOR, (buf, context) -> {
            context.queue(() -> {
                Minecraft.getInstance().setScreen(new ddraig.net.rpgmounts.evolution.client.AddonEvolutionEditorScreen());
            });
        });
    }

    private static void refreshActiveScreens() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ddraig.net.rpgmounts.evolution.client.AddonEvolutionEditorScreen editor) {
            editor.refreshTrees();
        }
    }
}
