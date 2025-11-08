package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class AliasManager {
    private final JustTeams plugin;
    private FileConfiguration commandsConfig;

    public AliasManager(JustTeams plugin) {
        this.plugin = plugin;
        this.loadConfig();
    }

    private void loadConfig() {
        File commandsFile = new File(this.plugin.getDataFolder(), "commands.yml");
        if (!commandsFile.exists()) {
            this.plugin.saveResource("commands.yml", false);
        }
        this.commandsConfig = YamlConfiguration.loadConfiguration((File)commandsFile);
    }

    public void reload() {
        this.loadConfig();
    }

    public void registerAliases() {
        try {
            this.registerAlias("guild", "team");
            this.registerAlias("clan", "team");
            this.registerAlias("party", "team");
            this.registerAlias("guildmsg", "teammsg");
            this.registerAlias("clanmsg", "teammsg");
            this.registerAlias("partymsg", "teammsg");
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to dynamically register command aliases.", e);
        }
    }

    private void registerAlias(String alias, String targetCommand) {
        try {
            PluginCommand target = this.plugin.getServer().getPluginCommand(targetCommand);
            if (target != null) {
                try {
                    Constructor constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
                    constructor.setAccessible(true);
                    PluginCommand aliasCommand = (PluginCommand)constructor.newInstance(new Object[]{alias, this.plugin});
                    if (target instanceof PluginCommand) {
                        PluginCommand pluginTarget = target;
                        aliasCommand.setExecutor(pluginTarget.getExecutor());
                        aliasCommand.setTabCompleter(pluginTarget.getTabCompleter());
                    }
                    aliasCommand.setDescription(target.getDescription());
                    aliasCommand.setUsage(target.getUsage());
                    this.plugin.getServer().getCommandMap().register(this.plugin.getName(), (Command)aliasCommand);
                    this.plugin.getLogger().info("Registered command alias: /" + alias + " -> /" + targetCommand);
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to create alias command: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to register alias " + alias + " for " + targetCommand + ": " + e.getMessage());
        }
    }
}

