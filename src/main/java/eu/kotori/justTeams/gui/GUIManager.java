package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.GUIUpdateThrottle;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class GUIManager {
    private final JustTeams plugin;
    private File guiConfigFile;
    private FileConfiguration guiConfig;
    private final GUIUpdateThrottle updateThrottle;

    public GUIManager(JustTeams plugin) {
        this.plugin = plugin;
        this.updateThrottle = new GUIUpdateThrottle(plugin);
        this.createGuiConfig();
    }

    public void reload() {
        if (this.guiConfigFile == null) {
            this.createGuiConfig();
        }
        this.guiConfig = YamlConfiguration.loadConfiguration((File)this.guiConfigFile);
    }

    private void createGuiConfig() {
        this.guiConfigFile = new File(this.plugin.getDataFolder(), "gui.yml");
        if (!this.guiConfigFile.exists()) {
            this.guiConfigFile.getParentFile().mkdirs();
            this.plugin.saveResource("gui.yml", false);
        }
        this.guiConfig = new YamlConfiguration();
        try {
            this.guiConfig.load(this.guiConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            this.plugin.getLogger().severe("Could not load gui.yml!");
            this.plugin.getLogger().log(Level.SEVERE, "GUI config load error details", e);
        }
    }

    public FileConfiguration getGuiConfig() {
        return this.guiConfig;
    }

    public ConfigurationSection getGUI(String key) {
        return this.guiConfig.getConfigurationSection(key);
    }

    public ConfigurationSection getNoTeamGUI() {
        return this.getGUI("no-team-gui");
    }

    public String getNoTeamGUITitle() {
        return this.getString("no-team-gui.title", "\u1d1b\u1d07\u1d00\u1d0d \u1d0d\u1d07\u0274\u1d1c");
    }

    public int getNoTeamGUISize() {
        return this.getInt("no-team-gui.size", 27);
    }

    public ConfigurationSection getCreateTeamButton() {
        return this.getGUI("no-team-gui.items.create-team");
    }

    public ConfigurationSection getLeaderboardsButton() {
        return this.getGUI("no-team-gui.items.leaderboards");
    }

    public ConfigurationSection getNoTeamGUIFillItem() {
        return this.getGUI("no-team-gui.fill-item");
    }

    public ConfigurationSection getTeamGUI() {
        return this.getGUI("team-gui");
    }

    public String getTeamGUITitle() {
        return this.getString("team-gui.title", "\u1d1b\u1d07\u1d00\u1d0d - <members>/<max_members>");
    }

    public int getTeamGUISize() {
        return this.getInt("team-gui.size", 54);
    }

    public ConfigurationSection getPlayerHeadSection() {
        return this.getGUI("team-gui.items.player-head");
    }

    public String getOnlineNameFormat() {
        return this.getString("team-gui.items.player-head.online-name-format", "<gradient:#4C9DDE:#4C96D2><status_indicator><role_icon><player></gradient>");
    }

    public String getOfflineNameFormat() {
        return this.getString("team-gui.items.player-head.offline-name-format", "<gray><status_indicator><role_icon><player>");
    }

    public List<String> getPlayerHeadLore() {
        return this.getStringList("team-gui.items.player-head.lore");
    }

    public String getCanEditPrompt() {
        return this.getString("team-gui.items.player-head.can-edit-prompt", "<yellow>Click to edit this member.</yellow>");
    }

    public String getCanViewPrompt() {
        return this.getString("team-gui.items.player-head.can-view-prompt", "<yellow>Click to view your information.</yellow>");
    }

    public String getCannotEditPrompt() {
        return this.getString("team-gui.items.player-head.cannot-edit-prompt", "");
    }

    public ConfigurationSection getJoinRequestsButton() {
        return this.getGUI("team-gui.items.join-requests");
    }

    public ConfigurationSection getJoinRequestsLockedButton() {
        return this.getGUI("team-gui.items.join-requests-locked");
    }

    public ConfigurationSection getWarpsButton() {
        return this.getGUI("team-gui.items.warps");
    }

    public ConfigurationSection getBankButton() {
        return this.getGUI("team-gui.items.bank");
    }

    public ConfigurationSection getBankLockedButton() {
        return this.getGUI("team-gui.items.bank-locked");
    }

    public ConfigurationSection getHomeButton() {
        return this.getGUI("team-gui.items.home");
    }

    public ConfigurationSection getTeamSettingsButton() {
        return this.getGUI("team-gui.items.team-settings");
    }

    public ConfigurationSection getTeamSettingsGUI() {
        return this.getGUI("team-settings-gui");
    }

    public String getTeamSettingsGUITitle() {
        return this.getString("team-settings-gui.title", "\u1d1b\u1d07\u1d00\u1d0d s\u1d07\u1d1b\u1d1b\u026a\u0274\u0262s");
    }

    public int getTeamSettingsGUISize() {
        return this.getInt("team-settings-gui.size", 27);
    }

    public ConfigurationSection getMemberEditGUI() {
        return this.getGUI("member-edit-gui");
    }

    public String getMemberEditGUITitle() {
        return this.getString("member-edit-gui.title", "\u1d07\u1d05\u026a\u1d1b \u1d0d\u1d07\u1d0d\u0299\u1d07\u0280");
    }

    public int getMemberEditGUISize() {
        return this.getInt("member-edit-gui.size", 27);
    }

    public ConfigurationSection getMemberPermissionsGUI() {
        return this.getGUI("member-permissions-gui");
    }

    public String getMemberPermissionsGUITitle() {
        return this.getString("member-permissions-gui.title", "\u1d0d\u1d07\u1d0d\u0299\u1d07\u0280 \u1d18\u1d07\u0280\u1d0d\u026ass\u026a\u1d0f\u0274s");
    }

    public int getMemberPermissionsGUISize() {
        return this.getInt("member-permissions-gui.size", 27);
    }

    public ConfigurationSection getBankGUI() {
        return this.getGUI("bank-gui");
    }

    public String getBankGUITitle() {
        return this.getString("bank-gui.title", "\u1d1b\u1d07\u1d00\u1d0d \u0299\u1d00\u0274\u1d0b");
    }

    public int getBankGUISize() {
        return this.getInt("bank-gui.size", 27);
    }

    public ConfigurationSection getWarpsGUI() {
        return this.getGUI("warps-gui");
    }

    public String getWarpsGUITitle() {
        return this.getString("warps-gui.title", "\u1d1b\u1d07\u1d00\u1d0d \u1d21\u1d00\u0280\u1d18s");
    }

    public int getWarpsGUISize() {
        return this.getInt("warps-gui.size", 27);
    }

    public ConfigurationSection getLeaderboardGUI() {
        return this.getGUI("leaderboard-gui");
    }

    public String getLeaderboardGUITitle() {
        return this.getString("leaderboard-gui.title", "\u1d1b\u1d07\u1d00\u1d0d \u029f\u1d07\u1d00\u1d05\u1d07\u0280\u0299\u1d0f\u1d00\u0280\u1d05");
    }

    public int getLeaderboardGUISize() {
        return this.getInt("leaderboard-gui.size", 27);
    }

    public ConfigurationSection getAdminGUI() {
        return this.getGUI("admin-gui");
    }

    public String getAdminGUITitle() {
        return this.getString("admin-gui.title", "\u1d00\u1d05\u1d0d\u026a\u0274 \u1d18\u1d00\u0274\u1d07\u029f");
    }

    public int getAdminGUISize() {
        return this.getInt("admin-gui.size", 27);
    }

    public String getString(String path, String defaultValue) {
        return this.guiConfig.getString(path, defaultValue);
    }

    public int getInt(String path, int defaultValue) {
        return this.guiConfig.getInt(path, defaultValue);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return this.guiConfig.getBoolean(path, defaultValue);
    }

    public List<String> getStringList(String path) {
        return this.guiConfig.getStringList(path);
    }

    public Material getMaterial(String path, Material defaultValue) {
        String materialName = this.guiConfig.getString(path, defaultValue.name());
        try {
            return Material.valueOf((String)materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.plugin.getLogger().log(Level.WARNING, "Invalid material " + materialName + " found in gui.yml at path " + path + ". Using default: " + defaultValue.name());
            return defaultValue;
        }
    }

    public Material getMaterial(String path) {
        return this.getMaterial(path, Material.STONE);
    }

    public boolean hasGUI(String key) {
        return this.guiConfig.contains(key);
    }

    public Set<String> getGUIKeys() {
        return this.guiConfig.getKeys(true);
    }

    public GUIUpdateThrottle getUpdateThrottle() {
        return this.updateThrottle;
    }

    public ConfigurationSection getItemConfig(String guiKey, String itemKey) {
        ConfigurationSection guiSection = this.getGUI(guiKey);
        if (guiSection != null) {
            return guiSection.getConfigurationSection("items." + itemKey);
        }
        return null;
    }

    public int getItemSlot(String guiKey, String itemKey, int defaultValue) {
        ConfigurationSection itemSection = this.getItemConfig(guiKey, itemKey);
        if (itemSection != null) {
            return itemSection.getInt("slot", defaultValue);
        }
        return defaultValue;
    }

    public Material getItemMaterial(String guiKey, String itemKey, Material defaultValue) {
        ConfigurationSection itemSection = this.getItemConfig(guiKey, itemKey);
        if (itemSection != null) {
            return this.getMaterial("items." + itemKey + ".material", defaultValue);
        }
        return defaultValue;
    }

    public String getItemName(String guiKey, String itemKey, String defaultValue) {
        ConfigurationSection itemSection = this.getItemConfig(guiKey, itemKey);
        if (itemSection != null) {
            return itemSection.getString("name", defaultValue);
        }
        return defaultValue;
    }

    public List<String> getItemLore(String guiKey, String itemKey) {
        ConfigurationSection itemSection = this.getItemConfig(guiKey, itemKey);
        if (itemSection != null) {
            return itemSection.getStringList("lore");
        }
        return Collections.emptyList();
    }
}

