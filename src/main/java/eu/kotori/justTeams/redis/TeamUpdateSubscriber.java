package eu.kotori.justTeams.redis;

import eu.kotori.justTeams.JustTeams;
import redis.clients.jedis.JedisPubSub;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.EffectsUtil;
import eu.kotori.justTeams.util.InventoryUtil;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class TeamUpdateSubscriber
extends JedisPubSub {
    private final JustTeams plugin;

    public TeamUpdateSubscriber(JustTeams plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessage(String channel, String message) {
        try {
            String[] parts = message.split("\\|", 5);
            if (parts.length < 4) {
                this.plugin.getLogger().warning("Invalid Redis update format: " + message);
                return;
            }
            int teamId = Integer.parseInt(parts[0]);
            String updateType = parts[1];
            String playerUuid = parts[2];
            String data = parts[3];
            long timestamp = parts.length > 4 ? Long.parseLong(parts[4]) : System.currentTimeMillis();
            long latency = System.currentTimeMillis() - timestamp;
            Team team = this.plugin.getTeamManager().getTeamById(teamId).orElse(null);
            if (team == null) {
                this.plugin.getLogger().warning("Received update for unknown team ID: " + teamId);
                return;
            }
            this.plugin.getTaskRunner().run(() -> this.processUpdate(team, updateType, playerUuid, data, latency));
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error processing Redis update: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processUpdate(Team team, String updateType, String playerUuidStr, String data, long latency) {
        try {
            UUID playerUuid = playerUuidStr != null && !playerUuidStr.isEmpty() ? UUID.fromString(playerUuidStr) : null;
            switch (updateType) {
                case "MEMBER_KICKED": {
                    if (playerUuid == null) {
                        return;
                    }
                    this.plugin.getTeamManager().forceTeamSync(team.getId());
                    this.plugin.getTeamManager().removeFromPlayerTeamCache(playerUuid);
                    team.removeMember(playerUuid);
                    Player kickedPlayer = Bukkit.getPlayer((UUID)playerUuid);
                    if (kickedPlayer != null && kickedPlayer.isOnline()) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)kickedPlayer, () -> {
                            kickedPlayer.closeInventory();
                            this.plugin.getMessageManager().sendMessage((CommandSender)kickedPlayer, "you_were_kicked", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                            EffectsUtil.playSound(kickedPlayer, EffectsUtil.SoundType.ERROR);
                        });
                    }
                    this.plugin.getLogger().info(String.format("\u2713 Redis update MEMBER_KICKED processed for %s (latency: %dms)", playerUuid, latency));
                    break;
                }
                case "MEMBER_PROMOTED": {
                    if (playerUuid == null) {
                        return;
                    }
                    Player promotedPlayer = Bukkit.getPlayer((UUID)playerUuid);
                    if (promotedPlayer != null && promotedPlayer.isOnline()) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)promotedPlayer, () -> {
                            this.plugin.getMessageManager().sendMessage((CommandSender)promotedPlayer, "player_promoted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)promotedPlayer.getName())});
                            EffectsUtil.playSound(promotedPlayer, EffectsUtil.SoundType.SUCCESS);
                        });
                    }
                    this.plugin.getTeamManager().forceTeamSync(team.getId());
                    this.plugin.getLogger().info(String.format("\u2713 Redis update MEMBER_PROMOTED processed (latency: %dms)", latency));
                    break;
                }
                case "MEMBER_DEMOTED": {
                    if (playerUuid == null) {
                        return;
                    }
                    Player demotedPlayer = Bukkit.getPlayer((UUID)playerUuid);
                    if (demotedPlayer != null && demotedPlayer.isOnline()) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)demotedPlayer, () -> {
                            this.plugin.getMessageManager().sendMessage((CommandSender)demotedPlayer, "player_demoted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)demotedPlayer.getName())});
                            EffectsUtil.playSound(demotedPlayer, EffectsUtil.SoundType.SUCCESS);
                        });
                    }
                    this.plugin.getTeamManager().forceTeamSync(team.getId());
                    this.plugin.getLogger().info(String.format("\u2713 Redis update MEMBER_DEMOTED processed (latency: %dms)", latency));
                    break;
                }
                case "MEMBER_LEFT": {
                    if (playerUuid == null) {
                        return;
                    }
                    this.plugin.getTeamManager().forceTeamSync(team.getId());
                    this.plugin.getTeamManager().removeFromPlayerTeamCache(playerUuid);
                    team.removeMember(playerUuid);
                    Player leftPlayer = Bukkit.getPlayer((UUID)playerUuid);
                    if (leftPlayer != null && leftPlayer.isOnline()) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)leftPlayer, () -> leftPlayer.closeInventory());
                    }
                    this.plugin.getLogger().info(String.format("\u2713 Redis update MEMBER_LEFT processed for %s (latency: %dms)", playerUuid, latency));
                    break;
                }
                case "MEMBER_JOINED": {
                    if (playerUuid == null) {
                        return;
                    }
                    this.plugin.getTeamManager().forceTeamSync(team.getId());
                    Player joinedPlayer = Bukkit.getPlayer((UUID)playerUuid);
                    if (joinedPlayer != null && joinedPlayer.isOnline()) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)joinedPlayer, () -> this.plugin.getTeamManager().addPlayerToTeamCache(joinedPlayer.getUniqueId(), team));
                    }
                    this.plugin.getLogger().info(String.format("\u2713 Redis update MEMBER_JOINED processed for %s (latency: %dms)", playerUuid, latency));
                    break;
                }
                case "TEAM_DISBANDED": {
                    this.plugin.getTeamManager().uncacheTeam(team.getId());
                    team.getMembers().stream().map(member -> Bukkit.getPlayer((UUID)member.getPlayerUuid())).filter(p -> p != null && p.isOnline()).forEach(p -> this.plugin.getTaskRunner().runOnEntity((Entity)p, () -> {
                        this.plugin.getTeamManager().removeFromPlayerTeamCache(p.getUniqueId());
                        this.plugin.getMessageManager().sendMessage((CommandSender)p, "team_disbanded_broadcast", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                        EffectsUtil.playSound(p, EffectsUtil.SoundType.SUCCESS);
                        p.closeInventory();
                    }));
                    this.plugin.getLogger().info(String.format("\u2713 Redis update TEAM_DISBANDED processed (latency: %dms)", latency));
                    break;
                }
                case "TEAM_UPDATED": {
                    this.plugin.getTeamManager().forceTeamSync(team.getId());
                    this.plugin.getLogger().info(String.format("\u2713 Redis update TEAM_UPDATED processed (latency: %dms)", latency));
                    break;
                }
                case "PLAYER_INVITED": {
                    if (playerUuid == null) {
                        return;
                    }
                    Player invitedPlayer = Bukkit.getPlayer((UUID)playerUuid);
                    if (invitedPlayer != null && invitedPlayer.isOnline()) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)invitedPlayer, () -> {
                            this.plugin.getMessageManager().sendRawMessage((CommandSender)invitedPlayer, this.plugin.getMessageManager().getRawMessage("prefix") + this.plugin.getMessageManager().getRawMessage("invite_received").replace("<team>", team.getName()), new TagResolver[0]);
                            EffectsUtil.playSound(invitedPlayer, EffectsUtil.SoundType.SUCCESS);
                        });
                    }
                    this.plugin.getLogger().info(String.format("\u2713 Redis update PLAYER_INVITED processed (latency: %dms)", latency));
                    break;
                }
                case "PUBLIC_STATUS_CHANGED": 
                case "PVP_STATUS_CHANGED": {
                    this.plugin.getTeamManager().forceTeamSync(team.getId());
                    this.plugin.getLogger().info(String.format("\u2713 Redis admin update %s processed (latency: %dms)", updateType, latency));
                    break;
                }
                case "ADMIN_BALANCE_SET": 
                case "ADMIN_STATS_SET": {
                    this.plugin.getTeamManager().forceTeamSync(team.getId());
                    this.plugin.getLogger().info(String.format("\u2713 Redis admin update %s processed (latency: %dms)", updateType, latency));
                    break;
                }
                case "ADMIN_PERMISSION_UPDATE": {
                    String[] parts = data.split(":");
                    if (parts.length == 3) {
                        UUID memberUuid = UUID.fromString(parts[0]);
                        this.plugin.getTeamManager().forceMemberPermissionRefresh(team.getId(), memberUuid);
                        this.plugin.getLogger().info(String.format("\u2713 Redis admin permission update processed for member %s (latency: %dms)", memberUuid, latency));
                    }
                    break;
                }
                case "ADMIN_MEMBER_KICK": {
                    UUID kickedUuid = UUID.fromString(data);
                    this.plugin.getTeamManager().forceTeamSync(team.getId());
                    this.plugin.getTeamManager().removeFromPlayerTeamCache(kickedUuid);
                    team.removeMember(kickedUuid);
                    Player kickedPlayer = Bukkit.getPlayer((UUID)kickedUuid);
                    if (kickedPlayer != null && kickedPlayer.isOnline()) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)kickedPlayer, () -> {
                            kickedPlayer.closeInventory();
                            this.plugin.getMessageManager().sendMessage((CommandSender)kickedPlayer, "you_were_kicked", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                            EffectsUtil.playSound(kickedPlayer, EffectsUtil.SoundType.ERROR);
                        });
                    }
                    this.plugin.getLogger().info(String.format("\u2713 Redis admin kick processed for %s (latency: %dms)", kickedUuid, latency));
                    break;
                }
                case "ADMIN_MEMBER_PROMOTE": 
                case "ADMIN_MEMBER_DEMOTE": {
                    UUID memberUuid = UUID.fromString(data);
                    this.plugin.getTeamManager().forceMemberPermissionRefresh(team.getId(), memberUuid);
                    Player targetPlayer = Bukkit.getPlayer((UUID)memberUuid);
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)targetPlayer, () -> {
                            String messageKey = updateType.equals("ADMIN_MEMBER_PROMOTE") ? "player_promoted" : "player_demoted";
                            this.plugin.getMessageManager().sendMessage((CommandSender)targetPlayer, messageKey, new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetPlayer.getName())});
                            EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.SUCCESS);
                        });
                    }
                    this.plugin.getLogger().info(String.format("\u2713 Redis admin %s processed for %s (latency: %dms)", updateType, memberUuid, latency));
                    break;
                }
                case "ENDERCHEST_UPDATED": {
                    this.plugin.getTaskRunner().runAsync(() -> {
                        try {
                            if (!team.isEnderChestLocked()) {
                                Inventory enderChest = team.getEnderChest();
                                if (enderChest == null) {
                                    enderChest = Bukkit.createInventory(null, (int)27, (String)"Team Enderchest");
                                    team.setEnderChest(enderChest);
                                }
                                Inventory finalEnderChest = enderChest;
                                InventoryUtil.deserializeInventory(finalEnderChest, data);
                                this.plugin.getTaskRunner().run(() -> this.plugin.getTeamManager().refreshEnderChestInventory(team));
                                this.plugin.getLogger().info(String.format("\u2713 Redis enderchest update processed for team %s (latency: %dms)", team.getName(), latency));
                            } else {
                                this.plugin.getLogger().info(String.format("Skipped Redis enderchest update (lock held) for team: %s", team.getName()));
                            }
                        } catch (Exception e) {
                            this.plugin.getLogger().warning("Failed to process Redis ENDERCHEST_UPDATED for team " + team.getName() + ": " + e.getMessage());
                        }
                    });
                    break;
                }
                default: {
                    this.plugin.getLogger().warning("Unknown Redis update type: " + updateType);
                }
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error processing update: " + e.getMessage());
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

