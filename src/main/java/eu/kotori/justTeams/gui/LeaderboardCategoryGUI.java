package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class LeaderboardCategoryGUI
implements InventoryHolder {
    private final JustTeams plugin;
    private final Player viewer;
    private final Inventory inventory;

    public LeaderboardCategoryGUI(JustTeams plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        GuiConfigManager guiManager = plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("leaderboard-category-gui");
        String title = guiConfig.getString("title", "\u1d1b\u1d07\u1d00\u1d0d \u029f\u1d07\u1d00\u1d05\u1d07\u0280\u0299\u1d0f\u1d00\u0280\u1d05");
        int size = guiConfig.getInt("size", 27);
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (Component)plugin.getMiniMessage().deserialize(title));
        this.initializeItems();
    }

    private void initializeItems() {
        this.inventory.clear();
        GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("leaderboard-category-gui");
        ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
        if (itemsConfig == null) {
            return;
        }
        this.setItemFromConfig(itemsConfig, "top-kills");
        this.setItemFromConfig(itemsConfig, "top-balance");
        this.setItemFromConfig(itemsConfig, "top-members");
        this.setItemFromConfig(itemsConfig, "back-button");
        ConfigurationSection fillConfig = guiConfig.getConfigurationSection("fill-item");
        if (fillConfig != null) {
            ItemStack fillItem = new ItemBuilder(Material.matchMaterial((String)fillConfig.getString("material", "GRAY_STAINED_GLASS_PANE"))).withName(fillConfig.getString("name", " ")).build();
            for (int i = 0; i < this.inventory.getSize(); ++i) {
                if (this.inventory.getItem(i) != null) continue;
                this.inventory.setItem(i, fillItem);
            }
        }
    }

    private void setItemFromConfig(ConfigurationSection itemsSection, String key) {
        ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
        if (itemConfig == null) {
            return;
        }
        int slot = itemConfig.getInt("slot", -1);
        if (slot == -1) {
            return;
        }
        Material material = Material.matchMaterial((String)itemConfig.getString("material", "STONE"));
        String name = itemConfig.getString("name", "");
        List lore = itemConfig.getStringList("lore");
        this.inventory.setItem(slot, new ItemBuilder(material).withName(name).withLore(lore).withAction(key).build());
    }

    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

