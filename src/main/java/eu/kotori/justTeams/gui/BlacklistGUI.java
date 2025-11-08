package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.team.BlacklistedPlayer;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

public class BlacklistGUI
implements InventoryHolder,
IRefreshableGUI {
    private final JustTeams plugin;
    private final Team team;
    private final Player viewer;
    private Inventory inventory;

    public BlacklistGUI(JustTeams plugin, Team team, Player viewer) {
        this.plugin = plugin;
        this.team = team;
        this.viewer = viewer;
        GuiConfigManager guiManager = plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("blacklist-gui");
        String title = guiConfig != null ? guiConfig.getString("title", "\u1d1b\u1d07\u1d00\u1d0d \u0299\u029f\u1d00\u1d04\u1d0b\u029f\u026as\u1d1b") : "\u1d1b\u1d07\u1d00\u1d0d \u0299\u029f\u1d00\u1d04\u1d0b\u029f\u026as\u1d1b";
        int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (Component)Component.text((String)title));
        this.initializeItems();
    }

    public void initializeItems() {
        int i;
        this.inventory.clear();
        ItemStack fillItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).withName(" ").build();
        for (i = 0; i < 9; ++i) {
            this.inventory.setItem(i, fillItem);
        }
        for (i = 45; i < 54; ++i) {
            this.inventory.setItem(i, fillItem);
        }
        this.inventory.setItem(4, new ItemBuilder(Material.BARRIER).withName("<white><bold>\u1d1b\u1d07\u1d00\u1d0d \u0299\u029f\u1d00\u1d04\u1d0b\u029f\u026as\u1d1b</bold></white>").withLore("<gray>Players who cannot join this team</gray>", "<gray>Click on a player head to remove them</gray>").withAction("title").build());
        this.loadBlacklistedPlayers();
        this.inventory.setItem(49, new ItemBuilder(Material.ARROW).withName("<gray><bold>\u0299\u1d00\u1d04\u1d0b</bold></gray>").withLore("<yellow>Click to return to the main menu.</yellow>").withAction("back-button").build());
    }

    private void loadBlacklistedPlayers() {
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                List<BlacklistedPlayer> blacklist = this.plugin.getStorageManager().getStorage().getTeamBlacklist(this.team.getId());
                this.plugin.getCacheManager().cacheTeamBlacklist(this.team.getId(), blacklist);
                if (blacklist.isEmpty()) {
                    this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> this.inventory.setItem(22, new ItemBuilder(Material.BOOK).withName("<gray><bold>No Blacklisted Players</bold></gray>").withLore("<gray>No players are currently blacklisted.</gray>", "<gray>Use /team blacklist <player> to add someone.</gray>").withAction("no-blacklisted").build()));
                    return;
                }
                List<BlacklistedDisplayData> display = blacklist.stream().map(bp -> {
                    UUID id = bp.getPlayerUuid();
                    String displayName = this.plugin.getCacheManager().getPlayerName(id);
                    if (displayName == null || displayName.isEmpty()) {
                        displayName = bp.getPlayerName() != null ? bp.getPlayerName() : id.toString();
                    }
                    String byName = bp.getBlacklistedByName() != null ? bp.getBlacklistedByName() : "Unknown";
                    return new BlacklistedDisplayData(id, displayName, bp.getReason(), byName, bp.getBlacklistedAt());
                }).toList();
                this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> {
                    int slot = 9;
                    for (BlacklistedDisplayData d : display) {
                        if (slot >= 45) break;
                        this.inventory.setItem(slot++, this.createBlacklistedPlayerItem(d));
                    }
                });
            } catch (Exception e) {
                this.plugin.getLogger().severe("Error loading team blacklist: " + e.getMessage());
                this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> this.inventory.setItem(22, new ItemBuilder(Material.BARRIER).withName("<red><bold>Error Loading Blacklist</bold></red>").withLore("<red>Could not load blacklisted players.</red>").withAction("error").build()));
            }
        });
    }

    private ItemStack createBlacklistedPlayerItem(BlacklistedDisplayData data) {
        String timeAgo = this.formatTimeAgo(data.blacklistedAt());
        String actionKey = "remove-blacklist:" + data.playerUuid().toString();
        ItemStack skull = new ItemBuilder(Material.PLAYER_HEAD)
                .asPlayerHead(data.playerUuid())
                .withName("<gradient:#4C9D9D:#4C96D2><bold>" + data.displayName() + "</bold></gradient>")
                .withLore(
                        "<gray>Reason: <white>" + data.reason() + "</white>",
                        "<gray>Blacklisted by: <white>" + data.blacklistedByName() + "</white>",
                        "<gray>Date: <white>" + timeAgo + "</white>",
                        "",
                        "<yellow>Click to remove from blacklist</yellow>")
                .withAction(actionKey)
                .build();
        return skull;
    }

    private record BlacklistedDisplayData(UUID playerUuid, String displayName, String reason, String blacklistedByName, Instant blacklistedAt) {}

    private String formatTimeAgo(Instant blacklistedAt) {
        Duration duration = Duration.between(blacklistedAt, Instant.now());
        if (duration.toDays() > 0L) {
            return duration.toDays() + " day" + (duration.toDays() == 1L ? "" : "s") + " ago";
        }
        if (duration.toHours() > 0L) {
            return duration.toHours() + " hour" + (duration.toHours() == 1L ? "" : "s") + " ago";
        }
        if (duration.toMinutes() > 0L) {
            return duration.toMinutes() + " minute" + (duration.toMinutes() == 1L ? "" : "s") + " ago";
        }
        return "Just now";
    }

    @Override
    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public Team getTeam() {
        return this.team;
    }

    @Override
    public void refresh() {
        if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getLogger().info("Refreshing blacklist GUI for team " + this.team.getName());
        }
        if (this.viewer != null && this.viewer.isOnline()) {
            this.plugin.getGuiManager().getUpdateThrottle().scheduleUpdate(this.viewer.getUniqueId(), () -> this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> {
                try {
                    this.initializeItems();
                    if (this.plugin.getConfigManager().isDebugEnabled()) {
                        this.plugin.getLogger().info("Blacklist GUI refresh completed for team " + this.team.getName());
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().severe("Error refreshing blacklist GUI for team " + this.team.getName() + ": " + e.getMessage());
                }
            }));
        }
    }
}

