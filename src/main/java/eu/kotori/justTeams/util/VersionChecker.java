package eu.kotori.justTeams.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.util.StartupMessage;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public class VersionChecker
implements Listener {
    private final JustTeams plugin;
    private final String currentVersion;
    private final String apiUrl;

    public VersionChecker(JustTeams plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        this.apiUrl = "https://api.kotori.ink/v1/version?product=justTeams";
        this.plugin.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plugin);
    }

    public void check() {
        this.plugin.getTaskRunner().runAsync(() -> {
            block10: {
                try {
                    this.plugin.getLogger().info("Checking for updates...");
                    URI uri = URI.create(this.apiUrl);
                    HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.setRequestProperty("User-Agent", "JustTeams Version Checker");
                    int responseCode = connection.getResponseCode();
                    if (responseCode == 200) {
                        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream());){
                            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                            String latestVersion = jsonObject.get("version").getAsString();
                            this.plugin.getLogger().info("Current version: " + this.currentVersion + " | Latest version: " + latestVersion);
                            if (!this.currentVersion.equalsIgnoreCase(latestVersion)) {
                                this.plugin.updateAvailable = true;
                                this.plugin.latestVersion = latestVersion;
                                this.plugin.getLogger().info("A new version is available: " + latestVersion);
                                StartupMessage.sendUpdateNotification(this.plugin);
                            }
                            this.plugin.updateAvailable = false;
                            this.plugin.getLogger().info("You are running the latest version!");
                        }
                    } else {
                        this.plugin.getLogger().warning("Version check failed with response code: " + responseCode);
                    }
                    connection.disconnect();
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
                    if (!this.plugin.getConfigManager().isDebugLoggingEnabled()) break block10;
                    e.printStackTrace();
                }
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("justteams.admin") && this.plugin.updateAvailable) {
            this.plugin.getTaskRunner().runEntityTaskLater((Entity)player, () -> {
                if (player.isOnline()) {
                    StartupMessage.sendUpdateNotification(player, this.plugin);
                }
            }, 60L);
        }
    }
}

