package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class BedrockSupport {
    private final JustTeams plugin;
    private final Map<UUID, Boolean> bedrockPlayerCache = new ConcurrentHashMap<UUID, Boolean>();
    private boolean floodgateAvailable = false;

    public BedrockSupport(JustTeams plugin) {
        this.plugin = plugin;
        this.checkFloodgateAvailability();
    }

    private void checkFloodgateAvailability() {
        try {
            if (this.plugin.getServer().getPluginManager().getPlugin("floodgate") != null) {
                Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                this.floodgateAvailable = true;
                this.plugin.getLogger().info("Floodgate detected! Bedrock support enabled.");
            } else {
                this.floodgateAvailable = false;
                this.plugin.getLogger().info("Floodgate not found. Bedrock support disabled.");
            }
        } catch (Exception e) {
            this.floodgateAvailable = false;
            this.plugin.getLogger().warning("Error checking Floodgate availability: " + e.getMessage());
        }
    }

    public boolean isBedrockPlayer(Player player) {
        if (!this.floodgateAvailable) {
            return false;
        }
        Boolean cached = this.bedrockPlayerCache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            boolean isBedrock = (Boolean)floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(floodgateApi, player.getUniqueId());
            this.bedrockPlayerCache.put(player.getUniqueId(), isBedrock);
            return isBedrock;
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error checking if player is Bedrock: " + e.getMessage());
            return false;
        }
    }

    public boolean isBedrockPlayer(UUID uuid) {
        if (!this.floodgateAvailable) {
            return false;
        }
        Boolean cached = this.bedrockPlayerCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            boolean isBedrock = (Boolean)floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(floodgateApi, uuid);
            this.bedrockPlayerCache.put(uuid, isBedrock);
            return isBedrock;
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error checking if UUID is Bedrock: " + e.getMessage());
            return false;
        }
    }

    public String getBedrockGamertag(Player player) {
        if (!this.floodgateAvailable || !this.isBedrockPlayer(player)) {
            return null;
        }
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            Object floodgatePlayer = floodgateApiClass.getMethod("getPlayer", UUID.class).invoke(floodgateApi, player.getUniqueId());
            if (floodgatePlayer != null) {
                return (String)floodgatePlayer.getClass().getMethod("getUsername", new Class[0]).invoke(floodgatePlayer, new Object[0]);
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error getting Bedrock gamertag: " + e.getMessage());
        }
        return null;
    }

    public String getBedrockGamertag(UUID uuid) {
        if (!this.floodgateAvailable || !this.isBedrockPlayer(uuid)) {
            return null;
        }
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            Object floodgatePlayer = floodgateApiClass.getMethod("getPlayer", UUID.class).invoke(floodgateApi, uuid);
            if (floodgatePlayer != null) {
                return (String)floodgatePlayer.getClass().getMethod("getUsername", new Class[0]).invoke(floodgatePlayer, new Object[0]);
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error getting Bedrock gamertag by UUID: " + e.getMessage());
        }
        return null;
    }

    public UUID getJavaEditionUuid(Player player) {
        if (!this.floodgateAvailable || !this.isBedrockPlayer(player)) {
            return player.getUniqueId();
        }
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            Object floodgatePlayer = floodgateApiClass.getMethod("getPlayer", UUID.class).invoke(floodgateApi, player.getUniqueId());
            if (floodgatePlayer != null) {
                return (UUID)floodgatePlayer.getClass().getMethod("getJavaUniqueId", new Class[0]).invoke(floodgatePlayer, new Object[0]);
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error getting Java edition UUID: " + e.getMessage());
        }
        return player.getUniqueId();
    }

    public UUID getJavaEditionUuid(UUID uuid) {
        if (!this.floodgateAvailable || !this.isBedrockPlayer(uuid)) {
            return uuid;
        }
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgateApi = floodgateApiClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            Object floodgatePlayer = floodgateApiClass.getMethod("getPlayer", UUID.class).invoke(floodgateApi, uuid);
            if (floodgatePlayer != null) {
                return (UUID)floodgatePlayer.getClass().getMethod("getJavaUniqueId", new Class[0]).invoke(floodgatePlayer, new Object[0]);
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error getting Java edition UUID by UUID: " + e.getMessage());
        }
        return uuid;
    }

    public boolean isFloodgateAvailable() {
        return this.floodgateAvailable;
    }

    public void clearPlayerCache(UUID uuid) {
        this.bedrockPlayerCache.remove(uuid);
    }

    public void clearAllCache() {
        this.bedrockPlayerCache.clear();
    }

    public String getPlatformDisplayName(Player player) {
        if (this.isBedrockPlayer(player)) {
            String gamertag = this.getBedrockGamertag(player);
            if (gamertag != null && !gamertag.equals(player.getName())) {
                return player.getName() + " <gray>(<#00D4FF>Bedrock</#00D4FF>: " + gamertag + "<gray>)";
            }
            return player.getName() + " <gray>(<#00D4FF>Bedrock</#00D4FF>)";
        }
        return player.getName() + " <gray>(<#00FF00>Java</#00FF00>)";
    }

    public String getPlatformIndicator(Player player) {
        return this.isBedrockPlayer(player) ? "<#00D4FF>BE</#00D4FF>" : "<#00FF00>JE</#00FF00>";
    }

    public boolean isValidPlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return name.matches("^[a-zA-Z0-9_.]+$");
    }

    public String normalizePlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.startsWith(".")) {
            return name.substring(1);
        }
        return name;
    }

    public boolean supportsCustomForms(Player player) {
        if (!this.isBedrockPlayer(player)) {
            return false;
        }
        try {
            Class.forName("org.geysermc.cumulus.Forms");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean canUseInventoryGUI(Player player) {
        return true;
    }

    public Material getBedrockFallbackMaterial(Material original) {
        if (original == Material.PLAYER_HEAD || original == Material.PLAYER_WALL_HEAD) {
            return Material.DIAMOND;
        }
        switch (original) {
            case KNOWLEDGE_BOOK: {
                return Material.BOOK;
            }
            case DEBUG_STICK: {
                return Material.STICK;
            }
            case BARRIER: {
                return original;
            }
        }
        return original;
    }

    public boolean isMaterialProblematic(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD || material == Material.KNOWLEDGE_BOOK || material == Material.DEBUG_STICK;
    }

    public String getDisplayNameWithPlatform(Player player) {
        if (!this.plugin.getConfig().getBoolean("bedrock_support.show_platform_indicators", true)) {
            return player.getName();
        }
        return this.isBedrockPlayer(player) ? player.getName() + " \u00a7b[BE]\u00a7r" : player.getName() + " \u00a7a[JE]\u00a7r";
    }

    public String getOptimizedInventoryTitle(String title, Player player) {
        if (!this.isBedrockPlayer(player)) {
            return title;
        }
        if (title.length() > 32) {
            return title.substring(0, 29) + "...";
        }
        return title;
    }

    public boolean supportsJavaFeature(Player player, String featureName) {
        if (!this.isBedrockPlayer(player)) {
            return true;
        }
        switch (featureName.toLowerCase()) {
            case "offhand": 
            case "shield": 
            case "spectator": 
            case "debug": {
                return false;
            }
            case "inventory": 
            case "chat": 
            case "commands": {
                return true;
            }
        }
        return true;
    }
}

