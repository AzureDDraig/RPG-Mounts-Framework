package ddraig.net.rpgmounts.integration;

import dev.architectury.platform.Platform;
import java.util.Collections;
import java.util.List;

public class RPGWaypointsServerIntegration {
    private static boolean waypointsLoaded = false;

    public static void init() {
        waypointsLoaded = Platform.isModLoaded("rpgwaypoints") || Platform.isModLoaded("rpg_waypoints");
    }

    public static void logAudit(String adminName, String action, String waypointName) {
        if (waypointsLoaded) {
            try {
                AuditHelper.log(adminName, action, waypointName);
            } catch (Throwable e) {
                // Prevent crashes
            }
        }
    }

    public static List<String> getAuditLogs(int page, int pageSize, String queryFilter) {
        if (waypointsLoaded) {
            try {
                return AuditHelper.getLogs(page, pageSize, queryFilter);
            } catch (Throwable e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    public static int getAuditLogsCount(String queryFilter) {
        if (waypointsLoaded) {
            try {
                return AuditHelper.getLogsCount(queryFilter);
            } catch (Throwable e) {
                return 0;
            }
        }
        return 0;
    }

    // Nested class using reflection to decouple compile-time dependency on com.rpgwaypoints.compass.db.DatabaseManager
    private static class AuditHelper {
        public static void log(String adminName, String action, String waypointName) {
            try {
                Class<?> dbClass = Class.forName("com.rpgwaypoints.compass.db.DatabaseManager");
                java.lang.reflect.Method m = dbClass.getMethod("logAudit", String.class, String.class, String.class);
                m.invoke(null, adminName, action, waypointName);
            } catch (Throwable ignored) {}
        }

        @SuppressWarnings("unchecked")
        public static List<String> getLogs(int page, int pageSize, String queryFilter) {
            try {
                Class<?> dbClass = Class.forName("com.rpgwaypoints.compass.db.DatabaseManager");
                java.lang.reflect.Method m = dbClass.getMethod("getAuditLogs", int.class, int.class, String.class);
                Object res = m.invoke(null, page, pageSize, queryFilter);
                if (res instanceof List) {
                    return (List<String>) res;
                }
            } catch (Throwable ignored) {}
            return Collections.emptyList();
        }

        public static int getLogsCount(String queryFilter) {
            try {
                Class<?> dbClass = Class.forName("com.rpgwaypoints.compass.db.DatabaseManager");
                java.lang.reflect.Method m = dbClass.getMethod("getAuditLogsCount", String.class);
                Object res = m.invoke(null, queryFilter);
                if (res instanceof Integer) {
                    return (Integer) res;
                }
            } catch (Throwable ignored) {}
            return 0;
        }
    }
}
