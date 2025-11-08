package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamManager;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener
implements Listener {
    private final JustTeams plugin;
    private final TeamManager teamManager;

    public PlayerConnectionListener(JustTeams plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.plugin.getTaskRunner().runAsync(() -> {
            String gamertag;
            this.plugin.getStorageManager().getStorage().cachePlayerName(player.getUniqueId(), player.getName());
            if (this.plugin.getConfigManager().isBedrockSupportEnabled() && this.plugin.getBedrockSupport().isBedrockPlayer(player) && (gamertag = this.plugin.getBedrockSupport().getBedrockGamertag(player)) != null && !gamertag.equals(player.getName())) {
                this.plugin.getStorageManager().getStorage().cachePlayerName(player.getUniqueId(), gamertag);
                this.plugin.getLogger().info("Cached Bedrock player: " + player.getName() + " (Gamertag: " + gamertag + ")");
            }
            if (this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
                String serverIdentifier = this.plugin.getConfigManager().getServerIdentifier();
                this.plugin.getStorageManager().getStorage().updatePlayerSession(player.getUniqueId(), serverIdentifier);
            }
        });
        if (this.plugin.getConfigManager().isBedrockSupportEnabled() && this.plugin.getBedrockSupport().isBedrockPlayer(player)) {
            String gamertag;
            this.plugin.getLogger().info("Bedrock player joined: " + player.getName() + " (UUID: " + String.valueOf(player.getUniqueId()) + ")");
            if (this.plugin.getConfigManager().isShowGamertags() && (gamertag = this.plugin.getBedrockSupport().getBedrockGamertag(player)) != null && !gamertag.equals(player.getName())) {
                this.plugin.getLogger().info("Bedrock player gamertag: " + gamertag);
            }
        }
        this.teamManager.handlePendingTeleport(player);
        this.teamManager.loadPlayerTeam(player);
        this.plugin.getTaskRunner().runAsyncTaskLater(() -> {
            List<Team> pendingInvites = this.teamManager.getPendingInvites(player.getUniqueId());
            if (!pendingInvites.isEmpty()) {
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                    for (Team team : pendingInvites) {
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("prefix") + this.plugin.getMessageManager().getRawMessage("invite_received").replace("<team>", team.getName()), new TagResolver[0]);
                    }
                    if (pendingInvites.size() == 1) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "pending_invites_singular", new TagResolver[0]);
                    } else {
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("pending_invites_plural").replace("<count>", String.valueOf(pendingInvites.size())), new TagResolver[0]);
                    }
                });
            }
        }, 40L);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (this.plugin.getConfigManager().isBedrockSupportEnabled()) {
            this.plugin.getBedrockSupport().clearPlayerCache(player.getUniqueId());
        }
        this.teamManager.unloadPlayer(player);
    }
}

