package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.util.ConfigUpdater;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class IntelligentConfigHelper {
    public static boolean performIntelligentFileUpdate(JustTeams plugin, String fileName, String timestamp) throws IOException {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            plugin.saveResource(fileName, false);
            plugin.getLogger().info("Created " + fileName + " from template");
            return true;
        }
        YamlConfiguration currentConfig = null;
        try {
            currentConfig = YamlConfiguration.loadConfiguration((File)configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("YAML corruption detected in " + fileName + ", performing auto-repair...");
            if (IntelligentConfigHelper.performYamlAutoRepair(configFile)) {
                currentConfig = YamlConfiguration.loadConfiguration((File)configFile);
                plugin.getLogger().info("Successfully auto-repaired " + fileName);
            }
            throw new IOException("Could not repair corrupted YAML in " + fileName);
        }
        try (InputStream defaultStream = plugin.getResource(fileName);){
            if (defaultStream == null) {
                plugin.getLogger().warning("No default template found for " + fileName);
                boolean bl = false;
                return bl;
            }
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(defaultStream));
            Map<String, Object> userValues = IntelligentConfigHelper.preserveUserCustomizations((FileConfiguration)currentConfig, fileName);
            boolean needsUpdate = IntelligentConfigHelper.intelligentVersionCheck((FileConfiguration)currentConfig, (FileConfiguration)defaultConfig, fileName);
            if (!needsUpdate && !IntelligentConfigHelper.hasMissingKeys((FileConfiguration)currentConfig, (FileConfiguration)defaultConfig)) {
                boolean bl = false;
                return bl;
            }
            IntelligentConfigHelper.createIntelligentBackup(plugin, configFile, fileName, timestamp);
            boolean updated = IntelligentConfigHelper.performSmartMerge((FileConfiguration)currentConfig, (FileConfiguration)defaultConfig, userValues, fileName);
            if (updated) {
                IntelligentConfigHelper.validateAndSanitizeConfig((FileConfiguration)currentConfig, fileName);
                currentConfig.save(configFile);
                plugin.getLogger().info("Intelligently updated " + fileName + " while preserving user customizations");
                boolean bl = true;
                return bl;
            }
        }
        return false;
    }

    public static Map<String, Object> preserveUserCustomizations(FileConfiguration config, String fileName) {
        LinkedHashMap<String, Object> userValues = new LinkedHashMap<String, Object>();
        Set<String> customizableKeys = ConfigUpdater.USER_CUSTOMIZABLE_KEYS.getOrDefault(fileName, Set.of());
        for (String key : customizableKeys) {
            Object value;
            if (!config.contains(key) || (value = config.get(key)) == null || IntelligentConfigHelper.isDefaultValue(key, value)) continue;
            userValues.put(key, value);
        }
        userValues.putAll(IntelligentConfigHelper.preserveCustomSections(config, fileName));
        return userValues;
    }

    public static Map<String, Object> preserveCustomSections(FileConfiguration config, String fileName) {
        LinkedHashMap<String, Object> customSections = new LinkedHashMap<String, Object>();
        if ("config.yml".equals(fileName)) {
            IntelligentConfigHelper.preserveSection(config, "storage.mysql", customSections);
            IntelligentConfigHelper.preserveSection(config, "webhook", customSections);
            IntelligentConfigHelper.preserveSection(config, "team_creation", customSections);
        }
        return customSections;
    }

    public static void preserveSection(FileConfiguration config, String sectionPath, Map<String, Object> storage) {
        ConfigurationSection section = config.getConfigurationSection(sectionPath);
        if (section != null) {
            for (String key : section.getKeys(true)) {
                String fullKey = sectionPath + "." + key;
                storage.put(fullKey, section.get(key));
            }
        }
    }

    public static boolean intelligentVersionCheck(FileConfiguration current, FileConfiguration defaultConfig, String fileName) {
        int defaultVersion;
        String versionKey = ConfigUpdater.getVersionKey(fileName);
        if (versionKey == null) {
            return false;
        }
        int currentVersion = current.getInt(versionKey, 0);
        if (currentVersion < (defaultVersion = defaultConfig.getInt(versionKey, 0))) {
            return true;
        }
        if (currentVersion > defaultVersion) {
            current.set(versionKey, (Object)defaultVersion);
            return true;
        }
        return false;
    }

    public static boolean hasMissingKeys(FileConfiguration current, FileConfiguration defaultConfig) {
        for (String key : defaultConfig.getKeys(true)) {
            if (current.contains(key) || defaultConfig.isConfigurationSection(key)) continue;
            return true;
        }
        return false;
    }

    public static void createIntelligentBackup(JustTeams plugin, File configFile, String fileName, String timestamp) throws IOException {
        File backupFolder = new File(plugin.getDataFolder(), "backups/intelligent/" + timestamp);
        backupFolder.mkdirs();
        File backupFile = new File(backupFolder, fileName);
        Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        plugin.getLogger().info("Created intelligent backup: backups/intelligent/" + timestamp + "/" + fileName);
    }

    public static void createUpdateSnapshot(JustTeams plugin, String timestamp) {
        try {
            File snapshotFolder = new File(plugin.getDataFolder(), "backups/snapshots/" + timestamp);
            snapshotFolder.mkdirs();
            List<String> configFiles = Arrays.asList("config.yml", "messages.yml", "gui.yml", "commands.yml", "placeholders.yml");
            for (String fileName : configFiles) {
                File configFile = new File(plugin.getDataFolder(), fileName);
                if (!configFile.exists()) continue;
                File snapshotFile = new File(snapshotFolder, fileName);
                Files.copy(configFile.toPath(), snapshotFile.toPath(), new CopyOption[0]);
            }
            plugin.getLogger().info("Created pre-update snapshot: backups/snapshots/" + timestamp);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create update snapshot: " + e.getMessage());
        }
    }

    public static boolean performSmartMerge(FileConfiguration current, FileConfiguration defaultConfig, Map<String, Object> userValues, String fileName) {
        boolean updated = false;
        String versionKey = ConfigUpdater.getVersionKey(fileName);
        if (versionKey != null) {
            int defaultVersion = defaultConfig.getInt(versionKey);
            current.set(versionKey, (Object)defaultVersion);
            updated = true;
        }
        for (String string : defaultConfig.getKeys(true)) {
            if (defaultConfig.isConfigurationSection(string)) continue;
            if (!current.contains(string)) {
                current.set(string, defaultConfig.get(string));
                updated = true;
                continue;
            }
            if (!IntelligentConfigHelper.shouldUpdateKey(string, current.get(string), defaultConfig.get(string), fileName)) continue;
            current.set(string, defaultConfig.get(string));
            updated = true;
        }
        for (Map.Entry entry : userValues.entrySet()) {
            Object value;
            String key = (String)entry.getKey();
            if (!IntelligentConfigHelper.isValidUserValue(key, value = entry.getValue())) continue;
            current.set(key, value);
        }
        return updated;
    }

    public static boolean shouldUpdateKey(String key, Object currentValue, Object defaultValue, String fileName) {
        if ("prefix".equals(key) && fileName.equals("messages.yml")) {
            String currentStr = currentValue.toString();
            return currentStr.contains("<gradient:") && !currentStr.contains("</gradient>");
        }
        if (key.contains("name") && fileName.equals("gui.yml")) {
            String currentStr = currentValue.toString();
            return currentStr.contains("<gradient:") && !currentStr.contains("</gradient>");
        }
        if (key.contains("color") && fileName.equals("placeholders.yml")) {
            String currentStr = currentValue.toString();
            return currentStr.trim().isEmpty() || currentStr.equals("\"\"");
        }
        return false;
    }

    public static boolean isDefaultValue(String key, Object value) {
        if (value == null) {
            return true;
        }
        String strValue = value.toString().trim();
        return strValue.isEmpty() || strValue.equals("\"\"") || strValue.equals("__STRING_PLACEHOLDER_0__") || strValue.contains("<gradient:") && !strValue.contains("</gradient>");
    }

    public static boolean isValidUserValue(String key, Object value) {
        if (value == null) {
            return false;
        }
        Pattern validator = ConfigUpdater.VALUE_VALIDATORS.get(key);
        if (validator != null) {
            return validator.matcher(value.toString()).matches();
        }
        return true;
    }

    public static void validateAndSanitizeConfig(FileConfiguration config, String fileName) {
        if ("config.yml".equals(fileName)) {
            IntelligentConfigHelper.validateConfigValues(config);
        } else if ("messages.yml".equals(fileName)) {
            IntelligentConfigHelper.validateMessageFormats(config);
        } else if ("gui.yml".equals(fileName)) {
            IntelligentConfigHelper.validateGuiConfiguration(config);
        } else if ("placeholders.yml".equals(fileName)) {
            IntelligentConfigHelper.validatePlaceholderValues(config);
        }
    }

    public static void validateConfigValues(FileConfiguration config) {
        int mysqlPort;
        int maxTeamSize = config.getInt("max_team_size", 10);
        if (maxTeamSize < 1 || maxTeamSize > 100) {
            config.set("max_team_size", (Object)10);
        }
        if ((mysqlPort = config.getInt("storage.mysql.port", 3306)) < 1 || mysqlPort > 65535) {
            config.set("storage.mysql.port", (Object)3306);
        }
    }

    public static void validateMessageFormats(FileConfiguration config) {
        String prefix = config.getString("prefix", "");
        if (prefix.contains("<gradient:") && !prefix.contains("</gradient>")) {
            config.set("prefix", (Object)"<bold><gradient:#4C9DDE:#FFD700>JustTeams</gradient></bold>");
        }
    }

    public static void validateGuiConfiguration(FileConfiguration config) {
    }

    public static void validatePlaceholderValues(FileConfiguration config) {
        IntelligentConfigHelper.validateColorValue(config, "colors.primary", "#4C9DDE");
        IntelligentConfigHelper.validateColorValue(config, "colors.secondary", "#4C96D2");
        IntelligentConfigHelper.validateColorValue(config, "colors.accent", "#FFD700");
        IntelligentConfigHelper.validateColorValue(config, "colors.success", "#00FF00");
        IntelligentConfigHelper.validateColorValue(config, "colors.error", "#FF0000");
        IntelligentConfigHelper.validateColorValue(config, "colors.warning", "#FFA500");
        IntelligentConfigHelper.validateColorValue(config, "colors.info", "#00BFFF");
    }

    public static void validateColorValue(FileConfiguration config, String key, String defaultValue) {
        String value = config.getString(key, "");
        if (value.trim().isEmpty() || !value.matches("^#[0-9A-Fa-f]{6}$")) {
            config.set(key, (Object)defaultValue);
        }
    }

    public static boolean performYamlAutoRepair(File configFile) {
        try {
            List<String> lines = Files.readAllLines(configFile.toPath());
            boolean repaired = false;
            for (int i = 0; i < lines.size(); ++i) {
                String indent;
                String line = lines.get(i);
                if (line.contains("<gradient:") && !line.contains("</gradient>")) {
                    if (line.contains("prefix:")) {
                        lines.set(i, "prefix: \"<bold><gradient:#4C9DDE:#FFD700>JustTeams</gradient></bold>\"");
                        repaired = true;
                    } else if (line.contains("name:")) {
                        indent = line.substring(0, line.indexOf("name:"));
                        lines.set(i, indent + "name: \"<gradient:#4C9DDE:#FFD700>Item</gradient>\"");
                        repaired = true;
                    }
                }
                if (!line.matches("\\s*color:\\s*\"?\\s*\"?\\s*$")) continue;
                indent = line.substring(0, line.indexOf("color:"));
                lines.set(i, indent + "color: \"#FFFFFF\"");
                repaired = true;
            }
            if (repaired) {
                Files.write(configFile.toPath(), lines, new OpenOption[0]);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static void performEmergencyRecovery(JustTeams plugin, String fileName) throws IOException {
        plugin.getLogger().warning("Performing emergency recovery for " + fileName);
        File configFile = new File(plugin.getDataFolder(), fileName);
        File backupFolder = new File(plugin.getDataFolder(), "backups");
        File emergencyBackup = new File(backupFolder, fileName + ".emergency." + System.currentTimeMillis());
        if (configFile.exists()) {
            Files.copy(configFile.toPath(), emergencyBackup.toPath(), new CopyOption[0]);
        }
        plugin.saveResource(fileName, true);
        plugin.getLogger().info("Emergency recovery completed for " + fileName + " (backup: " + emergencyBackup.getName() + ")");
    }

    public static void generateUpdateReport(JustTeams plugin, int successCount, int failCount, String timestamp) {
        try {
            File reportsFolder = new File(plugin.getDataFolder(), "reports");
            reportsFolder.mkdirs();
            File reportFile = new File(reportsFolder, "update_report_" + timestamp + ".txt");
            ArrayList<String> reportLines = new ArrayList<>();
            reportLines.add("=== JustTeams Configuration Update Report ===");
            reportLines.add("Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            reportLines.add("Update ID: " + timestamp);
            reportLines.add("");
            reportLines.add("Results:");
            reportLines.add("  Successful updates: " + successCount);
            reportLines.add("  Failed updates: " + failCount);
            reportLines.add("  Total files processed: 5");
            reportLines.add("");
            reportLines.add("Backup locations:");
            reportLines.add("  Snapshot: backups/snapshots/" + timestamp + "/");
            reportLines.add("  Individual backups: backups/intelligent/" + timestamp + "/");
            reportLines.add("");
            reportLines.add("Features applied:");
            reportLines.add("  \u2713 User customization preservation");
            reportLines.add("  \u2713 Intelligent version management");
            reportLines.add("  \u2713 YAML auto-repair");
            reportLines.add("  \u2713 Value validation and sanitization");
            reportLines.add("  \u2713 Emergency recovery procedures");
            Files.write(reportFile.toPath(), reportLines);
            plugin.getLogger().info("Update report generated: reports/update_report_" + timestamp + ".txt");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to generate update report: " + e.getMessage());
        }
    }

    public static boolean hasCorruptedValues(FileConfiguration config, String fileName) {
        String prefix;
        if ("messages.yml".equals(fileName) && (prefix = config.getString("prefix", "")).contains("<gradient:") && !prefix.contains("</gradient>")) {
            return true;
        }
        if ("placeholders.yml".equals(fileName)) {
            for (String colorKey : List.of("colors.primary", "colors.secondary", "colors.accent")) {
                String value = config.getString(colorKey, "");
                if (!value.trim().isEmpty() && !value.equals("\"\"")) continue;
                return true;
            }
        }
        return false;
    }
}

