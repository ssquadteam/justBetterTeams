package eu.kotori.justTeams.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.team.BlacklistedPlayer;
import eu.kotori.justTeams.storage.IDataStorage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CacheManager {
    private final JustTeams plugin;
    private final Cache<Integer, List<BlacklistedPlayer>> blacklistCache;
    private final Cache<Integer, List<UUID>> joinRequestCache;
    private final Cache<String, Boolean> permissionCache;
    private final Cache<UUID, String> playerNameCache;
    private final Cache<Integer, List<IDataStorage.TeamWarp>> warpsCache;
    private final Cache<Integer, Map<UUID, IDataStorage.PlayerSession>> teamSessionsCache;
    private final Cache<String, String> placeholderResultCache;
    private final Map<Integer, Long> lastDatabaseSync = new ConcurrentHashMap<Integer, Long>();
    private final long cacheExpiry;
    private final long syncCooldown;

    public CacheManager(JustTeams plugin) {
        this.plugin = plugin;
        this.cacheExpiry = plugin.getConfig().getLong("settings.sync_optimization.team_cache_ttl", 900L);
        this.syncCooldown = plugin.getConfig().getLong("settings.sync_optimization.cache_cleanup_interval", 600L) * 1000L;
        this.blacklistCache = CacheBuilder.newBuilder().maximumSize(1000L).expireAfterWrite(this.cacheExpiry, TimeUnit.SECONDS).build();
        this.joinRequestCache = CacheBuilder.newBuilder().maximumSize(1000L).expireAfterWrite(this.cacheExpiry, TimeUnit.SECONDS).build();
        this.permissionCache = CacheBuilder.newBuilder().maximumSize(5000L).expireAfterWrite(this.cacheExpiry, TimeUnit.SECONDS).build();
        this.playerNameCache = CacheBuilder.newBuilder().maximumSize(10000L).expireAfterWrite(1800L, TimeUnit.SECONDS).build();
        this.warpsCache = CacheBuilder.newBuilder().maximumSize(2000L).expireAfterWrite(this.cacheExpiry, TimeUnit.SECONDS).build();
        this.teamSessionsCache = CacheBuilder.newBuilder().maximumSize(2000L).expireAfterWrite(300L, TimeUnit.SECONDS).build();
        this.placeholderResultCache = CacheBuilder.newBuilder().maximumSize(1000L).expireAfterWrite(5L, TimeUnit.SECONDS).build();
    }

    public List<BlacklistedPlayer> getTeamBlacklist(int teamId) {
        return this.blacklistCache.getIfPresent(teamId);
    }

    public void cacheTeamBlacklist(int teamId, List<BlacklistedPlayer> blacklist) {
        this.blacklistCache.put(teamId, blacklist);
        this.lastDatabaseSync.put(teamId, System.currentTimeMillis());
    }

    public void invalidateTeamBlacklist(int teamId) {
        this.blacklistCache.invalidate(teamId);
        this.lastDatabaseSync.remove(teamId);
    }

    public List<UUID> getJoinRequests(int teamId) {
        return this.joinRequestCache.getIfPresent(teamId);
    }

    public void cacheJoinRequests(int teamId, List<UUID> requests) {
        this.joinRequestCache.put(teamId, requests);
    }

    public void invalidateJoinRequests(int teamId) {
        this.joinRequestCache.invalidate(teamId);
    }

    public Boolean getPermissionResult(String key) {
        return this.permissionCache.getIfPresent(key);
    }

    public void cachePermissionResult(String key, boolean result) {
        this.permissionCache.put(key, result);
    }

    public String getPlayerName(UUID playerUuid) {
        return this.playerNameCache.getIfPresent(playerUuid);
    }

    public void cachePlayerName(UUID playerUuid, String name) {
        this.playerNameCache.put(playerUuid, name);
    }

    public List<IDataStorage.TeamWarp> getTeamWarps(int teamId) {
        return this.warpsCache.getIfPresent(teamId);
    }

    public void cacheTeamWarps(int teamId, List<IDataStorage.TeamWarp> warps) {
        this.warpsCache.put(teamId, warps);
    }

    public void invalidateTeamWarps(int teamId) {
        this.warpsCache.invalidate(teamId);
    }

    public Map<UUID, IDataStorage.PlayerSession> getTeamSessions(int teamId) {
        return this.teamSessionsCache.getIfPresent(teamId);
    }

    public void cacheTeamSessions(int teamId, Map<UUID, IDataStorage.PlayerSession> sessions) {
        this.teamSessionsCache.put(teamId, sessions);
    }

    public void invalidateTeamSessions(int teamId) {
        this.teamSessionsCache.invalidate(teamId);
    }

    public String getCachedPlaceholder(UUID playerUuid, String placeholder) {
        return this.placeholderResultCache.getIfPresent(playerUuid.toString() + ":" + placeholder.toLowerCase());
    }

    public void cachePlaceholderResult(UUID playerUuid, String placeholder, String value) {
        if (value != null) {
            this.placeholderResultCache.put(playerUuid.toString() + ":" + placeholder.toLowerCase(), value);
        }
    }

    public void invalidatePlayerPlaceholders(UUID playerUuid) {
        String prefix = playerUuid.toString() + ":";
        this.placeholderResultCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    public boolean needsDatabaseSync(int teamId) {
        Long lastSync = this.lastDatabaseSync.get(teamId);
        return lastSync == null || System.currentTimeMillis() - lastSync > this.syncCooldown;
    }

    public void markSynced(int teamId) {
        this.lastDatabaseSync.put(teamId, System.currentTimeMillis());
    }

    public void cleanup() {
        this.blacklistCache.cleanUp();
        this.joinRequestCache.cleanUp();
        this.permissionCache.cleanUp();
        this.playerNameCache.cleanUp();
        this.warpsCache.cleanUp();
        this.teamSessionsCache.cleanUp();
        this.placeholderResultCache.cleanUp();
        long cutoff = System.currentTimeMillis() - this.syncCooldown * 2L;
        this.lastDatabaseSync.entrySet().removeIf(entry -> (Long)entry.getValue() < cutoff);
    }

    public void invalidateAll() {
        this.blacklistCache.invalidateAll();
        this.joinRequestCache.invalidateAll();
        this.permissionCache.invalidateAll();
        this.playerNameCache.invalidateAll();
        this.warpsCache.invalidateAll();
        this.teamSessionsCache.invalidateAll();
        this.placeholderResultCache.invalidateAll();
        this.lastDatabaseSync.clear();
    }

    public CacheStats getStats() {
        return new CacheStats(this.blacklistCache.size(), this.joinRequestCache.size(), this.permissionCache.size(), this.playerNameCache.size(), this.placeholderResultCache.size(), this.warpsCache.size(), this.teamSessionsCache.size(), this.lastDatabaseSync.size());
    }

    public static class CacheStats {
        public final long blacklistCacheSize;
        public final long joinRequestCacheSize;
        public final long permissionCacheSize;
        public final long playerNameCacheSize;
        public final long placeholderCacheSize;
        public final long warpsCacheSize;
        public final long teamSessionsCacheSize;
        public final int syncTrackingSize;

        public CacheStats(long blacklistCacheSize, long joinRequestCacheSize, long permissionCacheSize, long playerNameCacheSize, long placeholderCacheSize, long warpsCacheSize, long teamSessionsCacheSize, int syncTrackingSize) {
            this.blacklistCacheSize = blacklistCacheSize;
            this.joinRequestCacheSize = joinRequestCacheSize;
            this.permissionCacheSize = permissionCacheSize;
            this.playerNameCacheSize = playerNameCacheSize;
            this.placeholderCacheSize = placeholderCacheSize;
            this.warpsCacheSize = warpsCacheSize;
            this.teamSessionsCacheSize = teamSessionsCacheSize;
            this.syncTrackingSize = syncTrackingSize;
        }
    }
}

