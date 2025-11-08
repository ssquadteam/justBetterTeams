package eu.kotori.justTeams.redis;

import eu.kotori.justTeams.JustTeams;
import redis.clients.jedis.JedisPubSub;
import eu.kotori.justTeams.team.Team;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TeamMessageSubscriber
extends JedisPubSub {
    private final JustTeams plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TeamMessageSubscriber(JustTeams plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessage(String channel, String message) {
        try {
            String[] parts = message.split("\\|", 5);
            if (parts.length < 4) {
                this.plugin.getLogger().warning("Invalid Redis message format: " + message);
                return;
            }
            int teamId = Integer.parseInt(parts[0]);
            UUID senderUuid = UUID.fromString(parts[1]);
            String senderName = parts[2];
            String messageText = parts[3];
            long timestamp = parts.length > 4 ? Long.parseLong(parts[4]) : System.currentTimeMillis();
            long latency = System.currentTimeMillis() - timestamp;
            Team team = this.plugin.getTeamManager().getTeamById(teamId).orElse(null);
            if (team == null) {
                this.plugin.getLogger().warning("Received message for unknown team ID: " + teamId);
                return;
            }
            String currentServer = this.plugin.getConfigManager().getServerIdentifier();
            Player sender = Bukkit.getPlayer((UUID)senderUuid);
            if (sender != null && sender.isOnline()) {
                this.plugin.getLogger().fine("Skipping Redis message from local player: " + senderName);
                return;
            }
            String format = this.plugin.getMessageManager().getRawMessage("team_chat_format");
            String playerPrefix = "";
            String playerSuffix = "";
            Player onlineSender = Bukkit.getPlayer((UUID)senderUuid);
            if (onlineSender != null && onlineSender.isOnline()) {
                playerPrefix = this.plugin.getPlayerPrefix(onlineSender);
                playerSuffix = this.plugin.getPlayerSuffix(onlineSender);
            }
            String formattedMessage = format.replace("<team>", team.getName()).replace("<team_name>", team.getName()).replace("<player>", senderName).replace("<prefix>", playerPrefix).replace("<player_prefix>", playerPrefix).replace("<suffix>", playerSuffix).replace("<player_suffix>", playerSuffix).replace("<message>", messageText);
            Component component = this.mm.deserialize((Object)formattedMessage);
            this.plugin.getTaskRunner().run(() -> {
                int delivered = 0;
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!team.isMember(onlinePlayer.getUniqueId())) continue;
                    onlinePlayer.sendMessage(component);
                    ++delivered;
                }
                this.plugin.getLogger().info(String.format("\u2713 Redis message delivered to %d players (latency: %dms) [%s]", delivered, latency, channel));
            });
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error processing Redis message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        this.plugin.getLogger().info("\u2713 Subscribed to Redis channel: " + channel + " (total: " + subscribedChannels + ")");
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        this.plugin.getLogger().info("Unsubscribed from Redis channel: " + channel + " (remaining: " + subscribedChannels + ")");
    }
}

