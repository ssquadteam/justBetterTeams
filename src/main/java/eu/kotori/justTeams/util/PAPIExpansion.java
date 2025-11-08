package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PAPIExpansion
extends PlaceholderExpansion {
    private final JustTeams plugin;
    private final DecimalFormat kdrFormat = new DecimalFormat("#.##");

    public PAPIExpansion(JustTeams plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public String getIdentifier() {
        return "justteams";
    }

    @NotNull
    public String getAuthor() {
        return (String)this.plugin.getDescription().getAuthors().get(0);
    }

    @NotNull
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        try {
            if (params.equalsIgnoreCase("has_team")) {
                return this.plugin.getTeamManager().getPlayerTeam(player.getUniqueId()) != null ? "yes" : "no";
            }
            Team team = this.plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
            if (params.equalsIgnoreCase("display")) {
                if (team == null) {
                    return this.plugin.getGuiConfigManager().getPlaceholder("team_display.no_team", "<gray>No Team</gray>");
                }
                String format = this.plugin.getGuiConfigManager().getPlaceholder("team_display.format", "<team_color><team_icon><team_tag></team_color>");
                String teamIcon = this.plugin.getGuiConfigManager().getPlaceholder("team_display.team_icon", "\u2694 ");
                String teamColor = this.plugin.getGuiConfigManager().getPlaceholder("team_display.team_color", "#4C9DDE");
                String tagPrefix = this.plugin.getGuiConfigManager().getPlaceholder("team_display.tag_prefix", "[");
                String tagSuffix = this.plugin.getGuiConfigManager().getPlaceholder("team_display.tag_suffix", "]");
                String tagColor = this.plugin.getGuiConfigManager().getPlaceholder("team_display.tag_color", "#FFD700");
                boolean showIcon = this.plugin.getGuiConfigManager().getPlaceholder("team_display.show_icon", "true").equals("true");
                boolean showTag = this.plugin.getGuiConfigManager().getPlaceholder("team_display.show_tag", "true").equals("true") && this.plugin.getConfigManager().isTeamTagEnabled();
                boolean showName = this.plugin.getGuiConfigManager().getPlaceholder("team_display.show_name", "false").equals("true");
                String result = format;
                result = result.replace("%team_name%", team.getColoredName());
                result = result.replace("%team_tag%", (CharSequence)(showTag ? tagPrefix + team.getColoredTag() + tagSuffix : ""));
                result = result.replace("%team_color%", teamColor);
                result = result.replace("%team_icon%", showIcon ? teamIcon : "");
                if (showTag) {
                    result = result.replace(tagPrefix + team.getColoredTag() + tagSuffix, "<" + tagColor + ">" + tagPrefix + team.getColoredTag() + tagSuffix + "</" + tagColor + ">");
                }
                return result;
            }
            if (team == null) {
                return this.plugin.getMessageManager().getRawMessage("no_team_placeholder");
            }
            switch (params.toLowerCase()) {
                case "name": 
                case "team_name": {
                    return team.getName();
                }
                case "tag": 
                case "team_tag": {
                    return this.plugin.getConfigManager().isTeamTagEnabled() ? team.getTag() : "";
                }
                case "color_name": {
                    return team.getColoredName();
                }
                case "color_tag": {
                    return this.plugin.getConfigManager().isTeamTagEnabled() ? team.getColoredTag() : "";
                }
                case "description": 
                case "team_description": {
                    return team.getDescription();
                }
                case "owner": 
                case "team_owner": {
                    return this.plugin.getServer().getOfflinePlayer(team.getOwnerUuid()).getName();
                }
                case "team_id": {
                    return String.valueOf(team.getId());
                }
                case "member_count": 
                case "team_members": {
                    return String.valueOf(team.getMembers().size());
                }
                case "max_members": 
                case "team_max_members": {
                    return String.valueOf(this.plugin.getConfigManager().getMaxTeamSize());
                }
                case "members_online": {
                    return String.valueOf(team.getMembers().stream().filter(p -> p.isOnline()).count());
                }
                case "role": {
                    TeamPlayer member = team.getMember(player.getUniqueId());
                    return member != null ? member.getRole().name() : "Unknown";
                }
                case "role_level": {
                    TeamPlayer memberForLevel = team.getMember(player.getUniqueId());
                    if (memberForLevel == null) {
                        return "0";
                    }
                    switch (memberForLevel.getRole()) {
                        case OWNER: {
                            return "4";
                        }
                        case CO_OWNER: {
                            return "3";
                        }
                        case MEMBER: {
                            return "1";
                        }
                    }
                    return "0";
                }
                case "is_owner": {
                    return team.getOwnerUuid().equals(player.getUniqueId()) ? "true" : "false";
                }
                case "is_admin": {
                    TeamPlayer adminCheck = team.getMember(player.getUniqueId());
                    if (adminCheck == null) {
                        return "false";
                    }
                    return adminCheck.getRole() == TeamRole.OWNER || adminCheck.getRole() == TeamRole.CO_OWNER ? "true" : "false";
                }
                case "is_co_owner": {
                    TeamPlayer memberRole = team.getMember(player.getUniqueId());
                    return memberRole != null && memberRole.getRole().name().equals("CO_OWNER") ? "yes" : "no";
                }
                case "is_member": {
                    return team.getMember(player.getUniqueId()) != null ? "yes" : "no";
                }
                case "kills": {
                    return String.valueOf(team.getKills());
                }
                case "deaths": {
                    return String.valueOf(team.getDeaths());
                }
                case "kd": 
                case "kdr": {
                    if (team.getDeaths() == 0) {
                        return String.valueOf(team.getKills());
                    }
                    return this.kdrFormat.format((double)team.getKills() / (double)team.getDeaths());
                }
                case "bank_balance": {
                    DecimalFormat formatter = new DecimalFormat(this.plugin.getConfigManager().getCurrencyFormat());
                    return formatter.format(team.getBalance());
                }
                case "bank_formatted": {
                    DecimalFormat formatterWithSymbol = new DecimalFormat(this.plugin.getConfigManager().getCurrencyFormat());
                    return "$" + formatterWithSymbol.format(team.getBalance());
                }
                case "bank_balance_raw": {
                    return String.valueOf(team.getBalance());
                }
                case "team_public": {
                    return team.isPublic() ? "Public" : "Private";
                }
                case "team_pvp": {
                    return team.isPvpEnabled() ? "Enabled" : "Disabled";
                }
                case "pvp_enabled": {
                    return team.isPvpEnabled() ? "true" : "false";
                }
                case "is_public": {
                    return team.isPublic() ? "true" : "false";
                }
                case "has_home": {
                    return team.getHomeLocation() != null ? "true" : "false";
                }
                case "home_location": {
                    if (team.getHomeLocation() == null) {
                        return "No home set";
                    }
                    Location loc = team.getHomeLocation();
                    return loc.getWorld().getName() + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
                }
                case "warp_count": {
                    return "0";
                }
                case "max_warps": {
                    return String.valueOf(this.plugin.getConfigManager().getMaxWarpsPerTeam());
                }
                case "team_size": {
                    return String.valueOf(team.getMembers().size());
                }
                case "team_capacity": {
                    return String.valueOf(this.plugin.getConfigManager().getMaxTeamSize());
                }
                case "team_full": {
                    return team.getMembers().size() >= this.plugin.getConfigManager().getMaxTeamSize() ? "yes" : "no";
                }
                case "plain_name": {
                    return team.getPlainName();
                }
                case "plain_tag": {
                    return this.plugin.getConfigManager().isTeamTagEnabled() ? team.getPlainTag() : "";
                }
                case "join_date": {
                    TeamPlayer memberInfo = team.getMember(player.getUniqueId());
                    if (memberInfo != null) {
                        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
                        return memberInfo.getJoinDate().atZone(ZoneId.systemDefault()).format(dateFormatter);
                    }
                    return "Unknown";
                }
                case "created_at": {
                    return "Unknown";
                }
            }
            return null;
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error processing PlaceholderAPI request: " + params + " for player: " + player.getName() + " - " + e.getMessage());
            return "";
        }
    }
}

