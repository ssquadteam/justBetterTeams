package eu.kotori.justTeams.team;

import eu.kotori.justTeams.team.TeamRole;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TeamPlayer {
    private final UUID playerUuid;
    private volatile TeamRole role;
    private final Instant joinDate;
    private volatile boolean canWithdraw;
    private volatile boolean canUseEnderChest;
    private volatile boolean canSetHome;
    private volatile boolean canUseHome;
    private volatile boolean canEditMembers;
    private volatile boolean canEditCoOwners;
    private volatile boolean canKickMembers;
    private volatile boolean canPromoteMembers;
    private volatile boolean canDemoteMembers;
    private volatile boolean permissionsDirty;
    private volatile long permissionsVersion;

    public TeamPlayer(UUID playerUuid, TeamRole role, Instant joinDate, boolean canWithdraw, boolean canUseEnderChest, boolean canSetHome, boolean canUseHome) {
        this.playerUuid = playerUuid;
        this.role = role;
        this.joinDate = joinDate;
        this.canWithdraw = canWithdraw;
        this.canUseEnderChest = canUseEnderChest;
        this.canSetHome = canSetHome;
        this.canUseHome = canUseHome;
        this.setDefaultEditingPermissions();
        this.permissionsDirty = false;
        this.permissionsVersion = 0L;
    }

    public TeamPlayer(UUID playerUuid, TeamRole role, Instant joinDate, boolean canWithdraw, boolean canUseEnderChest, boolean canSetHome, boolean canUseHome, boolean canEditMembers, boolean canEditCoOwners, boolean canKickMembers, boolean canPromoteMembers, boolean canDemoteMembers) {
        this.playerUuid = playerUuid;
        this.role = role;
        this.joinDate = joinDate;
        this.canWithdraw = canWithdraw;
        this.canUseEnderChest = canUseEnderChest;
        this.canSetHome = canSetHome;
        this.canUseHome = canUseHome;
        this.canEditMembers = canEditMembers;
        this.canEditCoOwners = canEditCoOwners;
        this.canKickMembers = canKickMembers;
        this.canPromoteMembers = canPromoteMembers;
        this.canDemoteMembers = canDemoteMembers;
        this.permissionsDirty = false;
        this.permissionsVersion = 0L;
    }

    private void setDefaultEditingPermissions() {
        switch (this.role) {
            case OWNER: {
                this.canEditMembers = true;
                this.canEditCoOwners = true;
                this.canKickMembers = true;
                this.canPromoteMembers = true;
                this.canDemoteMembers = true;
                break;
            }
            case CO_OWNER: {
                this.canEditMembers = true;
                this.canEditCoOwners = false;
                this.canKickMembers = true;
                this.canPromoteMembers = false;
                this.canDemoteMembers = false;
                break;
            }
            case MEMBER: {
                this.canEditMembers = false;
                this.canEditCoOwners = false;
                this.canKickMembers = false;
                this.canPromoteMembers = false;
                this.canDemoteMembers = false;
            }
        }
    }

    public UUID getPlayerUuid() {
        return this.playerUuid;
    }

    public TeamRole getRole() {
        return this.role;
    }

    public Instant getJoinDate() {
        return this.joinDate;
    }

    public void setRole(TeamRole role) {
        if (this.role != role) {
            this.role = role;
            this.setDefaultEditingPermissions();
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canWithdraw() {
        return this.canWithdraw;
    }

    public void setCanWithdraw(boolean canWithdraw) {
        if (this.canWithdraw != canWithdraw) {
            this.canWithdraw = canWithdraw;
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canUseEnderChest() {
        return this.canUseEnderChest;
    }

    public void setCanUseEnderChest(boolean canUseEnderChest) {
        if (this.canUseEnderChest != canUseEnderChest) {
            this.canUseEnderChest = canUseEnderChest;
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canSetHome() {
        return this.canSetHome;
    }

    public void setCanSetHome(boolean canSetHome) {
        if (this.canSetHome != canSetHome) {
            this.canSetHome = canSetHome;
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canUseHome() {
        return this.canUseHome;
    }

    public void setCanUseHome(boolean canUseHome) {
        if (this.canUseHome != canUseHome) {
            this.canUseHome = canUseHome;
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canEditMembers() {
        return this.canEditMembers;
    }

    public void setCanEditMembers(boolean canEditMembers) {
        if (this.canEditMembers != canEditMembers) {
            this.canEditMembers = canEditMembers;
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canEditCoOwners() {
        return this.canEditCoOwners;
    }

    public void setCanEditCoOwners(boolean canEditCoOwners) {
        if (this.canEditCoOwners != canEditCoOwners) {
            this.canEditCoOwners = canEditCoOwners;
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canKickMembers() {
        return this.canKickMembers;
    }

    public void setCanKickMembers(boolean canKickMembers) {
        if (this.canKickMembers != canKickMembers) {
            this.canKickMembers = canKickMembers;
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canPromoteMembers() {
        return this.canPromoteMembers;
    }

    public void setCanPromoteMembers(boolean canPromoteMembers) {
        if (this.canPromoteMembers != canPromoteMembers) {
            this.canPromoteMembers = canPromoteMembers;
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canDemoteMembers() {
        return this.canDemoteMembers;
    }

    public void setCanDemoteMembers(boolean canDemoteMembers) {
        if (this.canDemoteMembers != canDemoteMembers) {
            this.canDemoteMembers = canDemoteMembers;
            this.permissionsDirty = true;
            this.permissionsVersion++;
        }
    }

    public boolean canEditPlayer(TeamPlayer target) {
        if (target == null) {
            return false;
        }
        if (this.playerUuid.equals(target.getPlayerUuid())) {
            return false;
        }
        if (this.role == TeamRole.OWNER) {
            return true;
        }
        if (this.role == TeamRole.CO_OWNER) {
            if (target.getRole() == TeamRole.MEMBER) {
                return this.canEditMembers;
            }
            if (target.getRole() == TeamRole.CO_OWNER) {
                return this.canEditCoOwners;
            }
        }
        return false;
    }

    public boolean canKickPlayer(TeamPlayer target) {
        if (target == null) {
            return false;
        }
        if (this.playerUuid.equals(target.getPlayerUuid())) {
            return false;
        }
        if (this.role == TeamRole.OWNER) {
            return true;
        }
        if (this.role == TeamRole.CO_OWNER && target.getRole() == TeamRole.MEMBER) {
            return this.canKickMembers;
        }
        return false;
    }

    public boolean canPromotePlayer(TeamPlayer target) {
        if (target == null) {
            return false;
        }
        if (this.playerUuid.equals(target.getPlayerUuid())) {
            return false;
        }
        if (this.role == TeamRole.OWNER) {
            return target.getRole() == TeamRole.MEMBER;
        }
        return false;
    }

    public boolean canDemotePlayer(TeamPlayer target) {
        if (target == null) {
            return false;
        }
        if (this.playerUuid.equals(target.getPlayerUuid())) {
            return false;
        }
        if (this.role == TeamRole.OWNER) {
            return target.getRole() == TeamRole.CO_OWNER;
        }
        return false;
    }

    public Player getBukkitPlayer() {
        return Bukkit.getPlayer((UUID)this.playerUuid);
    }

    public boolean isOnline() {
        Player player = this.getBukkitPlayer();
        return player != null && player.isOnline();
    }

    public boolean isPermissionsDirty() {
        return this.permissionsDirty;
    }

    public long getPermissionsVersion() {
        return this.permissionsVersion;
    }

    public void clearPermissionsDirty() {
        this.permissionsDirty = false;
    }
}

