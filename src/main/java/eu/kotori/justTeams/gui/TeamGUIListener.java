package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.gui.BankGUI;
import eu.kotori.justTeams.gui.BlacklistGUI;
import eu.kotori.justTeams.gui.ConfirmGUI;
import eu.kotori.justTeams.gui.IRefreshableGUI;
import eu.kotori.justTeams.gui.InvitesGUI;
import eu.kotori.justTeams.gui.JoinRequestGUI;
import eu.kotori.justTeams.gui.LeaderboardCategoryGUI;
import eu.kotori.justTeams.gui.LeaderboardViewGUI;
import eu.kotori.justTeams.gui.MemberEditGUI;
import eu.kotori.justTeams.gui.NoTeamGUI;
import eu.kotori.justTeams.gui.TeamGUI;
import eu.kotori.justTeams.gui.WarpsGUI;
import eu.kotori.justTeams.gui.admin.AdminGUI;
import eu.kotori.justTeams.gui.admin.AdminMemberEditGUI;
import eu.kotori.justTeams.gui.admin.AdminTeamListGUI;
import eu.kotori.justTeams.gui.admin.AdminTeamManageGUI;
import eu.kotori.justTeams.gui.sub.TeamSettingsGUI;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamManager;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import eu.kotori.justTeams.util.EffectsUtil;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class TeamGUIListener
implements Listener {
    private final JustTeams plugin;
    private final TeamManager teamManager;
    private final NamespacedKey actionKey;
    private final ConcurrentHashMap<String, Long> actionCooldowns = new ConcurrentHashMap();
    private final Object actionLock = new Object();

    public TeamGUIListener(JustTeams plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
        this.actionKey = JustTeams.getActionKey();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean checkActionCooldown(Player player, String action, long cooldownMs) {
        if (player == null || action == null) {
            return false;
        }
        String key = String.valueOf(player.getUniqueId()) + ":" + action;
        long currentTime = System.currentTimeMillis();
        Object object = this.actionLock;
        synchronized (object) {
            return this.actionCooldowns.compute(key, (k, lastActionTime) -> {
                if (lastActionTime == null || currentTime - lastActionTime >= cooldownMs) {
                    return currentTime;
                }
                return lastActionTime;
            }) == currentTime;
        }
    }

    @EventHandler
    public void onGUIClick(InventoryClickEvent event) {
        HumanEntity humanEntity = event.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player player = (Player)humanEntity;
        try {
            Object action;
            boolean isOurGui;
            InventoryHolder holder = event.getView().getTopInventory().getHolder();
            boolean bl = isOurGui = holder instanceof IRefreshableGUI || holder instanceof NoTeamGUI || holder instanceof ConfirmGUI || holder instanceof AdminGUI || holder instanceof AdminTeamListGUI || holder instanceof AdminTeamManageGUI || holder instanceof AdminMemberEditGUI || holder instanceof TeamSettingsGUI || holder instanceof LeaderboardCategoryGUI || holder instanceof LeaderboardViewGUI || holder instanceof JoinRequestGUI || holder instanceof InvitesGUI || holder instanceof WarpsGUI || holder instanceof BlacklistGUI;
            if (!isOurGui) {
                return;
            }
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals((Object)event.getView().getTopInventory())) {
                return;
            }
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null) {
                return;
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (holder instanceof BlacklistGUI && this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getDebugLogger().log("=== BLACKLIST GUI MAIN CLICK DEBUG ===");
                this.plugin.getDebugLogger().log("Player: " + player.getName());
                this.plugin.getDebugLogger().log("Clicked item type: " + String.valueOf(clickedItem.getType()));
                this.plugin.getDebugLogger().log("Clicked item has meta: " + (meta != null));
                this.plugin.getDebugLogger().log("PDC has action key: " + pdc.has(this.actionKey, PersistentDataType.STRING));
                if (pdc.has(this.actionKey, PersistentDataType.STRING)) {
                    action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
                    this.plugin.getDebugLogger().log("Action found: " + (String)action);
                } else {
                    this.plugin.getLogger().warning("No action key found in blacklist item!");
                    for (NamespacedKey key : pdc.getKeys()) {
                        this.plugin.getDebugLogger().log("PDC key found: " + key.toString());
                    }
                }
                this.plugin.getDebugLogger().log("=== END BLACKLIST GUI MAIN CLICK DEBUG ===");
            }
            if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
                this.plugin.getDebugLogger().log("GUI click without valid action key from " + player.getName());
                return;
            }
            action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
            if (action == null || ((String)action).isEmpty() || ((String)action).length() > 50) {
                this.plugin.getDebugLogger().log("Invalid action in GUI click from " + player.getName() + ": " + (String)action);
                return;
            }
            if ("back-button".equals(action)) {
                this.plugin.getDebugLogger().log("Back button clicked by " + player.getName() + " in " + holder.getClass().getSimpleName());
            }
            if (holder instanceof TeamGUI) {
                TeamGUI gui = (TeamGUI)holder;
                this.onTeamGUIClick(player, gui, clickedItem, pdc);
            } else if (holder instanceof MemberEditGUI) {
                MemberEditGUI gui = (MemberEditGUI)holder;
                this.onMemberEditGUIClick(player, gui, pdc);
            } else if (holder instanceof BankGUI) {
                BankGUI gui = (BankGUI)holder;
                this.onBankGUIClick(player, gui, pdc);
            } else if (holder instanceof TeamSettingsGUI) {
                TeamSettingsGUI gui = (TeamSettingsGUI)holder;
                this.onTeamSettingsGUIClick(player, gui, pdc);
            } else if (holder instanceof JoinRequestGUI) {
                JoinRequestGUI gui = (JoinRequestGUI)holder;
                this.onJoinRequestGUIClick(player, gui, event.getClick(), clickedItem);
            } else if (holder instanceof InvitesGUI) {
                InvitesGUI gui = (InvitesGUI)holder;
                this.onInvitesGUIClick(player, gui, event.getClick(), clickedItem);
            } else if (holder instanceof LeaderboardCategoryGUI) {
                this.onLeaderboardCategoryGUIClick(player, pdc);
            } else if (holder instanceof LeaderboardViewGUI) {
                this.onLeaderboardViewGUIClick(player, pdc);
            } else if (holder instanceof NoTeamGUI) {
                this.onNoTeamGUIClick(player, pdc);
            } else if (holder instanceof AdminGUI) {
                this.onAdminGUIClick(player, pdc);
            } else if (holder instanceof AdminTeamListGUI) {
                AdminTeamListGUI gui = (AdminTeamListGUI)holder;
                this.onAdminTeamListGUIClick(player, gui, clickedItem, pdc);
            } else if (holder instanceof AdminTeamManageGUI) {
                AdminTeamManageGUI gui = (AdminTeamManageGUI)holder;
                this.onAdminTeamManageGUIClick(player, gui, pdc);
            } else if (holder instanceof AdminMemberEditGUI) {
                AdminMemberEditGUI gui = (AdminMemberEditGUI)holder;
                this.onAdminMemberEditGUIClick(player, gui, pdc);
            } else if (holder instanceof ConfirmGUI) {
                ConfirmGUI gui = (ConfirmGUI)holder;
                this.onConfirmGUIClick(gui, pdc);
            } else if (holder instanceof WarpsGUI) {
                this.onWarpsGUIClick(player, (WarpsGUI)holder, event.getClick(), clickedItem, pdc);
            } else if (holder instanceof BlacklistGUI) {
                BlacklistGUI gui = (BlacklistGUI)holder;
                this.onBlacklistGUIClick(player, gui, event.getClick(), clickedItem, pdc);
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Error handling GUI click for " + player.getName() + ": " + e.getMessage());
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().severe("Error in GUI action: " + e.getMessage());
            }
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "gui_error", new TagResolver[0]);
            event.setCancelled(true);
        }
    }

    private void onTeamGUIClick(Player player, TeamGUI gui, ItemStack clickedItem, PersistentDataContainer pdc) {
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
        Team team = gui.getTeam();
        if (team == null) {
            this.plugin.getDebugLogger().log("TeamGUI click with null team for " + player.getName());
            return;
        }
        if (!team.isMember(player.getUniqueId())) {
            this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            player.closeInventory();
            return;
        }
        switch (action) {
            case "player-head": {
                SkullMeta skullMeta;
                ItemMeta itemMeta = clickedItem.getItemMeta();
                if (!(itemMeta instanceof SkullMeta) || (skullMeta = (SkullMeta)itemMeta).getPlayerProfile() == null) break;
                UUID profileId = skullMeta.getPlayerProfile().getId();
                UUID targetUuid = profileId;
                if (targetUuid == null) break;
                if (targetUuid.equals(player.getUniqueId())) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_edit_own_permissions", new TagResolver[0]);
                    return;
                }
                TeamPlayer viewerMember = team.getMember(player.getUniqueId());
                TeamPlayer targetMember = team.getMember(targetUuid);
                if (viewerMember == null || targetMember == null) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
                    return;
                }
                boolean canEdit = false;
                if (viewerMember.getRole() == TeamRole.OWNER) {
                    canEdit = true;
                } else if (viewerMember.getRole() == TeamRole.CO_OWNER) {
                    boolean bl = canEdit = targetMember.getRole() == TeamRole.MEMBER;
                }
                if (canEdit) {
                    new MemberEditGUI(this.plugin, team, player, targetUuid).open();
                    break;
                }
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
                break;
            }
            case "join-requests": {
                new JoinRequestGUI(this.plugin, player, team).open();
                break;
            }
            case "join-requests-locked": {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "join_requests_permission_denied", new TagResolver[0]);
                break;
            }
            case "warps": {
                if (!this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "warp")) {
                    return;
                }
                try {
                    Class.forName("eu.kotori.justTeams.gui.WarpsGUI");
                    this.teamManager.openWarpsGUI(player);
                } catch (ClassNotFoundException e) {
                    this.teamManager.listTeamWarps(player);
                }
                break;
            }
            case "bank": {
                TeamPlayer viewerMember = team.getMember(player.getUniqueId());
                if (viewerMember == null || !viewerMember.canWithdraw() && !player.hasPermission("justteams.bypass.bank.use")) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                    return;
                }
                new BankGUI(this.plugin, player, team).open();
                break;
            }
            case "bank-locked": {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "bank_permission_denied", new TagResolver[0]);
                break;
            }
            case "home": {
                TeamPlayer viewerMember = team.getMember(player.getUniqueId());
                if (viewerMember == null || !viewerMember.canUseHome() && !player.hasPermission("justteams.bypass.home.use")) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                    return;
                }
                if (!this.checkActionCooldown(player, "home", 5000L)) {
                    return;
                }
                this.teamManager.teleportToHome(player);
                break;
            }
            case "ender-chest": {
                TeamPlayer viewerMember;
                if (this.plugin.getConfigManager().isDebugEnabled()) {
                    this.plugin.getLogger().info("Enderchest clicked by " + player.getName() + " in team " + team.getName());
                }
                if ((viewerMember = team.getMember(player.getUniqueId())) == null) {
                    this.plugin.getLogger().warning("Player " + player.getName() + " not found in team " + team.getName());
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
                    return;
                }
                boolean hasPermission = viewerMember.canUseEnderChest();
                boolean hasBypass = player.hasPermission("justteams.bypass.enderchest.use");
                if (this.plugin.getConfigManager().isDebugEnabled()) {
                    this.plugin.getLogger().info("Enderchest permission check for " + player.getName() + " - canUseEnderChest: " + hasPermission + ", hasBypass: " + hasBypass + ", member: " + String.valueOf(viewerMember.getPlayerUuid()) + ", team: " + team.getName() + ", teamId: " + team.getId());
                }
                if (!hasPermission && !hasBypass) {
                    this.plugin.getLogger().warning("Player " + player.getName() + " attempted to access enderchest without permission!");
                    this.plugin.getLogger().warning("Permission details - canUseEnderChest: " + hasPermission + ", hasBypass: " + hasBypass);
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                    return;
                }
                if (!this.checkActionCooldown(player, "enderchest", 2000L)) {
                    if (this.plugin.getConfigManager().isDebugEnabled()) {
                        this.plugin.getLogger().info("Enderchest action blocked by cooldown for " + player.getName());
                    }
                    return;
                }
                if (this.plugin.getConfigManager().isDebugEnabled()) {
                    this.plugin.getLogger().info("Opening enderchest for " + player.getName());
                }
                this.teamManager.openEnderChest(player);
                break;
            }
            case "ender-chest-locked": {
                if (this.plugin.getConfigManager().isDebugEnabled()) {
                    this.plugin.getLogger().info("Enderchest-locked clicked by " + player.getName() + " in team " + team.getName());
                }
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "enderchest_permission_denied", new TagResolver[0]);
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                break;
            }
            case "sort": {
                team.cycleSortType();
                new TeamGUI(this.plugin, team, player).open();
                break;
            }
            case "pvp-toggle": {
                if (!this.checkActionCooldown(player, "pvp-toggle", 2000L)) {
                    return;
                }
                this.teamManager.togglePvpStatus(player);
                gui.initializeItems();
                break;
            }
            case "team-settings": {
                new TeamSettingsGUI(this.plugin, player, team).open();
                break;
            }
            case "settings": {
                new TeamSettingsGUI(this.plugin, player, team).open();
                break;
            }
            case "settings-locked": {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "settings_permission_denied", new TagResolver[0]);
                break;
            }
            case "disband-button": {
                if (!this.checkActionCooldown(player, "disband", 10000L)) {
                    return;
                }
                new ConfirmGUI(this.plugin, player, "Are you sure you want to disband your team? This cannot be undone.", confirmed -> {
                    if (confirmed.booleanValue()) {
                        this.teamManager.disbandTeam(player);
                    }
                }).open();
                break;
            }
            case "leave-button": {
                if (!this.checkActionCooldown(player, "leave", 5000L)) {
                    return;
                }
                new ConfirmGUI(this.plugin, player, "Are you sure you want to leave the team?", confirmed -> {
                    if (confirmed.booleanValue()) {
                        this.teamManager.leaveTeam(player);
                    }
                }).open();
                break;
            }
            case "blacklist": {
                new BlacklistGUI(this.plugin, team, player).open();
                break;
            }
            default: {
                this.plugin.getDebugLogger().log("Unknown TeamGUI action: " + action + " from " + player.getName());
            }
        }
    }

    private void onMemberEditGUIClick(Player player, MemberEditGUI gui, PersistentDataContainer pdc) {
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
        TeamPlayer targetMember = gui.getTargetMember();
        if (targetMember == null) {
            player.closeInventory();
            return;
        }
        switch (action) {
            case "promote-button": {
                if (!this.checkActionCooldown(player, "promote", 2000L)) {
                    return;
                }
                this.teamManager.promotePlayer(player, gui.getTargetUuid());
                break;
            }
            case "demote-button": {
                if (!this.checkActionCooldown(player, "demote", 2000L)) {
                    return;
                }
                this.teamManager.demotePlayer(player, gui.getTargetUuid());
                break;
            }
            case "kick-button": {
                if (!this.checkActionCooldown(player, "kick", 2000L)) {
                    return;
                }
                this.teamManager.kickPlayer(player, gui.getTargetUuid());
                break;
            }
            case "transfer-button": {
                if (!this.checkActionCooldown(player, "transfer", 5000L)) {
                    return;
                }
                this.teamManager.transferOwnership(player, gui.getTargetUuid());
                break;
            }
            case "back-button": {
                new TeamGUI(this.plugin, gui.getTeam(), player).open();
                break;
            }
            case "withdraw-permission": 
            case "enderchest-permission": 
            case "sethome-permission": 
            case "usehome-permission": {
                if (!this.checkActionCooldown(player, "permission-change", 1000L)) {
                    return;
                }
                boolean isSelfView = gui.getTargetUuid().equals(player.getUniqueId());
                if (isSelfView) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "cannot_edit_own_permissions", new TagResolver[0]);
                    return;
                }
                boolean canWithdraw = targetMember.canWithdraw();
                boolean canUseEC = targetMember.canUseEnderChest();
                boolean canSetHome = targetMember.canSetHome();
                boolean canUseHome = targetMember.canUseHome();
                switch (action) {
                    case "withdraw-permission": {
                        canWithdraw = !canWithdraw;
                        break;
                    }
                    case "enderchest-permission": {
                        canUseEC = !canUseEC;
                        break;
                    }
                    case "sethome-permission": {
                        canSetHome = !canSetHome;
                        break;
                    }
                    case "usehome-permission": {
                        canUseHome = !canUseHome;
                    }
                }
                this.teamManager.updateMemberPermissions(player, targetMember.getPlayerUuid(), canWithdraw, canUseEC, canSetHome, canUseHome);
                break;
            }
            case "withdraw-permission-view": 
            case "enderchest-permission-view": 
            case "sethome-permission-view": 
            case "usehome-permission-view": {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "view_only_mode", new TagResolver[0]);
                break;
            }
            default: {
                return;
            }
        }
        gui.initializeItems();
    }

    private void onBankGUIClick(Player player, BankGUI gui, PersistentDataContainer pdc) {
        String action;
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        switch (action = (String)pdc.get(this.actionKey, PersistentDataType.STRING)) {
            case "back-button": {
                new TeamGUI(this.plugin, gui.getTeam(), player).open();
                break;
            }
            case "withdraw-locked": {
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "gui_action_locked", new TagResolver[0]);
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                break;
            }
            case "deposit": 
            case "withdraw": {
                if (!this.checkActionCooldown(player, "bank-action", 1000L)) {
                    return;
                }
                player.closeInventory();
                boolean isDeposit = action.equals("deposit");
                String promptAction = isDeposit ? "deposit" : "withdraw";
                String prompt = this.plugin.getMessageManager().getRawMessage("prompt_bank_amount").replace("<action>", promptAction);
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, prompt, new TagResolver[0]);
                this.plugin.getChatInputManager().awaitInput(player, gui, input -> {
                    try {
                        double amount = Double.parseDouble(input);
                        if (amount <= 0.0) {
                            this.plugin.getMessageManager().sendMessage((CommandSender)player, "bank_invalid_amount", new TagResolver[0]);
                        } else if (amount > 1.0E9) {
                            this.plugin.getMessageManager().sendMessage((CommandSender)player, "bank_amount_too_large", new TagResolver[0]);
                        } else if (isDeposit) {
                            this.teamManager.deposit(player, amount);
                        } else {
                            this.teamManager.withdraw(player, amount);
                        }
                    } catch (NumberFormatException e) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "bank_invalid_amount", new TagResolver[0]);
                    }
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, gui::open);
                });
            }
        }
    }

    private void onTeamSettingsGUIClick(Player player, TeamSettingsGUI gui, PersistentDataContainer pdc) {
        String action;
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        switch (action = (String)pdc.get(this.actionKey, PersistentDataType.STRING)) {
            case "back-button": {
                new TeamGUI(this.plugin, gui.getTeam(), player).open();
                break;
            }
            case "toggle-public": {
                if (!this.checkActionCooldown(player, "toggle-public", 2000L)) {
                    return;
                }
                this.plugin.getTeamManager().togglePublicStatus(player);
                gui.initializeItems();
                break;
            }
            case "change-tag": 
            case "change-description": {
                String actionType;
                boolean isTag = action.equals("change-tag");
                if (isTag && !this.plugin.getConfigManager().isTeamTagEnabled()) {
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "feature_disabled", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                    return;
                }
                String string = actionType = action.equals("change-tag") ? "change-tag" : "change-description";
                if (!this.checkActionCooldown(player, actionType, 2000L)) {
                    return;
                }
                player.closeInventory();
                String setting = isTag ? "tag" : "description";
                String prompt = this.plugin.getMessageManager().getRawMessage("prompt_setting_change").replace("<setting>", setting);
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, prompt, new TagResolver[0]);
                this.plugin.getChatInputManager().awaitInput(player, gui, input -> {
                    if (isTag) {
                        this.teamManager.setTeamTag(player, (String)input);
                    } else {
                        this.teamManager.setTeamDescription(player, (String)input);
                    }
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, gui::open);
                });
            }
        }
    }

    private void onJoinRequestGUIClick(Player player, JoinRequestGUI gui, ClickType click, ItemStack clickedItem) {
        if (clickedItem == null) {
            return;
        }
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(this.actionKey, PersistentDataType.STRING)) {
            String playerUuidStr;
            String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
            if (action.equals("back-button")) {
                new TeamGUI(this.plugin, gui.getTeam(), player).open();
            } else if (action.equals("player-head") && (playerUuidStr = (String)pdc.get(new NamespacedKey((Plugin)JustTeams.getInstance(), "player_uuid"), PersistentDataType.STRING)) != null) {
                try {
                    UUID targetUuid = UUID.fromString(playerUuidStr);
                    if (click.isLeftClick()) {
                        this.teamManager.acceptJoinRequest(gui.getTeam(), targetUuid);
                    } else if (click.isRightClick()) {
                        this.teamManager.denyJoinRequest(gui.getTeam(), targetUuid);
                    }
                    gui.initializeItems();
                } catch (IllegalArgumentException e) {
                    this.plugin.getLogger().warning("Invalid UUID in join request GUI: " + playerUuidStr);
                }
            }
            return;
        }
    }

    private void onInvitesGUIClick(Player player, InvitesGUI gui, ClickType click, ItemStack clickedItem) {
        if (clickedItem == null) {
            return;
        }
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(this.actionKey, PersistentDataType.STRING)) {
            String action;
            switch (action = (String)pdc.get(this.actionKey, PersistentDataType.STRING)) {
                case "back-button": {
                    Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
                    if (team != null) {
                        new TeamGUI(this.plugin, team, player).open();
                        break;
                    }
                    new NoTeamGUI(this.plugin, player).open();
                    break;
                }
                case "close-button": {
                    player.closeInventory();
                    break;
                }
                case "team-icon": {
                    String teamIdStr = (String)pdc.get(new NamespacedKey((Plugin)this.plugin, "team_id"), PersistentDataType.STRING);
                    String teamName = (String)pdc.get(new NamespacedKey((Plugin)this.plugin, "team_name"), PersistentDataType.STRING);
                    if (teamIdStr == null || teamName == null) break;
                    try {
                        int teamId = Integer.parseInt(teamIdStr);
                        if (click.isLeftClick()) {
                            player.closeInventory();
                            this.teamManager.acceptInvite(player, teamName);
                            break;
                        }
                        if (!click.isRightClick()) break;
                        player.closeInventory();
                        this.teamManager.denyInvite(player, teamName);
                        break;
                    } catch (NumberFormatException e) {
                        this.plugin.getLogger().warning("Invalid team ID in invites GUI: " + teamIdStr);
                    }
                }
            }
        }
    }

    private void onLeaderboardCategoryGUIClick(Player player, PersistentDataContainer pdc) {
        String title;
        LeaderboardViewGUI.LeaderboardType type;
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
        if ("back-button".equals(action)) {
            this.plugin.getTaskRunner().runAsync(() -> {
                Team team = this.teamManager.getPlayerTeam(player.getUniqueId());
                if (team != null) {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> new TeamGUI(this.plugin, team, player).open());
                } else {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> new NoTeamGUI(this.plugin, player).open());
                }
            });
            return;
        }
        switch (action) {
            case "top-kills": {
                type = LeaderboardViewGUI.LeaderboardType.KILLS;
                title = "\u1d1b\u1d0f\u1d18 10 \u1d1b\u1d07\u1d00\u1d0ds \u0299\u028f \u1d0b\u026a\u029f\u029fs";
                break;
            }
            case "top-balance": {
                type = LeaderboardViewGUI.LeaderboardType.BALANCE;
                title = "\u1d1b\u1d0f\u1d18 10 \u1d1b\u1d07\u1d00\u1d0ds \u0299\u028f \u0299\u1d00\u029f\u1d00\u0274\u1d04\u1d07";
                break;
            }
            case "top-members": {
                type = LeaderboardViewGUI.LeaderboardType.MEMBERS;
                title = "\u1d1b\u1d0f\u1d18 10 \u1d1b\u1d07\u1d00\u1d0ds \u0299\u028f \u1d0d\u1d07\u1d0d\u0299\u1d07\u0280s";
                break;
            }
            default: {
                return;
            }
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            Map<Integer, Team> finalTopTeams = switch (type) {
                case LeaderboardViewGUI.LeaderboardType.KILLS -> this.plugin.getStorageManager().getStorage().getTopTeamsByKills(10);
                case LeaderboardViewGUI.LeaderboardType.BALANCE -> this.plugin.getStorageManager().getStorage().getTopTeamsByBalance(10);
                case LeaderboardViewGUI.LeaderboardType.MEMBERS -> this.plugin.getStorageManager().getStorage().getTopTeamsByMembers(10);
                default -> Map.of();
            };
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> new LeaderboardViewGUI(this.plugin, player, title, finalTopTeams, type).open());
        });
    }

    private void onLeaderboardViewGUIClick(Player player, PersistentDataContainer pdc) {
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
        if (action.equals("back-button")) {
            new LeaderboardCategoryGUI(this.plugin, player).open();
        }
    }

    private void onNoTeamGUIClick(Player player, PersistentDataContainer pdc) {
        String action;
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        switch (action = (String)pdc.get(this.actionKey, PersistentDataType.STRING)) {
            case "create-team": {
                player.closeInventory();
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("prompt_team_name"), new TagResolver[0]);
                this.plugin.getChatInputManager().awaitInput(player, null, teamName -> {
                    String validationError = this.teamManager.validateTeamName((String)teamName);
                    if (validationError != null) {
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("prefix") + validationError, new TagResolver[0]);
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> new NoTeamGUI(this.plugin, player).open());
                        return;
                    }
                    if (this.plugin.getConfigManager().isTeamTagEnabled()) {
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, this.plugin.getMessageManager().getRawMessage("prompt_team_tag"), new TagResolver[0]);
                        this.plugin.getChatInputManager().awaitInput(player, null, teamTag -> this.teamManager.createTeam(player, (String)teamName, (String)teamTag));
                    } else {
                        this.teamManager.createTeam(player, (String)teamName, "");
                    }
                });
                break;
            }
            case "leaderboards": {
                new LeaderboardCategoryGUI(this.plugin, player).open();
            }
        }
    }

    private void onAdminGUIClick(Player player, PersistentDataContainer pdc) {
        String action;
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        switch (action = (String)pdc.get(this.actionKey, PersistentDataType.STRING)) {
            case "back-button": 
            case "close": {
                player.closeInventory();
                break;
            }
            case "manage-teams": {
                this.plugin.getTaskRunner().runAsync(() -> {
                    List<Team> allTeams = this.teamManager.getAllTeams();
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> new AdminTeamListGUI(this.plugin, player, allTeams, 0).open());
                });
                break;
            }
            case "view-enderchest": {
                player.closeInventory();
                this.plugin.getChatInputManager().awaitInput(player, null, input -> {
                    if (input == null || input.trim().isEmpty()) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_input", new TagResolver[0]);
                        return;
                    }
                    this.teamManager.adminOpenEnderChest(player, input.trim());
                });
                this.plugin.getMessageManager().sendMessage((CommandSender)player, "admin_enderchest_input_prompt", new TagResolver[0]);
                break;
            }
            case "reload-plugin": {
                player.closeInventory();
                this.plugin.getTaskRunner().runAsync(() -> {
                    try {
                        this.plugin.getConfigManager().reloadConfig();
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                            this.plugin.getMessageManager().sendMessage((CommandSender)player, "admin_reload_success", new TagResolver[0]);
                            EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                        });
                    } catch (Exception e) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                            this.plugin.getMessageManager().sendMessage((CommandSender)player, "admin_reload_failed", new TagResolver[0]);
                            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                        });
                    }
                });
                break;
            }
            default: {
                this.plugin.getDebugLogger().log("Unknown admin GUI action: " + action + " from " + player.getName());
            }
        }
    }

    private void onAdminTeamListGUIClick(Player player, AdminTeamListGUI gui, ItemStack clickedItem, PersistentDataContainer pdc) {
        String action;
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        switch (action = (String)pdc.get(this.actionKey, PersistentDataType.STRING)) {
            case "next-page": {
                new AdminTeamListGUI(this.plugin, player, gui.getAllTeams(), gui.getPage() + 1).open();
                break;
            }
            case "previous-page": {
                new AdminTeamListGUI(this.plugin, player, gui.getAllTeams(), gui.getPage() - 1).open();
                break;
            }
            case "back-button": {
                new AdminGUI(this.plugin, player).open();
                break;
            }
            case "team-head": {
                Component displayName = clickedItem.getItemMeta().displayName();
                if (displayName == null) {
                    return;
                }
                String plainName = PlainTextComponentSerializer.plainText().serialize(displayName);
                this.plugin.getTaskRunner().runAsync(() -> {
                    Team targetTeam = this.teamManager.getTeamByName(plainName);
                    if (targetTeam != null) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> new AdminTeamManageGUI(this.plugin, player, targetTeam).open());
                    }
                });
            }
        }
    }

    private void onAdminTeamManageGUIClick(Player player, AdminTeamManageGUI gui, PersistentDataContainer pdc) {
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
        Team team = gui.getTargetTeam();
        switch (action) {
            case "back-button": {
                this.plugin.getTaskRunner().runAsync(() -> {
                    List<Team> allTeams = this.teamManager.getAllTeams();
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> new AdminTeamListGUI(this.plugin, player, allTeams, 0).open());
                });
                break;
            }
            case "disband-team": {
                new ConfirmGUI(this.plugin, player, "Disband " + team.getName() + "?", confirmed -> {
                    if (confirmed.booleanValue()) {
                        this.teamManager.adminDisbandTeam(player, team.getName());
                    } else {
                        gui.open();
                    }
                }).open();
                break;
            }
            case "rename-team": {
                player.closeInventory();
                this.plugin.getChatInputManager().awaitInput(player, null, input -> {
                    if (input == null || input.trim().isEmpty()) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_input", new TagResolver[0]);
                        return;
                    }
                    String newName = input.trim();
                    int minLength = this.plugin.getConfigManager().getMinNameLength();
                    int maxLength = this.plugin.getConfigManager().getMaxNameLength();
                    if (newName.length() < minLength || newName.length() > maxLength) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "name_too_long", new TagResolver[0]);
                        return;
                    }
                    if (!newName.matches("^[a-zA-Z0-9_]+$")) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_team_name", new TagResolver[0]);
                        return;
                    }
                    if (this.teamManager.getTeamByName(newName) != null) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "team_name_exists", new TagResolver[]{Placeholder.unparsed((String)"team", (String)newName)});
                        return;
                    }
                    String oldName = team.getName();
                    team.setName(newName);
                    this.plugin.getStorageManager().getStorage().setTeamName(team.getId(), newName);
                    this.teamManager.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "name_change|" + newName);
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "rename_success", new TagResolver[]{Placeholder.unparsed((String)"old_name", (String)oldName), Placeholder.unparsed((String)"new_name", (String)newName)});
                    new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage()).open();
                });
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<yellow>Enter the new team name in chat:", new TagResolver[0]);
                break;
            }
            case "edit-description": {
                player.closeInventory();
                this.plugin.getChatInputManager().awaitInput(player, null, input -> {
                    if (input == null || input.trim().isEmpty()) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_input", new TagResolver[0]);
                        return;
                    }
                    String newDesc = input.trim();
                    if (newDesc.length() > this.plugin.getConfigManager().getMaxDescriptionLength()) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "description_too_long", new TagResolver[0]);
                        return;
                    }
                    team.setDescription(newDesc);
                    this.plugin.getStorageManager().getStorage().setTeamDescription(team.getId(), newDesc);
                    this.teamManager.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "description_change");
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "description_set", new TagResolver[0]);
                    new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage()).open();
                });
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<yellow>Enter the new description in chat:", new TagResolver[0]);
                break;
            }
            case "edit-tag": {
                player.closeInventory();
                this.plugin.getChatInputManager().awaitInput(player, null, input -> {
                    if (input == null || input.trim().isEmpty()) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_input", new TagResolver[0]);
                        return;
                    }
                    String newTag = input.trim();
                    team.setTag(newTag);
                    this.plugin.getStorageManager().getStorage().setTeamTag(team.getId(), newTag);
                    this.teamManager.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "tag_change|" + newTag);
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "tag_set", new TagResolver[]{Placeholder.unparsed((String)"tag", (String)newTag)});
                    new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage()).open();
                    team.setTag(newTag);
                    this.plugin.getStorageManager().getStorage().setTeamTag(team.getId(), newTag);
                    this.plugin.getMessageManager().sendMessage((CommandSender)player, "tag_set", new TagResolver[]{Placeholder.unparsed((String)"tag", (String)newTag)});
                    new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage()).open();
                });
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<yellow>Enter the new tag in chat:", new TagResolver[0]);
                break;
            }
            case "toggle-public": {
                boolean newStatus = !team.isPublic();
                team.setPublic(newStatus);
                this.plugin.getStorageManager().getStorage().setPublicStatus(team.getId(), newStatus);
                this.teamManager.publishCrossServerUpdate(team.getId(), "PUBLIC_STATUS_CHANGED", player.getUniqueId().toString(), String.valueOf(newStatus));
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, newStatus ? "<green>Team is now public" : "<red>Team is now private", new TagResolver[0]);
                new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage()).open();
                break;
            }
            case "toggle-pvp": {
                boolean newStatus = !team.isPvpEnabled();
                team.setPvpEnabled(newStatus);
                this.plugin.getStorageManager().getStorage().setPvpStatus(team.getId(), newStatus);
                this.teamManager.publishCrossServerUpdate(team.getId(), "PVP_STATUS_CHANGED", player.getUniqueId().toString(), String.valueOf(newStatus));
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, newStatus ? "<green>PvP enabled" : "<red>PvP disabled", new TagResolver[0]);
                new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage()).open();
                break;
            }
            case "edit-balance": {
                player.closeInventory();
                this.plugin.getChatInputManager().awaitInput(player, null, input -> {
                    if (input == null || input.trim().isEmpty()) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_input", new TagResolver[0]);
                        return;
                    }
                    try {
                        double newBalance = Double.parseDouble(input.trim());
                        if (newBalance < 0.0) {
                            this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Balance cannot be negative", new TagResolver[0]);
                            return;
                        }
                        double oldBalance = team.getBalance();
                        team.setBalance(newBalance);
                        this.plugin.getStorageManager().getStorage().updateTeamBalance(team.getId(), newBalance);
                        this.teamManager.publishCrossServerUpdate(team.getId(), "ADMIN_BALANCE_SET", player.getUniqueId().toString(), String.valueOf(newBalance));
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<green>Balance set to <white>" + String.format("%.2f", newBalance), new TagResolver[0]);
                        new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage()).open();
                    } catch (NumberFormatException e) {
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Invalid number format", new TagResolver[0]);
                    }
                });
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<yellow>Enter the new balance:", new TagResolver[0]);
                break;
            }
            case "edit-stats": {
                player.closeInventory();
                this.plugin.getChatInputManager().awaitInput(player, null, input -> {
                    if (input == null || input.trim().isEmpty()) {
                        this.plugin.getMessageManager().sendMessage((CommandSender)player, "invalid_input", new TagResolver[0]);
                        return;
                    }
                    String[] parts = input.trim().split(" ");
                    if (parts.length != 2) {
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Usage: <kills> <deaths>", new TagResolver[0]);
                        return;
                    }
                    try {
                        int kills = Integer.parseInt(parts[0]);
                        int deaths = Integer.parseInt(parts[1]);
                        if (kills < 0 || deaths < 0) {
                            this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Stats cannot be negative", new TagResolver[0]);
                            return;
                        }
                        team.setKills(kills);
                        team.setDeaths(deaths);
                        this.plugin.getStorageManager().getStorage().updateTeamStats(team.getId(), kills, deaths);
                        this.teamManager.publishCrossServerUpdate(team.getId(), "ADMIN_STATS_SET", player.getUniqueId().toString(), kills + ":" + deaths);
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<green>Stats updated: <white>" + kills + " kills, " + deaths + " deaths", new TagResolver[0]);
                        new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage()).open();
                    } catch (NumberFormatException e) {
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Invalid number format", new TagResolver[0]);
                    }
                });
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<yellow>Enter kills and deaths (e.g., '100 50'):", new TagResolver[0]);
                break;
            }
            case "view-enderchest": {
                player.closeInventory();
                this.teamManager.adminOpenEnderChest(player, team.getName());
                break;
            }
            case "next-members": {
                new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage() + 1).open();
                break;
            }
            case "prev-members": {
                new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage() - 1).open();
                break;
            }
            case "refresh": {
                new AdminTeamManageGUI(this.plugin, player, team, gui.getMemberPage()).open();
                break;
            }
            default: {
                if (!action.startsWith("member-")) break;
                String uuidStr = action.substring(7);
                try {
                    UUID memberUuid = UUID.fromString(uuidStr);
                    TeamPlayer member = team.getMember(memberUuid);
                    if (member == null) {
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Member not found!", new TagResolver[0]);
                        return;
                    }
                    if (memberUuid.equals(team.getOwnerUuid())) {
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Cannot edit the team owner!", new TagResolver[0]);
                        return;
                    }
                    new AdminMemberEditGUI(this.plugin, player, team, member).open();
                    break;
                } catch (IllegalArgumentException e) {
                    this.plugin.getLogger().warning("Invalid UUID in admin member click: " + uuidStr);
                }
            }
        }
    }

    private void onAdminMemberEditGUIClick(Player player, AdminMemberEditGUI gui, PersistentDataContainer pdc) {
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
        Team team = gui.getTeam();
        TeamPlayer member = gui.getMember();
        switch (action) {
            case "back-button": {
                new AdminTeamManageGUI(this.plugin, player, team, 0).open();
                break;
            }
            case "kick-member": {
                this.plugin.getTaskRunner().runAsync(() -> {
                    this.plugin.getStorageManager().getStorage().removeMemberFromTeam(member.getPlayerUuid());
                    OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer((UUID)member.getPlayerUuid());
                    String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
                    this.teamManager.publishCrossServerUpdate(team.getId(), "ADMIN_MEMBER_KICK", player.getUniqueId().toString(), member.getPlayerUuid().toString());
                    this.plugin.getTaskRunner().run(() -> {
                        team.removeMember(member.getPlayerUuid());
                        this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<green>Kicked <white>" + targetName + " <green>from the team", new TagResolver[0]);
                        new AdminTeamManageGUI(this.plugin, player, team, 0).open();
                    });
                });
                this.plugin.getTaskRunner().runTaskLater(() -> new AdminTeamManageGUI(this.plugin, player, team, 0).open(), 10L);
                break;
            }
            case "toggle-withdraw": {
                boolean newValue = !member.canWithdraw();
                member.setCanWithdraw(newValue);
                try {
                    this.plugin.getStorageManager().getStorage().updateMemberPermission(team.getId(), member.getPlayerUuid(), "withdraw", newValue);
                } catch (Exception e) {
                    this.plugin.getLogger().severe("Failed to update withdraw permission: " + e.getMessage());
                }
                this.teamManager.publishCrossServerUpdate(team.getId(), "ADMIN_PERMISSION_UPDATE", player.getUniqueId().toString(), String.valueOf(member.getPlayerUuid()) + ":withdraw:" + newValue);
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, (newValue ? "<green>Enabled" : "<red>Disabled") + " <white>withdraw permission", new TagResolver[0]);
                new AdminMemberEditGUI(this.plugin, player, team, member).open();
                break;
            }
            case "toggle-enderchest": {
                boolean newValue = !member.canUseEnderChest();
                member.setCanUseEnderChest(newValue);
                try {
                    this.plugin.getStorageManager().getStorage().updateMemberPermission(team.getId(), member.getPlayerUuid(), "enderchest", newValue);
                } catch (Exception e) {
                    this.plugin.getLogger().severe("Failed to update enderchest permission: " + e.getMessage());
                }
                this.teamManager.publishCrossServerUpdate(team.getId(), "ADMIN_PERMISSION_UPDATE", player.getUniqueId().toString(), String.valueOf(member.getPlayerUuid()) + ":enderchest:" + newValue);
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, (newValue ? "<green>Enabled" : "<red>Disabled") + " <white>enderchest permission", new TagResolver[0]);
                new AdminMemberEditGUI(this.plugin, player, team, member).open();
                break;
            }
            case "toggle-sethome": {
                boolean newValue = !member.canSetHome();
                member.setCanSetHome(newValue);
                try {
                    this.plugin.getStorageManager().getStorage().updateMemberPermission(team.getId(), member.getPlayerUuid(), "sethome", newValue);
                } catch (Exception e) {
                    this.plugin.getLogger().severe("Failed to update sethome permission: " + e.getMessage());
                }
                this.teamManager.publishCrossServerUpdate(team.getId(), "ADMIN_PERMISSION_UPDATE", player.getUniqueId().toString(), String.valueOf(member.getPlayerUuid()) + ":sethome:" + newValue);
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, (newValue ? "<green>Enabled" : "<red>Disabled") + " <white>set home permission", new TagResolver[0]);
                new AdminMemberEditGUI(this.plugin, player, team, member).open();
                break;
            }
            case "toggle-usehome": {
                boolean newValue = !member.canUseHome();
                member.setCanUseHome(newValue);
                try {
                    this.plugin.getStorageManager().getStorage().updateMemberPermission(team.getId(), member.getPlayerUuid(), "usehome", newValue);
                } catch (Exception e) {
                    this.plugin.getLogger().severe("Failed to update usehome permission: " + e.getMessage());
                }
                this.teamManager.publishCrossServerUpdate(team.getId(), "ADMIN_PERMISSION_UPDATE", player.getUniqueId().toString(), String.valueOf(member.getPlayerUuid()) + ":usehome:" + newValue);
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, (newValue ? "<green>Enabled" : "<red>Disabled") + " <white>use home permission", new TagResolver[0]);
                new AdminMemberEditGUI(this.plugin, player, team, member).open();
                break;
            }
            case "promote-member": {
                if (member.getRole() == TeamRole.OWNER) {
                    this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Member is already the owner!", new TagResolver[0]);
                    return;
                }
                if (member.getRole() == TeamRole.CO_OWNER) {
                    this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Member is already a co-owner!", new TagResolver[0]);
                    return;
                }
                member.setRole(TeamRole.CO_OWNER);
                this.plugin.getStorageManager().getStorage().updateMemberRole(team.getId(), member.getPlayerUuid(), TeamRole.CO_OWNER);
                this.teamManager.publishCrossServerUpdate(team.getId(), "ADMIN_MEMBER_PROMOTE", player.getUniqueId().toString(), member.getPlayerUuid().toString());
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<green>Promoted member to Co-Owner", new TagResolver[0]);
                new AdminMemberEditGUI(this.plugin, player, team, member).open();
                break;
            }
            case "demote-member": {
                if (member.getRole() == TeamRole.MEMBER) {
                    this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Member is already at the lowest rank!", new TagResolver[0]);
                    return;
                }
                if (member.getRole() == TeamRole.OWNER) {
                    this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<red>Cannot demote the owner!", new TagResolver[0]);
                    return;
                }
                member.setRole(TeamRole.MEMBER);
                this.plugin.getStorageManager().getStorage().updateMemberRole(team.getId(), member.getPlayerUuid(), TeamRole.MEMBER);
                this.teamManager.publishCrossServerUpdate(team.getId(), "ADMIN_MEMBER_DEMOTE", player.getUniqueId().toString(), member.getPlayerUuid().toString());
                this.plugin.getMessageManager().sendRawMessage((CommandSender)player, "<green>Demoted member to regular Member", new TagResolver[0]);
                new AdminMemberEditGUI(this.plugin, player, team, member).open();
            }
        }
    }

    private void onConfirmGUIClick(ConfirmGUI gui, PersistentDataContainer pdc) {
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
        if (action.equals("confirm")) {
            gui.handleConfirm();
        } else if (action.equals("cancel")) {
            gui.handleCancel();
        }
    }

    private void onWarpsGUIClick(Player player, WarpsGUI gui, ClickType click, ItemStack clickedItem, PersistentDataContainer pdc) {
        String warpName;
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            return;
        }
        String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
        if (action.equals("back-button")) {
            new TeamGUI(this.plugin, gui.getTeam(), player).open();
        } else if (action.equals("warp_item") && (warpName = (String)pdc.get(new NamespacedKey((Plugin)JustTeams.getInstance(), "warp_name"), PersistentDataType.STRING)) != null) {
            if (click.isLeftClick()) {
                if (!this.plugin.getFeatureRestrictionManager().canAffordAndPay(player, "warp")) {
                    return;
                }
                this.plugin.getTeamManager().teleportToTeamWarp(player, warpName, null);
                player.closeInventory();
            } else if (click.isRightClick()) {
                this.plugin.getTeamManager().deleteTeamWarp(player, warpName);
                gui.initializeItems();
            }
        }
    }

    private void onBlacklistGUIClick(Player player, BlacklistGUI gui, ClickType click, ItemStack clickedItem, PersistentDataContainer pdc) {
        if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getDebugLogger().log("=== BLACKLIST GUI CLICK DEBUG ===");
            this.plugin.getDebugLogger().log("Player: " + player.getName());
            this.plugin.getDebugLogger().log("Click type: " + String.valueOf(click));
            this.plugin.getDebugLogger().log("Clicked item: " + String.valueOf(clickedItem != null ? clickedItem.getType() : "null"));
            this.plugin.getDebugLogger().log("PDC has action key: " + pdc.has(this.actionKey, PersistentDataType.STRING));
        }
        if (!pdc.has(this.actionKey, PersistentDataType.STRING)) {
            this.plugin.getLogger().warning("No action key found in PDC for blacklist click");
            return;
        }
        String action = (String)pdc.get(this.actionKey, PersistentDataType.STRING);
        this.plugin.getLogger().info("Action retrieved: " + action);
        if (action.equals("back-button")) {
            this.plugin.getLogger().info("Back button clicked, opening team GUI");
            new TeamGUI(this.plugin, gui.getTeam(), player).open();
        } else if (action.startsWith("remove-blacklist:")) {
            UUID targetUuid;
            this.plugin.getLogger().info("Remove blacklist action detected: " + action);
            if (!this.checkActionCooldown(player, "remove-blacklist", 2000L)) {
                this.plugin.getLogger().info("Rate limit hit for blacklist removal by " + player.getName());
                return;
            }
            String uuidString = action.substring("remove-blacklist:".length());
            this.plugin.getLogger().info("UUID string extracted: " + uuidString);
            try {
                targetUuid = UUID.fromString(uuidString);
                this.plugin.getLogger().info("UUID parsed successfully: " + String.valueOf(targetUuid));
                this.plugin.getLogger().info("Team ID: " + gui.getTeam().getId());
            } catch (IllegalArgumentException e) {
                this.plugin.getLogger().warning("Invalid UUID format in blacklist removal action: " + uuidString);
                return;
            }
            BlacklistGUI finalGui = gui;
            UUID finalTargetUuid = targetUuid;
            this.plugin.getLogger().info("Starting async blacklist removal...");
            this.plugin.getTaskRunner().runAsync(() -> {
                try {
                    this.plugin.getLogger().info("Executing blacklist removal in async thread for " + String.valueOf(finalTargetUuid));
                    this.plugin.getLogger().info("Storage manager: " + String.valueOf(this.plugin.getStorageManager()));
                    this.plugin.getLogger().info("Storage: " + String.valueOf(this.plugin.getStorageManager().getStorage()));
                    boolean success = this.plugin.getStorageManager().getStorage().removePlayerFromBlacklist(finalGui.getTeam().getId(), finalTargetUuid);
                    this.plugin.getLogger().info("Blacklist removal result: " + success + " for " + String.valueOf(finalTargetUuid));
                    if (success) {
                        this.plugin.getLogger().info("Blacklist removal successful, refreshing GUI...");
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                            try {
                                this.plugin.getMessageManager().sendMessage((CommandSender)player, "player_removed_from_blacklist", new TagResolver[]{Placeholder.unparsed((String)"target", (String)Bukkit.getOfflinePlayer((UUID)finalTargetUuid).getName())});
                                this.plugin.getLogger().info("Success message sent, now refreshing GUI for " + player.getName());
                                finalGui.refresh();
                                this.plugin.getLogger().info("GUI refresh called successfully");
                            } catch (Exception e) {
                                this.plugin.getLogger().severe("Error in sync thread for blacklist removal: " + e.getMessage());
                                this.plugin.getLogger().severe("Error in GUI action: " + e.getMessage());
                            }
                        });
                    } else {
                        this.plugin.getLogger().warning("Blacklist removal failed in database");
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "remove_blacklist_failed", new TagResolver[0]));
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().severe("Error removing player from blacklist: " + e.getMessage());
                    this.plugin.getLogger().severe("Error in GUI action: " + e.getMessage());
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.plugin.getMessageManager().sendMessage((CommandSender)player, "remove_blacklist_failed", new TagResolver[0]));
                }
            });
        } else {
            this.plugin.getLogger().warning("Unknown action in blacklist GUI: " + action);
        }
        if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getDebugLogger().log("=== END BLACKLIST GUI CLICK DEBUG ===");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        boolean isOurGui;
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        boolean bl = isOurGui = holder instanceof IRefreshableGUI || holder instanceof NoTeamGUI || holder instanceof ConfirmGUI || holder instanceof AdminGUI || holder instanceof AdminTeamListGUI || holder instanceof AdminTeamManageGUI || holder instanceof TeamSettingsGUI || holder instanceof LeaderboardCategoryGUI || holder instanceof LeaderboardViewGUI || holder instanceof JoinRequestGUI || holder instanceof InvitesGUI || holder instanceof WarpsGUI || holder instanceof BlacklistGUI;
        if (isOurGui) {
            event.setCancelled(true);
        }
    }
}

