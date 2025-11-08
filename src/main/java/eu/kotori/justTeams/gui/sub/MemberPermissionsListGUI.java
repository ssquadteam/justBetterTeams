package eu.kotori.justTeams.gui.sub;

import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.util.ItemBuilder;
import java.util.ArrayList;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class MemberPermissionsListGUI
implements InventoryHolder {
    private final Player viewer;
    private final Team team;
    private final Inventory inventory;

    public MemberPermissionsListGUI(Player viewer, Team team) {
        this.viewer = viewer;
        this.team = team;
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)54, (Component)Component.text((String)"Select Member to Edit"));
        this.initializeItems();
    }

    private void initializeItems() {
        int i;
        this.inventory.clear();
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).withName(" ").build();
        for (i = 0; i < 9; ++i) {
            this.inventory.setItem(i, border);
        }
        for (i = 45; i < 54; ++i) {
            this.inventory.setItem(i, border);
        }
        int slot = 9;
        for (TeamPlayer member : this.team.getMembers()) {
            if (slot >= 45 || this.team.isOwner(member.getPlayerUuid())) continue;
            String memberName = Bukkit.getOfflinePlayer((UUID)member.getPlayerUuid()).getName();
            ArrayList<String> lore = new ArrayList<String>();
            lore.add("<gray>Bank Withdraw: " + (member.canWithdraw() ? "<green>Yes" : "<red>No"));
            lore.add("<gray>Use Ender Chest: " + (member.canUseEnderChest() ? "<green>Yes" : "<red>No"));
            lore.add("<gray>Set Team Home: " + (member.canSetHome() ? "<green>Yes" : "<red>No"));
            lore.add("<gray>Use Team Home: " + (member.canUseHome() ? "<green>Yes" : "<red>No"));
            lore.add("");
            lore.add("<yellow>Click to edit permissions.</yellow>");
            this.inventory.setItem(slot++, new ItemBuilder(Material.PLAYER_HEAD).asPlayerHead(member.getPlayerUuid()).withName("<gradient:#95FD95:#FFFFFF><bold>" + memberName + "</bold></gradient>").withLore(lore).build());
        }
        this.inventory.setItem(49, new ItemBuilder(Material.ARROW).withName("<gray><bold>BACK</bold></gray>").build());
    }

    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    public Team getTeam() {
        return this.team;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

