package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.util.IntelligentConfigHelper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.invoke.CallSite;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigUpdater {
    private static final List<String> CONFIG_FILES = List.of((Object)"config.yml", (Object)"messages.yml", (Object)"gui.yml", (Object)"commands.yml", (Object)"placeholders.yml");
    public static final Map<String, Set<String>> USER_CUSTOMIZABLE_KEYS = new HashMap<String, Set<String>>(){
        {
            this.put("config.yml", Set.of((Object[])new String[]{"storage.mysql.host", "storage.mysql.port", "storage.mysql.database", "storage.mysql.username", "storage.mysql.password", "storage.mysql.useSSL", "server-identifier", "main_color", "accent_color", "currency_format", "max_team_size", "max_teams_per_player", "team_creation.min_tag_length", "team_creation.max_tag_length", "team_creation.min_name_length", "team_creation.max_name_length", "debug.enabled", "webhook.url", "webhook.enabled"}));
            this.put("messages.yml", Set.of((Object)"prefix", (Object)"team_chat_format", (Object)"help_header"));
            this.put("gui.yml", Set.of((Object)"no-team-gui.title", (Object)"team-gui.title", (Object)"admin-gui.title", (Object)"no-team-gui.items.create-team", (Object)"no-team-gui.items.leaderboards"));
            this.put("placeholders.yml", Set.of((Object[])new String[]{"colors.primary", "colors.secondary", "colors.accent", "colors.success", "colors.error", "colors.warning", "colors.info", "team_display.format", "team_display.team_icon", "team_display.team_color", "team_display.show_icon", "team_display.show_tag", "team_display.show_name", "team_display.no_team", "team_display.tag_prefix", "team_display.tag_suffix", "team_display.tag_color"}));
            this.put("commands.yml", Set.of());
        }
    };
    public static final Map<String, Pattern> VALUE_VALIDATORS = new HashMap<String, Pattern>(){
        {
            this.put("storage.mysql.port", Pattern.compile("^\\d{1,5}$"));
            this.put("max_team_size", Pattern.compile("^\\d+$"));
            this.put("max_teams_per_player", Pattern.compile("^\\d+$"));
            this.put("team_creation.min_tag_length", Pattern.compile("^\\d+$"));
            this.put("team_creation.max_tag_length", Pattern.compile("^\\d+$"));
            this.put("colors.primary", Pattern.compile("^#[0-9A-Fa-f]{6}$"));
            this.put("colors.secondary", Pattern.compile("^#[0-9A-Fa-f]{6}$"));
            this.put("colors.accent", Pattern.compile("^#[0-9A-Fa-f]{6}$"));
        }
    };

    public static void updateAllConfigs(JustTeams plugin) {
        plugin.getLogger().info("Starting automatic configuration update process...");
        ConfigUpdater.performConfigHealthCheck(plugin);
        int successCount = 0;
        int failCount = 0;
        for (String fileName : CONFIG_FILES) {
            try {
                boolean needsUpdate;
                File configFile = new File(plugin.getDataFolder(), fileName);
                if (configFile.exists()) {
                    try {
                        YamlConfiguration.loadConfiguration((File)configFile);
                    } catch (Exception e) {
                        plugin.getLogger().warning("YAML syntax error detected in " + fileName + ", attempting repair...");
                        if (IntelligentConfigHelper.performYamlAutoRepair(configFile)) {
                            plugin.getLogger().info("Successfully auto-repaired " + fileName + " using intelligent repair");
                        }
                        if (ConfigUpdater.repairYamlFile(configFile)) {
                            plugin.getLogger().info("Successfully repaired " + fileName + " using fallback method");
                        }
                        plugin.getLogger().severe("Failed to repair " + fileName + ", will recreate from defaults");
                    }
                }
                if (needsUpdate = ConfigUpdater.needsUpdate(plugin, fileName)) {
                    plugin.getLogger().info(fileName + " needs update, processing...");
                    boolean updated = ConfigUpdater.updateConfig(plugin, fileName);
                    if (updated) {
                        ++successCount;
                        plugin.getLogger().info("Successfully updated " + fileName);
                        continue;
                    }
                    plugin.getLogger().warning("Failed to update " + fileName + " despite needing update");
                    ++failCount;
                    continue;
                }
                plugin.getLogger().fine(fileName + " is already up to date");
            } catch (Exception e) {
                ++failCount;
                plugin.getLogger().log(Level.SEVERE, "Failed to update " + fileName + ": " + e.getMessage(), e);
                try {
                    plugin.getLogger().warning("Creating backup and force update for " + fileName);
                    ConfigUpdater.createBackupAndForceUpdate(plugin, fileName);
                    ++successCount;
                    plugin.getLogger().info("Successfully recovered " + fileName + " with force update");
                } catch (Exception recoveryException) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to recover " + fileName + ": " + recoveryException.getMessage(), recoveryException);
                }
            }
        }
        plugin.getLogger().info("Configuration update process completed! Success: " + successCount + ", Failed: " + failCount);
    }

    private static boolean updateConfig(JustTeams plugin, String fileName) throws IOException {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
            plugin.getLogger().info("Created " + fileName + " from default template.");
            return true;
        }
        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration((File)configFile);
        try (InputStream defaultConfigStream = plugin.getResource(fileName);){
            boolean bl;
            boolean updated;
            int defaultVersion;
            int currentVersion;
            if (defaultConfigStream == null) {
                plugin.getLogger().warning("Could not find default " + fileName + " in plugin resources!");
                boolean bl2 = false;
                return bl2;
            }
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(defaultConfigStream));
            String versionKey = ConfigUpdater.getVersionKey(fileName);
            boolean versionMismatch = false;
            if (versionKey != null && defaultConfig.contains(versionKey) && (currentVersion = currentConfig.getInt(versionKey, 0)) != (defaultVersion = defaultConfig.getInt(versionKey))) {
                versionMismatch = true;
                plugin.getLogger().info("Version mismatch detected for " + fileName + ": current=" + currentVersion + ", default=" + defaultVersion);
            }
            if ((updated = ConfigUpdater.performComprehensiveUpdate((FileConfiguration)currentConfig, (FileConfiguration)defaultConfig, fileName)) || versionMismatch) {
                if (versionMismatch) {
                    File backupFolder = new File(plugin.getDataFolder(), "backups");
                    if (!backupFolder.exists()) {
                        backupFolder.mkdirs();
                    }
                    File backupFile = new File(backupFolder, fileName + ".backup." + System.currentTimeMillis());
                    Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Created backup before version update: " + backupFile.getName());
                }
                currentConfig.save(configFile);
                plugin.getLogger().info(fileName + " has been automatically updated with new configuration options.");
                bl = true;
                return bl;
            }
            plugin.getLogger().fine(fileName + " is already up to date.");
            bl = false;
            return bl;
        }
    }

    private static boolean performComprehensiveUpdate(FileConfiguration currentConfig, FileConfiguration defaultConfig, String fileName) {
        boolean updated = false;
        try {
            updated |= ConfigUpdater.addMissingKeys(currentConfig, defaultConfig, "");
            updated |= ConfigUpdater.updateVersionNumbers(currentConfig, defaultConfig, fileName);
            updated |= ConfigUpdater.removeObsoleteKeys(currentConfig, defaultConfig, "");
        } catch (Exception e) {
            throw new RuntimeException("Failed to perform comprehensive update for " + fileName + ": " + e.getMessage(), e);
        }
        return updated |= ConfigUpdater.validateConfiguration(currentConfig, defaultConfig, fileName);
    }

    private static boolean validateConfiguration(FileConfiguration currentConfig, FileConfiguration defaultConfig, String fileName) {
        boolean updated = false;
        String versionKey = ConfigUpdater.getVersionKey(fileName);
        if (versionKey != null && !currentConfig.contains(versionKey)) {
            currentConfig.set(versionKey, (Object)defaultConfig.getInt(versionKey, 1));
            updated = true;
        }
        if (fileName.equals("config.yml")) {
            updated |= ConfigUpdater.validateConfigFile(currentConfig, defaultConfig);
        } else if (fileName.equals("messages.yml")) {
            updated |= ConfigUpdater.validateMessagesFile(currentConfig, defaultConfig);
        } else if (fileName.equals("gui.yml")) {
            updated |= ConfigUpdater.validateGuiFile(currentConfig, defaultConfig);
        } else if (fileName.equals("commands.yml")) {
            updated |= ConfigUpdater.validateCommandsFile(currentConfig, defaultConfig);
        } else if (fileName.equals("placeholders.yml")) {
            updated |= ConfigUpdater.validatePlaceholdersFile(currentConfig, defaultConfig);
        }
        return updated;
    }

    private static boolean validateConfigFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
        boolean updated = false;
        if (!currentConfig.contains("config-version")) {
            currentConfig.set("config-version", (Object)defaultConfig.getInt("config-version", 19));
            updated = true;
        }
        if (!currentConfig.contains("storage.type")) {
            currentConfig.set("storage.type", (Object)"h2");
            updated = true;
        }
        if (!currentConfig.contains("team_chat")) {
            currentConfig.set("team_chat.character_enabled", (Object)true);
            currentConfig.set("team_chat.character", (Object)"#");
            currentConfig.set("team_chat.require_space", (Object)false);
            updated = true;
        }
        if (!currentConfig.contains("features")) {
            currentConfig.set("features.team_creation", (Object)true);
            currentConfig.set("features.team_disband", (Object)true);
            currentConfig.set("features.team_invites", (Object)true);
            currentConfig.set("features.team_home", (Object)true);
            currentConfig.set("features.team_home_set", (Object)true);
            currentConfig.set("features.team_home_teleport", (Object)true);
            currentConfig.set("features.team_warps", (Object)true);
            currentConfig.set("features.team_warp_set", (Object)true);
            currentConfig.set("features.team_warp_delete", (Object)true);
            currentConfig.set("features.team_warp_teleport", (Object)true);
            currentConfig.set("features.team_pvp", (Object)true);
            currentConfig.set("features.team_bank", (Object)true);
            currentConfig.set("features.team_enderchest", (Object)true);
            currentConfig.set("features.team_chat", (Object)true);
            currentConfig.set("features.member_leave", (Object)true);
            currentConfig.set("features.member_kick", (Object)true);
            currentConfig.set("features.member_promote", (Object)true);
            currentConfig.set("features.member_demote", (Object)true);
            currentConfig.set("features.join_requests", (Object)true);
            updated = true;
        }
        return updated;
    }

    private static boolean validateMessagesFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
        boolean updated = false;
        if (!currentConfig.contains("messages-version")) {
            currentConfig.set("messages-version", (Object)defaultConfig.getInt("messages-version", 5));
            updated = true;
        }
        if (!currentConfig.contains("prefix")) {
            currentConfig.set("prefix", (Object)defaultConfig.getString("prefix"));
            updated = true;
        }
        if (!currentConfig.contains("feature_disabled")) {
            currentConfig.set("feature_disabled", (Object)"<red>This feature is disabled on this server.</red>");
            updated = true;
        }
        return updated;
    }

    private static boolean validateGuiFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
        boolean updated = false;
        if (!currentConfig.contains("gui-version")) {
            currentConfig.set("gui-version", (Object)defaultConfig.getInt("gui-version", 10));
            updated = true;
        }
        if (!currentConfig.contains("team-gui.items.pvp-toggle-locked")) {
            currentConfig.set("team-gui.items.pvp-toggle-locked.material", (Object)"BARRIER");
            currentConfig.set("team-gui.items.pvp-toggle-locked.slot", (Object)12);
            currentConfig.set("team-gui.items.pvp-toggle-locked.name", (Object)"<red>PvP Toggle (Disabled)</red>");
            currentConfig.set("team-gui.items.pvp-toggle-locked.lore", (Object)List.of((Object)"<gray>This feature has been disabled", (Object)"<gray>by the server administrator."));
            updated = true;
        }
        if (!currentConfig.contains("team-gui.items.warps-locked")) {
            currentConfig.set("team-gui.items.warps-locked.material", (Object)"BARRIER");
            currentConfig.set("team-gui.items.warps-locked.slot", (Object)14);
            currentConfig.set("team-gui.items.warps-locked.name", (Object)"<red>Team Warps (Disabled)</red>");
            currentConfig.set("team-gui.items.warps-locked.lore", (Object)List.of((Object)"<gray>This feature has been disabled", (Object)"<gray>by the server administrator."));
            updated = true;
        }
        return updated;
    }

    private static boolean validateCommandsFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
        boolean updated = false;
        if (!currentConfig.contains("commands-version")) {
            currentConfig.set("commands-version", (Object)defaultConfig.getInt("commands-version", 4));
            updated = true;
        }
        if (!currentConfig.contains("primary-command")) {
            currentConfig.set("primary-command", (Object)"team");
            updated = true;
        }
        return updated;
    }

    private static boolean validatePlaceholdersFile(FileConfiguration currentConfig, FileConfiguration defaultConfig) {
        String[] requiredSections;
        boolean updated = false;
        if (!currentConfig.contains("placeholders-version")) {
            currentConfig.set("placeholders-version", (Object)defaultConfig.getInt("placeholders-version", 3));
            updated = true;
        }
        for (String section : requiredSections = new String[]{"colors", "roles", "status", "sort", "date_time", "numbers", "gui", "indicators", "admin"}) {
            if (currentConfig.contains(section)) continue;
            currentConfig.set(section, (Object)defaultConfig.getConfigurationSection(section));
            updated = true;
        }
        return updated;
    }

    public static void migrateToPlaceholderSystem(JustTeams plugin) {
        plugin.getLogger().info("Starting migration to placeholder system...");
        try {
            ConfigUpdater.migrateGuiToPlaceholders(plugin);
            ConfigUpdater.updateExistingConfigurations(plugin);
            plugin.getLogger().info("Placeholder system migration completed successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to migrate to placeholder system: " + e.getMessage());
            plugin.getLogger().severe("Failed to update config: " + e.getMessage());
        }
    }

    private static void migrateGuiToPlaceholders(JustTeams plugin) {
        try {
            File guiFile = new File(plugin.getDataFolder(), "gui.yml");
            if (!guiFile.exists()) {
                plugin.getLogger().info("gui.yml not found, skipping GUI migration");
                return;
            }
            YamlConfiguration guiConfig = YamlConfiguration.loadConfiguration((File)guiFile);
            boolean updated = false;
            updated |= ConfigUpdater.migrateTeamGuiElements((FileConfiguration)guiConfig);
            updated |= ConfigUpdater.migrateAdminGuiElements((FileConfiguration)guiConfig);
            if (updated |= ConfigUpdater.migrateOtherGuiElements((FileConfiguration)guiConfig)) {
                guiConfig.save(guiFile);
                plugin.getLogger().info("GUI configuration migrated to use placeholder system");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to migrate GUI to placeholders: " + e.getMessage());
        }
    }

    private static boolean migrateTeamGuiElements(FileConfiguration guiConfig) {
        String currentName;
        ConfigurationSection sortItem;
        ConfigurationSection items;
        boolean updated = false;
        ConfigurationSection teamGui = guiConfig.getConfigurationSection("team-gui");
        if (teamGui != null && (items = teamGui.getConfigurationSection("items")) != null && (sortItem = items.getConfigurationSection("sort")) != null && (currentName = sortItem.getString("name", "")).contains("Sort by") && !currentName.contains("<placeholder:")) {
            sortItem.set("name", (Object)"<placeholder:sort.sort_button.name>");
            updated = true;
        }
        return updated;
    }

    private static boolean migrateAdminGuiElements(FileConfiguration guiConfig) {
        boolean updated = false;
        if (!guiConfig.contains("admin-team-list-gui")) {
            guiConfig.set("admin-team-list-gui.title", (Object)"All Teams - Page %page%");
            guiConfig.set("admin-team-list-gui.size", (Object)54);
            updated = true;
        }
        if (!guiConfig.contains("admin-team-manage-gui")) {
            guiConfig.set("admin-team-manage-gui.title", (Object)"Manage: %team%");
            guiConfig.set("admin-team-manage-gui.size", (Object)27);
            updated = true;
        }
        return updated;
    }

    private static boolean migrateOtherGuiElements(FileConfiguration guiConfig) {
        String[] guiTypes;
        boolean updated = false;
        for (String guiType : guiTypes = new String[]{"join-requests-gui", "warps-gui", "blacklist-gui", "leaderboard-view-gui", "leaderboard-category-gui"}) {
            if (guiConfig.contains(guiType)) continue;
            guiConfig.set(guiType + ".title", (Object)"Default Title");
            guiConfig.set(guiType + ".size", (Object)54);
            updated = true;
        }
        return updated;
    }

    private static void updateExistingConfigurations(JustTeams plugin) {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (configFile.exists()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration((File)configFile);
                boolean updated = false;
                if (!config.contains("team_chat")) {
                    config.set("team_chat.character_enabled", (Object)true);
                    config.set("team_chat.character", (Object)"#");
                    config.set("team_chat.require_space", (Object)false);
                    updated = true;
                }
                if (updated) {
                    config.save(configFile);
                    plugin.getLogger().info("Updated config.yml with new team chat settings");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update existing configurations: " + e.getMessage());
        }
    }

    private static void ensurePlaceholdersFile(JustTeams plugin) {
        try {
            File placeholdersFile = new File(plugin.getDataFolder(), "placeholders.yml");
            if (!placeholdersFile.exists()) {
                plugin.saveResource("placeholders.yml", false);
                plugin.getLogger().info("Created placeholders.yml from template");
            } else {
                YamlConfiguration placeholders = YamlConfiguration.loadConfiguration((File)placeholdersFile);
                boolean updated = false;
                if (!placeholders.contains("placeholders-version")) {
                    placeholders.set("placeholders-version", (Object)4);
                    updated = true;
                }
                if (updated) {
                    placeholders.save(placeholdersFile);
                    plugin.getLogger().info("Updated placeholders.yml with missing sections");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to ensure placeholders file: " + e.getMessage());
        }
    }

    private static boolean addMissingKeys(FileConfiguration currentConfig, FileConfiguration defaultConfig, String path) {
        boolean updated = false;
        for (String key : defaultConfig.getKeys(true)) {
            String fullPath;
            String string = fullPath = path.isEmpty() ? key : path + "." + key;
            if (!currentConfig.contains(key)) {
                if (ConfigUpdater.shouldSkipGuiItem(fullPath)) continue;
                Object defaultValue = defaultConfig.get(key);
                currentConfig.set(key, defaultValue);
                updated = true;
                continue;
            }
            if (!defaultConfig.isConfigurationSection(key) || !currentConfig.isConfigurationSection(key)) continue;
            updated |= ConfigUpdater.addMissingKeys(currentConfig.getConfigurationSection(key), defaultConfig.getConfigurationSection(key), fullPath);
        }
        return updated;
    }

    private static boolean shouldSkipGuiItem(String fullPath) {
        String[] userRemovableGuiItems;
        for (String removableItem : userRemovableGuiItems = new String[]{"no-team-gui.items.create-team", "team-gui.items.create-team"}) {
            if (!fullPath.startsWith(removableItem)) continue;
            return true;
        }
        return false;
    }

    private static boolean addMissingKeys(ConfigurationSection currentConfig, ConfigurationSection defaultConfig, String path) {
        boolean updated = false;
        for (String key : defaultConfig.getKeys(true)) {
            String fullPath;
            String string = fullPath = path.isEmpty() ? key : path + "." + key;
            if (!currentConfig.contains(key)) {
                if (ConfigUpdater.shouldSkipGuiItem(fullPath)) continue;
                Object defaultValue = defaultConfig.get(key);
                currentConfig.set(key, defaultValue);
                updated = true;
                continue;
            }
            if (!defaultConfig.isConfigurationSection(key) || !currentConfig.isConfigurationSection(key)) continue;
            updated |= ConfigUpdater.addMissingKeys(currentConfig.getConfigurationSection(key), defaultConfig.getConfigurationSection(key), fullPath);
        }
        return updated;
    }

    private static boolean updateVersionNumbers(FileConfiguration currentConfig, FileConfiguration defaultConfig, String fileName) {
        int defaultVersion;
        int currentVersion;
        boolean updated = false;
        String versionKey = ConfigUpdater.getVersionKey(fileName);
        if (versionKey != null && defaultConfig.contains(versionKey) && (currentVersion = currentConfig.getInt(versionKey, 0)) != (defaultVersion = defaultConfig.getInt(versionKey))) {
            currentConfig.set(versionKey, (Object)defaultVersion);
            updated = true;
        }
        return updated;
    }

    public static String getVersionKey(String fileName) {
        return switch (fileName) {
            case "config.yml" -> "config-version";
            case "gui.yml" -> "gui-version";
            case "messages.yml" -> "messages-version";
            case "commands.yml" -> "commands-version";
            case "placeholders.yml" -> "placeholders-version";
            default -> null;
        };
    }

    private static boolean removeObsoleteKeys(FileConfiguration currentConfig, FileConfiguration defaultConfig, String path) {
        boolean updated = false;
        Set currentKeys = currentConfig.getKeys(true);
        Set defaultKeys = defaultConfig.getKeys(true);
        for (String key : currentKeys) {
            if (defaultKeys.contains(key) || ConfigUpdater.isUserCustomizedValue(currentConfig, key)) continue;
            currentConfig.set(key, null);
            updated = true;
        }
        return updated;
    }

    private static boolean isUserCustomizedValue(FileConfiguration config, String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("server-identifier") || lowerKey.contains("username") || lowerKey.contains("password") || lowerKey.contains("host") || lowerKey.contains("database") || lowerKey.contains("custom") || lowerKey.contains("user") || lowerKey.contains("personal");
    }

    public static void forceUpdateConfig(JustTeams plugin, String fileName) {
        try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            File backupFolder = new File(plugin.getDataFolder(), "backups");
            if (!backupFolder.exists()) {
                backupFolder.mkdirs();
            }
            if (configFile.exists()) {
                File backupFile = new File(backupFolder, fileName + ".backup." + System.currentTimeMillis());
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Created backup: " + backupFile.getName());
                configFile.delete();
                plugin.getLogger().info("Deleted existing " + fileName + " for forced update.");
            }
            ConfigUpdater.updateConfig(plugin, fileName);
            ConfigUpdater.cleanupOldBackups(plugin, backupFolder, fileName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to force update " + fileName + ": " + e.getMessage(), e);
        }
    }

    private static void createBackupAndForceUpdate(JustTeams plugin, String fileName) throws IOException {
        File configFile = new File(plugin.getDataFolder(), fileName);
        File backupFolder = new File(plugin.getDataFolder(), "backups");
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
        File backupFile = new File(backupFolder, fileName + ".backup." + System.currentTimeMillis());
        if (configFile.exists()) {
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Created backup: " + backupFile.getName());
            configFile.delete();
            plugin.getLogger().info("Deleted corrupted " + fileName + " for recovery.");
        }
        plugin.saveResource(fileName, false);
        plugin.getLogger().info("Recovered " + fileName + " from template.");
        ConfigUpdater.cleanupOldBackups(plugin, backupFolder, fileName);
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public static boolean needsUpdate(JustTeams plugin, String fileName) {
        try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            if (!configFile.exists()) {
                return true;
            }
            YamlConfiguration currentConfig = null;
            try {
                currentConfig = YamlConfiguration.loadConfiguration((File)configFile);
            } catch (Exception e) {
                plugin.getLogger().warning("YAML syntax error detected in " + fileName + ", attempting repair...");
                if (!ConfigUpdater.repairYamlFile(configFile)) {
                    plugin.getLogger().severe("Failed to repair " + fileName + ", using default configuration");
                    return true;
                }
                plugin.getLogger().info("Successfully repaired " + fileName);
                currentConfig = YamlConfiguration.loadConfiguration((File)configFile);
            }
            try (InputStream defaultConfigStream = plugin.getResource(fileName);){
                int defaultVersion;
                if (defaultConfigStream == null) {
                    boolean bl = false;
                    return bl;
                }
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(defaultConfigStream));
                for (String key : defaultConfig.getKeys(true)) {
                    if (currentConfig.contains(key)) continue;
                    plugin.getLogger().info("Missing key detected in " + fileName + ": " + key);
                    boolean bl = true;
                    return bl;
                }
                String versionKey = ConfigUpdater.getVersionKey(fileName);
                if (versionKey == null) return false;
                if (!defaultConfig.contains(versionKey)) return false;
                int currentVersion = currentConfig.getInt(versionKey, 0);
                if (currentVersion == (defaultVersion = defaultConfig.getInt(versionKey))) return false;
                plugin.getLogger().info("Version mismatch in " + fileName + ": current=" + currentVersion + ", default=" + defaultVersion);
                boolean bl = true;
                return bl;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking if " + fileName + " needs update: " + e.getMessage());
            return true;
        }
    }

    public static List<String> getConfigsNeedingUpdate(JustTeams plugin) {
        ArrayList<String> needsUpdate = new ArrayList<String>();
        for (String fileName : CONFIG_FILES) {
            if (!ConfigUpdater.needsUpdate(plugin, fileName)) continue;
            needsUpdate.add(fileName);
        }
        return needsUpdate;
    }

    public static void testConfigurationSystem(JustTeams plugin) {
        plugin.getLogger().info("Testing configuration system...");
        for (String fileName : CONFIG_FILES) {
            try {
                boolean needsUpdate = ConfigUpdater.needsUpdate(plugin, fileName);
                plugin.getLogger().info("Config " + fileName + " needs update: " + needsUpdate);
                File configFile = new File(plugin.getDataFolder(), fileName);
                if (!configFile.exists()) continue;
                YamlConfiguration config = YamlConfiguration.loadConfiguration((File)configFile);
                String versionKey = ConfigUpdater.getVersionKey(fileName);
                if (versionKey == null || !config.contains(versionKey)) continue;
                int version = config.getInt(versionKey);
                plugin.getLogger().info("Config " + fileName + " current version: " + version);
            } catch (Exception e) {
                plugin.getLogger().warning("Error testing " + fileName + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Configuration system test completed!");
    }

    public static void forceUpdateAllConfigs(JustTeams plugin) {
        plugin.getLogger().info("=== FORCE UPDATING ALL CONFIGURATION FILES ===");
        int successCount = 0;
        int failCount = 0;
        for (String fileName : CONFIG_FILES) {
            try {
                plugin.getLogger().info("Force updating " + fileName + "...");
                boolean updated = ConfigUpdater.updateConfig(plugin, fileName);
                if (updated) {
                    ++successCount;
                    plugin.getLogger().info("Successfully force updated " + fileName);
                    continue;
                }
                plugin.getLogger().warning("Failed to force update " + fileName);
                ++failCount;
            } catch (Exception e) {
                ++failCount;
                plugin.getLogger().log(Level.SEVERE, "Failed to force update " + fileName + ": " + e.getMessage(), e);
            }
        }
        plugin.getLogger().info("Force update completed! Success: " + successCount + ", Failed: " + failCount);
    }

    public static boolean isConfigurationSystemHealthy(JustTeams plugin) {
        try {
            for (String fileName : CONFIG_FILES) {
                File configFile = new File(plugin.getDataFolder(), fileName);
                if (!configFile.exists()) {
                    plugin.getLogger().warning("Missing configuration file: " + fileName);
                    return false;
                }
                YamlConfiguration config = YamlConfiguration.loadConfiguration((File)configFile);
                String versionKey = ConfigUpdater.getVersionKey(fileName);
                if (versionKey == null || config.contains(versionKey)) continue;
                plugin.getLogger().warning("Configuration file " + fileName + " missing version key: " + versionKey);
                return false;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Configuration system health check failed: " + e.getMessage());
            return false;
        }
    }

    private static void cleanupOldBackups(JustTeams plugin, File backupFolder, String fileName) {
        try {
            if (!backupFolder.exists()) {
                return;
            }
            File[] backupFiles = backupFolder.listFiles((dir, name) -> name.startsWith(fileName + ".backup.") && name.endsWith(".yml"));
            if (backupFiles == null || backupFiles.length <= 5) {
                return;
            }
            Arrays.sort(backupFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 5; i < backupFiles.length; ++i) {
                if (!backupFiles[i].delete()) continue;
                plugin.getLogger().info("Cleaned up old backup: " + backupFiles[i].getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error cleaning up old backups for " + fileName + ": " + e.getMessage());
        }
    }

    public static void cleanupAllOldBackups(JustTeams plugin) {
        try {
            File backupFolder = new File(plugin.getDataFolder(), "backups");
            if (!backupFolder.exists()) {
                return;
            }
            plugin.getLogger().info("Cleaning up old backup files...");
            for (String fileName : CONFIG_FILES) {
                ConfigUpdater.cleanupOldBackups(plugin, backupFolder, fileName);
            }
            long sevenDaysAgo = System.currentTimeMillis() - 604800000L;
            File[] allBackupFiles = backupFolder.listFiles((dir, name) -> {
                if (!name.contains(".backup.")) {
                    return false;
                }
                try {
                    String timestampStr = name.substring(name.lastIndexOf(".backup.") + 8);
                    long timestamp = Long.parseLong(timestampStr);
                    return timestamp < sevenDaysAgo;
                } catch (NumberFormatException e) {
                    return false;
                }
            });
            if (allBackupFiles != null) {
                for (File oldBackup : allBackupFiles) {
                    if (!oldBackup.delete()) continue;
                    plugin.getLogger().info("Cleaned up old backup: " + oldBackup.getName());
                }
            }
            plugin.getLogger().info("Backup cleanup completed!");
        } catch (Exception e) {
            plugin.getLogger().warning("Error during backup cleanup: " + e.getMessage());
        }
    }

    public static int getBackupCount(JustTeams plugin, String fileName) {
        try {
            File backupFolder = new File(plugin.getDataFolder(), "backups");
            if (!backupFolder.exists()) {
                return 0;
            }
            File[] backupFiles = backupFolder.listFiles((dir, name) -> name.startsWith(fileName + ".backup.") && name.endsWith(".yml"));
            return backupFiles != null ? backupFiles.length : 0;
        } catch (Exception e) {
            plugin.getLogger().warning("Error counting backups for " + fileName + ": " + e.getMessage());
            return 0;
        }
    }

    private static boolean repairYamlFile(File configFile) {
        try {
            String content = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            content = content.replaceAll("\"<gradient:\\s*$", "\"<gradient:#4C9DDE:#4C96D2>JustTeams</gradient>\"");
            content = content.replaceAll("\"<gradient:\\s*\\n", "\"<gradient:#4C9DDE:#4C96D2>JustTeams</gradient>\"\\n");
            content = content.replaceAll("team_chat_password_warning: \"<red>Warning: Please do not shar\\s*$", "team_chat_password_warning: \"<red>Warning: Please do not share your team password with anyone!</red>\"");
            content = content.replaceAll("online-name-format: \"<gradient:\\s*$", "online-name-format: \"<gradient:#4C9DDE:#4C96D2><player></gradient>\"");
            content = content.replaceAll("offline-name-format: \"<gray><status_indicator><role_ic\\s*$", "offline-name-format: \"<gray><status_indicator><role_icon> <player></gray>\"");
            content = content.replaceAll(": \"[^\"]*$", ": \"Fixed incomplete string\"");
            Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
            try {
                YamlConfiguration.loadConfiguration((File)configFile);
                return true;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static void createConfigBackup(JustTeams plugin, String fileName) {
        try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            File backupFolder = new File(plugin.getDataFolder(), "backups");
            if (!backupFolder.exists()) {
                backupFolder.mkdirs();
            }
            if (configFile.exists()) {
                File backupFile = new File(backupFolder, fileName + ".backup." + System.currentTimeMillis());
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Created manual backup: " + backupFile.getName());
                ConfigUpdater.cleanupOldBackups(plugin, backupFolder, fileName);
            } else {
                plugin.getLogger().warning("Cannot backup " + fileName + " - file does not exist");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create backup for " + fileName + ": " + e.getMessage());
        }
    }

    public static void performIntelligentUpdate(JustTeams plugin) {
        plugin.getLogger().info("Starting intelligent configuration update system...");
        LocalDateTime updateTime = LocalDateTime.now();
        String timestamp = updateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        IntelligentConfigHelper.createUpdateSnapshot(plugin, timestamp);
        ConfigUpdater.restoreCreateTeamSection(plugin);
        int successCount = 0;
        int failCount = 0;
        for (String fileName : CONFIG_FILES) {
            try {
                if (IntelligentConfigHelper.performIntelligentFileUpdate(plugin, fileName, timestamp)) {
                    ++successCount;
                    plugin.getLogger().info("Successfully performed intelligent update on " + fileName);
                    continue;
                }
                plugin.getLogger().info(fileName + " was already up to date");
            } catch (Exception e) {
                ++failCount;
                plugin.getLogger().log(Level.SEVERE, "Failed intelligent update for " + fileName + ": " + e.getMessage(), e);
                try {
                    IntelligentConfigHelper.performEmergencyRecovery(plugin, fileName);
                    ++successCount;
                } catch (Exception recoveryError) {
                    plugin.getLogger().log(Level.SEVERE, "Emergency recovery failed for " + fileName, recoveryError);
                }
            }
        }
        IntelligentConfigHelper.generateUpdateReport(plugin, successCount, failCount, timestamp);
        plugin.getLogger().info("Intelligent update system completed! Success: " + successCount + ", Failed: " + failCount);
    }

    public static void performConfigHealthCheck(JustTeams plugin) {
        plugin.getLogger().info("Performing configuration health check...");
        boolean allHealthy = true;
        ArrayList<CallSite> issues = new ArrayList<CallSite>();
        for (String string : CONFIG_FILES) {
            File configFile = new File(plugin.getDataFolder(), string);
            if (!configFile.exists()) {
                issues.add((CallSite)((Object)(string + ": File missing")));
                allHealthy = false;
                continue;
            }
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration((File)configFile);
                String versionKey = ConfigUpdater.getVersionKey(string);
                if (versionKey != null && !config.contains(versionKey)) {
                    issues.add((CallSite)((Object)(string + ": Missing version key")));
                    allHealthy = false;
                }
                if (!IntelligentConfigHelper.hasCorruptedValues((FileConfiguration)config, string)) continue;
                issues.add((CallSite)((Object)(string + ": Contains corrupted values")));
                allHealthy = false;
            } catch (Exception e) {
                issues.add((CallSite)((Object)(string + ": YAML syntax error - " + e.getMessage())));
                allHealthy = false;
            }
        }
        if (allHealthy) {
            plugin.getLogger().info("\u2713 All configuration files are healthy");
        } else {
            plugin.getLogger().warning("\u2717 Configuration health check found issues:");
            for (String string : issues) {
                plugin.getLogger().warning("  - " + string);
            }
            plugin.getLogger().info("Running intelligent auto-repair...");
            ConfigUpdater.performIntelligentUpdate(plugin);
        }
    }

    private static void restoreCreateTeamSection(JustTeams plugin) {
        try {
            File guiFile = new File(plugin.getDataFolder(), "gui.yml");
            if (!guiFile.exists()) {
                return;
            }
            YamlConfiguration guiConfig = YamlConfiguration.loadConfiguration((File)guiFile);
            ConfigurationSection noTeamGui = guiConfig.getConfigurationSection("no-team-gui");
            if (noTeamGui == null) {
                plugin.getLogger().info("no-team-gui section missing, restoring...");
                return;
            }
            ConfigurationSection items = noTeamGui.getConfigurationSection("items");
            if (items == null) {
                plugin.getLogger().info("no-team-gui.items section missing, restoring...");
                return;
            }
            if (!items.contains("create-team")) {
                plugin.getLogger().info("create-team item missing, restoring...");
                items.set("create-team.slot", (Object)12);
                items.set("create-team.material", (Object)"WRITABLE_BOOK");
                items.set("create-team.name", (Object)"<gradient:#4C9DDE:#4C96D2><bold>\u1d04\u0280\u1d07\u1d00\u1d1b\u1d07 \u1d00 \u1d1b\u1d07\u1d00\u1d0d</bold></gradient>");
                items.set("create-team.lore", (Object)List.of((Object)"<gray>Start your own team and invite your friends!</gray>", (Object)"", (Object)"<yellow>Click to begin the creation process.</yellow>"));
                guiConfig.save(guiFile);
                plugin.getLogger().info("Successfully restored create-team section");
            }
            if (!items.contains("leaderboards")) {
                plugin.getLogger().info("leaderboards item missing, restoring...");
                items.set("leaderboards.slot", (Object)14);
                items.set("leaderboards.material", (Object)"EMERALD");
                items.set("leaderboards.name", (Object)"<gradient:#4C9DDE:#4C96D2><bold>\u1d20\u026a\u1d07\u1d21 \u029f\u1d07\u1d00\u1d05\u1d07\u0280\u0299\u1d0f\u1d00\u0280\u1d05s</bold></gradient>");
                items.set("leaderboards.lore", (Object)List.of((Object)"<gray>See the top teams on the server.</gray>", (Object)"", (Object)"<yellow>Click to view leaderboards.</yellow>"));
                guiConfig.save(guiFile);
                plugin.getLogger().info("Successfully restored leaderboards section");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to restore create-team section: " + e.getMessage());
        }
    }
}

