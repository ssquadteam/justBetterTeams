package eu.kotori.justTeams.config;

import eu.kotori.justTeams.JustTeams;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class MessageConfig {
    private final JustTeams plugin;
    private File customConfigFile;
    private FileConfiguration customConfig;

    public MessageConfig(JustTeams plugin) {
        this.plugin = plugin;
        this.createCustomConfig();
    }

    public FileConfiguration getCustomConfig() {
        return this.customConfig;
    }

    public void reload() {
        this.customConfigFile = new File(this.plugin.getDataFolder(), "messages.yml");
        this.customConfig = YamlConfiguration.loadConfiguration((File)this.customConfigFile);
    }

    private void createCustomConfig() {
        this.customConfigFile = new File(this.plugin.getDataFolder(), "messages.yml");
        if (!this.customConfigFile.exists()) {
            this.customConfigFile.getParentFile().mkdirs();
            this.plugin.saveResource("messages.yml", false);
        }
        this.customConfig = new YamlConfiguration();
        try {
            this.customConfig.load(this.customConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Message config load error details", e);
        }
    }
}

