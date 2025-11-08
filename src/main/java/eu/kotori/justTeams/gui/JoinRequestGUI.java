package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
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

public class JoinRequestGUI
implements IRefreshableGUI,
InventoryHolder {
    private final JustTeams plugin;
    private final Player viewer;
    private final Team team;
    private final Inventory inventory;

    public JoinRequestGUI(JustTeams plugin, Player viewer, Team team) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.team = team;
        GuiConfigManager guiManager = plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("join-requests-gui");
        String title = guiConfig != null ? guiConfig.getString("title", "\u1d0a\u1d0f\u026a\u0274 \u0280\u1d07\u01eb\u1d1c\u1d07s\u1d1bs") : "\u1d0a\u1d0f\u026a\u0274 \u0280\u1d07\u01eb\u1d1c\u1d07s\u1d1bs";
        int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (Component)plugin.getMiniMessage().deserialize(title));
        this.initializeItems();
    }

    public void initializeItems() {
        int i;
        this.inventory.clear();
        GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("join-requests-gui");
        if (guiConfig == null) {
            this.plugin.getLogger().warning("join-requests-gui section not found in gui.yml!");
            return;
        }
        ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
        if (itemsConfig == null) {
            return;
        }
        ItemStack border = new ItemBuilder(guiManager.getMaterial("join-requests-gui.fill-item.material", Material.GRAY_STAINED_GLASS_PANE)).withName(guiManager.getString("join-requests-gui.fill-item.name", " ")).build();
        for (i = 0; i < 9; ++i) {
            this.inventory.setItem(i, border);
        }
        for (i = 45; i < 54; ++i) {
            this.inventory.setItem(i, border);
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            List<UUID> requests = this.plugin.getStorageManager().getStorage().getJoinRequests(this.team.getId());
            this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> {
                if (requests.isEmpty()) {
                    ConfigurationSection noRequestsConfig = itemsConfig.getConfigurationSection("no-requests");
                    if (noRequestsConfig != null) {
                        ItemStack noRequestsItem = new ItemBuilder(Material.matchMaterial((String)noRequestsConfig.getString("material", "BARRIER"))).withName(noRequestsConfig.getString("name", "<red><bold>No Join Requests</bold></red>")).withLore(noRequestsConfig.getStringList("lore")).build();
                        this.inventory.setItem(noRequestsConfig.getInt("slot", 22), noRequestsItem);
                    }
                } else {
                    int slot = 9;
                    for (UUID requestUuid : requests) {
                        ConfigurationSection headConfig;
                        if (slot >= 45) break;
                        OfflinePlayer requester = Bukkit.getOfflinePlayer((UUID)requestUuid);
                        if (requester.getName() == null || (headConfig = itemsConfig.getConfigurationSection("player-head")) == null) continue;
                        String name = headConfig.getString("name", "<gradient:#4C9D9D:#4C96D2><status_indicator><bold><player_name></bold></gradient>").replace("<player_name>", requester.getName()).replace("<status_indicator>", requester.isOnline() ? "<green>\u25cf </green>" : "<red>\u25cf </red>");
                        List lore = headConfig.getStringList("lore");
                        ItemStack head = new ItemBuilder(Material.PLAYER_HEAD).asPlayerHead(requestUuid).withName(name).withLore(lore).withAction("player-head").withData("player_uuid", requestUuid.toString()).build();
                        this.inventory.setItem(slot++, head);
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

    public Team getTeam() {
        return this.team;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

