package eu.kotori.justTeams.gui.admin;

import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class AdminDisbandConfirmGUI
implements InventoryHolder {
    private final Player viewer;
    private final Team targetTeam;
    private final Inventory inventory;

    public AdminDisbandConfirmGUI(Player viewer, Team targetTeam) {
        this.viewer = viewer;
        this.targetTeam = targetTeam;
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)27, (Component)Component.text((String)("Confirm Disband: " + targetTeam.getName())));
        this.initializeItems();
    }

    private void initializeItems() {
        this.inventory.clear();
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).withName(" ").build();
        for (int i = 0; i < 27; ++i) {
            this.inventory.setItem(i, border);
        }
        this.inventory.setItem(11, new ItemBuilder(Material.GREEN_WOOL).withName("<green><bold>CONFIRM DISBAND</bold></green>").withLore("<gray>The team <white>" + this.targetTeam.getName() + "</white> will be deleted forever.").build());
        this.inventory.setItem(15, new ItemBuilder(Material.RED_WOOL).withName("<red><bold>CANCEL</bold></red>").withLore("<gray>Return to the previous menu.").build());
    }

    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    public Team getTargetTeam() {
        return this.targetTeam;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

