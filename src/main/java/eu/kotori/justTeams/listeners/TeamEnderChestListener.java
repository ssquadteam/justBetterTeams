package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.Team;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamEnderChestListener
implements Listener {
    private final JustTeams plugin;
    private final ConcurrentHashMap<UUID, Long> lastUpdateTime = new ConcurrentHashMap();
    private static final long UPDATE_COOLDOWN = 100L;
    private static final long SLOT_UPDATE_COOLDOWN = 50L;

    public TeamEnderChestListener(JustTeams plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder inventoryHolder = event.getInventory().getHolder();
        if (!(inventoryHolder instanceof Team)) {
            return;
        }
        Team team = (Team)inventoryHolder;
        Player player = (Player)event.getPlayer();
        team.addEnderChestViewer(player.getUniqueId());
        if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getLogger().info("Player " + player.getName() + " opened team enderchest for team " + team.getName() + " (viewers: " + team.getEnderChestViewers().size() + ")");
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder inventoryHolder = event.getInventory().getHolder();
        if (!(inventoryHolder instanceof Team)) {
            return;
        }
        Team team = (Team)inventoryHolder;
        Player player = (Player)event.getWhoClicked();
        long currentTime = System.currentTimeMillis();
        if (this.lastUpdateTime.containsKey(player.getUniqueId()) && currentTime - this.lastUpdateTime.get(player.getUniqueId()) < 50L) {
            return;
        }
        this.handleInventoryChange(team, player, event.getInventory(), "click");
        this.lastUpdateTime.put(player.getUniqueId(), currentTime);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder inventoryHolder = event.getInventory().getHolder();
        if (!(inventoryHolder instanceof Team)) {
            return;
        }
        Team team = (Team)inventoryHolder;
        Player player = (Player)event.getWhoClicked();
        long currentTime = System.currentTimeMillis();
        if (this.lastUpdateTime.containsKey(player.getUniqueId()) && currentTime - this.lastUpdateTime.get(player.getUniqueId()) < 50L) {
            return;
        }
        this.handleInventoryChange(team, player, event.getInventory(), "drag");
        this.lastUpdateTime.put(player.getUniqueId(), currentTime);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder inventoryHolder = event.getInventory().getHolder();
        if (!(inventoryHolder instanceof Team)) {
            return;
        }
        Team team = (Team)inventoryHolder;
        Player player = (Player)event.getPlayer();
        team.removeEnderChestViewer(player.getUniqueId());
        if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getLogger().info("Player " + player.getName() + " closed team enderchest for team " + team.getName() + " (remaining viewers: " + team.getEnderChestViewers().size() + ")");
        }
        if (!team.hasEnderChestViewers()) {
            this.plugin.getTaskRunner().runAsync(() -> {
                try {
                    this.plugin.getTeamManager().saveAndReleaseEnderChest(team);
                    if (this.plugin.getConfigManager().isDebugEnabled()) {
                        this.plugin.getLogger().info("\u2713 Last viewer closed enderchest for team " + team.getName() + ", saved and released lock");
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Error saving enderchest on close for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    private void handleInventoryChange(Team team, Player player, Inventory inventory, String changeType) {
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                this.plugin.getTeamManager().saveEnderChest(team);
                if (team.hasEnderChestViewers()) {
                    this.plugin.getTaskRunner().run(() -> this.notifyOtherViewers(team, player, changeType));
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Error handling enderchest change for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    private void notifyOtherViewers(Team team, Player changer, String changeType) {
        for (UUID viewerUuid : team.getEnderChestViewers()) {
            Player viewer;
            if (viewerUuid.equals(changer.getUniqueId()) || (viewer = Bukkit.getPlayer((UUID)viewerUuid)) == null || !viewer.isOnline()) continue;
            try {
                this.refreshViewerInventory(viewer, team);
            } catch (Exception e) {
                this.plugin.getLogger().warning("Failed to refresh enderchest for viewer " + viewer.getName() + ": " + e.getMessage());
            }
        }
    }

    private void refreshViewerInventory(Player viewer, Team team) {
        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof Team) {
            this.plugin.getTaskRunner().runOnEntity((Entity)viewer, () -> {
                try {
                    viewer.closeInventory();
                    this.plugin.getTaskRunner().runOnEntity((Entity)viewer, () -> viewer.openInventory(team.getEnderChest()));
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to refresh enderchest inventory for " + viewer.getName() + ": " + e.getMessage());
                }
            });
        }
    }
}

