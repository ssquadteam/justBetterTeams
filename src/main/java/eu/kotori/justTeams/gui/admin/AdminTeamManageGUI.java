package eu.kotori.justTeams.gui.admin;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.util.ItemBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class AdminTeamManageGUI
implements InventoryHolder {
    private final JustTeams plugin;
    private final Player viewer;
    private final Team targetTeam;
    private final Inventory inventory;
    private final int memberPage;
    private static final int MEMBERS_PER_PAGE = 21;

    public AdminTeamManageGUI(JustTeams plugin, Player viewer, Team targetTeam) {
        this(plugin, viewer, targetTeam, 0);
    }

    public AdminTeamManageGUI(JustTeams plugin, Player viewer, Team targetTeam, int memberPage) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetTeam = targetTeam;
        this.memberPage = memberPage;
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)54, (Component)plugin.getMiniMessage().deserialize("<gold><bold>\u1d0d\u1d00\u0274\u1d00\u0262\u1d07</bold></gold> <dark_gray>\u00bb <white>" + targetTeam.getName()));
        this.initializeItems();
    }

    private void initializeItems() {
        this.inventory.clear();
        OfflinePlayer owner = Bukkit.getOfflinePlayer((UUID)this.targetTeam.getOwnerUuid());
        String ownerName = owner.getName() != null ? owner.getName() : "Unknown";
        this.inventory.setItem(0, new ItemBuilder(Material.NAME_TAG).withName("<yellow><bold>\u1d1b\u1d07\u1d00\u1d0d \u026a\u0274\u0493\u1d0f").withLore("<gray>Name: <white>" + this.targetTeam.getName(), "<gray>Tag: <white>" + this.targetTeam.getTag(), "<gray>Owner: <white>" + ownerName, "<gray>Members: <white>" + this.targetTeam.getMembers().size() + "<gray>/<white>" + this.plugin.getConfigManager().getMaxTeamSize()).build());
        this.inventory.setItem(1, new ItemBuilder(Material.WRITABLE_BOOK).withName("<aqua><bold>\u1d05\u1d07s\u1d04\u0280\u026a\u1d18\u1d1b\u026a\u1d0f\u0274").withLore("<gray>Current: <white>" + this.targetTeam.getDescription(), "", "<yellow>Click to change description").withAction("edit-description").build());
        this.inventory.setItem(2, new ItemBuilder(Material.PAPER).withName("<gold><bold>\u0280\u1d07\u0274\u1d00\u1d0d\u1d07 \u1d1b\u1d07\u1d00\u1d0d").withLore("<gray>Current: <white>" + this.targetTeam.getName(), "", "<yellow>Click to rename team", "<gray>(No cooldown for admins)").withAction("rename-team").build());
        this.inventory.setItem(3, new ItemBuilder(Material.OAK_SIGN).withName("<light_purple><bold>\u1d04\u029c\u1d00\u0274\u0262\u1d07 \u1d1b\u1d00\u0262").withLore("<gray>Current: <white>" + this.targetTeam.getTag(), "", "<yellow>Click to change tag").withAction("edit-tag").build());
        this.inventory.setItem(9, new ItemBuilder(this.targetTeam.isPublic() ? Material.LIME_DYE : Material.GRAY_DYE).withName(this.targetTeam.isPublic() ? "<green><bold>\u1d18\u1d1c\u0299\u029f\u026a\u1d04 <gray>(Click to make private)" : "<gray><bold>\u1d18\u0280\u026a\u1d20\u1d00\u1d1b\u1d07 <gray>(Click to make public)").withLore("<gray>Status: " + (this.targetTeam.isPublic() ? "<green>Public" : "<red>Private"), "", "<yellow>Click to toggle").withAction("toggle-public").build());
        this.inventory.setItem(10, new ItemBuilder(this.targetTeam.isPvpEnabled() ? Material.NETHERITE_SWORD : Material.WOODEN_SWORD).withName(this.targetTeam.isPvpEnabled() ? "<red><bold>\u1d18\u1d20\u1d18 \u1d07\u0274\u1d00\u0299\u029f\u1d07\u1d05 <gray>(Click to disable)" : "<gray><bold>\u1d18\u1d20\u1d18 \u1d05\u026as\u1d00\u0299\u029f\u1d07\u1d05 <gray>(Click to enable)").withLore("<gray>PvP: " + (this.targetTeam.isPvpEnabled() ? "<green>Enabled" : "<red>Disabled"), "", "<yellow>Click to toggle").withAction("toggle-pvp").build());
        this.inventory.setItem(11, new ItemBuilder(Material.DIAMOND).withName("<yellow><bold>\u0299\u1d00\u029f\u1d00\u0274\u1d04\u1d07").withLore("<gray>Current: <white>" + String.format("%.2f", this.targetTeam.getBalance()), "", "<yellow>Click <gray>to set balance").withAction("edit-balance").build());
        this.inventory.setItem(12, new ItemBuilder(Material.IRON_SWORD).withName("<red><bold>s\u1d1b\u1d00\u1d1b\u026as\u1d1b\u026a\u1d04s").withLore("<gray>Kills: <white>" + this.targetTeam.getKills(), "<gray>Deaths: <white>" + this.targetTeam.getDeaths(), "<gray>K/D: <white>" + (this.targetTeam.getDeaths() > 0 ? String.format("%.2f", (double)this.targetTeam.getKills() / (double)this.targetTeam.getDeaths()) : "\u221e"), "", "<yellow>Click to edit stats").withAction("edit-stats").build());
        this.inventory.setItem(13, new ItemBuilder(Material.ENDER_CHEST).withName("<dark_purple><bold>\u1d07\u0274\u1d05\u1d07\u0280\u1d04\u029c\u1d07s\u1d1b").withLore("<gray>View team's ender chest", "", "<yellow>Click to open").withAction("view-enderchest").build());
        this.inventory.setItem(14, new ItemBuilder(Material.TNT).withName("<dark_red><bold>\u1d05\u026as\u0299\u1d00\u0274\u1d05 \u1d1b\u1d07\u1d00\u1d0d").withLore("<gray>Permanently delete this team", "<red>\u26a0 This cannot be undone!", "", "<yellow>Click to disband").withAction("disband-team").build());
        ItemStack divider = new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE).withName("<yellow><bold>\u25ac\u25ac\u25ac \u1d0d\u1d07\u1d0d\u0299\u1d07\u0280s \u25ac\u25ac\u25ac").build();
        for (int i = 18; i < 27; ++i) {
            this.inventory.setItem(i, divider);
        }
        List<TeamPlayer> members = this.targetTeam.getMembers();
        int totalPages = (int)Math.ceil((double)members.size() / 21.0);
        int startIndex = this.memberPage * 21;
        int endIndex = Math.min(startIndex + 21, members.size());
        for (int i = startIndex; i < endIndex; ++i) {
            TeamPlayer member = members.get(i);
            int slot = 27 + (i - startIndex);
            this.inventory.setItem(slot, this.createMemberItem(member));
        }
        if (this.memberPage > 0) {
            this.inventory.setItem(48, new ItemBuilder(Material.ARROW).withName("<yellow>\u25c0 \u1d18\u0280\u1d07\u1d20\u026a\u1d0f\u1d1cs").withAction("prev-members").build());
        }
        this.inventory.setItem(49, new ItemBuilder(Material.PLAYER_HEAD).withName("<aqua><bold>\u1d0d\u1d07\u1d0d\u0299\u1d07\u0280s").withLore("<gray>Total: <white>" + members.size(), "<gray>Page: <white>" + (this.memberPage + 1) + " <gray>of <white>" + Math.max(1, totalPages)).build());
        if (this.memberPage < totalPages - 1) {
            this.inventory.setItem(50, new ItemBuilder(Material.ARROW).withName("<yellow>\u0274\u1d07x\u1d1b \u25b6").withAction("next-members").build());
        }
        this.inventory.setItem(45, new ItemBuilder(Material.ARROW).withName("<gray><bold>\u25c0 \u0299\u1d00\u1d04\u1d0b").withLore("<gray>Return to team list").withAction("back-button").build());
        this.inventory.setItem(53, new ItemBuilder(Material.LIME_DYE).withName("<green><bold>\u0280\u1d07\u0493\u0280\u1d07s\u029c").withLore("<gray>Refresh this menu").withAction("refresh").build());
        ItemStack fillItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).withName(" ").build();
        for (int i = 45; i < 54; ++i) {
            if (this.inventory.getItem(i) != null) continue;
            this.inventory.setItem(i, fillItem);
        }
    }

    private ItemStack createMemberItem(TeamPlayer member) {
        OfflinePlayer player = Bukkit.getOfflinePlayer((UUID)member.getPlayerUuid());
        String playerName = player.getName() != null ? player.getName() : "Unknown";
        boolean isOwner = member.getPlayerUuid().equals(this.targetTeam.getOwnerUuid());
        ArrayList<String> lore = new ArrayList<String>();
        lore.add("<gray>Role: <white>" + member.getRole().toString());
        lore.add("<gray>Online: " + (member.isOnline() ? "<green>\u2713" : "<red>\u2717"));
        lore.add("");
        lore.add("<gray>Permissions:");
        lore.add("<gray>- Withdraw: " + (member.canWithdraw() ? "<green>\u2713" : "<red>\u2717"));
        lore.add("<gray>- Ender Chest: " + (member.canUseEnderChest() ? "<green>\u2713" : "<red>\u2717"));
        lore.add("<gray>- Set Home: " + (member.canSetHome() ? "<green>\u2713" : "<red>\u2717"));
        lore.add("<gray>- Use Home: " + (member.canUseHome() ? "<green>\u2713" : "<red>\u2717"));
        if (!isOwner) {
            lore.add("");
            lore.add("<yellow>Left-Click <gray>to edit permissions");
            lore.add("<yellow>Right-Click <gray>to kick member");
        }
        return new ItemBuilder(Material.PLAYER_HEAD).withName((isOwner ? "<gold><bold>\u2605 " : "<white>") + playerName).withLore(lore).asPlayerHead(member.getPlayerUuid()).withAction("member-" + member.getPlayerUuid().toString()).build();
    }

    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    public Team getTargetTeam() {
        return this.targetTeam;
    }

    public int getMemberPage() {
        return this.memberPage;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

