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

    // Nested class to defer classloading of com.rpgwaypoints.compass.db.DatabaseManager
    private static class AuditHelper {
        public static void log(String adminName, String action, String waypointName) {
            com.rpgwaypoints.compass.db.DatabaseManager.logAudit(adminName, action, waypointName);
        }

        public static List<String> getLogs(int page, int pageSize, String queryFilter) {
            return com.rpgwaypoints.compass.db.DatabaseManager.getAuditLogs(page, pageSize, queryFilter);
        }

        public static int getLogsCount(String queryFilter) {
            return com.rpgwaypoints.compass.db.DatabaseManager.getAuditLogsCount(queryFilter);
        }
    }
}
