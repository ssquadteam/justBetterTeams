package eu.kotori.justTeams.commands;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.config.MessageManager;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TeamMessageCommand
implements CommandExecutor,
TabCompleter {
    private final TeamManager teamManager;
    private final MessageManager messageManager;
    private final MiniMessage miniMessage;
    private final ConcurrentHashMap<UUID, Long> messageCooldowns = new ConcurrentHashMap();
    private final ConcurrentHashMap<UUID, Integer> messageCounts = new ConcurrentHashMap();
    private static final long MESSAGE_COOLDOWN = 2000L;
    private static final int MAX_MESSAGES_PER_MINUTE = 20;
    private static final int MAX_MESSAGE_LENGTH = 200;

    public TeamMessageCommand(JustTeams plugin) {
        this.teamManager = plugin.getTeamManager();
        this.messageManager = plugin.getMessageManager();
        this.miniMessage = plugin.getMiniMessage();
        plugin.getTaskRunner().runTimer(() -> this.messageCounts.clear(), 1200L, 1200L);
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            this.messageManager.sendMessage(sender, "player_only", new TagResolver[0]);
            return true;
        }
        Player player = (Player)sender;
        if (args.length == 0) {
            this.messageManager.sendRawMessage((CommandSender)player, "<gray>Usage: /" + label + " <message>", new TagResolver[0]);
            return true;
        }
        if (!this.checkMessageSpam(player)) {
            this.messageManager.sendMessage((CommandSender)player, "message_spam_protection", new TagResolver[0]);
            return true;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return true;
        }
        String message = String.join((CharSequence)" ", args);
        if (message.length() > 200) {
            this.messageManager.sendMessage((CommandSender)player, "message_too_long", new TagResolver[0]);
            return true;
        }
        if (this.containsInappropriateContent(message)) {
            this.messageManager.sendMessage((CommandSender)player, "inappropriate_message", new TagResolver[0]);
            return true;
        }
        String format = this.messageManager.getRawMessage("team_chat_format");
        String playerPrefix = JustTeams.getInstance().getPlayerPrefix(player);
        String playerSuffix = JustTeams.getInstance().getPlayerSuffix(player);
        Component formattedMessage = this.miniMessage.deserialize(format, new TagResolver[]{Placeholder.unparsed((String)"player", (String)player.getName()), Placeholder.unparsed((String)"prefix", (String)playerPrefix), Placeholder.unparsed((String)"player_prefix", (String)playerPrefix), Placeholder.unparsed((String)"suffix", (String)playerSuffix), Placeholder.unparsed((String)"player_suffix", (String)playerSuffix), Placeholder.unparsed((String)"team_name", (String)team.getName()), Placeholder.unparsed((String)"message", (String)message)});
        team.getMembers().stream().map(member -> member.getBukkitPlayer()).filter(onlinePlayer -> onlinePlayer != null).forEach(onlinePlayer -> onlinePlayer.sendMessage(formattedMessage));
        if (JustTeams.getInstance().getConfigManager().isCrossServerSyncEnabled()) {
            JustTeams.getInstance().getTaskRunner().runAsync(() -> {
                try {
                    String currentServer = JustTeams.getInstance().getConfigManager().getServerIdentifier();
                    if (JustTeams.getInstance().getConfigManager().isRedisEnabled() && JustTeams.getInstance().getRedisManager().isAvailable()) {
                        ((CompletableFuture)JustTeams.getInstance().getRedisManager().publishTeamMessage(team.getId(), player.getUniqueId().toString(), player.getName(), message).thenAccept(success -> {
                            if (success.booleanValue()) {
                                JustTeams.getInstance().getLogger().info("\u2713 Team message sent via Redis (instant)");
                            } else {
                                JustTeams.getInstance().getLogger().warning("Redis publish failed, storing in MySQL for polling");
                                this.storeMessageToMySQL(team.getId(), player.getUniqueId().toString(), message, currentServer);
                            }
                        })).exceptionally(ex -> {
                            JustTeams.getInstance().getLogger().warning("Redis error: " + ex.getMessage() + ", using MySQL fallback");
                            this.storeMessageToMySQL(team.getId(), player.getUniqueId().toString(), message, currentServer);
                            return null;
                        });
                    } else {
                        this.storeMessageToMySQL(team.getId(), player.getUniqueId().toString(), message, currentServer);
                    }
                } catch (Exception e) {
                    JustTeams.getInstance().getLogger().warning("Failed to send cross-server message: " + e.getMessage());
                }
            });
        }
        return true;
    }

    private void storeMessageToMySQL(int teamId, String playerUuid, String message, String sourceServer) {
        try {
            JustTeams.getInstance().getStorageManager().getStorage().addCrossServerMessage(teamId, playerUuid, message, sourceServer);
        } catch (Exception e) {
            JustTeams.getInstance().getLogger().warning("Failed to store message to MySQL: " + e.getMessage());
        }
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return new ArrayList<String>();
    }

    private boolean checkMessageSpam(Player player) {
        long currentTime = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        Long lastMessage = this.messageCooldowns.get(playerId);
        if (lastMessage != null && currentTime - lastMessage < 2000L) {
            return false;
        }
        int count = this.messageCounts.getOrDefault(playerId, 0);
        if (count >= 20) {
            return false;
        }
        this.messageCooldowns.put(playerId, currentTime);
        this.messageCounts.put(playerId, count + 1);
        return true;
    }

    private boolean containsInappropriateContent(String message) {
        String[] inappropriate;
        String lowerMessage = message.toLowerCase();
        for (String word : inappropriate = new String[]{"admin", "mod", "staff", "owner", "server", "minecraft", "bukkit", "spigot", "hack", "cheat", "exploit", "bug", "glitch", "dupe", "duplicate"}) {
            if (!lowerMessage.contains(word)) continue;
            return true;
        }
        return false;
    }
}

