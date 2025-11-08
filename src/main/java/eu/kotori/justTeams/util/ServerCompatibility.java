package eu.kotori.justTeams.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class ServerCompatibility {
    private static final String SERVER_NAME = Bukkit.getServer().getName();
    private static final String SERVER_VERSION = Bukkit.getServer().getVersion();
    private static final boolean IS_FOLIA;
    private static final boolean IS_PAPER;
    private static final boolean IS_SPIGOT;
    private static final ServerType SERVER_TYPE;

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static boolean isPaper() {
        return IS_PAPER;
    }

    public static boolean isSpigot() {
        return IS_SPIGOT;
    }

    public static ServerType getServerType() {
        return SERVER_TYPE;
    }

    public static String getServerName() {
        return SERVER_NAME;
    }

    public static String getServerVersion() {
        return SERVER_VERSION;
    }

    public static boolean supportsRegionThreading() {
        return IS_FOLIA;
    }

    public static boolean supportsPaperAPI() {
        return IS_PAPER || IS_FOLIA;
    }

    public static void logCompatibilityInfo(Plugin plugin) {
        plugin.getLogger().info("=".repeat(50));
        plugin.getLogger().info("Server Compatibility Information:");
        plugin.getLogger().info("  Server: " + SERVER_NAME);
        plugin.getLogger().info("  Version: " + SERVER_VERSION);
        plugin.getLogger().info("  Type: " + String.valueOf((Object)SERVER_TYPE));
        plugin.getLogger().info("  Folia Support: " + (IS_FOLIA ? "ENABLED" : "Not Running Folia"));
        plugin.getLogger().info("  Paper API: " + (ServerCompatibility.supportsPaperAPI() ? "Available" : "Not Available"));
        plugin.getLogger().info("  Region Threading: " + (ServerCompatibility.supportsRegionThreading() ? "ENABLED" : "Standard Threading"));
        plugin.getLogger().info("=".repeat(50));
    }

    static {
        String name = SERVER_NAME.toLowerCase();
        IS_FOLIA = name.contains("folia");
        IS_PAPER = name.contains("paper") || name.contains("purpur") || name.contains("airplane") || name.contains("pufferfish");
        boolean bl = IS_SPIGOT = name.contains("spigot") || name.contains("craftbukkit");
        SERVER_TYPE = IS_FOLIA ? ServerType.FOLIA : (IS_PAPER ? ServerType.PAPER : (IS_SPIGOT ? ServerType.SPIGOT : ServerType.BUKKIT));
    }

    public static enum ServerType {
        FOLIA("Folia", "Region-threaded server"),
        PAPER("Paper", "High-performance fork"),
        SPIGOT("Spigot", "Standard performance"),
        BUKKIT("Bukkit", "Base implementation");

        private final String displayName;
        private final String description;

        private ServerType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public String getDescription() {
            return this.description;
        }

        public String toString() {
            return this.displayName + " (" + this.description + ")";
        }
    }
}

