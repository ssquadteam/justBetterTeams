package eu.kotori.justTeams.gui.admin;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import eu.kotori.justTeams.util.ItemBuilder;
import java.util.ArrayList;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AdminMemberEditGUI
implements InventoryHolder {
    private final JustTeams plugin;
    private final Player viewer;
    private final Team team;
    private final TeamPlayer member;
    private final Inventory inventory;

    public AdminMemberEditGUI(JustTeams plugin, Player viewer, Team team, TeamPlayer member) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.team = team;
        this.member = member;
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer((UUID)member.getPlayerUuid());
        String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)54, (Component)plugin.getMiniMessage().deserialize((Object)("<gold><bold>\u1d07\u1d05\u026a\u1d1b \u1d0d\u1d07\u1d0d\u0299\u1d07\u0280</bold></gold> <dark_gray>\u00bb <white>" + playerName)));
        this.initializeItems();
    }

    private void initializeItems() {
        this.inventory.clear();
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer((UUID)this.member.getPlayerUuid());
        String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
        boolean isOwner = this.member.getPlayerUuid().equals(this.team.getOwnerUuid());
        ArrayList<String> infoLore = new ArrayList<String>();
        infoLore.add("<gray>Name: <white>" + playerName);
        infoLore.add("<gray>Role: <white>" + this.member.getRole().toString());
        infoLore.add("<gray>Online: " + (this.member.isOnline() ? "<green>\u2713" : "<red>\u2717"));
        infoLore.add("");
        infoLore.add("<gray>Team: <white>" + this.team.getName());
        this.inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD).withName((isOwner ? "<gold><bold>\u2605 " : "<white>") + playerName).withLore(infoLore).asPlayerHead(this.member.getPlayerUuid()).build());
        this.inventory.setItem(19, new ItemBuilder(this.member.canWithdraw() ? Material.LIME_DYE : Material.GRAY_DYE).withName(this.member.canWithdraw() ? "<green><bold>\u1d21\u026a\u1d1b\u029c\u1d05\u0280\u1d00\u1d21</bold> <gray>(Enabled)" : "<gray><bold>\u1d21\u026a\u1d1b\u029c\u1d05\u0280\u1d00\u1d21</bold> <gray>(Disabled)").withLore("<gray>Status: " + (this.member.canWithdraw() ? "<green>Enabled" : "<red>Disabled"), "", "<gray>Allow member to withdraw from bank", "", "<yellow>Click to toggle").withAction("toggle-withdraw").build());
        this.inventory.setItem(20, new ItemBuilder(this.member.canUseEnderChest() ? Material.LIME_DYE : Material.GRAY_DYE).withName(this.member.canUseEnderChest() ? "<dark_purple><bold>\u1d07\u0274\u1d05\u1d07\u0280\u1d04\u029c\u1d07s\u1d1b</bold> <gray>(Enabled)" : "<gray><bold>\u1d07\u0274\u1d05\u1d07\u0280\u1d04\u029c\u1d07s\u1d1b</bold> <gray>(Disabled)").withLore("<gray>Status: " + (this.member.canUseEnderChest() ? "<green>Enabled" : "<red>Disabled"), "", "<gray>Allow member to use team enderchest", "", "<yellow>Click to toggle").withAction("toggle-enderchest").build());
        this.inventory.setItem(21, new ItemBuilder(this.member.canSetHome() ? Material.LIME_DYE : Material.GRAY_DYE).withName(this.member.canSetHome() ? "<aqua><bold>s\u1d07\u1d1b \u029c\u1d0f\u1d0d\u1d07</bold> <gray>(Enabled)" : "<gray><bold>s\u1d07\u1d1b \u029c\u1d0f\u1d0d\u1d07</bold> <gray>(Disabled)").withLore("<gray>Status: " + (this.member.canSetHome() ? "<green>Enabled" : "<red>Disabled"), "", "<gray>Allow member to set team home", "", "<yellow>Click to toggle").withAction("toggle-sethome").build());
        this.inventory.setItem(22, new ItemBuilder(this.member.canUseHome() ? Material.LIME_DYE : Material.GRAY_DYE).withName(this.member.canUseHome() ? "<light_purple><bold>\u1d1cs\u1d07 \u029c\u1d0f\u1d0d\u1d07</bold> <gray>(Enabled)" : "<gray><bold>\u1d1cs\u1d07 \u029c\u1d0f\u1d0d\u1d07</bold> <gray>(Disabled)").withLore("<gray>Status: " + (this.member.canUseHome() ? "<green>Enabled" : "<red>Disabled"), "", "<gray>Allow member to teleport to team home", "", "<yellow>Click to toggle").withAction("toggle-usehome").build());
        if (!isOwner) {
            this.inventory.setItem(29, new ItemBuilder(Material.EMERALD).withName("<green><bold>\u1d18\u0280\u1d0f\u1d0d\u1d0f\u1d1b\u1d07</bold>").withLore("<gray>Current Role: <white>" + this.member.getRole().toString(), "", "<gray>Promote to " + (this.member.getRole() == TeamRole.MEMBER ? "Co-Owner" : "N/A"), "", "<yellow>Click to promote").withAction("promote-member").build());
            if (this.member.getRole() == TeamRole.CO_OWNER) {
                this.inventory.setItem(30, new ItemBuilder(Material.REDSTONE).withName("<red><bold>\u1d05\u1d07\u1d0d\u1d0f\u1d1b\u1d07</bold>").withLore("<gray>Current Role: <white>" + this.member.getRole().toString(), "", "<gray>Demote to Member", "", "<yellow>Click to demote").withAction("demote-member").build());
            }
        }
        if (!isOwner) {
            this.inventory.setItem(33, new ItemBuilder(Material.TNT).withName("<dark_red><bold>\u1d0b\u026a\u1d04\u1d0b \u1d0d\u1d07\u1d0d\u0299\u1d07\u0280</bold>").withLore("<gray>Remove <white>" + playerName + " <gray>from the team", "<red>\u26a0 This action cannot be undone!", "", "<yellow>Click to kick member").withAction("kick-member").build());
        }
        this.inventory.setItem(45, new ItemBuilder(Material.ARROW).withName("<gray><bold>\u25c0 \u0299\u1d00\u1d04\u1d0b</bold>").withLore("<gray>Return to team management").withAction("back-button").build());
        ItemStack fillItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).withName(" ").build();
        for (int i = 0; i < 54; ++i) {
            if (this.inventory.getItem(i) != null) continue;
            this.inventory.setItem(i, fillItem);
        }
    }

    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    public Team getTeam() {
        return this.team;
    }

    public TeamPlayer getMember() {
        return this.member;
    }

    @NotNull
    public Inventory getInventory() {
        return this.inventory;
    }
}

