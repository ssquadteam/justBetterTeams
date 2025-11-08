package eu.kotori.justTeams.gui.sub;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class TeamSettingsGUI
implements IRefreshableGUI,
InventoryHolder {
    private final JustTeams plugin;
    private final Player viewer;
    private final Team team;
    private final Inventory inventory;

    public TeamSettingsGUI(JustTeams plugin, Player viewer, Team team) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.team = team;
        GuiConfigManager guiManager = plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("team-settings-gui");
        String title = "\u1d1b\u1d07\u1d00\u1d0d s\u1d07\u1d1b\u1d1b\u026a\u0274\u0262s";
        int size = 27;
        if (guiConfig != null) {
            title = guiConfig.getString("title", "\u1d1b\u1d07\u1d00\u1d0d s\u1d07\u1d1b\u1d1b\u026a\u0274\u0262s");
            size = guiConfig.getInt("size", 27);
        }
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (Component)plugin.getMiniMessage().deserialize(title));
        this.initializeItems();
    }

    public void initializeItems() {
        this.inventory.clear();
        GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("team-settings-gui");
        if (guiConfig == null) {
            this.plugin.getLogger().warning("team-settings-gui section not found in gui.yml!");
            return;
        }
        ConfigurationSection itemsSection = guiConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }
        for (String key : itemsSection.getKeys(false)) {
            int slot;
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
            if (itemConfig == null || key.equals("change-tag") && !this.plugin.getConfigManager().isTeamTagEnabled() || (slot = itemConfig.getInt("slot", -1)) == -1) continue;
            Material material = Material.matchMaterial((String)itemConfig.getString("material", "STONE"));
            String name = this.replacePlaceholders(itemConfig.getString("name", ""));
            List<String> lore = itemConfig.getStringList("lore").stream().map(this::replacePlaceholders).collect(Collectors.toList());
            this.inventory.setItem(slot, new ItemBuilder(material).withName(name).withLore(lore).withAction(key).build());
        }
        ConfigurationSection fillItemSection = guiConfig.getConfigurationSection("fill-item");
        if (fillItemSection != null) {
            ItemStack fillItem = new ItemBuilder(Material.matchMaterial((String)fillItemSection.getString("material", "GRAY_STAINED_GLASS_PANE"))).withName(fillItemSection.getString("name", " ")).build();
            for (int i = 0; i < this.inventory.getSize(); ++i) {
                if (this.inventory.getItem(i) != null) continue;
                this.inventory.setItem(i, fillItem);
            }
        }
    }

    private String replacePlaceholders(String text) {
        if (this.team == null) {
            return text;
        }
        String status = this.team.isPublic() ? this.plugin.getGuiConfigManager().getString("team-settings-gui.items.toggle-public.status-public", "<green>Public") : this.plugin.getGuiConfigManager().getString("team-settings-gui.items.toggle-public.status-private", "<red>Private");
        return text.replace("<team_tag>", this.team.getTag()).replace("<team_description>", this.team.getDescription()).replace("<public_status>", status);
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

