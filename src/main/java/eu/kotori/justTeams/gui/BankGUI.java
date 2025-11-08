package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import java.text.DecimalFormat;
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

public class BankGUI
implements IRefreshableGUI,
InventoryHolder {
    private final JustTeams plugin;
    private final Player viewer;
    private final Team team;
    private final Inventory inventory;

    public BankGUI(JustTeams plugin, Player viewer, Team team) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.team = team;
        GuiConfigManager guiManager = plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("bank-gui");
        String title = guiConfig.getString("title", "\u1d1b\u1d07\u1d00\u1d0d \u0299\u1d00\u0274\u1d0b");
        int size = guiConfig.getInt("size", 27);
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (Component)plugin.getMiniMessage().deserialize((Object)title));
        this.initializeItems();
    }

    private void initializeItems() {
        boolean canWithdraw;
        this.inventory.clear();
        GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("bank-gui");
        ConfigurationSection itemsSection = guiConfig.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }
        this.setItemFromConfig(itemsSection, "deposit");
        this.setItemFromConfig(itemsSection, "balance");
        this.setItemFromConfig(itemsSection, "back-button");
        TeamPlayer member = this.team.getMember(this.viewer.getUniqueId());
        boolean bl = canWithdraw = member != null && member.canWithdraw() || this.viewer.hasPermission("justteams.bypass.bank.withdraw");
        if (canWithdraw) {
            this.setItemFromConfig(itemsSection, "withdraw");
        } else {
            this.setItemFromConfig(itemsSection, "withdraw-locked");
        }
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
        String name = this.replacePlaceholders(itemConfig.getString("name", ""));
        List<String> lore = itemConfig.getStringList("lore").stream().map(this::replacePlaceholders).collect(Collectors.toList());
        ItemBuilder builder = new ItemBuilder(material).withName(name).withLore(lore).withAction(key);
        if (key.equals("balance")) {
            builder.withGlow();
        }
        this.inventory.setItem(slot, builder.build());
    }

    private String replacePlaceholders(String text) {
        if (text == null) {
            return "";
        }
        DecimalFormat formatter = new DecimalFormat(this.plugin.getConfigManager().getCurrencyFormat());
        return text.replace("<balance>", formatter.format(this.team.getBalance()));
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

