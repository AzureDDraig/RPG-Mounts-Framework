package ddraig.net.rpgmounts.data;

import ddraig.net.rpgmounts.RPGMounts;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * RPG Mounts Database Manager
 * Manages SQLite database initialization, dynamic driver loading, caching, and async persist operations.
 * 
 * Change Log:
 * - 2026-06-18: [Initial Creation] - Implemented SQLite dynamic driver loading, tables init, thread-safe connections, and caches.
 */
public class DatabaseManager {
    private static Connection connection;
    private static File dbFile;
    private static Driver sqliteDriver;
    private static boolean driverLoaded = false;
    private static Path activeWorldPath;

    // Thread-safe caches
    public static final Map<UUID, Map<String, UnlockedMountData>> unlockedMountsCache = new ConcurrentHashMap<>();
    public static final Map<UUID, Map<String, MountGearData>> mountGearCache = new ConcurrentHashMap<>();
    public static final Map<UUID, ActiveMountData> activeMountsCache = new ConcurrentHashMap<>();
    public static final Map<UUID, List<String>> bestiaryCache = new ConcurrentHashMap<>();

    private static ExecutorService dbExecutor;
    private static ScheduledExecutorService scheduler;

    public static boolean isInitialized() {
        return dbExecutor != null && !dbExecutor.isShutdown() && connection != null;
    }

    // Precompiled PreparedStatements
    private static PreparedStatement insertUnlockedMountStmt;
    private static PreparedStatement deleteUnlockedMountStmt;
    private static PreparedStatement insertMountGearStmt;
    private static PreparedStatement deleteMountGearStmt;
    private static PreparedStatement insertActiveMountStmt;
    private static PreparedStatement deleteActiveMountStmt;

    public static synchronized void ensureDriverLoaded(Path worldPath) {
        if (driverLoaded) return;

        // Try standard classloading
        try {
            Class<?> jdbcClass;
            try {
                jdbcClass = Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ex) {
                jdbcClass = Class.forName("com.rpgmounts.compass.sqlite.JDBC");
            }
            sqliteDriver = (Driver) jdbcClass.getDeclaredConstructor().newInstance();
            driverLoaded = true;
            RPGMounts.LOGGER.info("SQLite JDBC driver found on classpath.");
            return;
        } catch (Exception e) {
            // Not found on classpath, load dynamically
        }

        // Copy and load dynamically
        try {
            File dbDir = new File(worldPath.toFile(), "rpg_mounts");
            File libDir = new File(dbDir, "lib");
            if (!libDir.exists()) {
                libDir.mkdirs();
            }
            File jarFile = new File(libDir, "sqlite-jdbc-3.43.0.0.jar");
            if (!jarFile.exists()) {
                try (InputStream in = DatabaseManager.class.getResourceAsStream("/rpgmounts/lib/sqlite-jdbc-3.43.0.0.jar")) {
                    if (in == null) {
                        throw new IOException("Embedded SQLite JDBC driver jar not found in resources!");
                    }
                    Files.copy(in, jarFile.toPath());
                }
            }

            URL jarUrl = jarFile.toURI().toURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[]{jarUrl}, DatabaseManager.class.getClassLoader());
            Class<?> jdbcClass = Class.forName("org.sqlite.JDBC", true, classLoader);
            sqliteDriver = (Driver) jdbcClass.getDeclaredConstructor().newInstance();
            driverLoaded = true;
            RPGMounts.LOGGER.info("Dynamically loaded SQLite JDBC driver from: " + jarFile.getAbsolutePath());
        } catch (Exception e) {
            RPGMounts.LOGGER.error("Failed to dynamically load SQLite JDBC driver:", e);
        }
    }

    public static Connection getSQLiteConnection(String url) throws SQLException {
        if (sqliteDriver != null) {
            return sqliteDriver.connect(url, new Properties());
        }
        return DriverManager.getConnection(url);
    }

    public static synchronized void init(Path worldPath) {
        activeWorldPath = worldPath;
        try {
            ensureDriverLoaded(worldPath);
            File dbDir = new File(worldPath.toFile(), "rpg_mounts");
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }
            dbFile = new File(dbDir, "mounts_db.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = getSQLiteConnection(url);
            createTables();
            precompileStatements();
            loadGlobalCaches();

            if (dbExecutor == null || dbExecutor.isShutdown()) {
                dbExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "RPG-Mounts-DB-Thread");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "RPG-Mounts-DB-Scheduler");
                    thread.setDaemon(true);
                    return thread;
                });
            }

            scheduler.scheduleAtFixedRate(DatabaseManager::flushDirtyUnlockedMounts, 300, 300, TimeUnit.SECONDS);
            RPGMounts.LOGGER.info("RPG Mounts SQLite Database initialized and cached successfully.");
        } catch (Exception e) {
            RPGMounts.LOGGER.error("Failed to initialize RPG Mounts database:", e);
        }
    }

    private static void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Check if column instance_id exists in unlocked_mounts
            boolean hasInstanceId = false;
            try {
                DatabaseMetaData dbm = connection.getMetaData();
                try (ResultSet tables = dbm.getTables(null, null, "unlocked_mounts", null)) {
                    if (tables.next()) {
                        try (ResultSet rs = dbm.getColumns(null, null, "unlocked_mounts", "instance_id")) {
                            if (rs.next()) {
                                hasInstanceId = true;
                            }
                        }
                    } else {
                        hasInstanceId = true;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }

            if (!hasInstanceId) {
                stmt.execute("ALTER TABLE unlocked_mounts RENAME TO unlocked_mounts_old");
                
                stmt.execute("CREATE TABLE IF NOT EXISTS unlocked_mounts (" +
                        "player_uuid TEXT, " +
                        "instance_id TEXT, " +
                        "mount_id TEXT, " +
                        "bonding_score INTEGER, " +
                        "level INTEGER DEFAULT 1, " +
                        "xp REAL DEFAULT 0.0, " +
                        "damage_dealt REAL DEFAULT 0.0, " +
                        "damage_taken REAL DEFAULT 0.0, " +
                        "hp_zero_count INTEGER DEFAULT 0, " +
                        "distance_travelled REAL DEFAULT 0.0, " +
                        "is_chroma INTEGER DEFAULT 0, " +
                        "ancestry_log TEXT DEFAULT '[]', " +
                        "custom_name TEXT DEFAULT '', " +
                        "PRIMARY KEY (player_uuid, instance_id))");

                stmt.execute("INSERT INTO unlocked_mounts (player_uuid, instance_id, mount_id, bonding_score, level, xp, damage_dealt, damage_taken, hp_zero_count, distance_travelled, is_chroma, ancestry_log, custom_name) " +
                        "SELECT player_uuid, mount_id, mount_id, bonding_score, level, xp, damage_dealt, damage_taken, hp_zero_count, distance_travelled, is_chroma, ancestry_log, '' FROM unlocked_mounts_old");

                stmt.execute("DROP TABLE IF EXISTS unlocked_mounts_old");
            } else {
                stmt.execute("CREATE TABLE IF NOT EXISTS unlocked_mounts (" +
                        "player_uuid TEXT, " +
                        "instance_id TEXT, " +
                        "mount_id TEXT, " +
                        "bonding_score INTEGER, " +
                        "level INTEGER DEFAULT 1, " +
                        "xp REAL DEFAULT 0.0, " +
                        "damage_dealt REAL DEFAULT 0.0, " +
                        "damage_taken REAL DEFAULT 0.0, " +
                        "hp_zero_count INTEGER DEFAULT 0, " +
                        "distance_travelled REAL DEFAULT 0.0, " +
                        "is_chroma INTEGER DEFAULT 0, " +
                        "ancestry_log TEXT DEFAULT '[]', " +
                        "custom_name TEXT DEFAULT '', " +
                        "PRIMARY KEY (player_uuid, instance_id))");
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS bestiary_discoveries (" +
                    "player_uuid TEXT, " +
                    "mount_id TEXT, " +
                    "PRIMARY KEY (player_uuid, mount_id))");

            boolean hasGearInstanceId = false;
            try {
                DatabaseMetaData dbm = connection.getMetaData();
                try (ResultSet tables = dbm.getTables(null, null, "mount_gear", null)) {
                    if (tables.next()) {
                        try (ResultSet rs = dbm.getColumns(null, null, "mount_gear", "instance_id")) {
                            if (rs.next()) {
                                hasGearInstanceId = true;
                            }
                        }
                    } else {
                        hasGearInstanceId = true;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }

            if (!hasGearInstanceId) {
                stmt.execute("ALTER TABLE mount_gear RENAME TO mount_gear_old");
                
                stmt.execute("CREATE TABLE IF NOT EXISTS mount_gear (" +
                        "player_uuid TEXT, " +
                        "instance_id TEXT, " +
                        "saddle_item TEXT, " +
                        "armor_item TEXT, " +
                        "cargo_item TEXT, " +
                        "cargo_nbt TEXT, " +
                        "PRIMARY KEY (player_uuid, instance_id))");

                stmt.execute("INSERT INTO mount_gear (player_uuid, instance_id, saddle_item, armor_item, cargo_item, cargo_nbt) " +
                        "SELECT player_uuid, mount_id, saddle_item, armor_item, cargo_item, cargo_nbt FROM mount_gear_old");

                stmt.execute("DROP TABLE IF EXISTS mount_gear_old");
            } else {
                stmt.execute("CREATE TABLE IF NOT EXISTS mount_gear (" +
                        "player_uuid TEXT, " +
                        "instance_id TEXT, " +
                        "saddle_item TEXT, " +
                        "armor_item TEXT, " +
                        "cargo_item TEXT, " +
                        "cargo_nbt TEXT, " +
                        "PRIMARY KEY (player_uuid, instance_id))");
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS active_mounts (" +
                    "player_uuid TEXT PRIMARY KEY, " +
                    "active_mount_uuid TEXT, " +
                    "status TEXT, " +
                    "timer_remaining INTEGER)");
        }
    }

    private static void precompileStatements() throws SQLException {
        insertUnlockedMountStmt = connection.prepareStatement("INSERT OR REPLACE INTO unlocked_mounts (player_uuid, instance_id, mount_id, bonding_score, level, xp, damage_dealt, damage_taken, hp_zero_count, distance_travelled, is_chroma, ancestry_log, custom_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        deleteUnlockedMountStmt = connection.prepareStatement("DELETE FROM unlocked_mounts WHERE player_uuid = ? AND instance_id = ?");
        insertMountGearStmt = connection.prepareStatement("INSERT OR REPLACE INTO mount_gear (player_uuid, instance_id, saddle_item, armor_item, cargo_item, cargo_nbt) VALUES (?, ?, ?, ?, ?, ?)");
        deleteMountGearStmt = connection.prepareStatement("DELETE FROM mount_gear WHERE player_uuid = ? AND instance_id = ?");
        insertActiveMountStmt = connection.prepareStatement("INSERT OR REPLACE INTO active_mounts (player_uuid, active_mount_uuid, status, timer_remaining) VALUES (?, ?, ?, ?)");
        deleteActiveMountStmt = connection.prepareStatement("DELETE FROM active_mounts WHERE player_uuid = ?");
    }

    private static void loadGlobalCaches() {
        unlockedMountsCache.clear();
        mountGearCache.clear();
        activeMountsCache.clear();
        bestiaryCache.clear();

        try (Statement stmt = connection.createStatement()) {
            // Load bestiary discoveries
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM bestiary_discoveries")) {
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String mountId = rs.getString("mount_id");
                    bestiaryCache.computeIfAbsent(playerUuid, k -> new CopyOnWriteArrayList<>()).add(mountId);
                }
            }

            // Load unlocked mounts
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM unlocked_mounts")) {
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String instanceId = rs.getString("instance_id");
                    String mountId = rs.getString("mount_id");
                    UnlockedMountData data = new UnlockedMountData();
                    data.playerUuid = playerUuid;
                    data.instanceId = instanceId;
                    data.mountId = mountId;
                    data.bondingScore = rs.getInt("bonding_score");
                    data.level = rs.getInt("level");
                    data.xp = rs.getDouble("xp");
                    data.damageDealt = rs.getDouble("damage_dealt");
                    data.damageTaken = rs.getDouble("damage_taken");
                    data.hpZeroCount = rs.getInt("hp_zero_count");
                    data.distanceTravelled = rs.getDouble("distance_travelled");
                    data.isChroma = rs.getInt("is_chroma") != 0;
                    data.ancestryLog = rs.getString("ancestry_log");
                    if (data.ancestryLog == null) {
                        data.ancestryLog = "[]";
                    }
                    data.customName = rs.getString("custom_name");
                    if (data.customName == null) {
                        data.customName = "";
                    }
                    unlockedMountsCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(instanceId, data);
                }
            }

            // Load mount gear
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM mount_gear")) {
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String instanceId = rs.getString("instance_id");
                    MountGearData gear = new MountGearData();
                    gear.saddleItem = rs.getString("saddle_item");
                    gear.armorItem = rs.getString("armor_item");
                    gear.cargoItem = rs.getString("cargo_item");
                    gear.cargoNbt = rs.getString("cargo_nbt");
                    mountGearCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(instanceId, gear);
                }
            }

            // Load active mounts
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM active_mounts")) {
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    ActiveMountData active = new ActiveMountData();
                    active.activeMountUuid = rs.getString("active_mount_uuid");
                    active.status = rs.getString("status");
                    active.timerRemaining = rs.getInt("timer_remaining");
                    activeMountsCache.put(playerUuid, active);
                }
            }
            if (ddraig.net.rpgmounts.config.ModConfig.get().general.prevent_duplicate_mounts) {
                deduplicateAllCachedMounts();
            }
        } catch (SQLException e) {
            RPGMounts.LOGGER.error("Failed to load global database caches:", e);
        }
    }

    public static boolean isSameTemplate(String idA, String idB) {
        if (idA == null || idB == null) return false;
        if (idA.equalsIgnoreCase(idB)) return true;
        String resA = MountRegistry.resolveTemplateId(idA);
        String resB = MountRegistry.resolveTemplateId(idB);
        if (resA != null && resB != null && resA.equalsIgnoreCase(resB)) return true;
        MountData tA = MountRegistry.getTemplate(idA);
        MountData tB = MountRegistry.getTemplate(idB);
        return tA != null && tB != null && tA.id != null && tB.id != null && tA.id.equalsIgnoreCase(tB.id);
    }

    public static boolean hasUnlockedMount(UUID playerUuid, String templateIdOrName) {
        if (playerUuid == null || templateIdOrName == null) return false;
        Map<String, UnlockedMountData> map = unlockedMountsCache.get(playerUuid);
        if (map == null || map.isEmpty()) return false;
        for (UnlockedMountData d : map.values()) {
            if (isSameTemplate(d.mountId, templateIdOrName)) {
                return true;
            }
            if (d.customName != null && !d.customName.isEmpty() && d.customName.equalsIgnoreCase(templateIdOrName)) {
                return true;
            }
        }
        return false;
    }

    public static int deduplicatePlayerMounts(UUID playerUuid) {
        if (playerUuid == null) return 0;
        Map<String, UnlockedMountData> map = unlockedMountsCache.get(playerUuid);
        if (map == null || map.size() <= 1) return 0;

        Map<String, List<UnlockedMountData>> groups = new HashMap<>();
        for (UnlockedMountData d : map.values()) {
            String resolved = MountRegistry.resolveTemplateId(d.mountId);
            groups.computeIfAbsent(resolved.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(d);
        }

        int removedCount = 0;
        for (List<UnlockedMountData> list : groups.values()) {
            if (list.size() > 1) {
                // Sort to find the highest value instance: highest level, highest bonding, highest xp
                list.sort((a, b) -> {
                    int c = Integer.compare(b.level, a.level);
                    if (c != 0) return c;
                    c = Integer.compare(b.bondingScore, a.bondingScore);
                    if (c != 0) return c;
                    return Double.compare(b.xp, a.xp);
                });

                // Keep the first (best) one, delete remaining duplicates
                for (int i = 1; i < list.size(); i++) {
                    UnlockedMountData duplicate = list.get(i);
                    map.remove(duplicate.instanceId);
                    removeUnlockedMountAsync(playerUuid, duplicate.instanceId);
                    removedCount++;
                }
            }
        }
        return removedCount;
    }

    public static void deduplicateAllCachedMounts() {
        for (UUID playerUuid : unlockedMountsCache.keySet()) {
            deduplicatePlayerMounts(playerUuid);
        }
    }

    // Async DB Operations
    public static void saveUnlockedMountAsync(UUID playerUuid, String instanceId, String mountId, int bondingScore) {
        saveUnlockedMountAsync(playerUuid, instanceId, mountId, bondingScore, "");
    }

    public static void saveUnlockedMountAsync(UUID playerUuid, String instanceId, String mountId, int bondingScore, String customName) {
        saveBestiaryDiscoveryAsync(playerUuid, mountId);
        Map<String, UnlockedMountData> playerMap = unlockedMountsCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
        
        String effectiveInstanceId = instanceId;
        if (ddraig.net.rpgmounts.config.ModConfig.get().general.prevent_duplicate_mounts && !playerMap.containsKey(effectiveInstanceId)) {
            for (UnlockedMountData existing : playerMap.values()) {
                if (isSameTemplate(existing.mountId, mountId)) {
                    effectiveInstanceId = existing.instanceId;
                    break;
                }
            }
        }
        final String finalInstId = effectiveInstanceId;

        UnlockedMountData data = playerMap.computeIfAbsent(finalInstId, k -> {
            UnlockedMountData d = new UnlockedMountData();
            d.playerUuid = playerUuid;
            d.instanceId = finalInstId;
            d.mountId = MountRegistry.resolveTemplateId(mountId);
            return d;
        });
        data.mountId = MountRegistry.resolveTemplateId(mountId);
        data.bondingScore = bondingScore;
        data.customName = customName == null ? "" : customName;
        data.dirty = true;
        saveUnlockedMountDataAsync(data);
    }

    public static java.util.concurrent.CompletableFuture<Void> saveUnlockedMountDataAsync(UnlockedMountData data) {
        saveBestiaryDiscoveryAsync(data.playerUuid, data.mountId);
        data.dirty = true;
        if (!isInitialized()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                insertUnlockedMountStmt.setString(1, data.playerUuid.toString());
                insertUnlockedMountStmt.setString(2, data.instanceId);
                insertUnlockedMountStmt.setString(3, data.mountId);
                insertUnlockedMountStmt.setInt(4, data.bondingScore);
                insertUnlockedMountStmt.setInt(5, data.level);
                insertUnlockedMountStmt.setDouble(6, data.xp);
                insertUnlockedMountStmt.setDouble(7, data.damageDealt);
                insertUnlockedMountStmt.setDouble(8, data.damageTaken);
                insertUnlockedMountStmt.setInt(9, data.hpZeroCount);
                insertUnlockedMountStmt.setDouble(10, data.distanceTravelled);
                insertUnlockedMountStmt.setInt(11, data.isChroma ? 1 : 0);
                insertUnlockedMountStmt.setString(12, data.ancestryLog);
                insertUnlockedMountStmt.setString(13, data.customName == null ? "" : data.customName);
                insertUnlockedMountStmt.executeUpdate();
                data.dirty = false;
            } catch (SQLException e) {
                RPGMounts.LOGGER.error("Failed to save unlocked mount data asynchronously:", e);
                throw new java.util.concurrent.CompletionException(e);
            }
        }, dbExecutor);
    }

    public static void saveUnlockedMountDataAsync(UUID playerUuid, String instanceId, String mountId, int bondingScore, int level, double xp,
                                                  double damageDealt, double damageTaken, int hpZeroCount, double distanceTravelled,
                                                  boolean isChroma) {
        saveUnlockedMountDataAsync(playerUuid, instanceId, mountId, bondingScore, level, xp, damageDealt, damageTaken, hpZeroCount, distanceTravelled, isChroma, "[]", "");
    }

    public static void saveUnlockedMountDataAsync(UUID playerUuid, String instanceId, String mountId, int bondingScore, int level, double xp,
                                                  double damageDealt, double damageTaken, int hpZeroCount, double distanceTravelled,
                                                  boolean isChroma, String ancestryLog, String customName) {
        Map<String, UnlockedMountData> playerMap = unlockedMountsCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
        String effectiveInstanceId = instanceId;
        if (ddraig.net.rpgmounts.config.ModConfig.get().general.prevent_duplicate_mounts && !playerMap.containsKey(effectiveInstanceId)) {
            for (UnlockedMountData existing : playerMap.values()) {
                if (isSameTemplate(existing.mountId, mountId)) {
                    effectiveInstanceId = existing.instanceId;
                    break;
                }
            }
        }
        final String finalInstId = effectiveInstanceId;

        UnlockedMountData data = playerMap.computeIfAbsent(finalInstId, k -> {
            UnlockedMountData d = new UnlockedMountData();
            d.playerUuid = playerUuid;
            d.instanceId = finalInstId;
            d.mountId = MountRegistry.resolveTemplateId(mountId);
            return d;
        });
        data.mountId = MountRegistry.resolveTemplateId(mountId);
        data.bondingScore = bondingScore;
        data.level = level;
        data.xp = xp;
        data.damageDealt = damageDealt;
        data.damageTaken = damageTaken;
        data.hpZeroCount = hpZeroCount;
        data.distanceTravelled = distanceTravelled;
        data.isChroma = isChroma;
        data.ancestryLog = ancestryLog;
        data.customName = customName == null ? "" : customName;
        data.dirty = true;
        saveUnlockedMountDataAsync(data);
    }

    public static java.util.concurrent.CompletableFuture<Void> removeUnlockedMountAsync(UUID playerUuid, String instanceId) {
        Map<String, UnlockedMountData> owned = unlockedMountsCache.get(playerUuid);
        if (owned != null) owned.remove(instanceId);
        deleteMountGearAsync(playerUuid, instanceId);
        if (!isInitialized()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                deleteUnlockedMountStmt.setString(1, playerUuid.toString());
                deleteUnlockedMountStmt.setString(2, instanceId);
                deleteUnlockedMountStmt.executeUpdate();
            } catch (SQLException e) {
                RPGMounts.LOGGER.error("Failed to remove unlocked mount asynchronously:", e);
                throw new java.util.concurrent.CompletionException(e);
            }
        }, dbExecutor);
    }

    public static java.util.concurrent.CompletableFuture<java.util.List<String>> removeMatchingUnlockedMountsAsync(UUID playerUuid, String targetQuery) {
        java.util.List<String> removedInstanceIds = new java.util.ArrayList<>();
        if (targetQuery == null || targetQuery.trim().isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(removedInstanceIds);
        }

        String raw = targetQuery.trim();
        String extracted = extractUuidFromQuery(raw);
        String queryClean = (extracted != null ? extracted : raw).toLowerCase();

        Map<String, UnlockedMountData> owned = unlockedMountsCache.get(playerUuid);
        if (owned != null) {
            java.util.List<String> toRemove = new java.util.ArrayList<>();
            for (Map.Entry<String, UnlockedMountData> entry : owned.entrySet()) {
                String instId = entry.getKey();
                UnlockedMountData data = entry.getValue();
                MountData template = MountRegistry.getTemplate(data.mountId);
                String templateName = template != null && template.name != null ? template.name : data.mountId;

                if (instId.toLowerCase().equals(queryClean) ||
                    data.mountId.toLowerCase().equals(queryClean) ||
                    (data.customName != null && data.customName.toLowerCase().equals(queryClean)) ||
                    templateName.toLowerCase().equals(queryClean) ||
                    raw.toLowerCase().contains(instId.toLowerCase())) {
                    toRemove.add(instId);
                }
            }
            for (String instId : toRemove) {
                owned.remove(instId);
                if (!removedInstanceIds.contains(instId)) {
                    removedInstanceIds.add(instId);
                }
                deleteMountGearAsync(playerUuid, instId);
            }
        }

        if (!isInitialized()) {
            return java.util.concurrent.CompletableFuture.completedFuture(removedInstanceIds);
        }

        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                String sql = "DELETE FROM unlocked_mounts WHERE player_uuid = ? AND (LOWER(instance_id) = ? OR LOWER(mount_id) = ? OR LOWER(custom_name) = ?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, queryClean);
                    stmt.setString(3, queryClean);
                    stmt.setString(4, queryClean);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                RPGMounts.LOGGER.error("Failed to remove matching unlocked mounts asynchronously:", e);
            }
            return removedInstanceIds;
        }, dbExecutor);
    }

    private static final java.util.regex.Pattern UUID_PATTERN = 
        java.util.regex.Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static String extractUuidFromQuery(String input) {
        if (input == null) return null;
        java.util.regex.Matcher matcher = UUID_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    public static void executeWithCallback(java.util.concurrent.CompletableFuture<Void> future, java.util.concurrent.Executor mainThreadExecutor, Runnable onSuccess, java.util.function.Consumer<Throwable> onFailure) {
        future.whenCompleteAsync((v, ex) -> {
            if (mainThreadExecutor != null) {
                mainThreadExecutor.execute(() -> {
                    if (ex != null) {
                        if (onFailure != null) onFailure.accept(ex);
                    } else {
                        if (onSuccess != null) onSuccess.run();
                    }
                });
            } else {
                if (ex != null) {
                    if (onFailure != null) onFailure.accept(ex);
                } else {
                    if (onSuccess != null) onSuccess.run();
                }
            }
        });
    }

    public static void saveMountGearAsync(UUID playerUuid, String instanceId, MountGearData gear) {
        mountGearCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(instanceId, gear);
        if (!isInitialized()) return;
        dbExecutor.submit(() -> {
            try {
                insertMountGearStmt.setString(1, playerUuid.toString());
                insertMountGearStmt.setString(2, instanceId);
                insertMountGearStmt.setString(3, gear.saddleItem);
                insertMountGearStmt.setString(4, gear.armorItem);
                insertMountGearStmt.setString(5, gear.cargoItem);
                insertMountGearStmt.setString(6, gear.cargoNbt);
                insertMountGearStmt.executeUpdate();
            } catch (SQLException e) {
                RPGMounts.LOGGER.error("Failed to save mount gear asynchronously:", e);
            }
        });
    }

    public static void deleteMountGearAsync(UUID playerUuid, String instanceId) {
        Map<String, MountGearData> playerGear = mountGearCache.get(playerUuid);
        if (playerGear != null) playerGear.remove(instanceId);
        if (!isInitialized()) return;
        dbExecutor.submit(() -> {
            try {
                deleteMountGearStmt.setString(1, playerUuid.toString());
                deleteMountGearStmt.setString(2, instanceId);
                deleteMountGearStmt.executeUpdate();
            } catch (SQLException e) {
                RPGMounts.LOGGER.error("Failed to delete mount gear asynchronously:", e);
            }
        });
    }

    public static void saveActiveMountAsync(UUID playerUuid, ActiveMountData active) {
        if (active == null) {
            activeMountsCache.remove(playerUuid);
            if (!isInitialized()) return;
            dbExecutor.submit(() -> {
                try {
                    deleteActiveMountStmt.setString(1, playerUuid.toString());
                    deleteActiveMountStmt.executeUpdate();
                } catch (SQLException e) {
                    RPGMounts.LOGGER.error("Failed to remove active mount asynchronously:", e);
                }
            });
        } else {
            activeMountsCache.put(playerUuid, active);
            if (!isInitialized()) return;
            dbExecutor.submit(() -> {
                try {
                    insertActiveMountStmt.setString(1, playerUuid.toString());
                    insertActiveMountStmt.setString(2, active.activeMountUuid);
                    insertActiveMountStmt.setString(3, active.status);
                    insertActiveMountStmt.setInt(4, active.timerRemaining);
                    insertActiveMountStmt.executeUpdate();
                } catch (SQLException e) {
                    RPGMounts.LOGGER.error("Failed to save active mount asynchronously:", e);
                }
            });
        }
    }

    public static synchronized void flushPlayerCachesSynchronously(UUID playerUuid) {
        if (!isInitialized()) return;
        Map<String, UnlockedMountData> map = unlockedMountsCache.get(playerUuid);
        if (map != null) {
            for (UnlockedMountData data : map.values()) {
                if (data.dirty) {
                    try {
                        insertUnlockedMountStmt.setString(1, data.playerUuid.toString());
                        insertUnlockedMountStmt.setString(2, data.instanceId);
                        insertUnlockedMountStmt.setString(3, data.mountId);
                        insertUnlockedMountStmt.setInt(4, data.bondingScore);
                        insertUnlockedMountStmt.setInt(5, data.level);
                        insertUnlockedMountStmt.setDouble(6, data.xp);
                        insertUnlockedMountStmt.setDouble(7, data.damageDealt);
                        insertUnlockedMountStmt.setDouble(8, data.damageTaken);
                        insertUnlockedMountStmt.setInt(9, data.hpZeroCount);
                        insertUnlockedMountStmt.setDouble(10, data.distanceTravelled);
                        insertUnlockedMountStmt.setInt(11, data.isChroma ? 1 : 0);
                        insertUnlockedMountStmt.setString(12, data.ancestryLog);
                        insertUnlockedMountStmt.setString(13, data.customName == null ? "" : data.customName);
                        insertUnlockedMountStmt.executeUpdate();
                        data.dirty = false;
                    } catch (SQLException e) {
                        RPGMounts.LOGGER.error("Failed to synchronously flush mount data for player " + playerUuid, e);
                    }
                }
            }
        }
    }

    public static synchronized void flushAllDirtyUnlockedMountsSynchronously() {
        if (!isInitialized()) return;
        try {
            for (Map<String, UnlockedMountData> map : unlockedMountsCache.values()) {
                for (UnlockedMountData data : map.values()) {
                    if (data.dirty) {
                        insertUnlockedMountStmt.setString(1, data.playerUuid.toString());
                        insertUnlockedMountStmt.setString(2, data.instanceId);
                        insertUnlockedMountStmt.setString(3, data.mountId);
                        insertUnlockedMountStmt.setInt(4, data.bondingScore);
                        insertUnlockedMountStmt.setInt(5, data.level);
                        insertUnlockedMountStmt.setDouble(6, data.xp);
                        insertUnlockedMountStmt.setDouble(7, data.damageDealt);
                        insertUnlockedMountStmt.setDouble(8, data.damageTaken);
                        insertUnlockedMountStmt.setInt(9, data.hpZeroCount);
                        insertUnlockedMountStmt.setDouble(10, data.distanceTravelled);
                        insertUnlockedMountStmt.setInt(11, data.isChroma ? 1 : 0);
                        insertUnlockedMountStmt.setString(12, data.ancestryLog);
                        insertUnlockedMountStmt.setString(13, data.customName == null ? "" : data.customName);
                        insertUnlockedMountStmt.executeUpdate();
                        data.dirty = false;
                    }
                }
            }
        } catch (SQLException e) {
            RPGMounts.LOGGER.error("Failed to synchronously flush all dirty unlocked mounts:", e);
        }
    }

    public static void flushDirtyUnlockedMounts() {
        if (!isInitialized()) return;
        dbExecutor.submit(() -> {
            try {
                for (Map<String, UnlockedMountData> map : unlockedMountsCache.values()) {
                    for (UnlockedMountData data : map.values()) {
                        if (data.dirty) {
                            insertUnlockedMountStmt.setString(1, data.playerUuid.toString());
                            insertUnlockedMountStmt.setString(2, data.instanceId);
                            insertUnlockedMountStmt.setString(3, data.mountId);
                            insertUnlockedMountStmt.setInt(4, data.bondingScore);
                            insertUnlockedMountStmt.setInt(5, data.level);
                            insertUnlockedMountStmt.setDouble(6, data.xp);
                            insertUnlockedMountStmt.setDouble(7, data.damageDealt);
                            insertUnlockedMountStmt.setDouble(8, data.damageTaken);
                            insertUnlockedMountStmt.setInt(9, data.hpZeroCount);
                            insertUnlockedMountStmt.setDouble(10, data.distanceTravelled);
                            insertUnlockedMountStmt.setInt(11, data.isChroma ? 1 : 0);
                            insertUnlockedMountStmt.setString(12, data.ancestryLog);
                            insertUnlockedMountStmt.setString(13, data.customName == null ? "" : data.customName);
                            insertUnlockedMountStmt.executeUpdate();
                            data.dirty = false;
                        }
                    }
                }
            } catch (SQLException e) {
                RPGMounts.LOGGER.error("Failed to flush dirty unlocked mounts:", e);
            }
        });
    }

    public static void saveBestiaryDiscoveryAsync(UUID playerUuid, String mountId) {
        List<String> list = bestiaryCache.computeIfAbsent(playerUuid, k -> new CopyOnWriteArrayList<>());
        if (!list.contains(mountId)) {
            list.add(mountId);
        }
        if (!isInitialized()) return;
        dbExecutor.submit(() -> {
            try (PreparedStatement stmt = connection.prepareStatement("INSERT OR REPLACE INTO bestiary_discoveries (player_uuid, mount_id) VALUES (?, ?)")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, mountId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                RPGMounts.LOGGER.error("Failed to save bestiary discovery asynchronously:", e);
            }
        });
    }

    public static void transferMountGearAsync(UUID playerUuid, String oldInstanceId, String newInstanceId) {
        Map<String, MountGearData> playerGear = mountGearCache.get(playerUuid);
        if (playerGear != null) {
            MountGearData gear = playerGear.remove(oldInstanceId);
            if (gear != null) {
                playerGear.put(newInstanceId, gear);
                if (!isInitialized()) return;
                dbExecutor.submit(() -> {
                    try {
                        insertMountGearStmt.setString(1, playerUuid.toString());
                        insertMountGearStmt.setString(2, newInstanceId);
                        insertMountGearStmt.setString(3, gear.saddleItem);
                        insertMountGearStmt.setString(4, gear.armorItem);
                        insertMountGearStmt.setString(5, gear.cargoItem);
                        insertMountGearStmt.setString(6, gear.cargoNbt);
                        insertMountGearStmt.executeUpdate();

                        deleteMountGearStmt.setString(1, playerUuid.toString());
                        deleteMountGearStmt.setString(2, oldInstanceId);
                        deleteMountGearStmt.executeUpdate();
                    } catch (SQLException e) {
                        RPGMounts.LOGGER.error("Failed to transfer mount gear asynchronously:", e);
                    }
                });
            }
        }
    }

    public static synchronized void close() {
        try {
            if (insertUnlockedMountStmt != null) insertUnlockedMountStmt.close();
            if (deleteUnlockedMountStmt != null) deleteUnlockedMountStmt.close();
            if (insertMountGearStmt != null) insertMountGearStmt.close();
            if (deleteMountGearStmt != null) deleteMountGearStmt.close();
            if (insertActiveMountStmt != null) insertActiveMountStmt.close();
            if (deleteActiveMountStmt != null) deleteActiveMountStmt.close();
        } catch (SQLException e) {
            RPGMounts.LOGGER.error("Error closing prepared statements:", e);
        }
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                RPGMounts.LOGGER.info("Closed RPG Mounts database connection.");
            }
        } catch (SQLException e) {
            RPGMounts.LOGGER.error("Error closing database connection:", e);
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                dbExecutor.shutdownNow();
            }
        }
    }

    public static class MountGearData {
        public String saddleItem = "";
        public String armorItem = "";
        public String cargoItem = "";
        public String cargoNbt = ""; // Stringified JSON or Base64 binary NBT
    }

    public static class ActiveMountData {
        public String activeMountUuid = "";
        public String status = "HEALTHY"; // HEALTHY, INJURED
        public int timerRemaining = 0; // Remaining recovery ticks
    }

    public static class UnlockedMountData {
        public UUID playerUuid;
        public String instanceId = "";
        public String mountId = "";
        public int bondingScore = 0;
        public int level = 1;
        public double xp = 0.0;
        public double damageDealt = 0.0;
        public double damageTaken = 0.0;
        public int hpZeroCount = 0;
        public double distanceTravelled = 0.0;
        public boolean isChroma = false;
        public String ancestryLog = "[]";
        public String customName = "";
        public transient boolean dirty = false;
    }
}
