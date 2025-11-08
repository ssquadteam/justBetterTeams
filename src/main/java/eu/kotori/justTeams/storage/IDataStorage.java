package eu.kotori.justTeams.storage;

import eu.kotori.justTeams.team.BlacklistedPlayer;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

public interface IDataStorage {
    public boolean init();

    public void shutdown();

    public void cleanup();

    public boolean isConnected();

    public Optional<Team> createTeam(String var1, String var2, UUID var3, boolean var4, boolean var5);

    public void deleteTeam(int var1);

    public boolean addMemberToTeam(int var1, UUID var2);

    public void removeMemberFromTeam(UUID var1);

    public Optional<Team> findTeamByPlayer(UUID var1);

    public Optional<Team> findTeamByName(String var1);

    public Optional<Team> findTeamById(int var1);

    public List<Team> getAllTeams();

    public List<TeamPlayer> getTeamMembers(int var1);

    public void setTeamHome(int var1, Location var2, String var3);

    public void deleteTeamHome(int var1);

    public Optional<TeamHome> getTeamHome(int var1);

    public void setTeamTag(int var1, String var2);

    public void setTeamDescription(int var1, String var2);

    public void transferOwnership(int var1, UUID var2, UUID var3);

    public void setPvpStatus(int var1, boolean var2);

    public void setPublicStatus(int var1, boolean var2);

    public void updateTeamBalance(int var1, double var2);

    public void updateTeamStats(int var1, int var2, int var3);

    public void saveEnderChest(int var1, String var2);

    public String getEnderChest(int var1);

    public void updateMemberPermissions(int var1, UUID var2, boolean var3, boolean var4, boolean var5, boolean var6) throws SQLException;

    public void updateMemberPermission(int var1, UUID var2, String var3, boolean var4) throws SQLException;

    public void updateMemberRole(int var1, UUID var2, TeamRole var3);

    public void updateMemberEditingPermissions(int var1, UUID var2, boolean var3, boolean var4, boolean var5, boolean var6, boolean var7);

    public Map<Integer, Team> getTopTeamsByKills(int var1);

    public Map<Integer, Team> getTopTeamsByBalance(int var1);

    public Map<Integer, Team> getTopTeamsByMembers(int var1);

    public void updateServerHeartbeat(String var1);

    public Map<String, Timestamp> getActiveServers();

    public void addPendingTeleport(UUID var1, String var2, Location var3);

    public Optional<Location> getAndRemovePendingTeleport(UUID var1, String var2);

    public boolean acquireEnderChestLock(int var1, String var2);

    public void releaseEnderChestLock(int var1);

    public Optional<TeamEnderChestLock> getEnderChestLock(int var1);

    public void addJoinRequest(int var1, UUID var2);

    public void removeJoinRequest(int var1, UUID var2);

    public List<UUID> getJoinRequests(int var1);

    public boolean hasJoinRequest(int var1, UUID var2);

    public void clearAllJoinRequests(UUID var1);

    public void setWarp(int var1, String var2, Location var3, String var4, String var5);

    public void deleteWarp(int var1, String var2);

    public Optional<TeamWarp> getWarp(int var1, String var2);

    public List<TeamWarp> getWarps(int var1);

    public int getTeamWarpCount(int var1);

    public boolean teamWarpExists(int var1, String var2);

    public boolean setTeamWarp(int var1, String var2, String var3, String var4, String var5);

    public boolean deleteTeamWarp(int var1, String var2);

    public Optional<TeamWarp> getTeamWarp(int var1, String var2);

    public List<TeamWarp> getTeamWarps(int var1);

    public void addCrossServerUpdate(int var1, String var2, String var3, String var4);

    public void addCrossServerUpdatesBatch(List<CrossServerUpdate> var1);

    public List<CrossServerUpdate> getCrossServerUpdates(String var1);

    public void removeCrossServerUpdate(int var1);

    public void addCrossServerMessage(int var1, String var2, String var3, String var4);

    public List<CrossServerMessage> getCrossServerMessages(String var1);

    public void removeCrossServerMessage(int var1);

    public void cleanupAllEnderChestLocks();

    public void cleanupStaleEnderChestLocks(int var1);

    public boolean addPlayerToBlacklist(int var1, UUID var2, String var3, String var4, UUID var5, String var6) throws SQLException;

    public boolean removePlayerFromBlacklist(int var1, UUID var2) throws SQLException;

    public boolean isPlayerBlacklisted(int var1, UUID var2) throws SQLException;

    public List<BlacklistedPlayer> getTeamBlacklist(int var1) throws SQLException;

    public Optional<UUID> getPlayerUuidByName(String var1);

    public void cachePlayerName(UUID var1, String var2);

    public Optional<String> getPlayerNameByUuid(UUID var1);

    public void addTeamInvite(int var1, UUID var2, UUID var3);

    public void removeTeamInvite(int var1, UUID var2);

    public boolean hasTeamInvite(int var1, UUID var2);

    public List<Integer> getPlayerInvites(UUID var1);

    public List<TeamInvite> getPlayerInvitesWithDetails(UUID var1);

    public void clearPlayerInvites(UUID var1);

    public void updatePlayerSession(UUID var1, String var2);

    public Optional<PlayerSession> getPlayerSession(UUID var1);

    public Map<UUID, PlayerSession> getTeamPlayerSessions(int var1);

    public void cleanupStaleSessions(int var1);

    public void setServerAlias(String var1, String var2);

    public Optional<String> getServerAlias(String var1);

    public Map<String, String> getAllServerAliases();

    public void removeServerAlias(String var1);

    public void setTeamRenameTimestamp(int var1, Timestamp var2);

    public Optional<Timestamp> getTeamRenameTimestamp(int var1);

    public void setTeamName(int var1, String var2);

    public record PlayerSession(UUID playerUuid, String serverName, Timestamp lastSeen) {
    }

    public record TeamInvite(int teamId, String teamName, UUID inviterUuid, String inviterName, Timestamp createdAt) {
    }

    public record CrossServerMessage(int id, int teamId, String playerUuid, String message, String serverName, Timestamp timestamp) {
    }

    public record CrossServerUpdate(int id, int teamId, String updateType, String playerUuid, String serverName, Timestamp timestamp) {
    }

    public record TeamEnderChestLock(int teamId, String serverName, Timestamp lockTime) {
    }

    public record TeamWarp(String name, String location, String serverName, String password) {
    }

    public record TeamHome(Location location, String serverName) {
    }
}

