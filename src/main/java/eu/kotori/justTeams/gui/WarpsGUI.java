package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.storage.IDataStorage;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class WarpsGUI
implements IRefreshableGUI,
InventoryHolder {
    private final JustTeams plugin;
    private final Player viewer;
    private final Team team;
    private final Inventory inventory;

    public WarpsGUI(JustTeams plugin, Team team, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.team = team;
        GuiConfigManager guiManager = plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("warps-gui");
        String title = guiConfig != null ? guiConfig.getString("title", "\u1d1b\u1d07\u1d00\u1d0d \u1d21\u1d00\u0280\u1d18s") : "\u1d1b\u1d07\u1d00\u1d0d \u1d21\u1d00\u0280\u1d18s";
        int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (Component)plugin.getMiniMessage().deserialize(title));
        this.initializeItems();
    }

    public void initializeItems() {
        int i;
        this.inventory.clear();
        GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("warps-gui");
        if (guiConfig == null) {
            this.plugin.getLogger().warning("warps-gui section not found in gui.yml!");
            return;
        }
        ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
        if (itemsConfig == null) {
            return;
        }
        ItemStack border = new ItemBuilder(guiManager.getMaterial("warps-gui.fill-item.material", Material.GRAY_STAINED_GLASS_PANE)).withName(guiManager.getString("warps-gui.fill-item.name", " ")).build();
        for (i = 0; i < 9; ++i) {
            this.inventory.setItem(i, border);
        }
        for (i = 45; i < 54; ++i) {
            this.inventory.setItem(i, border);
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            List<IDataStorage.TeamWarp> warps = this.plugin.getStorageManager().getStorage().getWarps(this.team.getId());
            this.plugin.getCacheManager().cacheTeamWarps(this.team.getId(), warps);
            this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> {
                if (warps.isEmpty()) {
                    ConfigurationSection noWarpsConfig = itemsConfig.getConfigurationSection("no-warps");
                    if (noWarpsConfig != null) {
                        ItemStack noWarps = new ItemBuilder(Material.matchMaterial((String)noWarpsConfig.getString("material", "BARRIER"))).withName(noWarpsConfig.getString("name", "<red><bold>No Warps Set</bold></red>")).withLore(noWarpsConfig.getStringList("lore")).build();
                        this.inventory.setItem(noWarpsConfig.getInt("slot", 22), noWarps);
                    }
                } else {
                    int slot = 9;
                    for (IDataStorage.TeamWarp warp : warps) {
                        if (slot >= 45) break;
                        boolean canDelete = this.team.hasElevatedPermissions(this.viewer.getUniqueId()) || warp.name().equals(this.viewer.getName());
                        ConfigurationSection warpConfig = itemsConfig.getConfigurationSection("warp-item");
                        if (warpConfig == null) continue;
                        String name = warpConfig.getString("name", "<gradient:#4C9D9D:#4C96D2><bold><warp_name></bold></gradient>").replace("<warp_name>", warp.name());
                        List lore = warpConfig.getStringList("lore");
                        for (int j = 0; j < lore.size(); ++j) {
                            String line = (String)lore.get(j);
                            line = line.replace("<server_name>", warp.serverName()).replace("<warp_protection_status>", warp.password() != null ? "<red>Password Protected" : "<green>Public").replace("<delete_prompt>", canDelete ? "<red>Right-Click to delete." : "");
                            lore.set(j, line);
                        }
                        ItemStack warpItem = new ItemBuilder(warp.password() != null ? Material.IRON_BLOCK : Material.GOLD_BLOCK).withName(name).withLore(lore).withAction("warp_item").withData("warp_name", warp.name()).build();
                        this.inventory.setItem(slot++, warpItem);
                    }
                }
                ConfigurationSection backConfig = itemsConfig.getConfigurationSection("back-button");
                if (backConfig != null) {
                    ItemStack backButton = new ItemBuilder(Material.matchMaterial((String)backConfig.getString("material", "ARROW"))).withName(backConfig.getString("name", "<gray><bold>\u0299\u1d00\u1d04\u1d0b</bold></gray>")).withLore(backConfig.getStringList("lore")).withAction("back-button").build();
                    this.inventory.setItem(backConfig.getInt("slot", 49), backButton);
                }
            });
        });
    }

    @Override
    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    @Override
    public void refresh() {
        this.open();
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public Team getTeam() {
        return this.team;
    }
}

