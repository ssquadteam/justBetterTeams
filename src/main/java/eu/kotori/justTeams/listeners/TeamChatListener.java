package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.config.MessageManager;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class TeamChatListener
implements Listener {
    private final TeamManager teamManager;
    private final MessageManager messageManager;
    private final Set<UUID> teamChatEnabled = new HashSet<UUID>();
    private final MiniMessage miniMessage;

    public TeamChatListener(JustTeams plugin) {
        this.teamManager = plugin.getTeamManager();
        this.messageManager = plugin.getMessageManager();
        this.miniMessage = plugin.getMiniMessage();
    }

    public void toggleTeamChat(Player player) {
        if (!JustTeams.getInstance().getConfigManager().getBoolean("features.team_chat", true)) {
            this.messageManager.sendMessage((CommandSender)player, "feature_disabled", new TagResolver[0]);
            return;
        }
        UUID uuid = player.getUniqueId();
        if (this.teamChatEnabled.contains(uuid)) {
            this.teamChatEnabled.remove(uuid);
            this.messageManager.sendMessage((CommandSender)player, "team_chat_disabled", new TagResolver[0]);
        } else {
            if (this.teamManager.getPlayerTeam(uuid) == null) {
                this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
                return;
            }
            this.teamChatEnabled.add(uuid);
            this.messageManager.sendMessage((CommandSender)player, "team_chat_enabled", new TagResolver[0]);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onPlayerChat(AsyncChatEvent event) {
        String finalMessageContent;
        String character;
        if (!JustTeams.getInstance().getConfigManager().getBoolean("features.team_chat", true)) {
            return;
        }
        Player player = event.getPlayer();
        String messageContent = PlainTextComponentSerializer.plainText().serialize(event.message());
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        boolean isCharacterBasedTeamChat = false;
        boolean isToggleTeamChat = this.teamChatEnabled.contains(player.getUniqueId());
        if (JustTeams.getInstance().getConfigManager().getBoolean("team_chat.character_enabled", true) && (character = JustTeams.getInstance().getConfigManager().getString("team_chat.character", "#")) != null && !character.isEmpty() && !character.isBlank()) {
            boolean requireSpace = JustTeams.getInstance().getConfigManager().getBoolean("team_chat.require_space", false);
            isCharacterBasedTeamChat = requireSpace ? messageContent.startsWith(character + " ") : messageContent.startsWith(character);
        }
        if (!isToggleTeamChat && !isCharacterBasedTeamChat) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
        if (isCharacterBasedTeamChat) {
            String character2 = JustTeams.getInstance().getConfigManager().getString("team_chat.character", "#");
            boolean requireSpace = JustTeams.getInstance().getConfigManager().getBoolean("team_chat.require_space", false);
            finalMessageContent = requireSpace ? messageContent.substring(character2.length() + 1) : messageContent.substring(character2.length());
        } else {
            finalMessageContent = messageContent;
        }
        if (finalMessageContent.toLowerCase().contains("password") || finalMessageContent.toLowerCase().contains("pass")) {
            this.messageManager.sendMessage((CommandSender)player, "team_chat_password_warning", new TagResolver[0]);
            return;
        }
        String format = this.messageManager.getRawMessage("team_chat_format");
        String playerPrefix = JustTeams.getInstance().getPlayerPrefix(player);
        String playerSuffix = JustTeams.getInstance().getPlayerSuffix(player);
        Component formattedMessage = this.miniMessage.deserialize(format, new TagResolver[]{Placeholder.unparsed((String)"player", (String)player.getName()), Placeholder.unparsed((String)"prefix", (String)playerPrefix), Placeholder.unparsed((String)"player_prefix", (String)playerPrefix), Placeholder.unparsed((String)"suffix", (String)playerSuffix), Placeholder.unparsed((String)"player_suffix", (String)playerSuffix), Placeholder.unparsed((String)"team_name", (String)team.getName()), Placeholder.unparsed((String)"message", (String)finalMessageContent)});
        team.getMembers().stream().map(member -> member.getBukkitPlayer()).filter(onlinePlayer -> onlinePlayer != null).forEach(onlinePlayer -> onlinePlayer.sendMessage(formattedMessage));
        if (JustTeams.getInstance().getConfigManager().isCrossServerSyncEnabled()) {
            JustTeams.getInstance().getTaskRunner().runAsync(() -> {
                try {
                    String currentServer = JustTeams.getInstance().getConfigManager().getServerIdentifier();
                    if (JustTeams.getInstance().getConfigManager().isRedisEnabled() && JustTeams.getInstance().getRedisManager().isAvailable()) {
                        JustTeams.getInstance().getRedisManager().publishTeamChat(team.getId(), player.getUniqueId().toString(), player.getName(), finalMessageContent).thenAccept(success -> {
                            if (success.booleanValue()) {
                                JustTeams.getInstance().getLogger().info("\u2713 Team chat sent via Redis (instant)");
                            } else {
                                JustTeams.getInstance().getLogger().warning("Redis publish failed, storing in MySQL for polling");
                                this.storeChatToMySQL(team.getId(), player.getUniqueId().toString(), finalMessageContent, currentServer);
                            }
                        }).exceptionally(ex -> {
                            JustTeams.getInstance().getLogger().warning("Redis error: " + ex.getMessage() + ", using MySQL fallback");
                            this.storeChatToMySQL(team.getId(), player.getUniqueId().toString(), finalMessageContent, currentServer);
                            return null;
                        });
                    } else {
                        this.storeChatToMySQL(team.getId(), player.getUniqueId().toString(), finalMessageContent, currentServer);
                    }
                } catch (Exception e) {
                    JustTeams.getInstance().getLogger().warning("Failed to send cross-server message: " + e.getMessage());
                }
            });
        }
    }

    private void storeChatToMySQL(int teamId, String playerUuid, String message, String sourceServer) {
        try {
            JustTeams.getInstance().getStorageManager().getStorage().addCrossServerMessage(teamId, playerUuid, message, sourceServer);
        } catch (Exception e) {
            JustTeams.getInstance().getLogger().warning("Failed to store message to MySQL: " + e.getMessage());
        }
    }
}

