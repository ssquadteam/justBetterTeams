package eu.kotori.justTeams.gui.sub;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import eu.kotori.justTeams.util.ItemBuilder;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class MemberPermissionsEditGUI
implements InventoryHolder,
IRefreshableGUI {
    private final JustTeams plugin;
    private final Player viewer;
    private final Team team;
    private final TeamPlayer targetMember;
    private final Inventory inventory;
    private final ConfigurationSection guiConfig;

    public MemberPermissionsEditGUI(JustTeams plugin, Player viewer, Team team, UUID targetUuid) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.team = team;
        this.targetMember = team.getMember(targetUuid);
        this.guiConfig = plugin.getGuiConfigManager().getGUI("member-permissions-edit-menu");
        String targetName = Bukkit.getOfflinePlayer((UUID)targetUuid).getName();
        String title = this.guiConfig.getString("title", "\u1d18\u1d07\u0280\u1d0ds: <target_name>").replace("<target_name>", targetName != null ? targetName : "Unknown");
        int size = this.guiConfig.getInt("size", 27);
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (Component)Component.text((String)title));
        this.initializeItems();
    }

    public void initializeItems() {
        this.inventory.clear();
        if (this.targetMember == null) {
            return;
        }
        boolean isSelfView = this.viewer.getUniqueId().equals(this.targetMember.getPlayerUuid());
        boolean canEdit = !isSelfView && (this.viewer.getUniqueId().equals(this.team.getOwnerUuid()) || this.team.getMember(this.viewer.getUniqueId()) != null && this.team.getMember(this.viewer.getUniqueId()).getRole() == TeamRole.CO_OWNER);
        this.setupHeader();
        this.setupPermissionToggles(canEdit);
        this.setupRoleManagement(canEdit);
        this.setupNavigation();
        this.fillEmptySlots();
    }

    private void setupHeader() {
        String targetName = Bukkit.getOfflinePlayer((UUID)this.targetMember.getPlayerUuid()).getName();
        if (targetName == null) {
            targetName = "Unknown";
        }
        String roleName = this.plugin.getGuiConfigManager().getRoleName(this.targetMember.getRole().name());
        String joinDate = this.formatJoinDate(this.targetMember.getJoinDate());
        ItemStack playerHead = new ItemBuilder(Material.PLAYER_HEAD).withName("<gold><b>" + targetName).withLore("<gray>Role: <yellow>" + roleName, "<gray>Join Date: <yellow>" + joinDate, "", "<gray>Click to view permissions").build();
        this.inventory.setItem(4, playerHead);
    }

    private void setupPermissionToggles(boolean canEdit) {
        if (canEdit) {
            this.setupToggleButton(19, Material.GOLD_INGOT, "withdraw-permission", "Bank Withdraw", this.targetMember.canWithdraw());
            this.setupToggleButton(21, Material.ENDER_CHEST, "enderchest-permission", "Ender Chest", this.targetMember.canUseEnderChest());
            this.setupToggleButton(23, Material.RED_BED, "sethome-permission", "Set Home", this.targetMember.canSetHome());
            this.setupToggleButton(25, Material.COMPASS, "usehome-permission", "Use Home", this.targetMember.canUseHome());
        } else {
            this.setupViewButton(19, Material.GOLD_INGOT, "Bank Withdraw", this.targetMember.canWithdraw());
            this.setupViewButton(21, Material.ENDER_CHEST, "Ender Chest", this.targetMember.canUseEnderChest());
            this.setupViewButton(23, Material.RED_BED, "Set Home", this.targetMember.canSetHome());
            this.setupViewButton(25, Material.COMPASS, "Use Home", this.targetMember.canUseHome());
        }
    }

    private void setupToggleButton(int slot, Material material, String action, String permissionName, boolean currentStatus) {
        String status = currentStatus ? "<green>ENABLED" : "<red>DISABLED";
        String toggleText = currentStatus ? "<red>Click to DISABLE" : "<green>Click to ENABLE";
        ItemStack item = new ItemBuilder(material).withName("<gold><b>" + permissionName).withLore("<gray>Current Status: " + status, "", toggleText, "<gray>Action: <yellow>" + action).build();
        this.inventory.setItem(slot, item);
    }

    private void setupViewButton(int slot, Material material, String permissionName, boolean currentStatus) {
        String status = currentStatus ? "<green>ENABLED" : "<red>DISABLED";
        ItemStack item = new ItemBuilder(material).withName("<gray><b>" + permissionName).withLore("<gray>Current Status: " + status, "", "<gray>View Only Mode").build();
        this.inventory.setItem(slot, item);
    }

    private String formatJoinDate(Instant joinDate) {
        try {
            if (joinDate != null) {
                String dateFormat = this.plugin.getGuiConfigManager().getPlaceholder("date_time.join_date_format", "dd MMM yyyy");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC);
                return formatter.format(joinDate);
            }
            return this.plugin.getGuiConfigManager().getPlaceholder("date_time.unknown_date", "Unknown");
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error formatting join date: " + e.getMessage());
            return this.plugin.getGuiConfigManager().getPlaceholder("date_time.unknown_date", "Unknown");
        }
    }

    private void setupRoleManagement(boolean canEdit) {
        if (!canEdit) {
            return;
        }
        if (this.targetMember.getRole() == TeamRole.MEMBER) {
            ItemStack promoteButton = new ItemBuilder(Material.EMERALD).withName("<green><b>Promote to Co-Owner").withLore("<gray>Click to promote", "<gray>player to co-owner role", "", "<yellow>Action: promote").build();
            this.inventory.setItem(37, promoteButton);
        } else if (this.targetMember.getRole() == TeamRole.CO_OWNER && !this.targetMember.getPlayerUuid().equals(this.team.getOwnerUuid())) {
            ItemStack demoteButton = new ItemBuilder(Material.REDSTONE).withName("<red><b>Demote to Member").withLore("<gray>Click to demote", "<gray>player to member role", "", "<yellow>Action: demote").build();
            this.inventory.setItem(37, demoteButton);
        }
    }

    private void setupNavigation() {
        ItemStack backButton = new ItemBuilder(Material.ARROW).withName("<yellow><b>\u2190 Back to Team").withLore("<gray>Return to team menu", "", "<yellow>Action: back").build();
        this.inventory.setItem(40, backButton);
    }

    private void fillEmptySlots() {
        ItemStack fillItem = new ItemBuilder(Material.LIGHT_BLUE_STAINED_GLASS_PANE).withName(" ").build();
        for (int i = 0; i < this.inventory.getSize(); ++i) {
            if (this.inventory.getItem(i) != null) continue;
            this.inventory.setItem(i, fillItem);
        }
    }

    @Override
    public void open() {
        this.viewer.openInventory(this.inventory);
    }

    public Team getTeam() {
        return this.team;
    }

    public TeamPlayer getTargetMember() {
        return this.targetMember;
    }

    @Override
    public void refresh() {
        this.open();
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

