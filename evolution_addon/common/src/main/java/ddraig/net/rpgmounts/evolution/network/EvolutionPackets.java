package ddraig.net.rpgmounts.evolution.network;

import com.google.gson.Gson;
import ddraig.net.rpgmounts.RPGMounts;
import ddraig.net.rpgmounts.data.DatabaseManager;
import ddraig.net.rpgmounts.entity.RPGMountEntity;
import ddraig.net.rpgmounts.api.EvolutionAPI;
import ddraig.net.rpgmounts.evolution.config.EvolutionTreeManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Handles network message packet channels, gzip compression, and chunk fragmentation/reassembly protocols.
 */
public class EvolutionPackets {
    public static final ResourceLocation C2S_REQUEST_BRANCH_EVOLVE = new ResourceLocation("rpg_mounts_evolution_framework", "request_branch_evolve");
    public static final ResourceLocation S2C_SYNC_EVOLUTION_DATA = new ResourceLocation("rpg_mounts_evolution_framework", "sync_evolution_data");
    public static final ResourceLocation C2S_SAVE_EVOLUTION_TREE = new ResourceLocation("rpg_mounts_evolution_framework", "save_evolution_tree");
    public static final ResourceLocation S2C_OPEN_EVOLUTION_EDITOR = new ResourceLocation("rpg_mounts_evolution_framework", "open_evolution_editor");

    public static void init() {
        // C2S Request Evolve
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_REQUEST_BRANCH_EVOLVE, (buf, context) -> {
            String mountUuidStr = buf.readUtf();
            String targetTemplateId = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                try {
                    UUID mountUuid = UUID.fromString(mountUuidStr);
                    Entity entity = player.serverLevel().getEntity(mountUuid);
                    if (entity instanceof RPGMountEntity mount) {
                        boolean success = EvolutionAPI.getProvider().processServerEvolution(player, mount, targetTemplateId);
                        if (!success) {
                            player.sendSystemMessage(Component.literal("§cEvolution execution failed. Check requirements."));
                        }
                    }
                } catch (Exception e) {
                    RPGMounts.LOGGER.error("Failed to process C2S_REQUEST_BRANCH_EVOLVE packet", e);
                }
            });
        });

        // C2S Save Evolution Tree
        NetworkManager.registerReceiver(NetworkManager.c2s(), C2S_SAVE_EVOLUTION_TREE, (buf, context) -> {
            String baseMountId = buf.readUtf();
            String treeJson = buf.readUtf();
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            context.queue(() -> {
                if (player.hasPermissions(2)) {
                    // Flush active dirty stats to DB before reload
                    DatabaseManager.flushDirtyUnlockedMounts();

                    boolean success = EvolutionTreeManager.saveTree(baseMountId, treeJson);
                    if (success) {
                        player.sendSystemMessage(Component.literal("§aEvolution tree configuration saved successfully."));
                        // Hot-sync to all online players
                        syncTreesToAll(player.server);
                    } else {
                        player.sendSystemMessage(Component.literal("§cFailed to save evolution tree. Validation error."));
                    }
                }
            });
        });
    }

    /**
     * Serializes all cached trees into a single Gzipped JSON block, fragments it into 32KB chunks,
     * and broadcasts it to all connected players.
     */
    public static void syncTreesToAll(MinecraftServer server) {
        if (server == null) return;
        try {
            // Serialize
            Collection<EvolutionTreeManager.EvolutionTree> trees = EvolutionTreeManager.loadedTrees.values();
            String json = new Gson().toJson(trees.toArray(new EvolutionTreeManager.EvolutionTree[0]));
            byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);

            // Compress GZIP
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
                gos.write(rawBytes);
            }
            byte[] compressedBytes = bos.toByteArray();

            // Segment and send
            long transactionId = new Random().nextLong();
            int chunkSize = 32768; // 32KB
            int totalChunks = (int) Math.ceil((double) compressedBytes.length / chunkSize);

            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(compressedBytes.length, start + chunkSize);
                byte[] chunk = Arrays.copyOfRange(compressedBytes, start, end);

                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeLong(transactionId);
                buf.writeInt(i);
                buf.writeInt(totalChunks);
                buf.writeInt(chunk.length);
                buf.writeByteArray(chunk);

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    NetworkManager.sendToPlayer(player, S2C_SYNC_EVOLUTION_DATA, buf);
                }
            }
        } catch (Exception e) {
            RPGMounts.LOGGER.error("Failed to serialize and synchronize evolution trees", e);
        }
    }

    /**
     * Syncs evolution tree configurations to a specific player upon logging in.
     */
    public static void syncTreesToPlayer(ServerPlayer player) {
        try {
            Collection<EvolutionTreeManager.EvolutionTree> trees = EvolutionTreeManager.loadedTrees.values();
            String json = new Gson().toJson(trees.toArray(new EvolutionTreeManager.EvolutionTree[0]));
            byte[] rawBytes = json.getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
                gos.write(rawBytes);
            }
            byte[] compressedBytes = bos.toByteArray();

            long transactionId = new Random().nextLong();
            int chunkSize = 32768;
            int totalChunks = (int) Math.ceil((double) compressedBytes.length / chunkSize);

            for (int i = 0; i < totalChunks; i++) {
                int start = i * chunkSize;
                int end = Math.min(compressedBytes.length, start + chunkSize);
                byte[] chunk = Arrays.copyOfRange(compressedBytes, start, end);

                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeLong(transactionId);
                buf.writeInt(i);
                buf.writeInt(totalChunks);
                buf.writeInt(chunk.length);
                buf.writeByteArray(chunk);

                NetworkManager.sendToPlayer(player, S2C_SYNC_EVOLUTION_DATA, buf);
            }
        } catch (Exception e) {
            RPGMounts.LOGGER.error("Failed to sync trees to player " + player.getName().getString(), e);
        }
    }

}
