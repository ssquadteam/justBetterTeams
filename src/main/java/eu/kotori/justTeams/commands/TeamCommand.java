package eu.kotori.justTeams.commands;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.BlacklistGUI;
import eu.kotori.justTeams.gui.InvitesGUI;
import eu.kotori.justTeams.gui.JoinRequestGUI;
import eu.kotori.justTeams.gui.LeaderboardCategoryGUI;
import eu.kotori.justTeams.gui.NoTeamGUI;
import eu.kotori.justTeams.gui.TeamGUI;
import eu.kotori.justTeams.gui.admin.AdminGUI;
import eu.kotori.justTeams.gui.sub.TeamSettingsGUI;
import eu.kotori.justTeams.storage.DatabaseFileManager;
import eu.kotori.justTeams.storage.DatabaseMigrationManager;
import eu.kotori.justTeams.storage.DatabaseStorage;
import eu.kotori.justTeams.storage.IDataStorage;
import eu.kotori.justTeams.team.BlacklistedPlayer;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamManager;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.util.ConfigUpdater;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class TeamCommand
implements CommandExecutor,
TabCompleter {
    private final JustTeams plugin;
    private final TeamManager teamManager;
    private final ConcurrentHashMap<UUID, Long> commandCooldowns = new ConcurrentHashMap();
    private final ConcurrentHashMap<UUID, Integer> commandCounts = new ConcurrentHashMap();
    private static final long COMMAND_COOLDOWN = 1000L;
    private static final int MAX_COMMANDS_PER_MINUTE = 30;
    private static final long COMMAND_RESET_INTERVAL = 60000L;

    public TeamCommand(JustTeams plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
        plugin.getTaskRunner().runTimer(() -> this.commandCounts.clear(), 1200L, 1200L);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subCommand;
        if (!(sender instanceof Player)) {
            this.plugin.getMessageManager().sendMessage(sender, "player_only", new TagResolver[0]);
            return true;
        }
        Player player = (Player)sender;
        if (!this.checkCommandSpam(player)) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "command_spam_protection", new TagResolver[0]);
            return true;
        }
        if (args.length == 0) {
            this.handleGUI(player);
            return true;
        }
        switch (subCommand = args[0].toLowerCase()) {
            case "create": {
                this.handleCreate(player, args);
                break;
            }
            case "disband": {
                this.handleDisband(player);
                break;
            }
            case "invite": {
                this.handleInvite(player, args);
                break;
            }
            case "invites": {
                this.handleInvites(player);
                break;
            }
            case "accept": {
                this.handleAccept(player, args);
                break;
            }
            case "deny": {
                this.handleDeny(player, args);
                break;
            }
            case "join": {
                this.handleJoin(player, args);
                break;
            }
            case "unjoin": {
                this.handleUnjoin(player, args);
                break;
            }
            case "kick": {
                this.handleKick(player, args);
                break;
            }
            case "leave": {
                this.handleLeave(player);
                break;
            }
            case "promote": {
                this.handlePromote(player, args);
                break;
            }
            case "demote": {
                this.handleDemote(player, args);
                break;
            }
            case "info": {
                this.handleInfo(player, args);
                break;
            }
            case "sethome": {
                this.handleSetHome(player);
                break;
            }
            case "delhome": {
                this.handleDelHome(player);
                break;
            }
            case "home": {
                this.handleHome(player);
                break;
            }
            case "settag": {
                this.handleSetTag(player, args);
                break;
            }
            case "setdesc": {
                this.handleSetDescription(player, args);
                break;
            }
            case "rename": {
                this.handleRename(player, args);
                break;
            }
            case "transfer": {
                this.handleTransfer(player, args);
                break;
            }
            case "pvp": {
                this.handlePvpToggle(player);
                break;
            }
            case "bank": {
                this.handleBank(player, args);
                break;
            }
            case "enderchest": 
            case "ec": {
                this.handleEnderChest(player);
                break;
            }
            case "public": {
                this.handlePublicToggle(player);
                break;
            }
            case "requests": {
                this.handleRequests(player);
                break;
            }
            case "setwarp": {
                this.handleSetWarp(player, args);
                break;
            }
            case "delwarp": {
                this.handleDelWarp(player, args);
                break;
            }
            case "warp": {
                this.handleWarp(player, args);
                break;
            }
            case "warps": {
                this.handleWarps(player);
                break;
            }
            case "blacklist": {
                this.handleBlacklist(player, args);
                break;
            }
            case "unblacklist": {
                this.handleUnblacklist(player, args);
                break;
            }
            case "settings": {
                this.handleSettings(player);
                break;
            }
            case "top": {
                this.handleTop(player, args);
                break;
            }
            case "admin": {
                this.handleAdmin(player, args);
                break;
            }
            case "serveralias": {
                this.handleServerAlias(player, args);
                break;
            }
            case "platform": {
                this.handlePlatform(player);
                break;
            }
            case "reload": {
                this.handleReload(player);
                break;
            }
            case "help": {
                this.handleHelp(player);
                break;
            }
            case "chat": {
                this.handleChat(player);
                break;
            }
            case "debug-permissions": {
                if (!this.hasAdminPermission(player)) {
                    return false;
                }
                this.plugin.getTaskRunner().runAsync(() -> {
                    try {
                        this.plugin.getLogger().info("=== DEBUG: Team " + this.teamManager.getPlayerTeam(player.getUniqueId()).getName() + " Permissions ===");
                        for (TeamPlayer member : this.teamManager.getPlayerTeam(player.getUniqueId()).getMembers()) {
                            this.plugin.getLogger().info("Member: " + String.valueOf(member.getPlayerUuid()) + " - Role: " + String.valueOf((Object)member.getRole()) + " - canUseEnderChest: " + member.canUseEnderChest() + " - canWithdraw: " + member.canWithdraw() + " - canSetHome: " + member.canSetHome() + " - canUseHome: " + member.canUseHome());
                        }
                        this.plugin.getLogger().info("=== END DEBUG ===");
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<green>Team permissions debug info sent to console. Check server logs.", new TagResolver[0]));
                    } catch (Exception e) {
                        this.plugin.getLogger().severe("Error in debug-permissions command: " + e.getMessage());
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Error occurred while checking permissions. Check server logs.", new TagResolver[0]));
                    }
                });
                return true;
            }
            case "debug-placeholders": {
                if (!this.hasAdminPermission(player)) {
                    return false;
                }
                this.plugin.getTaskRunner().runAsync(() -> {
                    try {
                        String[] placeholders;
                        this.plugin.getLogger().info("=== DEBUG: PlaceholderAPI Test for " + player.getName() + " ===");
                        if (this.plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
                            this.plugin.getLogger().warning("PlaceholderAPI is not installed!");
                            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>PlaceholderAPI is not installed!", new TagResolver[0]));
                            return;
                        }
                        for (String placeholder : placeholders = new String[]{"justteams_has_team", "justteams_name", "justteams_tag", "justteams_description", "justteams_owner", "justteams_role", "justteams_member_count", "justteams_max_members", "justteams_members_online", "justteams_kills", "justteams_deaths", "justteams_kdr", "justteams_bank_balance", "justteams_is_owner", "justteams_is_co_owner", "justteams_is_member"}) {
                            String result = PlaceholderAPI.setPlaceholders((Player)player, (String)("%" + placeholder + "%"));
                            this.plugin.getLogger().info(placeholder + ": " + result);
                        }
                        this.plugin.getLogger().info("=== END PLACEHOLDER DEBUG ===");
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<green>PlaceholderAPI test completed. Check server logs for results.", new TagResolver[0]));
                    } catch (Exception e) {
                        this.plugin.getLogger().severe("Error in debug-placeholders command: " + e.getMessage());
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Error occurred while testing placeholders. Check server logs.", new TagResolver[0]));
                    }
                });
                return true;
            }
            default: {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "unknown_command", new TagResolver[0]);
                return false;
            }
        }
        return true;
    }

    private boolean checkCommandSpam(Player player) {
        long currentTime = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        Long lastCommand = this.commandCooldowns.get(playerId);
        if (lastCommand != null && currentTime - lastCommand < 1000L) {
            return false;
        }
        int count = this.commandCounts.getOrDefault(playerId, 0);
        if (count >= 30) {
            return false;
        }
        this.commandCooldowns.put(playerId, currentTime);
        this.commandCounts.put(playerId, count + 1);
        return true;
    }

    private boolean checkFeatureEnabled(Player player, String feature) {
        if (!this.plugin.getConfigManager().isFeatureEnabled(feature)) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "feature_disabled", new TagResolver[0]);
            return false;
        }
        return true;
    }

    private boolean validateTeamNameAndTag(String name, String tag) {
        String[] inappropriate;
        if (name == null || name.length() < this.plugin.getConfigManager().getMinNameLength() || name.length() > this.plugin.getConfigManager().getMaxNameLength()) {
            return false;
        }
        if (tag == null || tag.length() < 2 || tag.length() > this.plugin.getConfigManager().getMaxTagLength()) {
            return false;
        }
        String plainName = this.stripColorCodes(name);
        String plainTag = this.stripColorCodes(tag);
        if (!plainName.matches("^[a-zA-Z0-9_]+$")) {
            return false;
        }
        if (!plainTag.matches("^[a-zA-Z0-9_]+$")) {
            return false;
        }
        if (plainName.matches("^[0-9_]+$") || plainTag.matches("^[0-9_]+$")) {
            return false;
        }
        String[] sqlPatterns = new String[]{"--", ";", "/*", "*/", "xp_", "sp_", "union", "select", "insert", "update", "delete", "drop", "create"};
        String lowerName = plainName.toLowerCase();
        String lowerTag = plainTag.toLowerCase();
        for (String pattern : sqlPatterns) {
            if (!lowerName.contains(pattern) && !lowerTag.contains(pattern)) continue;
            this.plugin.getLogger().warning("Potential SQL injection attempt detected in team name/tag: " + name + "/" + tag);
            return false;
        }
        for (String word : inappropriate = new String[]{"admin", "mod", "staff", "owner", "server", "minecraft", "bukkit", "spigot", "console", "system", "root"}) {
            if (!lowerName.contains(word) && !lowerTag.contains(word)) continue;
            return false;
        }
        return true;
    }

    private String stripColorCodes(String text) {
        if (text == null) {
            return "";
        }
        text = text.replaceAll("(?i)&[0-9A-FK-OR]", "");
        text = text.replaceAll("(?i)<#[0-9A-F]{6}>", "");
        text = text.replaceAll("(?i)</#[0-9A-F]{6}>", "");
        text = text.replaceAll("(?i)<[^>]+>", "");
        return text.trim();
    }

    private boolean isValidPlayerName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        int minLength = this.plugin.getConfigManager().getMinNameLength();
        int maxLength = this.plugin.getConfigManager().getMaxNameLength();
        if (name.length() < minLength || name.length() > maxLength) {
            return false;
        }
        if (!name.matches("^[a-zA-Z0-9_.]+$")) {
            return false;
        }
        String lowerName = name.toLowerCase();
        if (lowerName.contains("--") || lowerName.contains(";") || lowerName.contains("'") || lowerName.contains("\"")) {
            this.plugin.getLogger().warning("Potential injection attempt in player name: " + name);
            return false;
        }
        return true;
    }

    private void handleGUI(Player player) {
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            new NoTeamGUI(this.plugin, player).open();
        } else {
            new TeamGUI(this.plugin, team, player).open();
        }
    }

    private void handleCreate(Player player, String[] args) {
        String teamTag;
        if (!this.checkFeatureEnabled(player, "team_creation")) {
            return;
        }
        boolean tagEnabled = this.plugin.getConfigManager().isTeamTagEnabled();
        if (tagEnabled && args.length < 3) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_create", new TagResolver[0]);
            return;
        }
        if (!tagEnabled && args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_create_no_tag", new TagResolver[0]);
            return;
        }
        String teamName = args[1];
        String string = teamTag = tagEnabled && args.length >= 3 ? args[2] : "";
        if (tagEnabled) {
            if (!this.validateTeamNameAndTag(teamName, teamTag)) {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name_or_tag", new TagResolver[0]);
                return;
            }
        } else {
            String[] inappropriate;
            String plainName = this.stripColorCodes(teamName);
            if (!plainName.matches("^[a-zA-Z0-9_]+$")) {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
                return;
            }
            String lowerName = plainName.toLowerCase();
            for (String word : inappropriate = new String[]{"admin", "mod", "staff", "owner", "server", "minecraft", "bukkit", "spigot"}) {
                if (!lowerName.contains(word)) continue;
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
                return;
            }
        }
        if (this.teamManager.getPlayerTeam(player.getUniqueId()) != null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "already_in_team", new TagResolver[0]);
            return;
        }
        this.teamManager.createTeam(player, teamName, teamTag);
    }

    private void handleDisband(Player player) {
        if (!this.checkFeatureEnabled(player, "team_disband")) {
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.isOwner(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "must_be_owner", new TagResolver[0]);
            return;
        }
        this.teamManager.disbandTeam(player);
    }

    private void handleInvite(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_invites")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_invite", new TagResolver[0]);
            return;
        }
        String targetName = args[1];
        if (!this.isValidPlayerName(targetName)) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_player_name", new TagResolver[0]);
            return;
        }
        if (targetName.equalsIgnoreCase(player.getName())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_invite_yourself", new TagResolver[0]);
            return;
        }
        Player target = Bukkit.getPlayer((String)targetName);
        if (target != null && target.isOnline()) {
            if (this.teamManager.getPlayerTeam(target.getUniqueId()) != null) {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_already_in_team", new TagResolver[]{Placeholder.unparsed((String)"target", (String)target.getName())});
                return;
            }
            this.teamManager.invitePlayer(player, target);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            String normalizedName;
            this.plugin.getStorageManager().getStorage().cachePlayerName(player.getUniqueId(), player.getName());
            Optional<UUID> targetUuidOpt = this.plugin.getStorageManager().getStorage().getPlayerUuidByName(targetName);
            if (targetUuidOpt.isEmpty() && !(normalizedName = this.plugin.getBedrockSupport().normalizePlayerName(targetName)).equals(targetName)) {
                targetUuidOpt = this.plugin.getStorageManager().getStorage().getPlayerUuidByName(normalizedName);
            }
            if (targetUuidOpt.isEmpty()) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached((String)targetName);
                if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
                    UUID targetUuid = offlinePlayer.getUniqueId();
                    this.plugin.getStorageManager().getStorage().cachePlayerName(targetUuid, targetName);
                    String normalizedName2 = this.plugin.getBedrockSupport().normalizePlayerName(targetName);
                    if (!normalizedName2.equals(targetName)) {
                        this.plugin.getStorageManager().getStorage().cachePlayerName(targetUuid, normalizedName2);
                    }
                    targetUuidOpt = Optional.of(targetUuid);
                } else {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_found", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)}));
                    return;
                }
            }
            UUID targetUuid = targetUuidOpt.get();
            Optional<Team> existingTeam = this.plugin.getStorageManager().getStorage().findTeamByPlayer(targetUuid);
            if (existingTeam.isPresent()) {
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_already_in_team", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)}));
                return;
            }
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.teamManager.invitePlayerByUuid(player, targetUuid, targetName));
        });
    }

    private void handleInvites(Player player) {
        if (!this.checkFeatureEnabled(player, "team_invites")) {
            return;
        }
        new InvitesGUI(this.plugin, player).open();
    }

    private void handleAccept(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_invites")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_accept", new TagResolver[0]);
            return;
        }
        String teamName = args[1];
        if (teamName == null || teamName.isEmpty() || teamName.length() < this.plugin.getConfigManager().getMinNameLength() || teamName.length() > this.plugin.getConfigManager().getMaxNameLength()) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
            return;
        }
        String plainTeamName = this.stripColorCodes(teamName);
        if (plainTeamName.isEmpty() || !plainTeamName.matches("^[a-zA-Z0-9_]+$")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
            return;
        }
        if (this.teamManager.getPlayerTeam(player.getUniqueId()) != null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "already_in_team", new TagResolver[0]);
            return;
        }
        this.teamManager.acceptInvite(player, plainTeamName);
    }

    private void handleDeny(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_invites")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_deny", new TagResolver[0]);
            return;
        }
        String teamName = args[1];
        if (teamName.length() < this.plugin.getConfigManager().getMinNameLength() || teamName.length() > this.plugin.getConfigManager().getMaxNameLength() || !teamName.matches("^[a-zA-Z0-9_]+$")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
            return;
        }
        this.teamManager.denyInvite(player, teamName);
    }

    private void handleJoin(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_join_requests")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_join", new TagResolver[0]);
            return;
        }
        String teamName = args[1];
        if (teamName == null || teamName.isEmpty() || teamName.length() < this.plugin.getConfigManager().getMinNameLength() || teamName.length() > this.plugin.getConfigManager().getMaxNameLength()) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
            return;
        }
        String plainTeamName = this.stripColorCodes(teamName);
        if (plainTeamName.isEmpty() || !plainTeamName.matches("^[a-zA-Z0-9_]+$")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
            return;
        }
        if (this.teamManager.getPlayerTeam(player.getUniqueId()) != null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "already_in_team", new TagResolver[0]);
            return;
        }
        this.teamManager.joinTeam(player, plainTeamName);
    }

    private void handleKick(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "member_kick")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_kick", new TagResolver[0]);
            return;
        }
        String targetName = args[1];
        if (!this.isValidPlayerName(targetName)) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_player_name", new TagResolver[0]);
            return;
        }
        if (targetName.equalsIgnoreCase(player.getName())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_kick_yourself", new TagResolver[0]);
            return;
        }
        Player target = Bukkit.getPlayer((String)targetName);
        if (target == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_found", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)});
            return;
        }
        Team playerTeam = this.teamManager.getPlayerTeam(player.getUniqueId());
        Team targetTeam = this.teamManager.getPlayerTeam(target.getUniqueId());
        if (playerTeam == null || targetTeam == null || playerTeam.getId() != targetTeam.getId()) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_same_team", new TagResolver[0]);
            return;
        }
        this.teamManager.kickPlayer(player, target.getUniqueId());
    }

    private void handleLeave(Player player) {
        if (!this.checkFeatureEnabled(player, "member_leave")) {
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (team.isOwner(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "owner_cannot_leave", new TagResolver[0]);
            return;
        }
        this.teamManager.leaveTeam(player);
    }

    private void handlePromote(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "member_promote")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_promote", new TagResolver[0]);
            return;
        }
        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_promote_yourself", new TagResolver[0]);
            return;
        }
        Player target = Bukkit.getPlayer((String)targetName);
        if (target == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_found", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)});
            return;
        }
        this.teamManager.promotePlayer(player, target.getUniqueId());
    }

    private void handleDemote(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "member_demote")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_demote", new TagResolver[0]);
            return;
        }
        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_demote_yourself", new TagResolver[0]);
            return;
        }
        Player target = Bukkit.getPlayer((String)targetName);
        if (target == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_found", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)});
            return;
        }
        this.teamManager.demotePlayer(player, target.getUniqueId());
    }

    private void handleInfo(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_info")) {
            return;
        }
        if (args.length > 1) {
            String teamName = args[1];
            if (teamName.length() < this.plugin.getConfigManager().getMinNameLength() || teamName.length() > this.plugin.getConfigManager().getMaxNameLength() || !teamName.matches("^[a-zA-Z0-9_]+$")) {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
                return;
            }
            Team team = this.teamManager.getAllTeams().stream().filter(t -> t.getName().equalsIgnoreCase(teamName)).findFirst().orElse(null);
            if (team == null) {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "team_not_found", new TagResolver[0]);
                return;
            }
            this.displayTeamInfo(player, team);
        } else {
            Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
                return;
            }
            this.displayTeamInfo(player, team);
        }
    }

    private void displayTeamInfo(Player player, Team team) {
        if (player == null || team == null) {
            return;
        }
        String ownerName = Bukkit.getOfflinePlayer((UUID)team.getOwnerUuid()).getName();
        String safeOwnerName = ownerName != null ? ownerName : "Unknown";
        String coOwners = team.getCoOwners().stream().map(co -> Bukkit.getOfflinePlayer((UUID)co.getPlayerUuid()).getName()).filter(Objects::nonNull).collect(Collectors.joining(", "));
        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("team_info_header"), new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
        if (this.plugin.getConfigManager().isTeamTagEnabled()) {
            this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("team_info_tag"), new TagResolver[]{Placeholder.unparsed((String)"tag", (String)team.getTag())});
        }
        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("team_info_description"), new TagResolver[]{Placeholder.unparsed((String)"description", (String)team.getDescription())});
        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("team_info_owner"), new TagResolver[]{Placeholder.unparsed((String)"owner", (String)safeOwnerName)});
        if (!coOwners.isEmpty()) {
            this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("team_info_co_owners"), new TagResolver[]{Placeholder.unparsed((String)"co_owners", (String)coOwners)});
        }
        double kdr = team.getDeaths() == 0 ? (double)team.getKills() : (double)team.getKills() / (double)team.getDeaths();
        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("team_info_stats"), new TagResolver[]{Placeholder.unparsed((String)"kills", (String)String.valueOf(team.getKills())), Placeholder.unparsed((String)"deaths", (String)String.valueOf(team.getDeaths())), Placeholder.unparsed((String)"kdr", (String)String.format("%.2f", kdr))});
        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("team_info_members"), new TagResolver[]{Placeholder.unparsed((String)"member_count", (String)String.valueOf(team.getMembers().size())), Placeholder.unparsed((String)"max_members", (String)String.valueOf(this.plugin.getConfigManager().getMaxTeamSize()))});
        for (TeamPlayer member : team.getMembers()) {
            String memberName = Bukkit.getOfflinePlayer((UUID)member.getPlayerUuid()).getName();
            String safeMemberName = memberName != null ? memberName : "Unknown";
            this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("team_info_member_list"), new TagResolver[]{Placeholder.unparsed((String)"player", (String)safeMemberName)});
        }
        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("team_info_footer"), new TagResolver[0]);
    }

    private void handleSetHome(Player player) {
        if (!this.checkFeatureEnabled(player, "team_home_set")) {
            return;
        }
        if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "sethome")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "feature_disabled_in_world", new TagResolver[]{Placeholder.unparsed((String)"feature", (String)"sethome"), Placeholder.unparsed((String)"world", (String)player.getWorld().getName())});
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        if (!this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "sethome")) {
            return;
        }
        this.teamManager.setTeamHome(player);
    }

    private void handleDelHome(Player player) {
        if (!this.checkFeatureEnabled(player, "team_home_set")) {
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        this.teamManager.deleteTeamHome(player);
    }

    private void handleHome(Player player) {
        if (!this.checkFeatureEnabled(player, "team_home_teleport")) {
            return;
        }
        if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "home")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "feature_disabled_in_world", new TagResolver[]{Placeholder.unparsed((String)"feature", (String)"home"), Placeholder.unparsed((String)"world", (String)player.getWorld().getName())});
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "home")) {
            return;
        }
        this.teamManager.teleportToHome(player);
    }

    private void handleSetTag(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_tag")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_settag", new TagResolver[0]);
            return;
        }
        String tag = args[1];
        String plainTag = this.stripColorCodes(tag);
        if (plainTag.length() < 2 || plainTag.length() > this.plugin.getConfigManager().getMaxTagLength() || !plainTag.matches("^[a-zA-Z0-9_]+$")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_tag", new TagResolver[0]);
            return;
        }
        this.teamManager.setTeamTag(player, tag);
    }

    private void handleSetDescription(Player player, String[] args) {
        String[] inappropriate;
        if (!this.checkFeatureEnabled(player, "team_description")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_setdesc", new TagResolver[0]);
            return;
        }
        String description = String.join((CharSequence)" ", args).substring(args[0].length() + 1);
        if (description.length() > this.plugin.getConfigManager().getMaxDescriptionLength()) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "description_too_long", new TagResolver[0]);
            return;
        }
        String lowerDesc = description.toLowerCase();
        for (String word : inappropriate = new String[]{"admin", "mod", "staff", "owner", "server", "minecraft", "bukkit", "spigot"}) {
            if (!lowerDesc.contains(word)) continue;
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "inappropriate_description", new TagResolver[0]);
            return;
        }
        this.teamManager.setTeamDescription(player, description);
    }

    private void handleRename(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_rename")) {
            return;
        }
        if (!player.hasPermission("justteams.rename") && !player.hasPermission("justteams.admin")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "rename_usage", new TagResolver[0]);
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.isOwner(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "must_be_owner", new TagResolver[0]);
            return;
        }
        String newName = args[1];
        String oldName = team.getName();
        if (newName.equalsIgnoreCase(oldName)) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "rename_same_name", new TagResolver[0]);
            return;
        }
        String plainName = this.stripColorCodes(newName);
        int minLength = this.plugin.getConfigManager().getMinNameLength();
        int maxLength = this.plugin.getConfigManager().getMaxNameLength();
        if (plainName.length() < minLength) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "name_too_short", new TagResolver[]{Placeholder.unparsed((String)"min_length", (String)String.valueOf(minLength))});
            return;
        }
        if (plainName.length() > maxLength) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "name_too_long", new TagResolver[]{Placeholder.unparsed((String)"max_length", (String)String.valueOf(maxLength))});
            return;
        }
        if (!plainName.matches("^[a-zA-Z0-9_]+$")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
            return;
        }
        if (this.teamManager.getTeamByName(newName) != null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "team_name_exists", new TagResolver[]{Placeholder.unparsed((String)"team", (String)newName)});
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            long secondsSinceRename;
            long remaining;
            Optional<Timestamp> lastRename = this.plugin.getStorageManager().getStorage().getTeamRenameTimestamp(team.getId());
            long cooldownSeconds = this.plugin.getConfig().getLong("settings.rename_cooldown", 604800L);
            if (lastRename.isPresent() && cooldownSeconds > 0L && (remaining = cooldownSeconds - (secondsSinceRename = (System.currentTimeMillis() - lastRename.get().getTime()) / 1000L)) > 0L) {
                String timeLeft = this.formatTime(remaining);
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "rename_cooldown", new TagResolver[]{Placeholder.unparsed((String)"time", (String)timeLeft)}));
                return;
            }
            double cost = this.plugin.getConfig().getDouble("feature_costs.economy.rename", 500.0);
            boolean economyEnabled = this.plugin.getConfig().getBoolean("feature_costs.economy.enabled", true);
            if (economyEnabled && cost > 0.0 && this.plugin.getEconomy() != null) {
                double balance = this.plugin.getEconomy().getBalance((OfflinePlayer)player);
                if (balance < cost) {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "rename_too_expensive", new TagResolver[]{Placeholder.unparsed((String)"cost", (String)String.format("%.2f", cost)), Placeholder.unparsed((String)"balance", (String)String.format("%.2f", balance))}));
                    return;
                }
                this.plugin.getEconomy().withdrawPlayer((OfflinePlayer)player, cost);
            }
            team.setName(newName);
            this.plugin.getStorageManager().getStorage().setTeamName(team.getId(), newName);
            this.plugin.getStorageManager().getStorage().setTeamRenameTimestamp(team.getId(), new Timestamp(System.currentTimeMillis()));
            this.plugin.getWebhookHelper().sendTeamRenameWebhook(player.getName(), oldName, newName);
            this.plugin.getTaskRunner().runAsync(() -> {
                if (this.plugin.getRedisManager() != null && this.plugin.getRedisManager().isAvailable()) {
                    this.plugin.getRedisManager().publishTeamUpdate(team.getId(), "TEAM_RENAMED", player.getUniqueId().toString(), oldName + "|" + newName);
                }
                this.plugin.getStorageManager().getStorage().addCrossServerUpdate(team.getId(), "TEAM_RENAMED", player.getUniqueId().toString(), "ALL_SERVERS");
            });
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "rename_success", new TagResolver[]{Placeholder.unparsed((String)"old_name", (String)oldName), Placeholder.unparsed((String)"new_name", (String)newName)});
                team.broadcast("rename_broadcast", new TagResolver[]{Placeholder.unparsed((String)"old_name", (String)oldName), Placeholder.unparsed((String)"new_name", (String)newName)});
            });
        });
    }

    private String formatTime(long seconds) {
        if (seconds < 60L) {
            return seconds + " second" + (seconds != 1L ? "s" : "");
        }
        if (seconds < 3600L) {
            long minutes = seconds / 60L;
            return minutes + " minute" + (minutes != 1L ? "s" : "");
        }
        if (seconds < 86400L) {
            long hours = seconds / 3600L;
            return hours + " hour" + (hours != 1L ? "s" : "");
        }
        long days = seconds / 86400L;
        return days + " day" + (days != 1L ? "s" : "");
    }

    private void handleTransfer(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_transfer")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_transfer", new TagResolver[0]);
            return;
        }
        String targetName = args[1];
        if (targetName.equalsIgnoreCase(player.getName())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_transfer_to_yourself", new TagResolver[0]);
            return;
        }
        Player target = Bukkit.getPlayer((String)targetName);
        if (target == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_found", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)});
            return;
        }
        Team playerTeam = this.teamManager.getPlayerTeam(player.getUniqueId());
        Team targetTeam = this.teamManager.getPlayerTeam(target.getUniqueId());
        if (playerTeam == null || targetTeam == null || playerTeam.getId() != targetTeam.getId()) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_same_team", new TagResolver[0]);
            return;
        }
        this.teamManager.transferOwnership(player, target.getUniqueId());
    }

    private void handlePvpToggle(Player player) {
        if (!this.checkFeatureEnabled(player, "team_pvp_toggle")) {
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        this.teamManager.togglePvp(player);
    }

    private void handleBank(Player player, String[] args) {
        block11: {
            if (!this.checkFeatureEnabled(player, "team_bank")) {
                return;
            }
            if (args.length < 2) {
                Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
                    return;
                }
                new TeamGUI(this.plugin, team, player).open();
                return;
            }
            String action = args[1].toLowerCase();
            if (action.equals("deposit") || action.equals("withdraw")) {
                if (args.length < 3) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_bank", new TagResolver[0]);
                    return;
                }
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (amount <= 0.0) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "bank_invalid_amount", new TagResolver[0]);
                        return;
                    }
                    if (amount > 1.0E9) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "bank_amount_too_large", new TagResolver[0]);
                        return;
                    }
                    if (action.equals("deposit")) {
                        this.teamManager.deposit(player, amount);
                        break block11;
                    }
                    this.teamManager.withdraw(player, amount);
                } catch (NumberFormatException e) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "bank_invalid_amount", new TagResolver[0]);
                }
            } else {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_bank", new TagResolver[0]);
            }
        }
    }

    private void handleEnderChest(Player player) {
        if (!this.checkFeatureEnabled(player, "team_enderchest")) {
            return;
        }
        if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "enderchest")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "feature_disabled_in_world", new TagResolver[]{Placeholder.unparsed((String)"feature", (String)"enderchest"), Placeholder.unparsed((String)"world", (String)player.getWorld().getName())});
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "enderchest")) {
            return;
        }
        this.teamManager.openEnderChest(player);
    }

    private void handlePublicToggle(Player player) {
        if (!this.checkFeatureEnabled(player, "team_public_toggle")) {
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        this.teamManager.togglePublicStatus(player);
    }

    private void handleRequests(Player player) {
        if (!this.checkFeatureEnabled(player, "team_join_requests")) {
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        new JoinRequestGUI(this.plugin, player, team).open();
    }

    private void handleSetWarp(Player player, String[] args) {
        String password;
        if (!this.checkFeatureEnabled(player, "team_warp_set")) {
            return;
        }
        if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "setwarp")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "feature_disabled_in_world", new TagResolver[]{Placeholder.unparsed((String)"feature", (String)"setwarp"), Placeholder.unparsed((String)"world", (String)player.getWorld().getName())});
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_setwarp", new TagResolver[0]);
            return;
        }
        String warpName = args[1];
        if (warpName.length() < 2 || warpName.length() > this.plugin.getConfigManager().getMaxNameLength() || !warpName.matches("^[a-zA-Z0-9_]+$")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_warp_name", new TagResolver[0]);
            return;
        }
        String string = password = args.length > 2 ? args[2] : null;
        if (password != null && (password.length() < 3 || password.length() > 20)) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_warp_password", new TagResolver[0]);
            return;
        }
        if (!this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "setwarp")) {
            return;
        }
        this.teamManager.setTeamWarp(player, warpName, password);
    }

    private void handleDelWarp(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_warp_delete")) {
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_delwarp", new TagResolver[0]);
            return;
        }
        String warpName = args[1];
        if (warpName.length() < 2 || warpName.length() > this.plugin.getConfigManager().getMaxNameLength() || !warpName.matches("^[a-zA-Z0-9_]+$")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_warp_name", new TagResolver[0]);
            return;
        }
        this.teamManager.deleteTeamWarp(player, warpName);
    }

    private void handleWarp(Player player, String[] args) {
        String password;
        if (!this.checkFeatureEnabled(player, "team_warp_teleport")) {
            return;
        }
        if (!this.plugin.getFeatureRestrictionManager().isFeatureAllowed(player, "warp")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "feature_disabled_in_world", new TagResolver[]{Placeholder.unparsed((String)"feature", (String)"warp"), Placeholder.unparsed((String)"world", (String)player.getWorld().getName())});
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_warp", new TagResolver[0]);
            return;
        }
        String warpName = args[1];
        if (warpName.length() < 2 || warpName.length() > this.plugin.getConfigManager().getMaxNameLength() || !warpName.matches("^[a-zA-Z0-9_]+$")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_warp_name", new TagResolver[0]);
            return;
        }
        String string = password = args.length > 2 ? args[2] : null;
        if (!this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "warp")) {
            return;
        }
        this.teamManager.teleportToTeamWarp(player, warpName, password);
    }

    private void handleWarps(Player player) {
        if (!this.checkFeatureEnabled(player, "team_warps")) {
            return;
        }
        try {
            Class.forName("eu.kotori.justTeams.gui.WarpsGUI");
            this.teamManager.openWarpsGUI(player);
        } catch (ClassNotFoundException e) {
            this.teamManager.listTeamWarps(player);
        }
    }

    private void handleChat(Player player) {
        if (!this.checkFeatureEnabled(player, "team_chat")) {
            return;
        }
        this.plugin.getTeamChatListener().toggleTeamChat(player);
    }

    private void handleHelp(Player player) {
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_header", new TagResolver[0]);
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"gui"), Placeholder.unparsed((String)"description", (String)"Opens the team GUI.")});
        if (this.plugin.getConfigManager().isTeamTagEnabled()) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"create <name> <tag>"), Placeholder.unparsed((String)"description", (String)"Creates a team.")});
        } else {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"create <name>"), Placeholder.unparsed((String)"description", (String)"Creates a team.")});
        }
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"disband"), Placeholder.unparsed((String)"description", (String)"Disbands your team.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"invite <player>"), Placeholder.unparsed((String)"description", (String)"Invites a player.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"join <teamName>"), Placeholder.unparsed((String)"description", (String)"Joins a public team.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"unjoin <teamName>"), Placeholder.unparsed((String)"description", (String)"Cancels a join request to a team.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"kick <player>"), Placeholder.unparsed((String)"description", (String)"Kicks a player.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"leave"), Placeholder.unparsed((String)"description", (String)"Leaves your current team.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"promote <player>"), Placeholder.unparsed((String)"description", (String)"Promotes a member to Co-Owner.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"demote <player>"), Placeholder.unparsed((String)"description", (String)"Demotes a Co-Owner to Member.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"info [team]"), Placeholder.unparsed((String)"description", (String)"Shows team info.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"sethome"), Placeholder.unparsed((String)"description", (String)"Sets the team home.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"home"), Placeholder.unparsed((String)"description", (String)"Teleports to the team home.")});
        if (this.plugin.getConfigManager().isTeamTagEnabled()) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"settag <tag>"), Placeholder.unparsed((String)"description", (String)"Changes the team tag.")});
        }
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"setdesc <description>"), Placeholder.unparsed((String)"description", (String)"Changes the team description.")});
        if (this.plugin.getConfig().getBoolean("features.team_rename", true)) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"rename <newName>"), Placeholder.unparsed((String)"description", (String)"Renames the team (cooldown applies).")});
        }
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"transfer <player>"), Placeholder.unparsed((String)"description", (String)"Transfers ownership.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"pvp"), Placeholder.unparsed((String)"description", (String)"Toggles team PvP.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"bank [deposit|withdraw] [amount]"), Placeholder.unparsed((String)"description", (String)"Manages the team bank.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"enderchest"), Placeholder.unparsed((String)"description", (String)"Opens the team ender chest.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"public"), Placeholder.unparsed((String)"description", (String)"Toggles public join status.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"requests"), Placeholder.unparsed((String)"description", (String)"View join requests.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"setwarp <name> [password]"), Placeholder.unparsed((String)"description", (String)"Sets a team warp.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"delwarp <name>"), Placeholder.unparsed((String)"description", (String)"Deletes a team warp.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"warp <name> [password]"), Placeholder.unparsed((String)"description", (String)"Teleports to a team warp.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"warps"), Placeholder.unparsed((String)"description", (String)"Lists all team warps.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"top"), Placeholder.unparsed((String)"description", (String)"Shows team leaderboards.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"blacklist <player> [reason]"), Placeholder.unparsed((String)"description", (String)"Blacklists a player from your team.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"unblacklist <player>"), Placeholder.unparsed((String)"description", (String)"Unblacklists a player from your team.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"admin disband <teamName>"), Placeholder.unparsed((String)"description", (String)"Admin command to disband a team.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"platform"), Placeholder.unparsed((String)"description", (String)"Shows your platform information (Java/Bedrock).")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"debug-permissions"), Placeholder.unparsed((String)"description", (String)"Debugs the current permissions of your team.")});
        this.plugin.getMessageManager().sendMessage((CommandSender)player, "help_format", new TagResolver[]{Placeholder.unparsed((String)"command", (String)"debug-placeholders"), Placeholder.unparsed((String)"description", (String)"Tests all PlaceholderAPI placeholders for your team.")});
    }

    private void handleReload(Player player) {
        if (!this.hasAdminPermission(player)) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
            return;
        }
        try {
            this.plugin.getLogger().info("Reloading JustTeams configuration...");
            this.plugin.getConfigManager().reloadConfig();
            this.plugin.getMessageManager().reload();
            this.plugin.getGuiConfigManager().reload();
            this.plugin.getCommandManager().reload();
            this.plugin.getAliasManager().reload();
            this.plugin.getGuiConfigManager().testPlaceholders();
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "reload", new TagResolver[0]);
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "reload_commands_notice", new TagResolver[0]);
            this.plugin.getLogger().info("JustTeams configuration reloaded successfully!");
        } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
            player.sendMessage("\u00a7cFailed to reload configuration. Check console for details.");
        }
    }

    private void handleBlacklist(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_blacklist")) {
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        if (args.length == 1) {
            new BlacklistGUI(this.plugin, team, player).open();
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_blacklist", new TagResolver[0]);
            return;
        }
        String targetName = args[1];
        Player target = Bukkit.getPlayer((String)targetName);
        if (target == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_found", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)});
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_blacklist_self", new TagResolver[0]);
            return;
        }
        if (team.isMember(target.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_blacklist_team_member", new TagResolver[0]);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                if (this.plugin.getStorageManager().getStorage().isPlayerBlacklisted(team.getId(), target.getUniqueId())) {
                    List<BlacklistedPlayer> blacklist = this.plugin.getStorageManager().getStorage().getTeamBlacklist(team.getId());
                    BlacklistedPlayer blacklistedPlayer = blacklist.stream().filter(bp -> bp.getPlayerUuid().equals(target.getUniqueId())).findFirst().orElse(null);
                    String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_already_blacklisted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)target.getName()), Placeholder.unparsed((String)"blacklister", (String)blacklisterName)}));
                    return;
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Could not check if player is already blacklisted: " + e.getMessage());
            }
        });
        String reason = args.length > 2 ? String.join((CharSequence)" ", Arrays.copyOfRange(args, 2, args.length)) : "No reason specified";
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                boolean success = this.plugin.getStorageManager().getStorage().addPlayerToBlacklist(team.getId(), target.getUniqueId(), target.getName(), reason, player.getUniqueId(), player.getName());
                if (success) {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                        try {
                            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_blacklisted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)target.getName())});
                        } catch (Exception e) {
                            this.plugin.getLogger().severe("Error sending blacklist success message: " + e.getMessage());
                        }
                    });
                } else {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                        try {
                            this.plugin.getMessageManager().sendMessage((CommandSender)player, "blacklist_failed", new TagResolver[0]);
                        } catch (Exception e) {
                            this.plugin.getLogger().severe("Error sending blacklist failed message: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                this.plugin.getLogger().severe("Error adding player to blacklist: " + e.getMessage());
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                    try {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "blacklist_failed", new TagResolver[0]);
                    } catch (Exception e2) {
                        this.plugin.getLogger().severe("Error sending blacklist error message: " + e2.getMessage());
                    }
                });
            }
        });
    }

    private void handleUnblacklist(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_blacklist")) {
            return;
        }
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_unblacklist", new TagResolver[0]);
            return;
        }
        String targetName = args[1];
        Player target = Bukkit.getPlayer((String)targetName);
        if (target == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_found", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)});
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_unblacklist_self", new TagResolver[0]);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                if (!this.plugin.getStorageManager().getStorage().isPlayerBlacklisted(team.getId(), target.getUniqueId())) {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_blacklisted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)target.getName())}));
                    return;
                }
                boolean success = this.plugin.getStorageManager().getStorage().removePlayerFromBlacklist(team.getId(), target.getUniqueId());
                if (success) {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                        try {
                            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_unblacklisted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)target.getName())});
                        } catch (Exception e) {
                            this.plugin.getLogger().severe("Error sending unblacklist success message: " + e.getMessage());
                        }
                    });
                } else {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                        try {
                            this.plugin.getMessageManager().sendMessage((CommandSender)player, "unblacklist_failed", new TagResolver[0]);
                        } catch (Exception e) {
                            this.plugin.getLogger().severe("Error sending unblacklist failed message: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                this.plugin.getLogger().severe("Error removing player from blacklist: " + e.getMessage());
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                    try {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "unblacklist_failed", new TagResolver[0]);
                    } catch (Exception e2) {
                        this.plugin.getLogger().severe("Error sending unblacklist error message: " + e2.getMessage());
                    }
                });
            }
        });
    }

    private void handleSettings(Player player) {
        Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "settings_permission_denied", new TagResolver[0]);
            return;
        }
        new TeamSettingsGUI(this.plugin, player, team).open();
    }

    private void handleTop(Player player, String[] args) {
        if (!this.checkFeatureEnabled(player, "team_leaderboard")) {
            return;
        }
        try {
            Class.forName("eu.kotori.justTeams.gui.LeaderboardCategoryGUI");
            new LeaderboardCategoryGUI(this.plugin, player).open();
        } catch (ClassNotFoundException e) {
            this.plugin.getTaskRunner().runAsync(() -> {
                try {
                    Map<Integer, Team> topTeams = this.plugin.getStorageManager().getStorage().getTopTeamsByKills(10);
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "leaderboard_header", new TagResolver[0]);
                        for (Map.Entry entry : topTeams.entrySet()) {
                            Team team = (Team)entry.getValue();
                            this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("leaderboard_entry"), new TagResolver[]{Placeholder.unparsed((String)"rank", (String)String.valueOf(entry.getKey())), Placeholder.unparsed((String)"team", (String)team.getName()), Placeholder.unparsed((String)"score", (String)String.valueOf(team.getKills()))});
                        }
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "leaderboard_footer", new TagResolver[0]);
                    });
                } catch (Exception ex) {
                    this.plugin.getLogger().severe("Error loading top teams: " + ex.getMessage());
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "error_loading_leaderboard", new TagResolver[0]));
                }
            });
        }
    }

    private void handleUnjoin(Player player, String[] args) {
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_unjoin", new TagResolver[0]);
            return;
        }
        String teamName = args[1];
        if (teamName.length() < this.plugin.getConfigManager().getMinNameLength() || teamName.length() > this.plugin.getConfigManager().getMaxNameLength() || !teamName.matches("^[a-zA-Z0-9_]+$")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
            return;
        }
        if (this.teamManager.getPlayerTeam(player.getUniqueId()) != null) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "already_in_team", new TagResolver[0]);
            return;
        }
        this.teamManager.withdrawJoinRequest(player, teamName);
    }

    private void handleAdmin(Player player, String[] args) {
        String action;
        if (!this.hasAdminPermission(player)) {
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("gui")) {
            new AdminGUI(this.plugin, player).open();
            return;
        }
        switch (action = args[1].toLowerCase()) {
            case "disband": {
                if (args.length < 3) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_admin_disband", new TagResolver[0]);
                    return;
                }
                String teamName = args[2];
                this.teamManager.adminDisbandTeam(player, teamName);
                break;
            }
            case "enderchest": {
                if (args.length < 3) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_admin_enderchest", new TagResolver[0]);
                    return;
                }
                String teamName = args[2];
                this.teamManager.adminOpenEnderChest(player, teamName);
                break;
            }
            case "testmigration": {
                this.handleTestMigration(player, args);
                break;
            }
            case "performance": {
                this.handlePerformance(player, args);
                break;
            }
            default: {
                player.sendMessage("\u00a7cUsage: /team admin <gui|disband|testmigration|enderchest|performance> [args]");
            }
        }
    }

    private void handleServerAlias(Player player, String[] args) {
        String action;
        if (!this.hasAdminPermission(player)) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
            return;
        }
        if (args.length < 2) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_serveralias", new TagResolver[0]);
            return;
        }
        switch (action = args[1].toLowerCase()) {
            case "set": {
                if (args.length < 4) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_serveralias", new TagResolver[0]);
                    return;
                }
                String serverName = args[2];
                String alias = String.join((CharSequence)" ", Arrays.copyOfRange(args, 3, args.length));
                if (alias.length() > 64) {
                    player.sendMessage("\u00a7cServer alias too long! Maximum 64 characters.");
                    return;
                }
                this.plugin.getTaskRunner().runAsync(() -> {
                    this.plugin.getStorageManager().getStorage().setServerAlias(serverName, alias);
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "serveralias_set", new TagResolver[]{Placeholder.unparsed((String)"server", (String)serverName), Placeholder.unparsed((String)"alias", (String)alias)}));
                });
                break;
            }
            case "remove": {
                if (args.length < 3) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_serveralias", new TagResolver[0]);
                    return;
                }
                String serverName = args[2];
                this.plugin.getTaskRunner().runAsync(() -> {
                    this.plugin.getStorageManager().getStorage().removeServerAlias(serverName);
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "serveralias_removed", new TagResolver[]{Placeholder.unparsed((String)"server", (String)serverName)}));
                });
                break;
            }
            case "list": {
                this.plugin.getTaskRunner().runAsync(() -> {
                    Map<String, String> aliases = this.plugin.getStorageManager().getStorage().getAllServerAliases();
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                        if (aliases.isEmpty()) {
                            player.sendMessage("\u00a7eNo server aliases configured.");
                            return;
                        }
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "serveralias_list_header", new TagResolver[0]);
                        for (Map.Entry entry : aliases.entrySet()) {
                            this.plugin.getMessageManager().sendMessage((CommandSender)player, "serveralias_list_entry", new TagResolver[]{Placeholder.unparsed((String)"server", (String)((String)entry.getKey())), Placeholder.unparsed((String)"alias", (String)((String)entry.getValue()))});
                        }
                    });
                });
                break;
            }
            default: {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "usage_serveralias", new TagResolver[0]);
            }
        }
    }

    private boolean hasAdminPermission(Player player) {
        return player.isOp() || player.hasPermission("*") || player.hasPermission("justteams.admin");
    }

    private void handleTestMigration(Player player, String[] args) {
        block30: {
            if (args.length == 2) {
                player.sendMessage("\u00a7eTesting database migration system...");
                try {
                    DatabaseFileManager fileManager = new DatabaseFileManager(this.plugin);
                    boolean fileMigrationResult = fileManager.migrateOldDatabaseFiles();
                    player.sendMessage("\u00a7aFile migration result: " + (fileMigrationResult ? "SUCCESS" : "FAILED"));
                    boolean backupResult = fileManager.backupDatabase();
                    player.sendMessage("\u00a7aBackup creation result: " + (backupResult ? "SUCCESS" : "FAILED"));
                    boolean validationResult = fileManager.validateDatabaseFiles();
                    player.sendMessage("\u00a7aFile validation result: " + (validationResult ? "SUCCESS" : "FAILED"));
                    DatabaseMigrationManager migrationManager = new DatabaseMigrationManager(this.plugin, (DatabaseStorage)this.plugin.getStorageManager().getStorage());
                    boolean migrationResult = migrationManager.performMigration();
                    player.sendMessage("\u00a7aSchema migration result: " + (migrationResult ? "SUCCESS" : "FAILED"));
                    boolean configHealthy = ConfigUpdater.isConfigurationSystemHealthy(this.plugin);
                    player.sendMessage("\u00a7aConfiguration system health: " + (configHealthy ? "HEALTHY" : "UNHEALTHY"));
                    if (fileMigrationResult && migrationResult && configHealthy) {
                        player.sendMessage("\u00a7aAll migration tests passed! Database and configuration should be working correctly.");
                        break block30;
                    }
                    player.sendMessage("\u00a7cSome migration tests failed. Check the console for details.");
                } catch (Exception e) {
                    player.sendMessage("\u00a7cMigration test failed with exception: " + e.getMessage());
                    this.plugin.getLogger().severe("Migration test failed: " + e.getMessage());
                }
            } else {
                String action = args[2].toLowerCase();
                try {
                    DatabaseFileManager fileManager = new DatabaseFileManager(this.plugin);
                    DatabaseMigrationManager migrationManager = new DatabaseMigrationManager(this.plugin, (DatabaseStorage)this.plugin.getStorageManager().getStorage());
                    switch (action) {
                        case "test": {
                            player.sendMessage("\u00a7eRunning full migration test...");
                            boolean fileResult = fileManager.migrateOldDatabaseFiles();
                            boolean backupResult = fileManager.backupDatabase();
                            boolean validationResult = fileManager.validateDatabaseFiles();
                            boolean migrationResult = migrationManager.performMigration();
                            player.sendMessage("\u00a7aFile migration: " + (fileResult ? "SUCCESS" : "FAILED"));
                            player.sendMessage("\u00a7aBackup creation: " + (backupResult ? "SUCCESS" : "FAILED"));
                            player.sendMessage("\u00a7aFile validation: " + (validationResult ? "SUCCESS" : "FAILED"));
                            player.sendMessage("\u00a7aSchema migration: " + (migrationResult ? "SUCCESS" : "FAILED"));
                            break;
                        }
                        case "migrate": {
                            player.sendMessage("\u00a7eRunning database migration...");
                            boolean migrateResult = migrationManager.performMigration();
                            player.sendMessage("\u00a7aMigration result: " + (migrateResult ? "SUCCESS" : "FAILED"));
                            break;
                        }
                        case "validate": {
                            player.sendMessage("\u00a7eValidating database files...");
                            boolean validateResult = fileManager.validateDatabaseFiles();
                            player.sendMessage("\u00a7aValidation result: " + (validateResult ? "SUCCESS" : "FAILED"));
                            break;
                        }
                        case "backup": {
                            player.sendMessage("\u00a7eCreating database backup...");
                            boolean backupResult2 = fileManager.backupDatabase();
                            player.sendMessage("\u00a7aBackup result: " + (backupResult2 ? "SUCCESS" : "FAILED"));
                            break;
                        }
                        case "config": {
                            player.sendMessage("\u00a7eTesting configuration system...");
                            ConfigUpdater.testConfigurationSystem(this.plugin);
                            boolean configHealthy = ConfigUpdater.isConfigurationSystemHealthy(this.plugin);
                            player.sendMessage("\u00a7aConfiguration system health: " + (configHealthy ? "HEALTHY" : "UNHEALTHY"));
                            break;
                        }
                        case "update-config": {
                            player.sendMessage("\u00a7eUpdating configuration files...");
                            ConfigUpdater.updateAllConfigs(this.plugin);
                            player.sendMessage("\u00a7aConfiguration update completed! Check console for details.");
                            break;
                        }
                        case "force-update-config": {
                            player.sendMessage("\u00a7eForce updating all configuration files...");
                            ConfigUpdater.forceUpdateAllConfigs(this.plugin);
                            player.sendMessage("\u00a7aForce update completed! Check console for details.");
                            break;
                        }
                        case "backup-config": {
                            player.sendMessage("\u00a7eCreating configuration backups...");
                            for (String configFile : List.of("config.yml", "messages.yml", "gui.yml", "commands.yml")) {
                                ConfigUpdater.createConfigBackup(this.plugin, configFile);
                            }
                            player.sendMessage("\u00a7aConfiguration backups created! Check backups folder.");
                            break;
                        }
                        case "cleanup-backups": {
                            player.sendMessage("\u00a7eCleaning up old backup files...");
                            ConfigUpdater.cleanupAllOldBackups(this.plugin);
                            player.sendMessage("\u00a7aBackup cleanup completed! Check console for details.");
                            break;
                        }
                        default: {
                            player.sendMessage("\u00a7cUnknown action: " + action);
                            player.sendMessage("\u00a77Available actions: test, migrate, validate, backup, config, update-config, force-update-config, backup-config, cleanup-backups");
                            break;
                        }
                    }
                } catch (Exception e) {
                    player.sendMessage("\u00a7cCommand failed with exception: " + e.getMessage());
                    this.plugin.getLogger().severe("TestMigrationCommand failed: " + e.getMessage());
                }
            }
        }
    }

    private void handlePerformance(Player player, String[] args) {
        String action;
        if (!player.hasPermission("justteams.admin.performance")) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
            return;
        }
        if (args.length < 3) {
            this.showPerformanceHelp(player);
            return;
        }
        switch (action = args[2].toLowerCase()) {
            case "database": {
                this.showDatabaseStats(player);
                break;
            }
            case "cache": {
                this.showCacheStats(player);
                break;
            }
            case "tasks": {
                this.showTaskStats(player);
                break;
            }
            case "optimize": {
                this.optimizeDatabase(player);
                break;
            }
            case "cleanup": {
                this.cleanupCaches(player);
                break;
            }
            default: {
                this.showPerformanceHelp(player);
            }
        }
    }

    private void showPerformanceHelp(Player player) {
        player.sendMessage("\u00a76=== JustTeams Performance Commands ===");
        player.sendMessage("\u00a7e/team admin performance database \u00a77- Show database statistics");
        player.sendMessage("\u00a7e/team admin performance cache \u00a77- Show cache statistics");
        player.sendMessage("\u00a7e/team admin performance tasks \u00a77- Show task statistics");
        player.sendMessage("\u00a7e/team admin performance optimize \u00a77- Optimize database");
        player.sendMessage("\u00a7e/team admin performance cleanup \u00a77- Cleanup caches");
    }

    private void showDatabaseStats(Player player) {
        player.sendMessage("\u00a76=== Database Statistics ===");
        IDataStorage iDataStorage = this.plugin.getStorageManager().getStorage();
        if (iDataStorage instanceof DatabaseStorage) {
            DatabaseStorage dbStorage = (DatabaseStorage)iDataStorage;
            try {
                Map<String, Object> stats = dbStorage.getDatabaseStats();
                stats.forEach((key, value) -> player.sendMessage("\u00a7e" + key + ": \u00a7f" + String.valueOf(value)));
            } catch (Exception e) {
                player.sendMessage("\u00a7cError retrieving database stats: " + e.getMessage());
            }
        } else {
            player.sendMessage("\u00a7cDatabase storage not in use");
        }
    }

    private void showCacheStats(Player player) {
        player.sendMessage("\u00a76=== Cache Statistics ===");
        try {
            if (this.plugin.getTeamManager() != null) {
                player.sendMessage("\u00a7eTeam Cache: \u00a7f" + this.plugin.getTeamManager().getTeamNameCache().size() + " teams");
                player.sendMessage("\u00a7ePlayer Cache: \u00a7f" + this.plugin.getTeamManager().getPlayerTeamCache().size() + " players");
            }
            player.sendMessage("\u00a7eGUI Update Throttle: \u00a7aActive");
            player.sendMessage("\u00a7eTask Runner: \u00a7f" + this.plugin.getTaskRunner().getActiveTaskCount() + " active tasks");
        } catch (Exception e) {
            player.sendMessage("\u00a7cError retrieving cache statistics: " + e.getMessage());
        }
    }

    private void showTaskStats(Player player) {
        player.sendMessage("\u00a76=== Task Statistics ===");
        player.sendMessage("\u00a7eActive Tasks: \u00a7f" + this.plugin.getTaskRunner().getActiveTaskCount());
        player.sendMessage("\u00a7eFolia Support: \u00a7f" + (this.plugin.getTaskRunner().isFolia() ? "Enabled" : "Disabled"));
        player.sendMessage("\u00a7ePaper Support: \u00a7f" + (this.plugin.getTaskRunner().isPaper() ? "Enabled" : "Disabled"));
    }

    private void optimizeDatabase(Player player) {
        player.sendMessage("\u00a7eOptimizing database...");
        try {
            IDataStorage iDataStorage = this.plugin.getStorageManager().getStorage();
            if (iDataStorage instanceof DatabaseStorage) {
                DatabaseStorage dbStorage = (DatabaseStorage)iDataStorage;
                dbStorage.optimizeDatabase();
                player.sendMessage("\u00a7aDatabase optimization completed!");
            } else {
                player.sendMessage("\u00a7cDatabase optimization not available for current storage type");
            }
        } catch (Exception e) {
            player.sendMessage("\u00a7cDatabase optimization failed: " + e.getMessage());
        }
    }

    private void cleanupCaches(Player player) {
        player.sendMessage("\u00a7eCleaning up caches...");
        try {
            if (this.plugin.getTeamManager() != null) {
                this.plugin.getTeamManager().getTeamNameCache().clear();
                this.plugin.getTeamManager().getPlayerTeamCache().clear();
            }
            player.sendMessage("\u00a7aCache cleanup completed!");
        } catch (Exception e) {
            player.sendMessage("\u00a7cCache cleanup failed: " + e.getMessage());
        }
    }

    private void handlePlatform(Player player) {
        if (!this.plugin.getConfigManager().isBedrockSupportEnabled()) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "feature_disabled", new TagResolver[0]);
            return;
        }
        boolean isBedrock = this.plugin.getBedrockSupport().isBedrockPlayer(player);
        String platform = isBedrock ? "Bedrock Edition" : "Java Edition";
        String platformColor = isBedrock ? "<#00D4FF>" : "<#00FF00>";
        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<white>Your Platform: " + platformColor + platform + "</white>", new TagResolver[0]);
        if (isBedrock) {
            UUID javaUuid;
            String gamertag = this.plugin.getBedrockSupport().getBedrockGamertag(player);
            if (gamertag != null && !gamertag.equals(player.getName())) {
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<gray>Xbox Gamertag: <white>" + gamertag + "</white>", new TagResolver[0]);
            }
            if (!(javaUuid = this.plugin.getBedrockSupport().getJavaEditionUuid(player)).equals(player.getUniqueId())) {
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<gray>Java Edition UUID: <white>" + javaUuid.toString() + "</white>", new TagResolver[0]);
            }
        }
        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<gray>Current UUID: <white>" + player.getUniqueId().toString() + "</white>", new TagResolver[0]);
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String subCommand;
        if (!(sender instanceof Player)) {
            return new ArrayList<String>();
        }
        Player player = (Player)sender;
        if (args.length == 1) {
            ArrayList<String> completions = new ArrayList<String>();
            completions.add("accept");
            completions.add("create");
            completions.add("deny");
            completions.add("disband");
            completions.add("invite");
            completions.add("invites");
            completions.add("join");
            completions.add("unjoin");
            completions.add("kick");
            completions.add("leave");
            completions.add("promote");
            completions.add("demote");
            completions.add("info");
            completions.add("sethome");
            completions.add("delhome");
            completions.add("home");
            if (this.plugin.getConfigManager().isTeamTagEnabled()) {
                completions.add("settag");
            }
            completions.add("setdesc");
            if (this.plugin.getConfig().getBoolean("features.team_rename", true)) {
                completions.add("rename");
            }
            completions.add("transfer");
            completions.add("pvp");
            completions.add("bank");
            completions.add("blacklist");
            completions.add("unblacklist");
            completions.add("settings");
            completions.add("enderchest");
            completions.add("public");
            completions.add("requests");
            completions.add("setwarp");
            completions.add("delwarp");
            completions.add("warp");
            completions.add("warps");
            completions.add("top");
            completions.add("admin");
            completions.add("platform");
            completions.add("reload");
            completions.add("chat");
            completions.add("help");
            return completions.stream().filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            switch (subCommand = args[0].toLowerCase()) {
                case "accept": 
                case "deny": {
                    return this.teamManager.getPendingInvites(player.getUniqueId()).stream().map(Team::getName).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
                case "invite": {
                    Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
                    if (team == null) break;
                    return Bukkit.getOnlinePlayers().stream().filter(target -> !team.isMember(target.getUniqueId()) && this.teamManager.getPlayerTeam(target.getUniqueId()) == null).map(Player::getName).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
                case "kick": 
                case "promote": 
                case "demote": 
                case "transfer": {
                    Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
                    if (team == null) break;
                    return team.getMembers().stream().map(member -> Bukkit.getOfflinePlayer((UUID)member.getPlayerUuid()).getName()).filter(name -> name != null && name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
                case "join": {
                    return this.teamManager.getAllTeams().stream().map(Team::getName).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
                case "info": {
                    return this.teamManager.getAllTeams().stream().map(Team::getName).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
                case "setwarp": 
                case "delwarp": 
                case "warp": {
                    Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
                    if (team == null) break;
                    List<IDataStorage.TeamWarp> warps = this.plugin.getStorageManager().getStorage().getWarps(team.getId());
                    return warps.stream().map(IDataStorage.TeamWarp::name).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
                case "blacklist": {
                    Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
                    if (team == null) break;
                    try {
                        List<BlacklistedPlayer> blacklist = this.plugin.getStorageManager().getStorage().getTeamBlacklist(team.getId());
                        return blacklist.stream().map(BlacklistedPlayer::getPlayerName).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    } catch (Exception e) {
                        this.plugin.getLogger().warning("Could not get blacklist for tab completion: " + e.getMessage());
                        return new ArrayList<String>();
                    }
                }
                case "unblacklist": {
                    Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
                    if (team == null) break;
                    try {
                        List<BlacklistedPlayer> blacklist = this.plugin.getStorageManager().getStorage().getTeamBlacklist(team.getId());
                        return blacklist.stream().map(BlacklistedPlayer::getPlayerName).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                    } catch (Exception e) {
                        this.plugin.getLogger().warning("Could not get blacklist for tab completion: " + e.getMessage());
                        return new ArrayList<String>();
                    }
                }
                case "admin": {
                    if (!this.hasAdminPermission(player)) break;
                    return List.of("disband", "testmigration", "enderchest", "performance").stream().filter(cmd -> cmd.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
                case "serveralias": {
                    if (!this.hasAdminPermission(player)) break;
                    return List.of("set", "remove", "list").stream().filter(cmd -> cmd.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
            }
        }
        if (args.length == 3 && (subCommand = args[0].toLowerCase()).equals("admin") && this.hasAdminPermission(player)) {
            if (args[1].toLowerCase().equals("disband")) {
                return this.teamManager.getAllTeams().stream().map(Team::getName).filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (args[1].toLowerCase().equals("enderchest")) {
                return this.teamManager.getAllTeams().stream().map(Team::getName).filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (args[1].toLowerCase().equals("testmigration")) {
                return List.of("test", "migrate", "validate", "backup", "config", "update-config", "force-update-config", "backup-config", "cleanup-backups").stream().filter(cmd -> cmd.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (args[1].toLowerCase().equals("performance")) {
                return List.of("database", "cache", "tasks", "optimize", "cleanup").stream().filter(cmd -> cmd.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
        }
        return new ArrayList<String>();
    }
}

