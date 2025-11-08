package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.storage.IDataStorage;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.GuiConfigManager;
import eu.kotori.justTeams.util.ItemBuilder;
import java.util.List;
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
import org.jetbrains.annotations.NotNull;

public class InvitesGUI
implements IRefreshableGUI,
InventoryHolder {
    private final JustTeams plugin;
    private final Player viewer;
    private final Inventory inventory;

    public InvitesGUI(JustTeams plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        GuiConfigManager guiManager = plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("invites-gui");
        String title = guiConfig != null ? guiConfig.getString("title", "\u1d18\u1d07\u0274\u1d05\u026a\u0274\u0262 \u026a\u0274\u1d20\u026a\u1d1b\u1d07s") : "\u1d18\u1d07\u0274\u1d05\u026a\u0274\u0262 \u026a\u0274\u1d20\u026a\u1d1b\u1d07s";
        int size = guiConfig != null ? guiConfig.getInt("size", 54) : 54;
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (Component)plugin.getMiniMessage().deserialize(title));
        this.initializeItems();
    }

    public void initializeItems() {
        ConfigurationSection closeConfig;
        int i;
        this.inventory.clear();
        GuiConfigManager guiManager = this.plugin.getGuiConfigManager();
        ConfigurationSection guiConfig = guiManager.getGUI("invites-gui");
        if (guiConfig == null) {
            this.plugin.getLogger().warning("invites-gui section not found in gui.yml!");
            return;
        }
        ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
        if (itemsConfig == null) {
            return;
        }
        ItemStack border = new ItemBuilder(guiManager.getMaterial("invites-gui.fill-item.material", Material.GRAY_STAINED_GLASS_PANE)).withName(guiManager.getString("invites-gui.fill-item.name", " ")).build();
        for (i = 0; i < 9; ++i) {
            this.inventory.setItem(i, border);
        }
        for (i = 45; i < 54; ++i) {
            this.inventory.setItem(i, border);
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            List<IDataStorage.TeamInvite> inviteDetails = this.plugin.getStorageManager().getStorage().getPlayerInvitesWithDetails(this.viewer.getUniqueId());
            this.plugin.getTaskRunner().runOnEntity((Entity)this.viewer, () -> {
                if (inviteDetails.isEmpty()) {
                    ConfigurationSection noInvitesConfig = itemsConfig.getConfigurationSection("no-invites");
                    if (noInvitesConfig != null) {
                        ItemStack noInvitesItem = new ItemBuilder(Material.matchMaterial((String)noInvitesConfig.getString("material", "BARRIER"))).withName(noInvitesConfig.getString("name", "<red><bold>No Pending Invites</bold></red>")).withLore(noInvitesConfig.getStringList("lore")).build();
                        this.inventory.setItem(noInvitesConfig.getInt("slot", 22), noInvitesItem);
                    }
                } else {
                    int slot = 9;
                    for (IDataStorage.TeamInvite invite : inviteDetails) {
                        ConfigurationSection teamIconConfig;
                        if (slot >= 45) break;
                        Team team = this.plugin.getTeamManager().getTeamByName(invite.teamName());
                        if (team == null || (teamIconConfig = itemsConfig.getConfigurationSection("team-icon")) == null) continue;
                        String name = teamIconConfig.getString("name", "<gradient:#4C9D9D:#4C96D2><bold><team_name></bold></gradient>").replace("<team_name>", invite.teamName()).replace("<team_tag>", team.getTag());
                        List<String> loreList = teamIconConfig.getStringList("lore");
                        loreList = loreList.stream().map(line -> line.replace("<team_name>", invite.teamName()).replace("<team_tag>", team.getTag()).replace("<inviter>", invite.inviterName()).replace("<member_count>", String.valueOf(team.getMembers().size())).replace("<max_members>", String.valueOf(this.plugin.getConfigManager().getMaxTeamSize())).replace("<description>", team.getDescription() != null ? team.getDescription() : "No description")).collect(Collectors.toList());
                        ItemStack teamIcon = new ItemBuilder(Material.matchMaterial((String)teamIconConfig.getString("material", "DIAMOND"))).withName(name).withLore(loreList).withAction("team-icon").withData("team_id", String.valueOf(invite.teamId())).withData("team_name", invite.teamName()).build();
                        this.inventory.setItem(slot++, teamIcon);
                    }
                }
            });
        });
        ConfigurationSection backConfig = itemsConfig.getConfigurationSection("back-button");
        if (backConfig != null) {
            ItemStack backButton = new ItemBuilder(Material.matchMaterial((String)backConfig.getString("material", "ARROW"))).withName(backConfig.getString("name", "<gray><bold>\u0299\u1d00\u1d04\u1d0b</bold></gray>")).withLore(backConfig.getStringList("lore")).withAction("back-button").build();
            this.inventory.setItem(backConfig.getInt("slot", 49), backButton);
        }
        if ((closeConfig = itemsConfig.getConfigurationSection("close-button")) != null) {
            ItemStack closeButton = new ItemBuilder(Material.matchMaterial((String)closeConfig.getString("material", "BARRIER"))).withName(closeConfig.getString("name", "<red><bold>\u1d04\u029f\u1d0fs\u1d07</bold></red>")).withLore(closeConfig.getStringList("lore")).withAction("close-button").build();
            this.inventory.setItem(closeConfig.getInt("slot", 53), closeButton);
        }
    }

    @Override
    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    @Override
    public void refresh() {
        this.initializeItems();
    }

    @NotNull
    public Inventory getInventory() {
        return this.inventory;
    }

    public Player getViewer() {
        return this.viewer;
    }
}

