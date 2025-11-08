package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.storage.IDataStorage;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class TeamGUI
implements IRefreshableGUI,
InventoryHolder {
    private final JustTeams plugin;
    private final Team team;
    private final Inventory inventory;
    private final Player viewer;
    private Team.SortType currentSort;

    public TeamGUI(JustTeams plugin, Team team, Player viewer) {
        this.plugin = plugin;
        this.team = team;
        this.viewer = viewer;
        this.currentSort = team.getCurrentSortType();
        GuiConfigManager guiManager = plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("team-gui");
        String title = guiConfig.getString("title", "Team").replace("<members>", String.valueOf(team.getMembers().size())).replace("<max_members>", String.valueOf(plugin.getConfigManager().getMaxTeamSize()));
        int size = guiConfig.getInt("size", 54);
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (Component)plugin.getMiniMessage().deserialize(title));
        this.initializeItems();
    }

    public void initializeItems() {
        block26: {
            try {
                TeamPlayer viewerMember;
                int i;
                this.inventory.clear();
                GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
                if (guiManager == null) {
                    this.plugin.getLogger().severe("GUI Config Manager not available!");
                    return;
                }
                ConfigurationSection guiConfig = guiManager.getGUI("team-gui");
                if (guiConfig == null) {
                    this.plugin.getLogger().warning("Team GUI configuration not found!");
                    return;
                }
                ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
                if (itemsConfig == null) {
                    this.plugin.getLogger().warning("Team GUI items configuration not found!");
                    return;
                }
                ItemStack border = new ItemBuilder(guiManager.getMaterial("team-gui.fill-item.material", Material.GRAY_STAINED_GLASS_PANE)).withName(guiManager.getString("team-gui.fill-item.name", " ")).build();
                for (i = 0; i < 9; ++i) {
                    this.inventory.setItem(i, border);
                }
                for (i = 45; i < 54; ++i) {
                    this.inventory.setItem(i, border);
                }
                int memberSlot = 9;
                List<TeamPlayer> snapshotMembers = new ArrayList<>(this.team.getSortedMembers(this.currentSort));
                ConfigurationSection headConfig = itemsConfig.getConfigurationSection("player-head");
                this.plugin.getTaskRunner().runAsync(() -> {
                    List<MemberDisplayData> memberData = this.fetchMemberDisplayData(snapshotMembers);
                    this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> this.populateMemberHeads(snapshotMembers, memberData, headConfig));
                });
                if ((viewerMember = this.team.getMember(this.viewer.getUniqueId())) == null) {
                    this.viewer.closeInventory();
                    return;
                }
                if (this.plugin.getConfigManager().isTeamJoinRequestsEnabled() && this.team.hasElevatedPermissions(this.viewer.getUniqueId())) {
                    this.setItemFromConfig(itemsConfig, "join-requests");
                } else {
                    this.setItemFromConfig(itemsConfig, "join-requests-locked");
                }
                this.setItemFromConfig(itemsConfig, "sort");
                if (this.plugin.getConfigManager().isTeamPvpToggleEnabled()) {
                    this.setItemFromConfig(itemsConfig, "pvp-toggle");
                } else {
                    this.setItemFromConfig(itemsConfig, "pvp-toggle-locked");
                }
                if (this.team.hasElevatedPermissions(this.viewer.getUniqueId())) {
                    this.setItemFromConfig(itemsConfig, "team-settings-button");
                }
                if (this.plugin.getConfigManager().isTeamDisbandEnabled() && this.team.isOwner(this.viewer.getUniqueId())) {
                    this.setItemFromConfig(itemsConfig, "disband-button");
                } else if (this.plugin.getConfigManager().isMemberLeaveEnabled()) {
                    this.setItemFromConfig(itemsConfig, "leave-button");
                }
                if (this.plugin.getConfigManager().isTeamBankEnabled()) {
                    this.setItemFromConfig(itemsConfig, "bank");
                } else {
                    this.setItemFromConfig(itemsConfig, "bank-locked");
                }
                if (this.plugin.getConfigManager().isTeamEnderchestEnabled()) {
                    String itemKey;
                    boolean hasAccess;
                    boolean bl = hasAccess = this.viewer.hasPermission("justteams.bypass.enderchest.use") || viewerMember.canUseEnderChest();
                    if (this.plugin.getConfigManager().isDebugEnabled()) {
                        this.plugin.getDebugLogger().log("Setting enderchest item for " + this.viewer.getName() + " - hasAccess: " + hasAccess + ", canUseEnderChest: " + viewerMember.canUseEnderChest());
                    }
                    String string = itemKey = hasAccess ? "ender-chest" : "ender-chest-locked";
                    if (this.plugin.getConfigManager().isDebugEnabled()) {
                        this.plugin.getDebugLogger().log("Player " + this.viewer.getName() + " will see enderchest item: " + itemKey + " (hasAccess: " + hasAccess + ")");
                        this.plugin.getDebugLogger().log("DEBUG: " + this.viewer.getName() + " - viewerMember UUID: " + String.valueOf(viewerMember.getPlayerUuid()) + ", canUseEnderChest: " + viewerMember.canUseEnderChest() + ", hasBypass: " + this.viewer.hasPermission("justteams.bypass.enderchest.use") + ", team: " + this.team.getName() + ", teamId: " + this.team.getId());
                    }
                    this.setItemFromConfig(itemsConfig, itemKey);
                } else {
                    if (this.plugin.getConfigManager().isDebugEnabled()) {
                        this.plugin.getDebugLogger().log("Enderchest disabled in config for " + this.viewer.getName());
                    }
                    this.setItemFromConfig(itemsConfig, "ender-chest-locked");
                }
                if (this.plugin.getConfigManager().isTeamWarpsEnabled()) {
                    this.setItemFromConfig(itemsConfig, "warps");
                } else {
                    this.setItemFromConfig(itemsConfig, "warps-locked");
                }
                this.setHomeItemAsync(itemsConfig);
            } catch (Exception e) {
                this.plugin.getLogger().severe("Error initializing Team GUI items: " + e.getMessage());
                if (!this.plugin.getConfigManager().isDebugEnabled()) break block26;
                this.plugin.getLogger().severe("Error in TeamGUI: " + e.getMessage());
            }
        }
    }

    private void setItemFromConfig(ConfigurationSection itemsConfig, String key) {
        ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(key);
        if (itemConfig == null) {
            return;
        }
        int slot = itemConfig.getInt("slot", -1);
        if (slot == -1) {
            return;
        }
        Material material = Material.matchMaterial((String)itemConfig.getString("material", "STONE"));
        ItemBuilder builder = new ItemBuilder(material);
        String name = this.replacePlaceholders(itemConfig.getString("name", ""));
        builder.withName(name);
        ArrayList<String> lore = new ArrayList<>(itemConfig.getStringList("lore"));
        builder.withLore(lore.stream().map(this::replacePlaceholders).collect(Collectors.toList()));
        String action = itemConfig.getString("action", key);
        builder.withAction(action);
        this.inventory.setItem(slot, builder.build());
    }

    private void setHomeItemAsync(ConfigurationSection itemsConfig) {
        ConfigurationSection itemConfig = itemsConfig.getConfigurationSection("home");
        if (itemConfig == null) {
            return;
        }
        int slot = itemConfig.getInt("slot", -1);
        if (slot == -1) {
            return;
        }
        Material homeMaterial = Material.matchMaterial((String)itemConfig.getString("material", "ENDER_PEARL"));
        ItemStack loadingItem = new ItemBuilder(homeMaterial).withName("<gray>Loading Home Status...").build();
        this.inventory.setItem(slot, loadingItem);
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<IDataStorage.TeamHome> teamHomeOpt = this.plugin.getStorageManager().getStorage().getTeamHome(this.team.getId());
            this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> {
                ItemBuilder builder = new ItemBuilder(homeMaterial);
                String name = this.replacePlaceholders(itemConfig.getString("name", ""));
                builder.withName(name);
                List<String> lore = itemConfig.getStringList(teamHomeOpt.isPresent() ? "lore-set" : "lore-not-set");
                builder.withLore(lore.stream().map(this::replacePlaceholders).collect(Collectors.toList()));
                builder.withAction("home");
                this.inventory.setItem(slot, builder.build());
            });
        });
    }

    private String replacePlaceholders(String text) {
        if (text == null) {
            return "";
        }
        String pvpStatus = this.team.isPvpEnabled() ? this.plugin.getGuiConfigManager().getString("team-gui.items.pvp-toggle.status-on", "<green>ON") : this.plugin.getGuiConfigManager().getString("team-gui.items.pvp-toggle.status-off", "<red>OFF");
        String pvpPrompt = this.team.hasElevatedPermissions(this.viewer.getUniqueId()) ? this.plugin.getGuiConfigManager().getString("team-gui.items.pvp-toggle.can-toggle-prompt", "<yellow>Click to toggle") : this.plugin.getGuiConfigManager().getString("team-gui.items.pvp-toggle.cannot-toggle-prompt", "<red>Permission denied");
        String currencyFormat = this.plugin.getConfigManager().getCurrencyFormat();
        DecimalFormat formatter = new DecimalFormat(currencyFormat);
        return text.replace("<balance>", formatter.format(this.team.getBalance())).replace("<status>", pvpStatus).replace("<permission_prompt>", pvpPrompt).replace("<sort_status_join_date>", this.getSortLore(Team.SortType.JOIN_DATE)).replace("<sort_status_alphabetical>", this.getSortLore(Team.SortType.ALPHABETICAL)).replace("<sort_status_online_status>", this.getSortLore(Team.SortType.ONLINE_STATUS));
    }

    private ItemStack createMemberHead(MemberDisplayData data, ConfigurationSection headConfig) {
        boolean isBedrockPlayer = data.isBedrockPlayer;
        String platformIndicator = "";
        if (this.plugin.getGuiConfigManager().getPlaceholder("platform.show_in_gui", "true").equals("true")) {
            if (isBedrockPlayer && this.plugin.getGuiConfigManager().getPlaceholder("platform.bedrock.enabled", "true").equals("true")) {
                platformIndicator = this.plugin.getGuiConfigManager().getPlaceholder("platform.bedrock.format", " <#00D4FF>[BE]</#00D4FF>");
            } else if (!isBedrockPlayer && this.plugin.getGuiConfigManager().getPlaceholder("platform.java.enabled", "true").equals("true")) {
                platformIndicator = this.plugin.getGuiConfigManager().getPlaceholder("platform.java.format", " <#00FF00>[JE]</#00FF00>");
            }
        }
        String crossServerStatus = "";
        String currentServer = this.plugin.getConfigManager().getServerIdentifier();
        if (data.isOnline && this.plugin.getConfigManager().isCrossServerSyncEnabled() && this.plugin.getConfigManager().getBoolean("features.show_cross_server_status", true) && data.serverName != null && !currentServer.equalsIgnoreCase(data.serverName)) {
            crossServerStatus = " <gray>(<yellow>" + data.serverName + "</yellow>)</gray>";
        }
        String onlineFmt = "<green><player>";
        String offlineFmt = "<gray><player>";
        List<String> defaultLore = List.of("<gray>Role:</gray> <role>", "<gray>Joined:</gray> <joindate>", "<gray>Server:</gray> <server>");
        if (headConfig != null) {
            onlineFmt = headConfig.getString("online-name-format", onlineFmt);
            offlineFmt = headConfig.getString("offline-name-format", offlineFmt);
            defaultLore = headConfig.getStringList("lore");
        }
        String nameFormat = data.isOnline ? onlineFmt : offlineFmt;
        String name = nameFormat
                .replace("<status_indicator>", this.getStatusIndicator(data.isOnline))
                .replace("<role_icon>", this.getRoleIcon(data.role))
                .replace("<player>", data.playerName != null ? data.playerName : "Unknown")
                + platformIndicator + crossServerStatus;
        String joinDateStr = this.formatJoinDate(data.joinDate, data.playerName);
        String serverInfo = data.isOnline && this.plugin.getConfigManager().isCrossServerSyncEnabled() && this.plugin.getConfigManager().getBoolean("features.show_cross_server_status", true)
                ? (data.serverName != null ? (currentServer.equalsIgnoreCase(data.serverName) ? currentServer : data.serverName) : "Local")
                : (!data.isOnline ? "<dark_gray>Offline</dark_gray>" : "Local");
        ArrayList<String> loreLines = new ArrayList<>(defaultLore.stream()
                .map(line -> line.replace("<role>", this.getRoleName(data.role))
                        .replace("<joindate>", joinDateStr)
                        .replace("<server>", serverInfo))
                .collect(Collectors.toList()));
        if (isBedrockPlayer && this.plugin.getGuiConfigManager().getPlaceholder("platform.show_gamertags", "true").equals("true") && data.gamertag != null && !data.gamertag.equals(data.playerName)) {
            String gamertagColor = this.plugin.getGuiConfigManager().getPlaceholder("platform.bedrock.color", "#00D4FF");
            loreLines.add("<gray>Gamertag: <" + gamertagColor + ">" + data.gamertag + "</" + gamertagColor + ">");
        }
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD).asPlayerHead(data.playerUuid).withName(name).withLore(loreLines);
        TeamPlayer viewerMember = this.team.getMember(this.viewer.getUniqueId());
        if (viewerMember != null) {
            boolean canEdit = false;
            boolean isSelfClick = data.playerUuid.equals(this.viewer.getUniqueId());
            if (viewerMember.getRole() == TeamRole.OWNER) {
                canEdit = !isSelfClick;
            } else if (viewerMember.getRole() == TeamRole.CO_OWNER) {
                canEdit = !isSelfClick && data.role == TeamRole.MEMBER;
            }
            if (canEdit) {
                builder.withAction("player-head");
            }
        }
        if (data.role == TeamRole.OWNER) {
            builder.withGlow();
        }
        return builder.build();
    }

    private String formatJoinDate(Instant joinDate, String playerName) {
        try {
            if (joinDate != null) {
                String dateFormat = this.plugin.getGuiConfigManager().getPlaceholder("date_time.join_date_format", "dd MMM yyyy");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC);
                return formatter.format(joinDate);
            }
            return this.plugin.getGuiConfigManager().getPlaceholder("date_time.unknown_date", "Unknown");
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error formatting join date for " + playerName + ": " + e.getMessage());
            return this.plugin.getGuiConfigManager().getPlaceholder("date_time.unknown_date", "Unknown");
        }
    }

    private String getSortLore(Team.SortType type) {
        String sortTypeKey = type.name().toLowerCase();
        String name = this.plugin.getGuiConfigManager().getSortName(sortTypeKey);
        String icon = this.plugin.getGuiConfigManager().getSortIcon(sortTypeKey);
        String prefix = this.currentSort == type ? this.plugin.getGuiConfigManager().getSortSelectedPrefix() : this.plugin.getGuiConfigManager().getSortUnselectedPrefix();
        return prefix + icon + name;
    }

    private String getRoleIcon(TeamRole role) {
        return this.plugin.getGuiConfigManager().getRoleIcon(role.name());
    }

    private String getStatusIndicator(boolean isOnline) {
        String icon = this.plugin.getGuiConfigManager().getStatusIcon(isOnline);
        String color = this.plugin.getGuiConfigManager().getStatusColor(isOnline);
        return "<" + color + ">" + icon + " </" + color + ">";
    }

    private String getRoleName(TeamRole role) {
        return this.plugin.getGuiConfigManager().getRoleName(role.name());
    }

    @Override
    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    @Override
    public void refresh() {
        this.open();
    }

    public void cycleSort() {
        this.currentSort = switch (this.currentSort) {
            case Team.SortType.JOIN_DATE -> Team.SortType.ALPHABETICAL;
            case Team.SortType.ALPHABETICAL -> Team.SortType.ONLINE_STATUS;
            case Team.SortType.ONLINE_STATUS -> Team.SortType.JOIN_DATE;
            default -> throw new IllegalStateException("Unknown sort type: " + this.currentSort);
        };
        this.team.setSortType(this.currentSort);
        this.initializeItems();
    }

    private List<MemberDisplayData> fetchMemberDisplayData(List<TeamPlayer> members) {
        ArrayList<MemberDisplayData> list = new ArrayList<>();
        try {
            Map<UUID, IDataStorage.PlayerSession> sessions = this.plugin.getStorageManager().getStorage().getTeamPlayerSessions(this.team.getId());
            this.plugin.getCacheManager().cacheTeamSessions(this.team.getId(), sessions);
            Map<String, String> aliases = this.plugin.getStorageManager().getStorage().getAllServerAliases();
            String currentServer = this.plugin.getConfigManager().getServerIdentifier();
            for (TeamPlayer member : members) {
                UUID id = member.getPlayerUuid();
                String name = this.plugin.getCacheManager().getPlayerName(id);
                if (name == null) {
                    // Fallback to DB then UUID string; avoid Bukkit API off-main thread
                    Optional<String> dbName = this.plugin.getStorageManager().getStorage().getPlayerNameByUuid(id);
                    if (dbName.isPresent()) {
                        name = dbName.get();
                        this.plugin.getCacheManager().cachePlayerName(id, name);
                    } else {
                        name = id.toString();
                        this.plugin.getCacheManager().cachePlayerName(id, name);
                    }
                }
                boolean isBedrock = this.plugin.getBedrockSupport() != null && this.plugin.getBedrockSupport().isBedrockPlayer(id);
                String gamertag = null;
                if (isBedrock) {
                    gamertag = this.plugin.getBedrockSupport().getBedrockGamertag(id);
                }
                String serverName = null;
                IDataStorage.PlayerSession session = sessions.get(id);
                if (session != null) {
                    String raw = session.serverName();
                    String alias = aliases.getOrDefault(raw, raw);
                    serverName = currentServer.equalsIgnoreCase(raw) ? currentServer : alias;
                }
                boolean isOnline = sessions.containsKey(id);
                list.add(new MemberDisplayData(id, name, isOnline, member.getRole(), member.getJoinDate(), serverName, isBedrock, gamertag));
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to prefetch member data: " + e.getMessage());
        }
        return list;
    }
    private void populateMemberHeads(List<TeamPlayer> orderedMembers, List<MemberDisplayData> data, ConfigurationSection headConfig) {
        Map<UUID, MemberDisplayData> byId = new HashMap<>();
        for (MemberDisplayData d : data) byId.put(d.playerUuid, d);
        int memberSlot = 9;
        if (this.currentSort == Team.SortType.ALPHABETICAL) {
            List<MemberDisplayData> sorted = new ArrayList<>(data);
            sorted.sort((a, b) -> {
                String an = a.playerName != null ? a.playerName.toLowerCase() : "";
                String bn = b.playerName != null ? b.playerName.toLowerCase() : "";
                return an.compareTo(bn);
            });
            for (MemberDisplayData d : sorted) {
                if (memberSlot >= 45) break;
                this.inventory.setItem(memberSlot++, this.createMemberHead(d, headConfig));
            }
            return;
        }
        for (TeamPlayer member : orderedMembers) {
            if (memberSlot >= 45) break;
            MemberDisplayData d = byId.getOrDefault(member.getPlayerUuid(), new MemberDisplayData(member.getPlayerUuid(), "Loading...", data.stream().anyMatch(md -> md.playerUuid.equals(member.getPlayerUuid()) && md.isOnline), member.getRole(), member.getJoinDate(), null, false, null));
            this.inventory.setItem(memberSlot++, this.createMemberHead(d, headConfig));
        }
    }

    public Team getTeam() {
        return this.team;
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    static class MemberDisplayData {
        final UUID playerUuid;
        final String playerName;
        final boolean isOnline;
        final TeamRole role;
        final Instant joinDate;
        final String serverName;
        final boolean isBedrockPlayer;
        final String gamertag;
        MemberDisplayData(UUID playerUuid, String playerName, boolean isOnline, TeamRole role, Instant joinDate, String serverName, boolean isBedrockPlayer, String gamertag) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.isOnline = isOnline;
            this.role = role;
            this.joinDate = joinDate;
            this.serverName = serverName;
            this.isBedrockPlayer = isBedrockPlayer;
            this.gamertag = gamertag;
        }
    }
}

