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
                if (blacklist.isEmpty()) {
                    this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> this.inventory.setItem(22, new ItemBuilder(Material.BOOK).withName("<gray><bold>No Blacklisted Players</bold></gray>").withLore("<gray>No players are currently blacklisted.</gray>", "<gray>Use /team blacklist <player> to add someone.</gray>").withAction("no-blacklisted").build()));
                    return;
                }
                int slot = 9;
                for (BlacklistedPlayer blacklistedPlayer : blacklist) {
                    if (slot < 45) {
                        int currentSlot = slot++;
                        this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> this.inventory.setItem(currentSlot, this.createBlacklistedPlayerItem(blacklistedPlayer)));
                        if ((slot - 9) % 9 != 0) continue;
                        slot += 0;
                        continue;
                    }
                    break;
                }
            } catch (Exception e) {
                this.plugin.getLogger().severe("Error loading team blacklist: " + e.getMessage());
                this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> this.inventory.setItem(22, new ItemBuilder(Material.BARRIER).withName("<red><bold>Error Loading Blacklist</bold></red>").withLore("<red>Could not load blacklisted players.</red>").withAction("error").build()));
            }
        });
    }

    private ItemStack createBlacklistedPlayerItem(BlacklistedPlayer blacklistedPlayer) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer((UUID)blacklistedPlayer.getPlayerUuid());
        OfflinePlayer blacklistedBy = Bukkit.getOfflinePlayer((UUID)blacklistedPlayer.getBlacklistedByUuid());
        String timeAgo = this.formatTimeAgo(blacklistedPlayer.getBlacklistedAt());
        String actionKey = "remove-blacklist:" + blacklistedPlayer.getPlayerUuid().toString();
        this.plugin.getLogger().info("Creating blacklist item for " + blacklistedPlayer.getPlayerName() + " with action: " + actionKey);
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta itemMeta = skull.getItemMeta();
        if (itemMeta instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta)itemMeta;
            skullMeta.setOwningPlayer(offlinePlayer);
            skull.setItemMeta((ItemMeta)skullMeta);
        }
        if ((skull = new ItemBuilder(skull).withName("<gradient:#4C9D9D:#4C96D2><bold>" + blacklistedPlayer.getPlayerName() + "</bold></gradient>").withLore("<gray>Reason: <white>" + blacklistedPlayer.getReason() + "</white>", "<gray>Blacklisted by: <white>" + blacklistedBy.getName() + "</white>", "<gray>Date: <white>" + timeAgo + "</white>", "", "<yellow>Click to remove from blacklist</yellow>").withAction(actionKey).build()).getItemMeta() != null) {
            String actualAction = (String)skull.getItemMeta().getPersistentDataContainer().get(JustTeams.getActionKey(), PersistentDataType.STRING);
            this.plugin.getLogger().info("Action key verification for " + blacklistedPlayer.getPlayerName() + " - Expected: " + actionKey + ", Actual: " + actualAction);
            if (!actionKey.equals(actualAction)) {
                this.plugin.getLogger().warning("Action key mismatch! Expected: " + actionKey + ", Actual: " + actualAction);
                ItemMeta meta = skull.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(JustTeams.getActionKey(), PersistentDataType.STRING, (Object)actionKey);
                    skull.setItemMeta(meta);
                    this.plugin.getLogger().info("Manually set action key for " + blacklistedPlayer.getPlayerName());
                }
            }
        }
        return skull;
    }

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

