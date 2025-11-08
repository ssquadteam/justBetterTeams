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
import java.util.List;
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
                for (TeamPlayer member : this.team.getSortedMembers(this.currentSort)) {
                    if (memberSlot >= 45) break;
                    this.inventory.setItem(memberSlot++, this.createMemberHead(member, itemsConfig.getConfigurationSection("player-head")));
                }
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

    private ItemStack createMemberHead(TeamPlayer member, ConfigurationSection headConfig) {
        String gamertag;
        String displayServer;
        String playerServer;
        String currentServer;
        Optional<IDataStorage.PlayerSession> sessionOpt;
        String playerServer2;
        String currentServer2;
        Optional<IDataStorage.PlayerSession> sessionOpt2;
        String playerName = Bukkit.getOfflinePlayer((UUID)member.getPlayerUuid()).getName();
        boolean isBedrockPlayer = this.plugin.getBedrockSupport() != null && this.plugin.getBedrockSupport().isBedrockPlayer(member.getPlayerUuid());
        String platformIndicator = "";
        if (this.plugin.getGuiConfigManager().getPlaceholder("platform.show_in_gui", "true").equals("true")) {
            if (isBedrockPlayer && this.plugin.getGuiConfigManager().getPlaceholder("platform.bedrock.enabled", "true").equals("true")) {
                platformIndicator = this.plugin.getGuiConfigManager().getPlaceholder("platform.bedrock.format", " <#00D4FF>[BE]</#00D4FF>");
            } else if (!isBedrockPlayer && this.plugin.getGuiConfigManager().getPlaceholder("platform.java.enabled", "true").equals("true")) {
                platformIndicator = this.plugin.getGuiConfigManager().getPlaceholder("platform.java.format", " <#00FF00>[JE]</#00FF00>");
            }
        }
        Object crossServerStatus = "";
        if (member.isOnline() && this.plugin.getConfigManager().isCrossServerSyncEnabled() && this.plugin.getConfigManager().getBoolean("features.show_cross_server_status", true) && (sessionOpt2 = this.plugin.getStorageManager().getStorage().getPlayerSession(member.getPlayerUuid())).isPresent() && !(currentServer2 = this.plugin.getConfigManager().getServerIdentifier()).equalsIgnoreCase(playerServer2 = sessionOpt2.get().serverName())) {
            String displayServer2 = this.plugin.getStorageManager().getStorage().getServerAlias(playerServer2).orElse(playerServer2);
            crossServerStatus = " <gray>(<yellow>" + displayServer2 + "</yellow>)</gray>";
        }
        String nameFormat = member.isOnline() ? headConfig.getString("online-name-format", "<green><player>") : headConfig.getString("offline-name-format", "<gray><player>");
        String name = nameFormat.replace("<status_indicator>", this.getStatusIndicator(member.isOnline())).replace("<role_icon>", this.getRoleIcon(member.getRole())).replace("<player>", playerName != null ? playerName : "Unknown") + platformIndicator + (String)crossServerStatus;
        String joinDateStr = this.formatJoinDate(member.getJoinDate(), playerName);
        String serverInfo = member.isOnline() && this.plugin.getConfigManager().isCrossServerSyncEnabled() && this.plugin.getConfigManager().getBoolean("features.show_cross_server_status", true) ? ((sessionOpt = this.plugin.getStorageManager().getStorage().getPlayerSession(member.getPlayerUuid())).isPresent() ? (!(currentServer = this.plugin.getConfigManager().getServerIdentifier()).equalsIgnoreCase(playerServer = sessionOpt.get().serverName()) ? (displayServer = this.plugin.getStorageManager().getStorage().getServerAlias(playerServer).orElse(playerServer)) : currentServer) : "Local") : (!member.isOnline() ? "<dark_gray>Offline</dark_gray>" : "Local");
        ArrayList<String> loreLines = new ArrayList<String>(headConfig.getStringList("lore").stream().map(line -> line.replace("<role>", this.getRoleName(member.getRole())).replace("<joindate>", joinDateStr).replace("<server>", serverInfo)).collect(Collectors.toList()));
        if (isBedrockPlayer && this.plugin.getGuiConfigManager().getPlaceholder("platform.show_gamertags", "true").equals("true") && (gamertag = this.plugin.getBedrockSupport().getBedrockGamertag(member.getPlayerUuid())) != null && !gamertag.equals(playerName)) {
            String gamertagColor = this.plugin.getGuiConfigManager().getPlaceholder("platform.bedrock.color", "#00D4FF");
            loreLines.add("<gray>Gamertag: <" + gamertagColor + ">" + gamertag + "</" + gamertagColor + ">");
        }
        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD).asPlayerHead(member.getPlayerUuid()).withName(name).withLore(loreLines);
        TeamPlayer viewerMember = this.team.getMember(this.viewer.getUniqueId());
        if (viewerMember != null) {
            boolean canEdit = false;
            boolean isSelfClick = member.getPlayerUuid().equals(this.viewer.getUniqueId());
            if (viewerMember.getRole() == TeamRole.OWNER) {
                canEdit = !isSelfClick;
            } else if (viewerMember.getRole() == TeamRole.CO_OWNER) {
                boolean bl = canEdit = !isSelfClick && member.getRole() == TeamRole.MEMBER;
            }
            if (canEdit) {
                builder.withAction("player-head");
            }
        }
        if (member.getRole() == TeamRole.OWNER) {
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

    public Team getTeam() {
        return this.team;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

