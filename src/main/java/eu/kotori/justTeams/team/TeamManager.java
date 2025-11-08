package eu.kotori.justTeams.team;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.config.ConfigManager;
import eu.kotori.justTeams.config.MessageManager;
import eu.kotori.justTeams.gui.ConfirmGUI;
import eu.kotori.justTeams.gui.MemberEditGUI;
import eu.kotori.justTeams.gui.TeamGUI;
import eu.kotori.justTeams.gui.admin.AdminGUI;
import eu.kotori.justTeams.storage.IDataStorage;
import eu.kotori.justTeams.team.BlacklistedPlayer;
import eu.kotori.justTeams.team.Team;
import eu.kotori.justTeams.team.TeamPlayer;
import eu.kotori.justTeams.team.TeamRole;
import eu.kotori.justTeams.util.CancellableTask;
import eu.kotori.justTeams.util.EffectsUtil;
import eu.kotori.justTeams.util.InventoryUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamManager {
    private final JustTeams plugin;
    private final IDataStorage storage;
    private final MessageManager messageManager;
    private final ConfigManager configManager;
    private final Map<String, Team> teamNameCache = new ConcurrentHashMap<String, Team>();
    private final Map<UUID, Team> playerTeamCache = new ConcurrentHashMap<UUID, Team>();
    private final Cache<UUID, List<String>> teamInvites;
    private final Cache<UUID, Instant> joinRequestCooldowns;
    private final Map<UUID, Instant> homeCooldowns = new ConcurrentHashMap<UUID, Instant>();
    private final Map<UUID, CancellableTask> teleportTasks = new ConcurrentHashMap<UUID, CancellableTask>();
    private final Map<UUID, Instant> warpCooldowns = new ConcurrentHashMap<UUID, Instant>();
    private final Map<UUID, Instant> teamStatusCooldowns = new ConcurrentHashMap<UUID, Instant>();
    private final Map<Integer, Long> teamLastModified = new ConcurrentHashMap<Integer, Long>();
    private final Object cacheLock = new Object();
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final ConcurrentHashMap<Integer, Long> lastSyncTimes = new ConcurrentHashMap();
    private static final long SYNC_COOLDOWN = 5000L;
    private static final long TEAM_STATE_TIMEOUT_TICKS = 40L; // ~2s
    private final List<IDataStorage.CrossServerUpdate> pendingCrossServerUpdates = new CopyOnWriteArrayList<IDataStorage.CrossServerUpdate>();
    private final Object crossServerUpdateLock = new Object();
    private final Map<UUID, Long> loadingPlayersUntil = new ConcurrentHashMap<>();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void markTeamModified(int teamId) {
        if (teamId <= 0) {
            return;
        }
        Object object = this.cacheLock;
        synchronized (object) {
            this.teamLastModified.put(teamId, System.currentTimeMillis());
        }
    }

    private String formatCurrency(double amount) {
        if (amount >= 1.0E9) {
            return String.format("%.2fB", amount / 1.0E9);
        }
        if (amount >= 1000000.0) {
            return String.format("%.2fM", amount / 1000000.0);
        }
        if (amount >= 1000.0) {
            return String.format("%.2fK", amount / 1000.0);
        }
        return String.format("%.2f", amount);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean hasTeamBeenModified(int teamId, long withinMs) {
        Object object = this.cacheLock;
        synchronized (object) {
            Long lastModified = this.teamLastModified.get(teamId);
            if (lastModified == null) {
                return false;
            }
            return System.currentTimeMillis() - lastModified < withinMs;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private List<Team> getRecentlyModifiedTeams(long withinMs) {
        Object object = this.cacheLock;
        synchronized (object) {
            return this.teamNameCache.values().stream().filter(team -> this.hasTeamBeenModified(team.getId(), withinMs)).collect(Collectors.toList());
        }
    }

    public TeamManager(JustTeams plugin) {
        this.plugin = plugin;
        this.storage = plugin.getStorageManager().getStorage();
        this.messageManager = plugin.getMessageManager();
        this.configManager = plugin.getConfigManager();
        this.teamInvites = CacheBuilder.newBuilder().expireAfterWrite(5L, TimeUnit.MINUTES).build();
        this.joinRequestCooldowns = CacheBuilder.newBuilder().expireAfterWrite(1L, TimeUnit.MINUTES).build();
    }

    public void publishCrossServerUpdate(int teamId, String updateType, String playerUuid, String data) {
        if (this.plugin.getRedisManager() != null && this.plugin.getRedisManager().isAvailable()) {
            this.plugin.getRedisManager().publishTeamUpdate(teamId, updateType, playerUuid, data).thenAccept(success -> {
                if (!success.booleanValue()) {
                    this.plugin.getLogger().info("Redis publish failed for " + updateType + ", using MySQL fallback");
                }
            });
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            String serverName = this.plugin.getConfigManager().getServerIdentifier();
            this.storage.addCrossServerUpdate(teamId, updateType, playerUuid != null ? playerUuid : "", "ALL_SERVERS");
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().info("\u2713 Wrote cross-server update to MySQL: " + updateType + " for team " + teamId + " (fallback for non-Redis servers)");
            }
        });
    }

    public void handlePendingTeleport(Player player) {
        String currentServer = this.plugin.getConfigManager().getServerIdentifier();
        UUID effective = this.getEffectiveUuid(player.getUniqueId());
        this.plugin.getDebugLogger().log("Handling pending teleport check for " + player.getName() + " on server " + currentServer);
        this.plugin.getTaskRunner().runAsync(() -> this.storage.getAndRemovePendingTeleport(effective, currentServer).ifPresent(location -> {
            this.plugin.getDebugLogger().log("Found pending teleport for " + player.getName() + " to " + String.valueOf(location));
            this.plugin.getTaskRunner().runEntityTaskLater((Entity)player, () -> this.teleportPlayer(player, (Location)location), 5L);
        }));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<Team> getAllTeams() {
        Object object = this.cacheLock;
        synchronized (object) {
            if (this.teamNameCache.isEmpty()) {
                List<Team> dbTeams = this.storage.getAllTeams();
                for (Team team : dbTeams) {
                    this.loadTeamIntoCache(team);
                }
            }
            return new ArrayList<Team>(this.teamNameCache.values());
        }
    }

    public void adminDisbandTeam(Player admin, String teamName) {
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> teamOpt = this.storage.findTeamByName(teamName);
            this.plugin.getTaskRunner().runOnEntity((Entity)admin, () -> {
                if (teamOpt.isEmpty()) {
                    this.messageManager.sendMessage((CommandSender)admin, "team_not_found", new TagResolver[0]);
                    return;
                }
                Team team = (Team)teamOpt.get();
                new ConfirmGUI(this.plugin, admin, "Disband " + team.getName() + "?", confirmed -> {
                    if (confirmed.booleanValue()) {
                        this.plugin.getTaskRunner().runAsync(() -> {
                            this.storage.deleteTeam(team.getId());
                            this.publishCrossServerUpdate(team.getId(), "TEAM_DISBANDED", admin.getUniqueId().toString(), team.getName());
                            this.plugin.getTaskRunner().run(() -> {
                                this.invalidateTeamPlaceholders(team);
                                team.broadcast("admin_team_disbanded_broadcast", new TagResolver[0]);
                                this.uncacheTeam(team.getId());
                                this.messageManager.sendMessage((CommandSender)admin, "admin_team_disbanded", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                                EffectsUtil.playSound(admin, EffectsUtil.SoundType.SUCCESS);
                            });
                        });
                    } else {
                        new AdminGUI(this.plugin, admin).open();
                    }
                }).open();
            });
        });
    }

    public void adminOpenEnderChest(Player admin, String teamNameOrTag) {
        if (!this.plugin.getConfigManager().isTeamEnderchestEnabled()) {
            this.messageManager.sendMessage((CommandSender)admin, "feature_disabled", new TagResolver[0]);
            return;
        }
        if (!admin.hasPermission("justteams.admin.enderchest")) {
            this.messageManager.sendMessage((CommandSender)admin, "no_permission", new TagResolver[0]);
            return;
        }
        if (teamNameOrTag == null || teamNameOrTag.trim().isEmpty() || teamNameOrTag.length() > 32) {
            this.messageManager.sendMessage((CommandSender)admin, "invalid_input", new TagResolver[0]);
            return;
        }
        this.plugin.getLogger().info("Admin " + admin.getName() + " accessing enderchest for team: " + teamNameOrTag);
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> teamOpt = this.storage.findTeamByName(teamNameOrTag);
            if (teamOpt.isEmpty()) {
                teamOpt = this.storage.getAllTeams().stream().filter(team -> team != null && team.getTag() != null && team.getTag().equalsIgnoreCase(teamNameOrTag)).findFirst();
            }
            Optional<Team> finalTeamOpt = teamOpt;
            this.plugin.getTaskRunner().runOnEntity((Entity)admin, () -> {
                if (finalTeamOpt.isEmpty()) {
                    this.messageManager.sendMessage((CommandSender)admin, "team_not_found", new TagResolver[0]);
                    return;
                }
                Team team = (Team)finalTeamOpt.get();
                if (this.plugin.getConfigManager().isSingleServerMode()) {
                    this.loadAndOpenEnderChestDirect(admin, team);
                } else if (team.tryLockEnderChest()) {
                    this.plugin.getTaskRunner().runAsync(() -> {
                        boolean lockAcquired = this.storage.acquireEnderChestLock(team.getId(), this.configManager.getServerIdentifier());
                        this.plugin.getTaskRunner().runOnEntity((Entity)admin, () -> {
                            if (lockAcquired) {
                                this.loadAndOpenEnderChestDirect(admin, team);
                            } else {
                                team.unlockEnderChest();
                                this.messageManager.sendMessage((CommandSender)admin, "enderchest_in_use", new TagResolver[0]);
                                EffectsUtil.playSound(admin, EffectsUtil.SoundType.ERROR);
                            }
                        });
                    });
                } else {
                    this.messageManager.sendMessage((CommandSender)admin, "enderchest_in_use", new TagResolver[0]);
                    EffectsUtil.playSound(admin, EffectsUtil.SoundType.ERROR);
                }
            });
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void loadTeamIntoCache(Team team) {
        String lowerCaseName = team.getName().toLowerCase();
        List<TeamPlayer> members = this.storage.getTeamMembers(team.getId());
        List<UUID> joinRequests = this.storage.getJoinRequests(team.getId());
        Object object = this.cacheLock;
        synchronized (object) {
            if (this.teamNameCache.containsKey(lowerCaseName)) {
                Team cachedTeam = this.teamNameCache.get(lowerCaseName);
                cachedTeam.getMembers().forEach(member -> this.playerTeamCache.put(member.getPlayerUuid(), cachedTeam));
                return;
            }
            team.getMembers().clear();
            team.getMembers().addAll(members);
            team.getJoinRequests().clear();
            for (UUID requestUuid : joinRequests) {
                team.addJoinRequest(requestUuid);
            }
            this.teamNameCache.put(lowerCaseName, team);
            team.getMembers().forEach(member -> this.playerTeamCache.put(member.getPlayerUuid(), team));
            this.plugin.getLogger().info("Loaded team " + team.getName() + " with " + team.getMembers().size() + " members and " + joinRequests.size() + " join requests");
        }
        List<UUID> memberUuids = team.getMembers().stream().map(TeamPlayer::getPlayerUuid).collect(Collectors.toList());
        this.resolvePlayerNames(memberUuids, resolved -> {});
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void uncacheTeam(int teamId) {
        Object object = this.cacheLock;
        synchronized (object) {
            Team team = this.teamNameCache.values().stream().filter(t -> t.getId() == teamId).findFirst().orElse(null);
            if (team != null) {
                if (team.getEnderChest() != null) {
                    this.saveEnderChest(team);
                }
                this.teamNameCache.remove(team.getName().toLowerCase());
                team.getMembers().forEach(member -> this.playerTeamCache.remove(member.getPlayerUuid()));
                this.teamLastModified.remove(teamId);
            }
        }
        this.plugin.getCacheManager().invalidateTeamWarps(teamId);
        this.plugin.getCacheManager().invalidateTeamBlacklist(teamId);
        this.plugin.getCacheManager().invalidateTeamSessions(teamId);
        this.plugin.getCacheManager().invalidateJoinRequests(teamId);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void addTeamToCache(Team team) {
        Object object = this.cacheLock;
        synchronized (object) {
            this.teamNameCache.put(team.getName().toLowerCase(), team);
            for (TeamPlayer member : team.getMembers()) {
                this.playerTeamCache.put(member.getPlayerUuid(), team);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void removeFromPlayerTeamCache(UUID playerUuid) {
        UUID effective = this.getEffectiveUuid(playerUuid);
        if (this.plugin.getConfigManager().isDebugEnabled() && !effective.equals(playerUuid)) {
            this.plugin.getDebugLogger().log("UUID normalized on cache remove: raw=" + playerUuid + ", effective=" + effective);
        }
        this.playerTeamCache.remove(effective);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void addPlayerToTeamCache(UUID playerUuid, Team team) {
        UUID effective = this.getEffectiveUuid(playerUuid);
        if (this.plugin.getConfigManager().isDebugEnabled() && !effective.equals(playerUuid)) {
            this.plugin.getDebugLogger().log("UUID normalized on cache put: raw=" + playerUuid + ", effective=" + effective);
        }
        this.playerTeamCache.put(effective, team);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Optional<Team> getTeamById(int teamId) {
        Object object = this.cacheLock;
        synchronized (object) {
            Team cachedTeam = this.teamNameCache.values().stream().filter(t -> t.getId() == teamId).findFirst().orElse(null);
            if (cachedTeam != null) {
                return Optional.of(cachedTeam);
            }
            Optional<Team> dbTeam = this.storage.findTeamById(teamId);
            if (dbTeam.isPresent()) {
                this.loadTeamIntoCache(dbTeam.get());
                return dbTeam;
            }
            return Optional.empty();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    /**
     * WARNING: Performs database I/O on cache miss. Do NOT call on hot paths or plugin threads that must not block.
     * Prefer getPlayerTeam (cache-only) or getPlayerTeamAsync for non-blocking access.
     */
    private Team getPlayerTeamWithFallback(UUID playerUuid) {
        Object object = this.cacheLock;
        synchronized (object) {
            UUID effectiveUuid = this.getEffectiveUuid(playerUuid);
            Team cachedTeam = this.playerTeamCache.get(effectiveUuid);
            if (cachedTeam != null) {
                return cachedTeam;
            }
            Optional<Team> dbTeam = this.storage.findTeamByPlayer(effectiveUuid);
            if (dbTeam.isPresent()) {
                this.loadTeamIntoCache(dbTeam.get());
                return dbTeam.get();
            }
            return null;
        }
    }

    /**
     * Cache-only team lookup. Never blocks on database I/O.
     * @return Team if cached, null otherwise. Never blocks on database queries. Safe to call from any thread including PlaceholderAPI threads.
     */
    public Team getPlayerTeam(UUID playerUuid) {
        UUID effectiveUuid = this.getEffectiveUuid(playerUuid);
        return this.playerTeamCache.get(effectiveUuid);
    }

    public void requireTeamAsync(Player player, Consumer<Team> callback) {
        this.requireTeamAsync(player, TEAM_STATE_TIMEOUT_TICKS, callback);
    }

    public void requireTeamAsync(Player player, long timeoutTicks, Consumer<Team> callback) {
        UUID raw = player.getUniqueId();
        Team cached = this.getPlayerTeam(raw);
        if (cached != null) {
            this.plugin.getTaskRunner().run(() -> callback.accept(cached));
            return;
        }
        AtomicBoolean completed = new AtomicBoolean(false);
        UUID effective = this.getEffectiveUuid(raw);
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> dbTeam = this.storage.findTeamByPlayer(effective);
            Team result = null;
            if (dbTeam.isPresent()) {
                Team team = dbTeam.get();
                this.loadTeamIntoCache(team);
                result = team;
            }
            Team finalResult = result;
            if (completed.compareAndSet(false, true)) {
                this.plugin.getTaskRunner().run(() -> callback.accept(finalResult));
            }
        });
        this.plugin.getTaskRunner().runTaskLater(() -> {
            if (completed.compareAndSet(false, true)) {
                Team fallback = this.getPlayerTeam(raw);
                callback.accept(fallback);
            }
        }, timeoutTicks);
    }

    public void requireTeamStateAsync(Player player, Consumer<Boolean> hasTeamCallback) {
        this.requireTeamStateAsync(player, TEAM_STATE_TIMEOUT_TICKS, hasTeamCallback);
    }

    public void requireTeamStateAsync(Player player, long timeoutTicks, Consumer<Boolean> hasTeamCallback) {
        this.requireTeamAsync(player, timeoutTicks, team -> hasTeamCallback.accept(team != null));
    }

    public void requireOwnershipAsync(Player player, Consumer<Boolean> isOwnerCallback) {
        this.requireOwnershipAsync(player, TEAM_STATE_TIMEOUT_TICKS, isOwnerCallback);
    }

    public void requireOwnershipAsync(Player player, long timeoutTicks, Consumer<Boolean> isOwnerCallback) {
        this.requireTeamAsync(player, timeoutTicks, team -> {
            if (team == null) {
                isOwnerCallback.accept(false);
                return;
            }
            UUID effective = this.getEffectiveUuid(player.getUniqueId());
            isOwnerCallback.accept(team.isOwner(effective));
        });
    }

    public void markPlayerLoading(UUID playerUuid, long millis) {
        this.loadingPlayersUntil.put(playerUuid, System.currentTimeMillis() + millis);
    }

    public boolean isPlayerLoading(UUID playerUuid) {
        Long until = this.loadingPlayersUntil.get(playerUuid);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            this.loadingPlayersUntil.remove(playerUuid);
            return false;
        }
        return true;
    }

    /**
     * Asynchronously resolves a player's team. If not cached, queries the database off the main thread,
     * warms the cache, then invokes the callback on the main thread.
     */
    public void getPlayerTeamAsync(UUID playerUuid, Consumer<Team> callback) {
        Team cached = this.getPlayerTeam(playerUuid);
        if (cached != null) {
            this.plugin.getTaskRunner().run(() -> callback.accept(cached));
            return;
        }
        UUID effectiveUuid = this.getEffectiveUuid(playerUuid);
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> dbTeam = this.storage.findTeamByPlayer(effectiveUuid);
            Team result = null;
            if (dbTeam.isPresent()) {
                Team team = dbTeam.get();
                this.loadTeamIntoCache(team);
                result = team;
            }
            Team finalResult = result;
            this.plugin.getTaskRunner().run(() -> callback.accept(finalResult));
        });
    }

    private void invalidateTeamPlaceholders(Team team) {
        if (team == null) return;
        try {
            for (TeamPlayer member : team.getMembers()) {
                this.plugin.getCacheManager().invalidatePlayerPlaceholders(member.getPlayerUuid());
            }
        } catch (Exception ignored) {}
    }

    private UUID getEffectiveUuid(UUID playerUuid) {
        if (!this.plugin.getConfigManager().isBedrockSupportEnabled()) {
            return playerUuid;
        }
        String uuidMode = this.plugin.getConfigManager().getUuidMode();
        switch (uuidMode.toLowerCase()) {
            case "floodgate": {
                if (this.plugin.getBedrockSupport().isBedrockPlayer(playerUuid)) {
                    return this.plugin.getBedrockSupport().getJavaEditionUuid(playerUuid);
                }
                return playerUuid;
            }
            case "bedrock": {
                return playerUuid;
            }
        }
        if (this.plugin.getBedrockSupport().isBedrockPlayer(playerUuid)) {
            return this.plugin.getBedrockSupport().getJavaEditionUuid(playerUuid);
        }
        return playerUuid;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Team getTeamByName(String teamName) {
        Object object = this.cacheLock;
        synchronized (object) {
            Team cachedTeam = this.teamNameCache.get(teamName.toLowerCase());
            if (cachedTeam != null) {
                return cachedTeam;
            }
            Optional<Team> dbTeam = this.storage.findTeamByName(teamName);
            if (dbTeam.isPresent()) {
                this.loadTeamIntoCache(dbTeam.get());
                return dbTeam.get();
            }
            return null;
        }
    }

    public void unloadPlayer(Player player) {
        Team team;
        CancellableTask task = this.teleportTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        if ((team = this.getPlayerTeam(player.getUniqueId())) != null) {
            UUID effective = this.getEffectiveUuid(player.getUniqueId());
            this.playerTeamCache.remove(effective);
            boolean isTeamEmptyOnline = team.getMembers().stream().allMatch(member -> member.getPlayerUuid().equals(effective) || !member.isOnline());
            if (isTeamEmptyOnline) {
                if (team.getEnderChest() != null && !team.getEnderChest().getViewers().isEmpty()) {
                    this.saveEnderChest(team);
                }
                this.uncacheTeam(team.getId());
            }
        }
    }

    public void loadPlayerTeam(Player player) {
        UUID effective = this.getEffectiveUuid(player.getUniqueId());
        if (this.playerTeamCache.containsKey(effective)) {
            return;
        }
        if (this.plugin.getConfigManager().isDebugEnabled() && !effective.equals(player.getUniqueId())) {
            this.plugin.getDebugLogger().log("Normalizing UUID for loadPlayerTeam: raw=" + player.getUniqueId() + ", effective=" + effective);
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> teamOpt = this.storage.findTeamByPlayer(effective);
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                if (teamOpt.isPresent()) {
                    Team team = (Team)teamOpt.get();
                    this.loadTeamIntoCache(team);
                    this.plugin.getTaskRunner().runAsync(() -> {
                        try {
                            if (this.plugin.getCacheManager().needsDatabaseSync(team.getId())) {
                                try {
                                    List<IDataStorage.TeamWarp> warps = this.storage.getWarps(team.getId());
                                    this.plugin.getCacheManager().cacheTeamWarps(team.getId(), warps);
                                } catch (Exception ignored) {
                                }
                                try {
                                    List<BlacklistedPlayer> blacklist = this.storage.getTeamBlacklist(team.getId());
                                    this.plugin.getCacheManager().cacheTeamBlacklist(team.getId(), blacklist);
                                } catch (Exception ignored) {
                                }
                            }
                        } catch (Exception e) {
                            this.plugin.getLogger().warning("Failed to pre-warm caches for team " + team.getName() + ": " + e.getMessage());
                        }
                    });
                    this.checkPendingJoinRequests(player, team);
                }
            });
        });
    }

    private void checkPendingJoinRequests(Player player, Team team) {
        List<UUID> requests;
        if (team.hasElevatedPermissions(player.getUniqueId()) && !(requests = this.storage.getJoinRequests(team.getId())).isEmpty()) {
            this.plugin.getLogger().info("Player " + player.getName() + " has " + requests.size() + " pending join requests");
            this.messageManager.sendMessage((CommandSender)player, "join_request_count", new TagResolver[]{Placeholder.unparsed((String)"count", (String)String.valueOf(requests.size()))});
            this.messageManager.sendMessage((CommandSender)player, "join_request_notification", new TagResolver[]{Placeholder.unparsed((String)"player", (String)"a player")});
        }
    }

    public String validateTeamName(String name) {
        String plainName = this.stripColorCodes(name);
        if (plainName.length() < this.configManager.getMinNameLength()) {
            return this.messageManager.getRawMessage("name_too_short").replace("<min_length>", String.valueOf(this.configManager.getMinNameLength()));
        }
        if (plainName.length() > this.configManager.getMaxNameLength()) {
            return this.messageManager.getRawMessage("name_too_long").replace("<max_length>", String.valueOf(this.configManager.getMaxNameLength()));
        }
        if (!plainName.matches("^[a-zA-Z0-9_]{" + this.configManager.getMinNameLength() + "," + this.configManager.getMaxNameLength() + "}$")) {
            return this.messageManager.getRawMessage("name_invalid");
        }
        if (this.storage.findTeamByName(plainName).isPresent() || this.teamNameCache.containsKey(plainName.toLowerCase())) {
            return this.messageManager.getRawMessage("team_name_exists").replace("<team>", plainName);
        }
        return null;
    }

    private String stripColorCodes(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)&[0-9A-FK-OR]", "").replaceAll("(?i)<#[0-9A-F]{6}>", "").replaceAll("(?i)</#[0-9A-F]{6}>", "");
    }

    public void createTeam(Player owner, String name, String tag) {
        this.plugin.getTaskRunner().runAsync(() -> {
            if (this.getPlayerTeam(owner.getUniqueId()) != null) {
                this.plugin.getTaskRunner().runOnEntity((Entity)owner, () -> {
                    this.messageManager.sendMessage((CommandSender)owner, "already_in_team", new TagResolver[0]);
                    EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                });
                return;
            }
            String nameError = this.validateTeamName(name);
            if (nameError != null) {
                this.plugin.getTaskRunner().runOnEntity((Entity)owner, () -> {
                    this.messageManager.sendRawMessage((CommandSender)owner, this.messageManager.getRawMessage("prefix") + nameError, new TagResolver[0]);
                    EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                });
                return;
            }
            String plainTag = this.stripColorCodes(tag);
            if (plainTag.length() > this.configManager.getMaxTagLength() || !plainTag.matches("[a-zA-Z0-9]+")) {
                this.plugin.getTaskRunner().runOnEntity((Entity)owner, () -> {
                    this.messageManager.sendMessage((CommandSender)owner, "tag_invalid", new TagResolver[0]);
                    EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
                });
                return;
            }
            boolean defaultPvp = this.configManager.getDefaultPvpStatus();
            boolean defaultPublic = this.configManager.isDefaultPublicStatus();
            this.storage.createTeam(name, tag, owner.getUniqueId(), defaultPvp, defaultPublic).ifPresent(team -> this.plugin.getTaskRunner().runOnEntity((Entity)owner, () -> {
                this.loadTeamIntoCache((Team)team);
                this.messageManager.sendMessage((CommandSender)owner, "team_created", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                this.plugin.getCacheManager().invalidatePlayerPlaceholders(owner.getUniqueId());
                EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
                this.plugin.getWebhookHelper().sendTeamCreateWebhook(owner, (Team)team);
                this.publishCrossServerUpdate(team.getId(), "TEAM_CREATED", owner.getUniqueId().toString(), team.getName());
                if (this.plugin.getConfigManager().isBroadcastTeamCreatedEnabled()) {
                    Component broadcastMessage = this.plugin.getMiniMessage().deserialize(this.plugin.getMessageManager().getRawMessage("team_created_broadcast"), new TagResolver[]{Placeholder.unparsed((String)"player", (String)owner.getName()), Placeholder.unparsed((String)"team", (String)team.getName())});
                    Bukkit.broadcast((Component)broadcastMessage);
                }
            }));
        });
    }

    public void disbandTeam(Player owner) {
        Team team = this.getPlayerTeam(owner.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)owner, "player_not_in_team", new TagResolver[0]);
            EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (!team.isOwner(owner.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)owner, "not_owner", new TagResolver[0]);
            EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
            return;
        }
        new ConfirmGUI(this.plugin, owner, "Disband " + team.getName() + "?", confirmed -> {
            if (confirmed.booleanValue()) {
                this.plugin.getTaskRunner().runAsync(() -> {
                    int memberCount = team.getMembers().size();
                    this.uncacheTeam(team.getId());
                    this.storage.deleteTeam(team.getId());
                    this.publishCrossServerUpdate(team.getId(), "TEAM_DISBANDED", owner.getUniqueId().toString(), team.getName());
                    this.plugin.getTaskRunner().run(() -> {
                        this.plugin.getWebhookHelper().sendTeamDeleteWebhook(owner, team, memberCount);
                        if (this.plugin.getConfigManager().isBroadcastTeamDisbandedEnabled()) {
                            team.broadcast("team_disbanded_broadcast", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                        }
                        this.invalidateTeamPlaceholders(team);
                        this.messageManager.sendMessage((CommandSender)owner, "team_disbanded", new TagResolver[0]);
                        EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
                    });
                });
            } else {
                new TeamGUI(this.plugin, team, owner).open();
            }
        }).open();
    }

    public void invitePlayer(Player inviter, Player target) {
        Team team = this.getPlayerTeam(inviter.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)inviter, "player_not_in_team", new TagResolver[0]);
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (!team.hasElevatedPermissions(inviter.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)inviter, "must_be_owner_or_co_owner", new TagResolver[0]);
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (inviter.getUniqueId().equals(target.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)inviter, "invite_self", new TagResolver[0]);
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (this.getPlayerTeam(target.getUniqueId()) != null) {
            this.messageManager.sendMessage((CommandSender)inviter, "target_already_in_team", new TagResolver[]{Placeholder.unparsed((String)"target", (String)target.getName())});
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (team.getMembers().size() >= this.configManager.getMaxTeamSize()) {
            this.messageManager.sendMessage((CommandSender)inviter, "team_is_full", new TagResolver[0]);
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        try {
            if (this.storage.isPlayerBlacklisted(team.getId(), target.getUniqueId())) {
                List<BlacklistedPlayer> blacklist = this.storage.getTeamBlacklist(team.getId());
                BlacklistedPlayer blacklistedPlayer = blacklist.stream().filter(bp -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(target.getUniqueId())).findFirst().orElse(null);
                String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                this.messageManager.sendMessage((CommandSender)inviter, "player_is_blacklisted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)target.getName()), Placeholder.unparsed((String)"blacklister", (String)blacklisterName)});
                EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                return;
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Could not check blacklist status for player " + target.getName() + " being invited to team " + team.getName() + ": " + e.getMessage());
        }
        List<String> invites = this.teamInvites.getIfPresent(target.getUniqueId());
        if (invites != null && invites.contains(team.getName().toLowerCase())) {
            this.messageManager.sendMessage((CommandSender)inviter, "invite_spam", new TagResolver[0]);
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (invites == null) {
            invites = new ArrayList<String>();
        }
        invites.add(team.getName().toLowerCase());
        this.teamInvites.put(target.getUniqueId(), invites);
        this.messageManager.sendMessage((CommandSender)inviter, "invite_sent", new TagResolver[]{Placeholder.unparsed((String)"target", (String)target.getName())});
        this.messageManager.sendRawMessage((CommandSender)target, this.messageManager.getRawMessage("prefix") + this.messageManager.getRawMessage("invite_received").replace("<team>", team.getName()), new TagResolver[0]);
        EffectsUtil.playSound(inviter, EffectsUtil.SoundType.SUCCESS);
        EffectsUtil.playSound(target, EffectsUtil.SoundType.SUCCESS);
    }

    public void invitePlayerByUuid(Player inviter, UUID targetUuid, String targetName) {
        Team team = this.getPlayerTeam(inviter.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)inviter, "player_not_in_team", new TagResolver[0]);
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (!team.hasElevatedPermissions(inviter.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)inviter, "must_be_owner_or_co_owner", new TagResolver[0]);
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (inviter.getUniqueId().equals(targetUuid)) {
            this.messageManager.sendMessage((CommandSender)inviter, "invite_self", new TagResolver[0]);
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (team.getMembers().size() >= this.configManager.getMaxTeamSize()) {
            this.messageManager.sendMessage((CommandSender)inviter, "team_is_full", new TagResolver[0]);
            EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                Optional<Team> existingTeam = this.storage.findTeamByPlayer(targetUuid);
                if (existingTeam.isPresent()) {
                    this.plugin.getTaskRunner().runOnEntity((Entity)inviter, () -> {
                        this.messageManager.sendMessage((CommandSender)inviter, "target_already_in_team", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)});
                        EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                    });
                    return;
                }
                if (this.storage.isPlayerBlacklisted(team.getId(), targetUuid)) {
                    List<BlacklistedPlayer> blacklist = this.storage.getTeamBlacklist(team.getId());
                    BlacklistedPlayer blacklistedPlayer = blacklist.stream().filter(bp -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(targetUuid)).findFirst().orElse(null);
                    String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                    this.plugin.getTaskRunner().runOnEntity((Entity)inviter, () -> {
                        this.messageManager.sendMessage((CommandSender)inviter, "player_is_blacklisted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName), Placeholder.unparsed((String)"blacklister", (String)blacklisterName)});
                        EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                    });
                    return;
                }
                this.storage.addTeamInvite(team.getId(), targetUuid, inviter.getUniqueId());
                this.plugin.getTaskRunner().runOnEntity((Entity)inviter, () -> {
                    List<String> invites = this.teamInvites.getIfPresent(targetUuid);
                    if (invites == null) {
                        invites = new ArrayList<String>();
                    }
                    if (!invites.contains(team.getName().toLowerCase())) {
                        invites.add(team.getName().toLowerCase());
                        this.teamInvites.put(targetUuid, invites);
                    }
                    this.messageManager.sendMessage((CommandSender)inviter, "invite_sent", new TagResolver[]{Placeholder.unparsed((String)"target", (String)targetName)});
                    EffectsUtil.playSound(inviter, EffectsUtil.SoundType.SUCCESS);
                    this.publishCrossServerUpdate(team.getId(), "PLAYER_INVITED", targetUuid.toString(), team.getName());
                });
            } catch (Exception e) {
                this.plugin.getLogger().warning("Failed to send cross-server invite: " + e.getMessage());
                this.plugin.getTaskRunner().runOnEntity((Entity)inviter, () -> {
                    this.messageManager.sendMessage((CommandSender)inviter, "invite_failed", new TagResolver[0]);
                    EffectsUtil.playSound(inviter, EffectsUtil.SoundType.ERROR);
                });
            }
        });
    }

    public void acceptInvite(Player player, String teamName) {
        if (this.getPlayerTeam(player.getUniqueId()) != null) {
            this.messageManager.sendMessage((CommandSender)player, "already_in_team", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        List<String> invites = this.teamInvites.getIfPresent(player.getUniqueId());
        if (invites == null || !invites.contains(teamName.toLowerCase())) {
            this.messageManager.sendMessage((CommandSender)player, "no_pending_invite", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            Team team = this.getTeamByName(teamName);
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                if (team == null) {
                    this.messageManager.sendMessage((CommandSender)player, "team_not_found", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                    return;
                }
                if (team.getMembers().size() >= this.configManager.getMaxTeamSize()) {
                    this.messageManager.sendMessage((CommandSender)player, "team_is_full", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                    return;
                }
                try {
                    if (this.storage.isPlayerBlacklisted(team.getId(), player.getUniqueId())) {
                        List<BlacklistedPlayer> blacklist = this.storage.getTeamBlacklist(team.getId());
                        BlacklistedPlayer blacklistedPlayer = blacklist.stream().filter(bp -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(player.getUniqueId())).findFirst().orElse(null);
                        String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                        this.messageManager.sendMessage((CommandSender)player, "player_is_blacklisted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)player.getName()), Placeholder.unparsed((String)"blacklister", (String)blacklisterName)});
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                        return;
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Could not check blacklist status for player " + player.getName() + " accepting invite to team " + team.getName() + ": " + e.getMessage());
                }
                invites.remove(teamName.toLowerCase());
                if (invites.isEmpty()) {
                    this.teamInvites.invalidate(player.getUniqueId());
                }
                this.plugin.getTaskRunner().runAsync(() -> {
                    this.storage.addMemberToTeam(team.getId(), player.getUniqueId());
                    this.storage.clearAllJoinRequests(player.getUniqueId());
                    this.publishCrossServerUpdate(team.getId(), "MEMBER_JOINED", player.getUniqueId().toString(), player.getName());
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                        team.addMember(new TeamPlayer(player.getUniqueId(), TeamRole.MEMBER, Instant.now(), false, true, false, true));
                        UUID effective = this.getEffectiveUuid(player.getUniqueId());
                        this.playerTeamCache.put(effective, team);
                        this.plugin.getCacheManager().invalidatePlayerPlaceholders(player.getUniqueId());
                        this.invalidateTeamPlaceholders(team);
                        this.plugin.getTaskRunner().runAsync(() -> {
                            Map<UUID, IDataStorage.PlayerSession> sessions = this.storage.getTeamPlayerSessions(team.getId());
                            this.plugin.getCacheManager().cacheTeamSessions(team.getId(), sessions);
                        });
                        this.messageManager.sendMessage((CommandSender)player, "invite_accepted", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                        team.broadcast("invite_accepted_broadcast", new TagResolver[]{Placeholder.unparsed((String)"player", (String)player.getName())});
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                        this.plugin.getWebhookHelper().sendPlayerJoinWebhook(player, team);
                    });
                });
            });
        });
    }

    public void denyInvite(Player player, String teamName) {
        List<String> invites = this.teamInvites.getIfPresent(player.getUniqueId());
        if (invites == null || !invites.contains(teamName.toLowerCase())) {
            this.messageManager.sendMessage((CommandSender)player, "no_pending_invite", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        invites.remove(teamName.toLowerCase());
        if (invites.isEmpty()) {
            this.teamInvites.invalidate(player.getUniqueId());
        }
        this.messageManager.sendMessage((CommandSender)player, "invite_denied", new TagResolver[]{Placeholder.unparsed((String)"team", (String)teamName)});
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
        Team team = this.getTeamByName(teamName);
        if (team != null) {
            team.getMembers().stream().filter(member -> team.hasElevatedPermissions(member.getPlayerUuid()) && member.isOnline()).forEach(privilegedMember -> {
                this.messageManager.sendMessage((CommandSender)privilegedMember.getBukkitPlayer(), "invite_denied_broadcast", new TagResolver[]{Placeholder.unparsed((String)"player", (String)player.getName())});
                EffectsUtil.playSound(privilegedMember.getBukkitPlayer(), EffectsUtil.SoundType.ERROR);
            });
        }
    }

    public List<Team> getPendingInvites(UUID playerUuid) {
        List<String> inviteNames = this.teamInvites.getIfPresent(playerUuid);
        if (inviteNames == null || inviteNames.isEmpty()) {
            return new ArrayList<Team>();
        }
        return inviteNames.stream().map(teamName -> this.getTeamByName((String)teamName)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public void leaveTeam(Player player) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (team.isOwner(player.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)player, "owner_must_disband", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            this.storage.removeMemberFromTeam(player.getUniqueId());
            this.publishCrossServerUpdate(team.getId(), "MEMBER_LEFT", player.getUniqueId().toString(), player.getName());
            this.plugin.getWebhookHelper().sendPlayerLeaveWebhook(player.getName(), team);
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                UUID effective = this.getEffectiveUuid(player.getUniqueId());
                team.removeMember(effective);
                this.playerTeamCache.remove(effective);
                this.plugin.getCacheManager().invalidatePlayerPlaceholders(player.getUniqueId());
                this.invalidateTeamPlaceholders(team);
                this.plugin.getCacheManager().invalidateTeamSessions(team.getId());
                this.plugin.getTaskRunner().runAsync(() -> {
                    Map<UUID, IDataStorage.PlayerSession> sessions = this.storage.getTeamPlayerSessions(team.getId());
                    this.plugin.getCacheManager().cacheTeamSessions(team.getId(), sessions);
                });
                this.messageManager.sendMessage((CommandSender)player, "you_left_team", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                team.broadcast("player_left_broadcast", new TagResolver[]{Placeholder.unparsed((String)"player", (String)player.getName())});
                EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
                player.closeInventory();
            });
        });
    }

    public void kickPlayer(Player kicker, UUID targetUuid) {
        String safeTargetName;
        Team team = this.getPlayerTeam(kicker.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)kicker, "player_not_in_team", new TagResolver[0]);
            EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (!team.hasElevatedPermissions(kicker.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)kicker, "must_be_owner_or_co_owner", new TagResolver[0]);
            EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
            return;
        }
        TeamPlayer targetMember = team.getMember(targetUuid);
        String targetName = Bukkit.getOfflinePlayer((UUID)targetUuid).getName();
        String string = safeTargetName = targetName != null ? targetName : "Unknown";
        if (targetMember == null) {
            this.messageManager.sendMessage((CommandSender)kicker, "target_not_in_your_team", new TagResolver[]{Placeholder.unparsed((String)"target", (String)safeTargetName)});
            EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (targetMember.getRole() == TeamRole.OWNER) {
            this.messageManager.sendMessage((CommandSender)kicker, "cannot_kick_owner", new TagResolver[0]);
            EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (targetMember.getRole() == TeamRole.CO_OWNER && !team.isOwner(kicker.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)kicker, "cannot_kick_co_owner", new TagResolver[0]);
            EffectsUtil.playSound(kicker, EffectsUtil.SoundType.ERROR);
            return;
        }
        new ConfirmGUI(this.plugin, kicker, "Kick " + safeTargetName + "?", confirmed -> {
            if (confirmed.booleanValue()) {
                this.plugin.getTaskRunner().runAsync(() -> {
                    this.storage.removeMemberFromTeam(targetUuid);
                    this.publishCrossServerUpdate(team.getId(), "MEMBER_KICKED", targetUuid.toString(), safeTargetName);
                    this.plugin.getWebhookHelper().sendPlayerKickWebhook(safeTargetName, kicker.getName(), team);
                    this.plugin.getTaskRunner().run(() -> {
                        UUID effectiveTarget = this.getEffectiveUuid(targetUuid);
                        team.removeMember(effectiveTarget);
                        this.playerTeamCache.remove(effectiveTarget);
                        this.plugin.getCacheManager().invalidatePlayerPlaceholders(targetUuid);
                        this.invalidateTeamPlaceholders(team);
                        this.plugin.getCacheManager().invalidateTeamSessions(team.getId());
                        this.plugin.getTaskRunner().runAsync(() -> {
                            Map<UUID, IDataStorage.PlayerSession> sessions = this.storage.getTeamPlayerSessions(team.getId());
                            this.plugin.getCacheManager().cacheTeamSessions(team.getId(), sessions);
                        });
                        this.messageManager.sendMessage((CommandSender)kicker, "player_kicked", new TagResolver[]{Placeholder.unparsed((String)"target", (String)safeTargetName)});
                        team.broadcast("player_left_broadcast", new TagResolver[]{Placeholder.unparsed((String)"player", (String)safeTargetName)});
                        EffectsUtil.playSound(kicker, EffectsUtil.SoundType.SUCCESS);
                        Player targetPlayer = Bukkit.getPlayer((UUID)targetUuid);
                        if (targetPlayer != null) {
                            this.messageManager.sendMessage((CommandSender)targetPlayer, "you_were_kicked", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                            EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.ERROR);
                        }
                    });
                });
            } else {
                new MemberEditGUI(this.plugin, team, kicker, targetUuid).open();
            }
        }).open();
    }

    public void kickPlayerDirect(Player kicker, UUID targetUuid) {
        Team team = this.getPlayerTeam(kicker.getUniqueId());
        if (team == null) {
            return;
        }
        if (!team.hasElevatedPermissions(kicker.getUniqueId())) {
            return;
        }
        TeamPlayer targetMember = team.getMember(targetUuid);
        if (targetMember == null) {
            return;
        }
        if (targetMember.getRole() == TeamRole.OWNER) {
            return;
        }
        if (targetMember.getRole() == TeamRole.CO_OWNER && !team.isOwner(kicker.getUniqueId())) {
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                if (team.isMember(targetUuid)) {
                    this.storage.removeMemberFromTeam(targetUuid);
                    String targetName = Bukkit.getOfflinePlayer((UUID)targetUuid).getName();
                    String safeTargetName = targetName != null ? targetName : "Unknown";
                    this.publishCrossServerUpdate(team.getId(), "MEMBER_KICKED", targetUuid.toString(), safeTargetName);
                    this.plugin.getWebhookHelper().sendPlayerKickWebhook(safeTargetName, kicker.getName(), team);
                    this.plugin.getTaskRunner().run(() -> {
                        try {
                            if (team.isMember(targetUuid)) {
                                UUID effectiveTarget = this.getEffectiveUuid(targetUuid);
                                team.removeMember(effectiveTarget);
                                this.playerTeamCache.remove(effectiveTarget);
                                this.plugin.getCacheManager().invalidatePlayerPlaceholders(targetUuid);
                                this.invalidateTeamPlaceholders(team);
                                this.plugin.getCacheManager().invalidateTeamSessions(team.getId());
                                // Eager recache sessions
                                this.plugin.getTaskRunner().runAsync(() -> {
                                    Map<UUID, IDataStorage.PlayerSession> sessions = this.storage.getTeamPlayerSessions(team.getId());
                                    this.plugin.getCacheManager().cacheTeamSessions(team.getId(), sessions);
                                });
                                team.broadcast("player_left_broadcast", new TagResolver[]{Placeholder.unparsed((String)"player", (String)safeTargetName)});
                                Player targetPlayer = Bukkit.getPlayer((UUID)targetUuid);
                                if (targetPlayer != null) {
                                    this.messageManager.sendMessage((CommandSender)targetPlayer, "you_were_kicked", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                                    EffectsUtil.playSound(targetPlayer, EffectsUtil.SoundType.ERROR);
                                }
                            }
                        } catch (Exception e) {
                            this.plugin.getLogger().severe("Error during kick operation on main thread: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                this.plugin.getLogger().severe("Error during kick operation on async thread: " + e.getMessage());
            }
        });
    }

    public void promotePlayer(Player promoter, UUID targetUuid) {
        String safeTargetName;
        Team team = this.getPlayerTeam(promoter.getUniqueId());
        if (team == null || !team.isOwner(promoter.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)promoter, "not_owner", new TagResolver[0]);
            EffectsUtil.playSound(promoter, EffectsUtil.SoundType.ERROR);
            return;
        }
        TeamPlayer target = team.getMember(targetUuid);
        String targetName = Bukkit.getOfflinePlayer((UUID)targetUuid).getName();
        String string = safeTargetName = targetName != null ? targetName : "Unknown";
        if (target == null) {
            this.messageManager.sendMessage((CommandSender)promoter, "target_not_in_your_team", new TagResolver[]{Placeholder.unparsed((String)"target", (String)safeTargetName)});
            return;
        }
        if (target.getRole() == TeamRole.CO_OWNER) {
            this.messageManager.sendMessage((CommandSender)promoter, "already_that_role", new TagResolver[]{Placeholder.unparsed((String)"target", (String)safeTargetName)});
            return;
        }
        if (target.getRole() == TeamRole.OWNER) {
            this.messageManager.sendMessage((CommandSender)promoter, "cannot_promote_owner", new TagResolver[0]);
            return;
        }
        target.setRole(TeamRole.CO_OWNER);
        target.setCanWithdraw(true);
        target.setCanUseEnderChest(true);
        target.setCanSetHome(true);
        target.setCanUseHome(true);
        try {
            this.storage.updateMemberRole(team.getId(), targetUuid, TeamRole.CO_OWNER);
            this.storage.updateMemberPermissions(team.getId(), targetUuid, true, true, true, true);
            this.storage.updateMemberEditingPermissions(team.getId(), targetUuid, true, false, true, false, false);
            this.plugin.getCacheManager().invalidatePlayerPlaceholders(targetUuid);
            this.plugin.getLogger().info("Successfully promoted " + String.valueOf(targetUuid) + " in team " + team.getName() + " with updated permissions");
            this.markTeamModified(team.getId());
            this.publishCrossServerUpdate(team.getId(), "MEMBER_PROMOTED", targetUuid.toString(), safeTargetName);
            this.plugin.getWebhookHelper().sendPlayerPromoteWebhook(safeTargetName, promoter.getName(), team, TeamRole.MEMBER, TeamRole.CO_OWNER);
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Failed to update member permissions in database: " + e.getMessage());
            target.setRole(TeamRole.MEMBER);
            target.setCanWithdraw(false);
            target.setCanSetHome(false);
            this.messageManager.sendMessage((CommandSender)promoter, "promotion_failed", new TagResolver[0]);
            EffectsUtil.playSound(promoter, EffectsUtil.SoundType.ERROR);
            return;
        }
        team.broadcast("player_promoted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)safeTargetName)});
        EffectsUtil.playSound(promoter, EffectsUtil.SoundType.SUCCESS);
    }

    public void demotePlayer(Player demoter, UUID targetUuid) {
        String safeTargetName;
        Team team = this.getPlayerTeam(demoter.getUniqueId());
        if (team == null || !team.isOwner(demoter.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)demoter, "not_owner", new TagResolver[0]);
            EffectsUtil.playSound(demoter, EffectsUtil.SoundType.ERROR);
            return;
        }
        TeamPlayer target = team.getMember(targetUuid);
        String targetName = Bukkit.getOfflinePlayer((UUID)targetUuid).getName();
        String string = safeTargetName = targetName != null ? targetName : "Unknown";
        if (target == null) {
            this.messageManager.sendMessage((CommandSender)demoter, "target_not_in_your_team", new TagResolver[]{Placeholder.unparsed((String)"target", (String)safeTargetName)});
            return;
        }
        if (target.getRole() == TeamRole.MEMBER) {
            this.messageManager.sendMessage((CommandSender)demoter, "already_that_role", new TagResolver[]{Placeholder.unparsed((String)"target", (String)safeTargetName)});
            return;
        }
        if (target.getRole() == TeamRole.OWNER) {
            this.messageManager.sendMessage((CommandSender)demoter, "cannot_demote_owner", new TagResolver[0]);
            return;
        }
        target.setRole(TeamRole.MEMBER);
        target.setCanWithdraw(false);
        target.setCanUseEnderChest(true);
        target.setCanSetHome(false);
        target.setCanUseHome(true);
        try {
            this.storage.updateMemberRole(team.getId(), targetUuid, TeamRole.MEMBER);
            this.storage.updateMemberPermissions(team.getId(), targetUuid, false, true, false, true);
            this.storage.updateMemberEditingPermissions(team.getId(), targetUuid, false, false, false, false, false);
            this.plugin.getLogger().info("Successfully demoted " + String.valueOf(targetUuid) + " in team " + team.getName() + " with updated permissions");
            this.markTeamModified(team.getId());
            this.publishCrossServerUpdate(team.getId(), "MEMBER_DEMOTED", targetUuid.toString(), safeTargetName);
            this.plugin.getWebhookHelper().sendPlayerDemoteWebhook(safeTargetName, demoter.getName(), team, TeamRole.CO_OWNER, TeamRole.MEMBER);
        } catch (SQLException e) {
            this.plugin.getLogger().severe("Failed to update member permissions in database: " + e.getMessage());
            target.setRole(TeamRole.CO_OWNER);
            target.setCanWithdraw(true);
            target.setCanSetHome(true);
            this.messageManager.sendMessage((CommandSender)demoter, "demotion_failed", new TagResolver[0]);
            EffectsUtil.playSound(demoter, EffectsUtil.SoundType.ERROR);
            return;
        }
        team.broadcast("player_demoted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)safeTargetName)});
        EffectsUtil.playSound(demoter, EffectsUtil.SoundType.SUCCESS);
    }

    public void setTeamTag(Player player, String newTag) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        String plainTag = this.stripColorCodes(newTag);
        if (plainTag.length() > this.configManager.getMaxTagLength() || !plainTag.matches("[a-zA-Z0-9]+")) {
            this.messageManager.sendMessage((CommandSender)player, "tag_invalid", new TagResolver[0]);
            return;
        }
        team.setTag(newTag);
        this.plugin.getTaskRunner().runAsync(() -> this.storage.setTeamTag(team.getId(), newTag));
        this.markTeamModified(team.getId());
        this.messageManager.sendMessage((CommandSender)player, "tag_set", new TagResolver[]{Placeholder.unparsed((String)"tag", (String)newTag)});
        this.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "tag_change|" + newTag);
        this.forceTeamSync(team.getId());
        this.invalidateTeamPlaceholders(team);
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
    }

    public void setTeamDescription(Player player, String newDescription) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        if (newDescription.length() > this.configManager.getMaxDescriptionLength()) {
            this.messageManager.sendMessage((CommandSender)player, "description_too_long", new TagResolver[]{Placeholder.unparsed((String)"max_length", (String)String.valueOf(this.configManager.getMaxDescriptionLength()))});
            return;
        }
        team.setDescription(newDescription);
        this.plugin.getTaskRunner().runAsync(() -> this.storage.setTeamDescription(team.getId(), newDescription));
        this.markTeamModified(team.getId());
        this.messageManager.sendMessage((CommandSender)player, "description_set", new TagResolver[0]);
        this.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "description_change");
        this.invalidateTeamPlaceholders(team);
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
    }

    public void transferOwnership(Player oldOwner, UUID newOwnerUuid) {
        Team team = this.getPlayerTeam(oldOwner.getUniqueId());
        if (team == null || !team.isOwner(oldOwner.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)oldOwner, "not_owner", new TagResolver[0]);
            EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (oldOwner.getUniqueId().equals(newOwnerUuid)) {
            this.messageManager.sendMessage((CommandSender)oldOwner, "cannot_transfer_to_self", new TagResolver[0]);
            EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (!team.isMember(newOwnerUuid)) {
            String targetName = Bukkit.getOfflinePlayer((UUID)newOwnerUuid).getName();
            this.messageManager.sendMessage((CommandSender)oldOwner, "target_not_in_your_team", new TagResolver[]{Placeholder.unparsed((String)"target", (String)(targetName != null ? targetName : "Unknown"))});
            EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.ERROR);
            return;
        }
        new ConfirmGUI(this.plugin, oldOwner, "Transfer ownership?", confirmed -> {
            if (confirmed.booleanValue()) {
                this.plugin.getTaskRunner().runAsync(() -> {
                    String safeNewOwnerName;
                    this.storage.transferOwnership(team.getId(), newOwnerUuid, oldOwner.getUniqueId());
                    String newOwnerName = Bukkit.getOfflinePlayer((UUID)newOwnerUuid).getName();
                    String string = safeNewOwnerName = newOwnerName != null ? newOwnerName : "Unknown";
                    if (this.plugin.getRedisManager() != null && this.plugin.getRedisManager().isAvailable()) {
                        this.plugin.getRedisManager().publishTeamUpdate(team.getId(), "TEAM_UPDATED", newOwnerUuid.toString(), "ownership_transfer|" + safeNewOwnerName);
                    }
                    this.plugin.getWebhookHelper().sendOwnershipTransferWebhook(oldOwner.getName(), safeNewOwnerName, team);
                    this.plugin.getTaskRunner().run(() -> {
                        TeamPlayer oldOwnerMember;
                        team.setOwnerUuid(newOwnerUuid);
                        TeamPlayer newOwnerMember = team.getMember(newOwnerUuid);
                        if (newOwnerMember != null) {
                            newOwnerMember.setRole(TeamRole.OWNER);
                            newOwnerMember.setCanWithdraw(true);
                            newOwnerMember.setCanUseEnderChest(true);
                            newOwnerMember.setCanSetHome(true);
                            newOwnerMember.setCanUseHome(true);
                        }
                        if ((oldOwnerMember = team.getMember(oldOwner.getUniqueId())) != null) {
                            oldOwnerMember.setRole(TeamRole.MEMBER);
                        }
                        this.messageManager.sendMessage((CommandSender)oldOwner, "transfer_success", new TagResolver[]{Placeholder.unparsed((String)"player", (String)safeNewOwnerName)});
                        team.broadcast("transfer_broadcast", new TagResolver[]{Placeholder.unparsed((String)"owner", (String)oldOwner.getName()), Placeholder.unparsed((String)"player", (String)safeNewOwnerName)});
                        this.invalidateTeamPlaceholders(team);
                        EffectsUtil.playSound(oldOwner, EffectsUtil.SoundType.SUCCESS);
                    });
                });
            } else {
                new MemberEditGUI(this.plugin, team, oldOwner, newOwnerUuid).open();
            }
        }).open();
    }

    public void togglePvp(Player player) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        boolean newStatus = !team.isPvpEnabled();
        team.setPvpEnabled(newStatus);
        this.plugin.getTaskRunner().runAsync(() -> {
            this.storage.setPvpStatus(team.getId(), newStatus);
            this.markTeamModified(team.getId());
        this.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "pvp_toggle|" + newStatus);
        this.invalidateTeamPlaceholders(team);
        });
        if (newStatus) {
            team.broadcast("team_pvp_enabled", new TagResolver[0]);
        } else {
            team.broadcast("team_pvp_disabled", new TagResolver[0]);
        }
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
    }

    public void togglePvpStatus(Player player) {
        this.togglePvp(player);
    }

    public void setTeamHome(Player player) {
        TeamPlayer member;
        Team team = this.getPlayerTeam(player.getUniqueId());
        TeamPlayer teamPlayer = member = team != null ? team.getMember(player.getUniqueId()) : null;
        if (team == null || member == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!member.canSetHome()) {
            this.messageManager.sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
            return;
        }
        Location home = player.getLocation();
        String serverName = this.plugin.getConfigManager().getServerIdentifier();
        team.setHomeLocation(home);
        team.setHomeServer(serverName);
        this.plugin.getTaskRunner().runAsync(() -> this.storage.setTeamHome(team.getId(), home, serverName));
        this.markTeamModified(team.getId());
        this.publishCrossServerUpdate(team.getId(), "HOME_SET", player.getUniqueId().toString(), serverName);
        this.messageManager.sendMessage((CommandSender)player, "home_set", new TagResolver[0]);
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
    }

    public void deleteTeamHome(Player player) {
        TeamPlayer member;
        Team team = this.getPlayerTeam(player.getUniqueId());
        TeamPlayer teamPlayer = member = team != null ? team.getMember(player.getUniqueId()) : null;
        if (team == null || member == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!member.canSetHome()) {
            this.messageManager.sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
            return;
        }
        if (team.getHomeLocation() == null) {
            this.messageManager.sendMessage((CommandSender)player, "home_not_set", new TagResolver[0]);
            return;
        }
        team.setHomeLocation(null);
        team.setHomeServer(null);
        this.plugin.getTaskRunner().runAsync(() -> this.storage.deleteTeamHome(team.getId()));
        this.markTeamModified(team.getId());
        this.publishCrossServerUpdate(team.getId(), "HOME_DELETED", player.getUniqueId().toString(), "");
        this.messageManager.sendMessage((CommandSender)player, "home_deleted", new TagResolver[0]);
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
    }

    public void teleportToHome(Player player) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<IDataStorage.TeamHome> teamHomeOpt = this.storage.getTeamHome(team.getId());
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                if (teamHomeOpt.isEmpty()) {
                    this.messageManager.sendMessage((CommandSender)player, "home_not_set", new TagResolver[0]);
                    return;
                }
                IDataStorage.TeamHome teamHome = (IDataStorage.TeamHome)((Object)((Object)((Object)teamHomeOpt.get())));
                final UUID effective = this.getEffectiveUuid(player.getUniqueId());
                TeamPlayer member = team.getMember(player.getUniqueId());
                if (member == null || !member.canUseHome()) {
                    this.messageManager.sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
                    return;
                }
                if (!player.hasPermission("justteams.bypass.home.cooldown") && this.homeCooldowns.containsKey(player.getUniqueId())) {
                    Instant cooldownEnd = this.homeCooldowns.get(player.getUniqueId());
                    if (Instant.now().isBefore(cooldownEnd)) {
                        long secondsLeft = Duration.between(Instant.now(), cooldownEnd).toSeconds();
                        this.messageManager.sendMessage((CommandSender)player, "teleport_cooldown", new TagResolver[]{Placeholder.unparsed((String)"time", (String)(secondsLeft + "s"))});
                        return;
                    }
                }
                String currentServer = this.plugin.getConfigManager().getServerIdentifier();
                String homeServer = teamHome.serverName();
                this.plugin.getDebugLogger().log("Teleport initiated for " + player.getName() + ". Current Server: " + currentServer + ", Home Server: " + homeServer);
                if (currentServer.equalsIgnoreCase(homeServer)) {
                    this.plugin.getDebugLogger().log("Player is on the correct server. Initiating local teleport.");
                    this.initiateLocalTeleport(player, teamHome.location());
                } else {
                    this.plugin.getDebugLogger().log("Player is on the wrong server. Initiating cross-server teleport via database.");
                    this.plugin.getTaskRunner().runAsync(() -> {
                        this.storage.addPendingTeleport(effective, homeServer, teamHome.location());
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                            String connectChannel = "BungeeCord";
                            this.messageManager.sendMessage((CommandSender)player, "proxy_not_enabled", new TagResolver[0]);
                        });
                    });
                }
            });
        });
    }

    private void initiateLocalTeleport(Player player, Location location) {
        int warmup = this.configManager.getWarmupSeconds();
        if (warmup <= 0 || player.hasPermission("justteams.bypass.home.cooldown")) {
            this.teleportPlayer(player, location);
            this.setCooldown(player);
            return;
        }
        Location startLocation = player.getLocation();
        AtomicInteger countdown = new AtomicInteger(warmup);
        CancellableTask task = this.plugin.getTaskRunner().runEntityTaskTimer((Entity)player, () -> {
            if (!player.isOnline() || !Objects.equals(player.getWorld(), startLocation.getWorld()) || player.getLocation().distanceSquared(startLocation) > 1.0) {
                CancellableTask runningTask;
                if (player.isOnline()) {
                    this.messageManager.sendMessage((CommandSender)player, "teleport_moved", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                }
                if ((runningTask = this.teleportTasks.remove(player.getUniqueId())) != null) {
                    runningTask.cancel();
                }
                return;
            }
            if (countdown.get() > 0) {
                this.messageManager.sendMessage((CommandSender)player, "teleport_warmup", new TagResolver[]{Placeholder.unparsed((String)"seconds", (String)String.valueOf(countdown.get()))});
                EffectsUtil.spawnParticles(player.getLocation().add(0.0, 1.0, 0.0), Particle.valueOf((String)this.configManager.getWarmupParticle()), 10);
                countdown.decrementAndGet();
            } else {
                this.teleportPlayer(player, location);
                this.setCooldown(player);
                CancellableTask runningTask = this.teleportTasks.remove(player.getUniqueId());
                if (runningTask != null) {
                    runningTask.cancel();
                }
            }
        }, 0L, 20L);
        this.teleportTasks.put(player.getUniqueId(), task);
    }

    public void teleportPlayer(Player player, Location location) {
        this.plugin.getDebugLogger().log("Executing final teleport for " + player.getName() + " to " + String.valueOf(location));
        this.plugin.getTaskRunner().runAtLocation(location, () -> player.teleportAsync(location).thenAccept(success -> {
            if (success.booleanValue()) {
                this.messageManager.sendMessage((CommandSender)player, "teleport_success", new TagResolver[0]);
                EffectsUtil.playSound(player, EffectsUtil.SoundType.TELEPORT);
                EffectsUtil.spawnParticles(player.getLocation(), Particle.valueOf((String)this.configManager.getSuccessParticle()), 30);
            } else {
                this.messageManager.sendMessage((CommandSender)player, "teleport_moved", new TagResolver[0]);
                EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            }
        }));
    }

    private void setCooldown(Player player) {
        if (player.hasPermission("justteams.bypass.home.cooldown")) {
            return;
        }
        int cooldownSeconds = this.configManager.getHomeCooldownSeconds();
        if (cooldownSeconds > 0) {
            this.homeCooldowns.put(player.getUniqueId(), Instant.now().plusSeconds(cooldownSeconds));
        }
    }

    private void startWarpTeleportWarmup(Player player, Location location) {
        int warmup = 5;
        if (warmup <= 0 || player.hasPermission("justteams.bypass.warp.cooldown")) {
            this.teleportPlayer(player, location);
            this.setWarpCooldown(player);
            return;
        }
        Location startLocation = player.getLocation();
        AtomicInteger countdown = new AtomicInteger(warmup);
        CancellableTask task = this.plugin.getTaskRunner().runEntityTaskTimer((Entity)player, () -> {
            if (!player.isOnline() || !Objects.equals(player.getWorld(), startLocation.getWorld()) || player.getLocation().distanceSquared(startLocation) > 1.0) {
                CancellableTask runningTask;
                if (player.isOnline()) {
                    this.messageManager.sendMessage((CommandSender)player, "teleport_moved", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                }
                if ((runningTask = this.teleportTasks.remove(player.getUniqueId())) != null) {
                    runningTask.cancel();
                }
                return;
            }
            if (countdown.get() > 0) {
                this.messageManager.sendMessage((CommandSender)player, "teleport_warmup", new TagResolver[]{Placeholder.unparsed((String)"seconds", (String)String.valueOf(countdown.get()))});
                EffectsUtil.spawnParticles(player.getLocation().add(0.0, 1.0, 0.0), Particle.valueOf((String)this.configManager.getWarmupParticle()), 10);
                countdown.decrementAndGet();
            } else {
                this.teleportPlayer(player, location);
                this.setWarpCooldown(player);
                CancellableTask runningTask = this.teleportTasks.remove(player.getUniqueId());
                if (runningTask != null) {
                    runningTask.cancel();
                }
            }
        }, 0L, 20L);
        this.teleportTasks.put(player.getUniqueId(), task);
    }

    private void setWarpCooldown(Player player) {
        if (player.hasPermission("justteams.bypass.warp.cooldown")) {
            return;
        }
        int cooldownSeconds = this.configManager.getWarpCooldownSeconds();
        if (cooldownSeconds > 0) {
            this.warpCooldowns.put(player.getUniqueId(), Instant.now().plusSeconds(cooldownSeconds));
        }
    }

    public void deposit(Player player, double amount) {
        if (!this.configManager.isBankEnabled()) {
            this.messageManager.sendMessage((CommandSender)player, "feature_disabled", new TagResolver[0]);
            return;
        }
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (amount <= 0.0) {
            this.messageManager.sendMessage((CommandSender)player, "bank_invalid_amount", new TagResolver[0]);
            return;
        }
        if (this.plugin.getEconomy() == null) {
            this.messageManager.sendMessage((CommandSender)player, "economy_not_available", new TagResolver[0]);
            return;
        }
        if (!this.plugin.getEconomy().has((OfflinePlayer)player, amount)) {
            this.messageManager.sendMessage((CommandSender)player, "bank_insufficient_player_funds", new TagResolver[0]);
            return;
        }
        this.plugin.getEconomy().withdrawPlayer((OfflinePlayer)player, amount);
        team.addBalance(amount);
        this.plugin.getTaskRunner().runAsync(() -> this.storage.updateTeamBalance(team.getId(), team.getBalance()));
        this.markTeamModified(team.getId());
        this.publishCrossServerUpdate(team.getId(), "BANK_DEPOSIT", player.getUniqueId().toString(), String.valueOf(amount));
        this.invalidateTeamPlaceholders(team);
        String formattedAmount = this.formatCurrency(amount);
        String formattedBalance = this.formatCurrency(team.getBalance());
        this.messageManager.sendMessage((CommandSender)player, "bank_deposit_success", new TagResolver[]{Placeholder.unparsed((String)"amount", (String)formattedAmount), Placeholder.unparsed((String)"balance", (String)formattedBalance)});
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
    }

    public void withdraw(Player player, double amount) {
        if (!this.configManager.isBankEnabled()) {
            this.messageManager.sendMessage((CommandSender)player, "feature_disabled", new TagResolver[0]);
            return;
        }
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        TeamPlayer member = team.getMember(player.getUniqueId());
        if (member == null || !member.canWithdraw() && !player.hasPermission("justteams.bypass.bank.withdraw")) {
            this.messageManager.sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
            return;
        }
        if (amount <= 0.0) {
            this.messageManager.sendMessage((CommandSender)player, "bank_invalid_amount", new TagResolver[0]);
            return;
        }
        if (team.getBalance() < amount) {
            this.messageManager.sendMessage((CommandSender)player, "bank_insufficient_funds", new TagResolver[0]);
            return;
        }
        if (this.plugin.getEconomy() == null) {
            this.messageManager.sendMessage((CommandSender)player, "economy_not_available", new TagResolver[0]);
            return;
        }
        team.removeBalance(amount);
        this.plugin.getEconomy().depositPlayer((OfflinePlayer)player, amount);
        this.plugin.getTaskRunner().runAsync(() -> this.storage.updateTeamBalance(team.getId(), team.getBalance()));
        this.markTeamModified(team.getId());
        this.publishCrossServerUpdate(team.getId(), "BANK_WITHDRAW", player.getUniqueId().toString(), String.valueOf(amount));
        this.invalidateTeamPlaceholders(team);
        String formattedAmount = this.formatCurrency(amount);
        String formattedBalance = this.formatCurrency(team.getBalance());
        this.messageManager.sendMessage((CommandSender)player, "bank_withdraw_success", new TagResolver[]{Placeholder.unparsed((String)"amount", (String)formattedAmount), Placeholder.unparsed((String)"balance", (String)formattedBalance)});
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
    }

    public void openEnderChest(Player player) {
        if (!this.plugin.getConfigManager().isTeamEnderchestEnabled()) {
            this.messageManager.sendMessage((CommandSender)player, "feature_disabled", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "not_in_team", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        TeamPlayer member = team.getMember(player.getUniqueId());
        if (member == null || !member.canUseEnderChest() && !player.hasPermission("justteams.bypass.enderchest.use")) {
            this.messageManager.sendMessage((CommandSender)player, "no_permission", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        if (this.plugin.getConfigManager().isSingleServerMode()) {
            this.loadAndOpenEnderChestDirect(player, team);
        } else if (team.tryLockEnderChest()) {
            this.plugin.getTaskRunner().runAsync(() -> {
                boolean lockAcquired = this.storage.acquireEnderChestLock(team.getId(), this.configManager.getServerIdentifier());
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                    if (lockAcquired) {
                        this.loadAndOpenEnderChest(player, team);
                    } else {
                        team.unlockEnderChest();
                        this.messageManager.sendMessage((CommandSender)player, "enderchest_locked_by_proxy", new TagResolver[0]);
                        EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                    }
                });
            });
        } else {
            this.messageManager.sendMessage((CommandSender)player, "enderchest_locked_by_proxy", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
        }
    }

    private void loadAndOpenEnderChest(Player player, Team team) {
        this.plugin.getTaskRunner().runAsync(() -> {
            String data = this.storage.getEnderChest(team.getId());
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().info("Loaded enderchest data for team " + team.getName() + ": " + (String)(data != null ? "data length: " + data.length() : "null"));
            }
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                int rows = this.configManager.getEnderChestRows();
                Inventory enderChest = Bukkit.createInventory((InventoryHolder)team, (int)(rows * 9), (Component)Component.text((String)"\u1d1b\u1d07\u1d00\u1d0d \u1d07\u0274\u1d05\u1d07\u0280 \u1d04\u029c\u1d07s\u1d1b"));
                if (data != null && !data.isEmpty()) {
                    try {
                        InventoryUtil.deserializeInventory(enderChest, data);
                        if (this.plugin.getConfigManager().isDebugEnabled()) {
                            this.plugin.getLogger().info("Successfully deserialized enderchest for team " + team.getName());
                        }
                    } catch (IOException e) {
                        this.plugin.getLogger().warning("Could not deserialize ender chest for team " + team.getName() + ": " + e.getMessage());
                    }
                }
                team.setEnderChest(enderChest);
                if (this.plugin.getConfigManager().isDebugEnabled()) {
                    this.plugin.getLogger().info("Opening enderchest inventory for player " + player.getName());
                }
                player.openInventory(team.getEnderChest());
                this.messageManager.sendMessage((CommandSender)player, "enderchest_opened", new TagResolver[0]);
                EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
            });
        });
    }

    private void loadAndOpenEnderChestDirect(Player player, Team team) {
        if (!team.tryLockEnderChest()) {
            this.messageManager.sendMessage((CommandSender)player, "enderchest_locked_by_proxy", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            String data = this.storage.getEnderChest(team.getId());
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().info("Loading enderchest directly for team " + team.getName() + " (single-server mode)");
            }
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                int rows = this.configManager.getEnderChestRows();
                Inventory enderChest = Bukkit.createInventory((InventoryHolder)team, (int)(rows * 9), (Component)Component.text((String)"\u1d1b\u1d07\u1d00\u1d0d \u1d07\u0274\u1d05\u1d07\u0280 \u1d04\u029c\u1d07s\u1d1b"));
                if (data != null && !data.isEmpty()) {
                    try {
                        InventoryUtil.deserializeInventory(enderChest, data);
                        if (this.plugin.getConfigManager().isDebugEnabled()) {
                            this.plugin.getLogger().info("Successfully deserialized enderchest for team " + team.getName());
                        }
                    } catch (IOException e) {
                        this.plugin.getLogger().warning("Could not deserialize ender chest for team " + team.getName() + ": " + e.getMessage());
                    }
                }
                team.setEnderChest(enderChest);
                if (this.plugin.getConfigManager().isDebugEnabled()) {
                    this.plugin.getLogger().info("Opening enderchest inventory for player " + player.getName() + " (single-server mode)");
                }
                if (player.hasPermission("justteams.admin.enderchest")) {
                    this.plugin.getLogger().info("Admin " + player.getName() + " successfully accessed enderchest for team: " + team.getName());
                    this.messageManager.sendMessage((CommandSender)player, "admin_opened_enderchest", new TagResolver[]{Placeholder.unparsed((String)"team_name", (String)team.getName())});
                }
                player.openInventory(team.getEnderChest());
                this.messageManager.sendMessage((CommandSender)player, "enderchest_opened", new TagResolver[0]);
                EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
            });
        });
    }

    public void saveEnderChest(Team team) {
        if (team == null || team.getEnderChest() == null) {
            return;
        }
        if (!team.isEnderChestLocked()) {
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().warning("Attempted to save enderchest for team " + team.getName() + " without holding lock!");
            }
            return;
        }
        try {
            String data = InventoryUtil.serializeInventory(team.getEnderChest());
            this.storage.saveEnderChest(team.getId(), data);
            if (this.isCrossServerEnabled()) {
                this.sendCrossServerEnderChestUpdate(team.getId(), data);
            }
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().info("\u2713 Saved enderchest for team " + team.getName() + " (data length: " + data.length() + ")");
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Could not save ender chest for team " + team.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveAndReleaseEnderChest(Team team) {
        if (team == null || team.getEnderChest() == null) {
            return;
        }
        if (!team.isEnderChestLocked()) {
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().warning("Attempted to save enderchest for team " + team.getName() + " without holding lock!");
            }
            return;
        }
        try {
            String data = InventoryUtil.serializeInventory(team.getEnderChest());
            this.storage.saveEnderChest(team.getId(), data);
            this.storage.releaseEnderChestLock(team.getId());
            if (this.isCrossServerEnabled()) {
                this.sendCrossServerEnderChestUpdate(team.getId(), data);
            }
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().info("\u2713 Saved and released enderchest for team " + team.getName() + " (data length: " + data.length() + ")");
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Could not save ender chest for team " + team.getName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            team.unlockEnderChest();
        }
    }

    public void saveAllOnlineTeamEnderChests() {
        this.teamNameCache.values().forEach(this::saveEnderChest);
    }

    public boolean isCrossServerEnabled() {
        return this.plugin.getConfigManager().isCrossServerSyncEnabled() && !this.plugin.getConfigManager().isSingleServerMode();
    }

    public void sendCrossServerEnderChestUpdate(int teamId, String enderChestData) {
        if (!this.isCrossServerEnabled()) {
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                this.publishCrossServerUpdate(teamId, "ENDERCHEST_UPDATED", "", enderChestData);
                this.plugin.getLogger().info("\u2713 Published cross-server enderchest update for team " + teamId + " (data length: " + enderChestData.length() + ")");
            } catch (Exception e) {
                this.plugin.getLogger().warning("Failed to send cross-server enderchest update for team " + teamId + ": " + e.getMessage());
            }
        });
    }

    public void refreshEnderChestInventory(Team team) {
        if (team.getEnderChest() == null || team.getEnderChestViewers().isEmpty()) {
            return;
        }
        this.plugin.getTaskRunner().run(() -> {
            for (UUID viewerUuid : team.getEnderChestViewers()) {
                Player viewer = Bukkit.getPlayer((UUID)viewerUuid);
                if (viewer == null || !viewer.isOnline()) continue;
                try {
                    viewer.closeInventory();
                    this.plugin.getTaskRunner().runOnEntity((Entity)viewer, () -> viewer.openInventory(team.getEnderChest()));
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to refresh enderchest for viewer " + viewer.getName() + ": " + e.getMessage());
                }
            }
        });
    }

    public void updateMemberPermissions(Player owner, UUID targetUuid, boolean canWithdraw, boolean canUseEnderChest, boolean canSetHome, boolean canUseHome) {
        Team team = this.getPlayerTeam(owner.getUniqueId());
        if (team == null || !team.isOwner(owner.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)owner, "not_owner", new TagResolver[0]);
            return;
        }
        TeamPlayer member = team.getMember(targetUuid);
        if (member == null) {
            return;
        }
        member.setCanWithdraw(canWithdraw);
        member.setCanUseEnderChest(canUseEnderChest);
        member.setCanSetHome(canSetHome);
        member.setCanUseHome(canUseHome);
        try {
            this.storage.updateMemberPermissions(team.getId(), targetUuid, canWithdraw, canUseEnderChest, canSetHome, canUseHome);
            this.plugin.getLogger().info("Successfully updated permissions for " + String.valueOf(targetUuid) + " in team " + team.getName() + " - canUseEnderChest: " + canUseEnderChest);
            this.markTeamModified(team.getId());
            this.forceMemberPermissionRefresh(team.getId(), targetUuid);
        } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to update permissions in database for " + String.valueOf(targetUuid) + " in team " + team.getName() + ": " + e.getMessage());
            member.setCanWithdraw(!canWithdraw);
            member.setCanUseEnderChest(!canUseEnderChest);
            member.setCanSetHome(!canSetHome);
            member.setCanUseHome(!canUseHome);
            this.messageManager.sendMessage((CommandSender)owner, "permission_update_failed", new TagResolver[0]);
            EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
            return;
        }
        Player targetPlayer = Bukkit.getPlayer((UUID)targetUuid);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            this.plugin.getTaskRunner().runOnEntity((Entity)targetPlayer, () -> {
                if (targetPlayer.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                    new TeamGUI(this.plugin, team, targetPlayer).open();
                }
            });
        }
        if (owner.isOnline()) {
            this.plugin.getTaskRunner().runOnEntity((Entity)owner, () -> {
                if (owner.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                    new TeamGUI(this.plugin, team, owner).open();
                }
            });
        }
        this.forceTeamSync(team.getId());
        this.messageManager.sendMessage((CommandSender)owner, "permissions_updated", new TagResolver[0]);
        EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
    }

    public void updateMemberEditingPermissions(Player owner, UUID targetUuid, boolean canEditMembers, boolean canEditCoOwners, boolean canKickMembers, boolean canPromoteMembers, boolean canDemoteMembers) {
        Team team = this.getPlayerTeam(owner.getUniqueId());
        if (team == null || !team.isOwner(owner.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)owner, "not_owner", new TagResolver[0]);
            return;
        }
        TeamPlayer member = team.getMember(targetUuid);
        if (member == null) {
            return;
        }
        member.setCanEditMembers(canEditMembers);
        member.setCanEditCoOwners(canEditCoOwners);
        member.setCanKickMembers(canKickMembers);
        member.setCanPromoteMembers(canPromoteMembers);
        member.setCanDemoteMembers(canDemoteMembers);
        try {
            this.storage.updateMemberEditingPermissions(team.getId(), targetUuid, canEditMembers, canEditCoOwners, canKickMembers, canPromoteMembers, canDemoteMembers);
            this.plugin.getLogger().info("Successfully updated editing permissions for " + String.valueOf(targetUuid) + " in team " + team.getName());
            this.markTeamModified(team.getId());
            this.forceMemberPermissionRefresh(team.getId(), targetUuid);
        } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to update editing permissions in database for " + String.valueOf(targetUuid) + " in team " + team.getName() + ": " + e.getMessage());
            member.setCanEditMembers(!canEditMembers);
            member.setCanEditCoOwners(!canEditCoOwners);
            member.setCanKickMembers(!canKickMembers);
            member.setCanPromoteMembers(!canPromoteMembers);
            member.setCanDemoteMembers(!canDemoteMembers);
            this.messageManager.sendMessage((CommandSender)owner, "permission_update_failed", new TagResolver[0]);
            EffectsUtil.playSound(owner, EffectsUtil.SoundType.ERROR);
            return;
        }
        Player targetPlayer = Bukkit.getPlayer((UUID)targetUuid);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            this.plugin.getTaskRunner().runOnEntity((Entity)targetPlayer, () -> {
                if (targetPlayer.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                    new TeamGUI(this.plugin, team, targetPlayer).open();
                }
            });
        }
        if (owner.isOnline()) {
            this.plugin.getTaskRunner().runOnEntity((Entity)owner, () -> {
                if (owner.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                    new TeamGUI(this.plugin, team, owner).open();
                }
            });
        }
        this.forceTeamSync(team.getId());
        this.messageManager.sendMessage((CommandSender)owner, "editing_permissions_updated", new TagResolver[0]);
        EffectsUtil.playSound(owner, EffectsUtil.SoundType.SUCCESS);
    }

    public void togglePublicStatus(Player player) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        boolean newStatus = !team.isPublic();
        team.setPublic(newStatus);
        this.plugin.getTaskRunner().runAsync(() -> {
            this.storage.setPublicStatus(team.getId(), newStatus);
            this.markTeamModified(team.getId());
            this.publishCrossServerUpdate(team.getId(), "TEAM_UPDATED", player.getUniqueId().toString(), "public_toggle|" + newStatus);
        });
        if (newStatus) {
            this.messageManager.sendMessage((CommandSender)player, "team_made_public", new TagResolver[0]);
        } else {
            this.messageManager.sendMessage((CommandSender)player, "team_made_private", new TagResolver[0]);
        }
        EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
        this.invalidateTeamPlaceholders(team);
    }

    public void joinTeam(Player player, String teamName) {
        if (this.getPlayerTeam(player.getUniqueId()) != null) {
            this.messageManager.sendMessage((CommandSender)player, "already_in_team", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        Instant cooldown = this.joinRequestCooldowns.getIfPresent(player.getUniqueId());
        if (cooldown != null && Instant.now().isBefore(cooldown)) {
            long secondsLeft = Duration.between(Instant.now(), cooldown).toSeconds();
            this.messageManager.sendMessage((CommandSender)player, "teleport_cooldown", new TagResolver[]{Placeholder.unparsed((String)"time", (String)(secondsLeft + "s"))});
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> teamOpt = this.storage.findTeamByName(teamName);
            if (teamOpt.isEmpty()) {
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                    this.messageManager.sendMessage((CommandSender)player, "team_not_found", new TagResolver[]{Placeholder.unparsed((String)"team", (String)teamName)});
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                });
                return;
            }
            Team team = teamOpt.get();
            if (team.getMembers().size() >= this.configManager.getMaxTeamSize()) {
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                    this.messageManager.sendMessage((CommandSender)player, "team_is_full", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                });
                return;
            }
            try {
                if (this.storage.isPlayerBlacklisted(team.getId(), player.getUniqueId())) {
                    List<BlacklistedPlayer> blacklist = this.storage.getTeamBlacklist(team.getId());
                    BlacklistedPlayer blacklistedPlayer = blacklist.stream().filter(bp -> bp != null && bp.getPlayerUuid() != null && bp.getPlayerUuid().equals(player.getUniqueId())).findFirst().orElse(null);
                    String blacklisterName = blacklistedPlayer != null ? blacklistedPlayer.getBlacklistedByName() : "Unknown";
                    this.messageManager.sendMessage((CommandSender)player, "player_is_blacklisted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)player.getName()), Placeholder.unparsed((String)"blacklister", (String)blacklisterName)});
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                    return;
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Could not check blacklist status for player " + player.getName() + " accepting invite to team " + team.getName() + ": " + e.getMessage());
            }
            this.ensureTeamFullyLoaded(team);
            if (team.isMember(player.getUniqueId())) {
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                    this.messageManager.sendMessage((CommandSender)player, "already_in_team", new TagResolver[0]);
                    EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
                });
                return;
            }
            if (team.isPublic()) {
                this.handlePublicTeamJoin(player, team);
            } else {
                this.plugin.getTaskRunner().runAsync(() -> {
                    if (this.storage.hasJoinRequest(team.getId(), player.getUniqueId())) {
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.messageManager.sendMessage((CommandSender)player, "already_requested_to_join", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())}));
                    } else {
                        this.storage.addJoinRequest(team.getId(), player.getUniqueId());
                        team.addJoinRequest(player.getUniqueId());
                        this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                            this.messageManager.sendMessage((CommandSender)player, "join_request_sent", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                            team.getMembers().stream().filter(m -> m.isOnline()).forEach(member -> {
                                Player bukkitPlayer = member.getBukkitPlayer();
                                if (bukkitPlayer != null) {
                                    this.messageManager.sendMessage((CommandSender)bukkitPlayer, "join_request_received", new TagResolver[]{Placeholder.unparsed((String)"player", (String)player.getName())});
                                }
                            });
                            this.joinRequestCooldowns.put(player.getUniqueId(), Instant.now().plusSeconds(60L));
                        });
                    }
                });
            }
        });
    }

    private void handlePublicTeamJoin(Player player, Team team) {
        try {
            this.storage.addMemberToTeam(team.getId(), player.getUniqueId());
            this.storage.clearAllJoinRequests(player.getUniqueId());
            this.publishCrossServerUpdate(team.getId(), "MEMBER_JOINED", player.getUniqueId().toString(), player.getName());
            TeamPlayer newMember = new TeamPlayer(player.getUniqueId(), TeamRole.MEMBER, Instant.now(), false, true, false, true);
            team.addMember(newMember);
            this.playerTeamCache.put(player.getUniqueId(), team);
            this.plugin.getCacheManager().invalidateTeamSessions(team.getId());
            this.plugin.getTaskRunner().runAsync(() -> {
                Map<UUID, IDataStorage.PlayerSession> sessions = this.storage.getTeamPlayerSessions(team.getId());
                this.plugin.getCacheManager().cacheTeamSessions(team.getId(), sessions);
            });
            this.refreshTeamMembers(team);
            this.messageManager.sendMessage((CommandSender)player, "player_joined_public_team", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
            team.broadcast("player_joined_team", new TagResolver[]{Placeholder.unparsed((String)"player", (String)player.getName())});
            EffectsUtil.playSound(player, EffectsUtil.SoundType.SUCCESS);
            this.plugin.getWebhookHelper().sendPlayerJoinWebhook(player, team);
        } catch (Exception e) {
            this.plugin.getLogger().severe("Error handling public team join for " + player.getName() + ": " + e.getMessage());
            this.messageManager.sendMessage((CommandSender)player, "team_join_error", new TagResolver[0]);
            EffectsUtil.playSound(player, EffectsUtil.SoundType.ERROR);
        }
    }

    public void resolvePlayerName(UUID playerUuid, Consumer<String> callback) {
        String cached = this.plugin.getCacheManager().getPlayerName(playerUuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                Optional<String> dbName = this.storage.getPlayerNameByUuid(playerUuid);
                if (dbName.isPresent()) {
                    String name = dbName.get();
                    this.plugin.getCacheManager().cachePlayerName(playerUuid, name);
                    callback.accept(name);
                    return;
                }
            } catch (Exception ignored) {
            }
            String name = playerUuid.toString();
            this.plugin.getCacheManager().cachePlayerName(playerUuid, name);
            callback.accept(name);
            this.plugin.getTaskRunner().run(() -> {
                Player p = Bukkit.getPlayer(playerUuid);
                if (p != null) {
                    this.plugin.getCacheManager().cachePlayerName(playerUuid, p.getName());
                }
            });
        });
    }

    public void resolvePlayerNames(List<UUID> playerUuids, Consumer<Map<UUID, String>> callback) {
        this.plugin.getTaskRunner().runAsync(() -> {
            Map<UUID, String> result = new HashMap<>();
            List<UUID> misses = new ArrayList<>();
            for (UUID id : playerUuids) {
                String cached = this.plugin.getCacheManager().getPlayerName(id);
                if (cached != null) {
                    result.put(id, cached);
                } else {
                    misses.add(id);
                }
            }
            for (UUID id : misses) {
                try {
                    Optional<String> dbName = this.storage.getPlayerNameByUuid(id);
                    if (dbName.isPresent()) {
                        String name = dbName.get();
                        this.plugin.getCacheManager().cachePlayerName(id, name);
                        result.put(id, name);
                        continue;
                    }
                } catch (Exception ignored) {
                }
                // Off-thread fallback: UUID string; schedule a main-thread name attempt
                String name = id.toString();
                this.plugin.getCacheManager().cachePlayerName(id, name);
                result.put(id, name);
                this.plugin.getTaskRunner().run(() -> {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        this.plugin.getCacheManager().cachePlayerName(id, p.getName());
                    }
                });
            }
            callback.accept(result);
        });
    }

    private void ensureTeamFullyLoaded(Team team) {
        try {
            synchronized (this.cacheLock) {
                List<TeamPlayer> freshMembers = this.storage.getTeamMembers(team.getId());
                team.getMembers().clear();
                team.getMembers().addAll(freshMembers);
                team.getMembers().forEach(member -> this.playerTeamCache.put(member.getPlayerUuid(), team));
                this.plugin.getLogger().info("Ensured team " + team.getName() + " is fully loaded with " + team.getMembers().size() + " members");
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Error ensuring team " + team.getName() + " is fully loaded: " + e.getMessage());
        }
    }

    public void withdrawJoinRequest(Player player, String teamName) {
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> teamOpt = this.storage.findTeamByName(teamName);
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                if (teamOpt.isEmpty()) {
                    this.messageManager.sendMessage((CommandSender)player, "team_not_found", new TagResolver[0]);
                    return;
                }
                Team team = (Team)teamOpt.get();
                if (this.storage.hasJoinRequest(team.getId(), player.getUniqueId())) {
                    this.storage.removeJoinRequest(team.getId(), player.getUniqueId());
                    team.removeJoinRequest(player.getUniqueId());
                    this.messageManager.sendMessage((CommandSender)player, "join_request_withdrawn", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                } else {
                    this.messageManager.sendMessage((CommandSender)player, "join_request_not_found", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
                }
            });
        });
    }

    public void acceptJoinRequest(Team team, UUID targetUuid) {
        if (team == null) {
            return;
        }
        Player target = Bukkit.getPlayer((UUID)targetUuid);
        if (target == null) {
            this.plugin.getLogger().info("Accepting join request for offline player " + String.valueOf(targetUuid) + " to team " + team.getName());
        }
        if (team.isMember(targetUuid)) {
            if (target != null) {
                this.messageManager.sendMessage((CommandSender)target, "already_in_team", new TagResolver[0]);
            }
            return;
        }
        if (team.getMembers().size() >= this.configManager.getMaxTeamSize()) {
            if (target != null) {
                this.messageManager.sendMessage((CommandSender)target, "already_in_team", new TagResolver[0]);
            }
            return;
        }
        try {
            if (this.storage.isPlayerBlacklisted(team.getId(), targetUuid)) {
                if (target != null) {
                    this.messageManager.sendMessage((CommandSender)target, "player_is_blacklisted", new TagResolver[]{Placeholder.unparsed((String)"target", (String)target.getName())});
                }
                return;
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Could not check blacklist status for player " + String.valueOf(targetUuid) + " accepting join request to team " + team.getName() + ": " + e.getMessage());
        }
        this.storage.removeJoinRequest(team.getId(), targetUuid);
        team.removeJoinRequest(targetUuid);
        this.storage.addMemberToTeam(team.getId(), targetUuid);
        TeamPlayer newMember = new TeamPlayer(targetUuid, TeamRole.MEMBER, Instant.now(), false, true, false, true);
        team.addMember(newMember);
        this.playerTeamCache.put(targetUuid, team);
        this.plugin.getCacheManager().invalidateTeamSessions(team.getId());
        this.plugin.getTaskRunner().runAsync(() -> {
            Map<UUID, IDataStorage.PlayerSession> sessions = this.storage.getTeamPlayerSessions(team.getId());
            this.plugin.getCacheManager().cacheTeamSessions(team.getId(), sessions);
        });
        team.broadcast("player_joined_team", new TagResolver[]{Placeholder.unparsed((String)"player", (String)(target != null ? target.getName() : "Unknown Player"))});
        if (target != null) {
            this.messageManager.sendMessage((CommandSender)target, "joined_team", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
            EffectsUtil.playSound(target, EffectsUtil.SoundType.SUCCESS);
        }
        this.forceTeamSync(team.getId());
        this.sendCrossServerTeamUpdate(team.getId(), "MEMBER_ADDED", targetUuid);
        this.refreshAllTeamMemberGUIs(team);
    }

    public void denyJoinRequest(Team team, UUID targetUuid) {
        this.storage.removeJoinRequest(team.getId(), targetUuid);
        team.removeJoinRequest(targetUuid);
        OfflinePlayer target = Bukkit.getOfflinePlayer((UUID)targetUuid);
        team.broadcast("request_denied_team", new TagResolver[]{Placeholder.unparsed((String)"player", (String)(target.getName() != null ? target.getName() : "A player"))});
        if (target.isOnline()) {
            this.messageManager.sendMessage((CommandSender)target.getPlayer(), "request_denied_player", new TagResolver[]{Placeholder.unparsed((String)"team", (String)team.getName())});
        }
    }

    private String locationToString(Location location) {
        if (location == null) {
            return null;
        }
        return String.format("%s,%f,%f,%f,%f,%f", location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), Float.valueOf(location.getYaw()), Float.valueOf(location.getPitch()));
    }

    private Location stringToLocation(String locationString) {
        if (locationString == null || locationString.isEmpty()) {
            return null;
        }
        String[] parts = locationString.split(",");
        if (parts.length != 6) {
            return null;
        }
        try {
            World world = Bukkit.getWorld((String)parts[0]);
            if (world == null) {
                return null;
            }
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = Float.parseFloat(parts[4]);
            float pitch = Float.parseFloat(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setTeamWarp(Player player, String warpName, String password) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (!team.hasElevatedPermissions(player.getUniqueId())) {
            this.messageManager.sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            int maxWarps;
            int currentWarps = this.storage.getTeamWarpCount(team.getId());
            if (currentWarps >= (maxWarps = 5) && !this.storage.teamWarpExists(team.getId(), warpName)) {
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.messageManager.sendMessage((CommandSender)player, "warp_limit_reached", new TagResolver[]{Placeholder.unparsed((String)"limit", (String)String.valueOf(maxWarps))}));
                return;
            }
            String serverName = this.configManager.getServerIdentifier();
            String locationString = this.locationToString(player.getLocation());
            if (this.storage.setTeamWarp(team.getId(), warpName, locationString, serverName, password)) {
                this.plugin.getCacheManager().invalidateTeamWarps(team.getId());
                this.plugin.getTaskRunner().runAsync(() -> {
                    List<IDataStorage.TeamWarp> warps = this.storage.getTeamWarps(team.getId());
                    this.plugin.getCacheManager().cacheTeamWarps(team.getId(), warps);
                });
                this.publishCrossServerUpdate(team.getId(), "WARP_CREATED", player.getUniqueId().toString(), warpName);
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.messageManager.sendMessage((CommandSender)player, "warp_set", new TagResolver[]{Placeholder.unparsed((String)"warp", (String)warpName)}));
            }
        });
    }

    public void deleteTeamWarp(Player player, String warpName) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            boolean canDelete;
            Optional<IDataStorage.TeamWarp> warpOpt = this.storage.getTeamWarp(team.getId(), warpName);
            if (warpOpt.isEmpty()) {
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.messageManager.sendMessage((CommandSender)player, "warp_not_found", new TagResolver[0]));
                return;
            }
            IDataStorage.TeamWarp warp = warpOpt.get();
            boolean bl = canDelete = team.hasElevatedPermissions(player.getUniqueId()) || warp.name().equals(player.getName());
            if (!canDelete) {
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.messageManager.sendMessage((CommandSender)player, "must_be_owner_or_co_owner", new TagResolver[0]));
                return;
            }
            if (this.storage.deleteTeamWarp(team.getId(), warpName)) {
                this.plugin.getCacheManager().invalidateTeamWarps(team.getId());
                this.plugin.getTaskRunner().runAsync(() -> {
                    List<IDataStorage.TeamWarp> warps = this.storage.getTeamWarps(team.getId());
                    this.plugin.getCacheManager().cacheTeamWarps(team.getId(), warps);
                });
                this.publishCrossServerUpdate(team.getId(), "WARP_DELETED", player.getUniqueId().toString(), warpName);
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> this.messageManager.sendMessage((CommandSender)player, "warp_deleted", new TagResolver[]{Placeholder.unparsed((String)"warp", (String)warpName)}));
            }
        });
    }

    public void teleportToTeamWarp(Player player, String warpName, String password) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        if (this.checkWarpCooldown(player)) {
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<IDataStorage.TeamWarp> warpOpt = this.storage.getTeamWarp(team.getId(), warpName);
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                if (warpOpt.isEmpty()) {
                    this.messageManager.sendMessage((CommandSender)player, "warp_not_found", new TagResolver[0]);
                    return;
                }
                IDataStorage.TeamWarp warp = (IDataStorage.TeamWarp)((Object)((Object)((Object)warpOpt.get())));
                if (warp.password() != null && !warp.password().equals(password)) {
                    if (password == null) {
                        this.messageManager.sendMessage((CommandSender)player, "warp_password_protected", new TagResolver[0]);
                        this.messageManager.sendMessage((CommandSender)player, "prompt_warp_password", new TagResolver[]{Placeholder.unparsed((String)"warp", (String)warpName)});
                        this.plugin.getChatInputManager().awaitInput(player, null, input -> {
                            if (input.equalsIgnoreCase("cancel")) {
                                this.messageManager.sendMessage((CommandSender)player, "action_cancelled", new TagResolver[0]);
                                return;
                            }
                            this.teleportToTeamWarp(player, warpName, (String)input);
                        });
                    } else {
                        this.messageManager.sendMessage((CommandSender)player, "warp_incorrect_password", new TagResolver[]{Placeholder.unparsed((String)"warp", (String)warpName)});
                    }
                    return;
                }
                this.messageManager.sendMessage((CommandSender)player, "warp_teleport", new TagResolver[]{Placeholder.unparsed((String)"warp", (String)warpName)});
                String currentServer = this.configManager.getServerIdentifier();
                if (warp.serverName().equals(currentServer)) {
                    Location location = this.stringToLocation(warp.location());
                    if (location != null) {
                        this.startWarpTeleportWarmup(player, location);
                    }
                } else {
                    Location location = this.stringToLocation(warp.location());
                    if (location != null) {
                        this.messageManager.sendMessage((CommandSender)player, "proxy_not_enabled", new TagResolver[0]);
                    }
                }
            });
        });
    }

    private boolean checkWarpCooldown(Player player) {
        if (player.hasPermission("justteams.bypass.warp.cooldown")) {
            return false;
        }
        Instant cooldownEnd = this.warpCooldowns.get(player.getUniqueId());
        if (cooldownEnd != null && Instant.now().isBefore(cooldownEnd)) {
            long remainingSeconds = cooldownEnd.getEpochSecond() - Instant.now().getEpochSecond();
            this.messageManager.sendMessage((CommandSender)player, "warp_cooldown", new TagResolver[]{Placeholder.unparsed((String)"seconds", (String)String.valueOf(remainingSeconds))});
            return true;
        }
        return false;
    }

    public void openWarpsGUI(Player player) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        try {
            Class<?> warpsGUIClass = Class.forName("eu.kotori.justTeams.gui.WarpsGUI");
            Object warpsGUI = warpsGUIClass.getConstructor(((Object)((Object)this.plugin)).getClass(), Team.class, Player.class).newInstance(new Object[]{this.plugin, team, player});
            warpsGUIClass.getMethod("open", new Class[0]).invoke(warpsGUI, new Object[0]);
        } catch (Exception e) {
            this.listTeamWarps(player);
        }
    }

    public void listTeamWarps(Player player) {
        Team team = this.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            this.messageManager.sendMessage((CommandSender)player, "player_not_in_team", new TagResolver[0]);
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            List<IDataStorage.TeamWarp> warps = this.storage.getTeamWarps(team.getId());
            this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                if (warps.isEmpty()) {
                    this.messageManager.sendMessage((CommandSender)player, "no_warps_set", new TagResolver[0]);
                    return;
                }
                this.messageManager.sendMessage((CommandSender)player, "warp_list_header", new TagResolver[0]);
                for (IDataStorage.TeamWarp warp : warps) {
                    String statusIcon = warp.password() != null ? "\ud83d\udd12" : "";
                    this.messageManager.sendMessage((CommandSender)player, "warp_list_entry", new TagResolver[]{Placeholder.unparsed((String)"warp_name", (String)warp.name()), Placeholder.unparsed((String)"status_icon", (String)statusIcon)});
                }
                this.messageManager.sendMessage((CommandSender)player, "warp_list_footer", new TagResolver[0]);
            });
        });
    }

    public void syncCrossServerData() {
        if (!this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
            return;
        }
        if (this.syncInProgress.get()) {
            return;
        }
        this.syncInProgress.set(true);
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                HashSet<String> teamNames = new HashSet<String>();
                Object object = this.cacheLock;
                synchronized (object) {
                    teamNames.addAll(this.teamNameCache.keySet());
                }
                if (teamNames.isEmpty()) {
                    return;
                }
                int maxBatchSize = this.plugin.getConfigManager().getMaxTeamsPerBatch();
                ArrayList<String> teamNamesList = new ArrayList<>(teamNames);
                for (int i = 0; i < teamNamesList.size(); i += maxBatchSize) {
                    int endIndex = Math.min(i + maxBatchSize, teamNamesList.size());
                    List<String> batch = teamNamesList.subList(i, endIndex);
                    this.plugin.getTaskRunner().runAsync(() -> {
                        for (String teamName : batch) {
                            try {
                                Optional<Team> dbTeam = this.storage.findTeamByName(teamName);
                                if (!dbTeam.isPresent()) continue;
                                synchronized (this.cacheLock) {
                                    Team cachedTeam = this.teamNameCache.get(teamName);
                                    if (cachedTeam != null) {
                                        this.syncTeamDataAsync(cachedTeam, dbTeam.get());
                                    }
                                }
                            } catch (Exception e) {
                                this.plugin.getLogger().warning("Error syncing team " + teamName + ": " + e.getMessage());
                            }
                        }
                    });
                    if (endIndex >= teamNamesList.size()) continue;
                    try {
                        Thread.sleep(20L);
                        continue;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                long duration = System.currentTimeMillis() - startTime;
                if (this.plugin.getConfigManager().isDebugEnabled()) {
                    this.plugin.getLogger().info("Cross-server sync completed in " + duration + "ms for " + teamNames.size() + " teams");
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Error during cross-server sync: " + e.getMessage());
                if (this.plugin.getConfigManager().isDebugEnabled()) {
                    this.plugin.getLogger().log(Level.FINE, "Cross-server sync error details", e);
                }
            } finally {
                this.syncInProgress.set(false);
            }
        });
    }

    private void syncTeamDataAsync(Team cachedTeam, Team databaseTeam) {
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                List<UUID> databaseJoinRequests = this.storage.getJoinRequests(databaseTeam.getId());
                this.plugin.getTaskRunner().run(() -> {
                    List<UUID> cachedJoinRequests = cachedTeam.getJoinRequests();
                    for (UUID requestUuid : databaseJoinRequests) {
                        if (cachedJoinRequests.contains(requestUuid)) continue;
                        cachedTeam.addJoinRequest(requestUuid);
                        if (this.plugin.getConfigManager().isDebugEnabled()) {
                            this.plugin.getLogger().info("Synced join request for team " + databaseTeam.getName() + " from player " + String.valueOf(requestUuid));
                        }
                        for (TeamPlayer member : cachedTeam.getMembers()) {
                            if (!member.isOnline() || !cachedTeam.hasElevatedPermissions(member.getPlayerUuid())) continue;
                            this.messageManager.sendMessage((CommandSender)member.getBukkitPlayer(), "join_request_notification", new TagResolver[]{Placeholder.unparsed((String)"player", (String)"a player")});
                        }
                    }
                });
            } catch (Exception e) {
                this.plugin.getLogger().warning("Error in async team sync for " + cachedTeam.getName() + ": " + e.getMessage());
            }
        });
    }

    private void syncTeamData(Team cachedTeam, Team databaseTeam) {
        List<UUID> databaseJoinRequests = this.storage.getJoinRequests(databaseTeam.getId());
        List<UUID> cachedJoinRequests = cachedTeam.getJoinRequests();
        for (UUID requestUuid : databaseJoinRequests) {
            if (cachedJoinRequests.contains(requestUuid)) continue;
            cachedTeam.addJoinRequest(requestUuid);
            this.plugin.getLogger().info("Synced join request for team " + databaseTeam.getName() + " from player " + String.valueOf(requestUuid));
            for (TeamPlayer member : cachedTeam.getMembers()) {
                if (!member.isOnline() || !cachedTeam.hasElevatedPermissions(member.getPlayerUuid())) continue;
                this.messageManager.sendMessage((CommandSender)member.getBukkitPlayer(), "join_request_notification", new TagResolver[]{Placeholder.unparsed((String)"player", (String)"a player")});
            }
        }
        for (UUID requestUuid : cachedJoinRequests) {
            if (databaseJoinRequests.contains(requestUuid)) continue;
            cachedTeam.removeJoinRequest(requestUuid);
            this.plugin.getLogger().info("Removed stale join request for team " + databaseTeam.getName() + " from player " + String.valueOf(requestUuid));
        }
    }

    public void syncCriticalUpdates() {
        if (!this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            block4: {
                try {
                    List<IDataStorage.CrossServerUpdate> pendingUpdates = this.storage.getCrossServerUpdates(this.plugin.getConfigManager().getServerIdentifier());
                    if (pendingUpdates.isEmpty()) {
                        return;
                    }
                    int processedCount = this.processCrossServerUpdatesWithRetry();
                    if (processedCount > 0 && this.plugin.getConfigManager().isDebugLoggingEnabled()) {
                        this.plugin.getDebugLogger().log("Processed " + processedCount + " cross-server updates");
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Error during critical updates sync: " + e.getMessage());
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block4;
                    this.plugin.getLogger().log(Level.FINE, "Critical updates sync error details", e);
                }
            }
        });
    }

    private int processCrossServerUpdatesWithRetry() {
        int maxRetries = this.plugin.getConfigManager().getMaxSyncRetries();
        int retryDelay = this.plugin.getConfigManager().getSyncRetryDelay();
        for (int attempt = 0; attempt <= maxRetries; ++attempt) {
            try {
                return this.processCrossServerUpdates();
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    this.plugin.getLogger().severe("Failed to process cross-server updates after " + maxRetries + " attempts: " + e.getMessage());
                    if (this.plugin.getConfigManager().isDebugEnabled()) {
                        this.plugin.getLogger().log(Level.FINE, "Cross-server updates retry error details", e);
                    }
                    return 0;
                }
                this.plugin.getLogger().warning("Cross-server update attempt " + (attempt + 1) + " failed, retrying in " + retryDelay + "ms: " + e.getMessage());
                try {
                    Thread.sleep(retryDelay);
                    continue;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return 0;
    }

    public void forceTeamSync(int teamId) {
        long lastSyncTime;
        if (!this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - (lastSyncTime = this.lastSyncTimes.getOrDefault(teamId, 0L).longValue()) < 5000L) {
            if (this.plugin.getConfigManager().isDebugEnabled()) {
                this.plugin.getLogger().fine("Skipping force sync for team " + teamId + " due to cooldown");
            }
            return;
        }
        this.lastSyncTimes.put(teamId, currentTime);
        this.plugin.getTaskRunner().runAsync(() -> {
            block5: {
                try {
                    Optional<Team> databaseTeamOpt = this.storage.findTeamById(teamId);
                    if (databaseTeamOpt.isPresent()) {
                        Team databaseTeam = databaseTeamOpt.get();
                        Team cachedTeam = this.teamNameCache.values().stream().filter(team -> team.getId() == teamId).findFirst().orElse(null);
                        if (cachedTeam != null) {
                            this.syncTeamDataOptimized(cachedTeam, databaseTeam);
                            if (this.plugin.getConfigManager().isDebugEnabled()) {
                                this.plugin.getDebugLogger().log("Force synced team " + databaseTeam.getName() + " (ID: " + teamId + ")");
                            }
                        }
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Error during force team sync for ID " + teamId + ": " + e.getMessage());
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block5;
                    this.plugin.getLogger().log(Level.FINE, "Force team sync error details", e);
                }
            }
        });
    }

    private void syncTeamDataOptimized(Team cachedTeam, Team databaseTeam) {
        block6: {
            try {
                boolean needsUpdate = false;
                if (!cachedTeam.getName().equals(databaseTeam.getName()) || !cachedTeam.getTag().equals(databaseTeam.getTag()) || cachedTeam.isPvpEnabled() != databaseTeam.isPvpEnabled() || cachedTeam.isPublic() != databaseTeam.isPublic() || cachedTeam.getBalance() != databaseTeam.getBalance() || cachedTeam.getKills() != databaseTeam.getKills() || cachedTeam.getDeaths() != databaseTeam.getDeaths()) {
                    needsUpdate = true;
                }
                if (!cachedTeam.getOwnerUuid().equals(databaseTeam.getOwnerUuid())) {
                    needsUpdate = true;
                }
                if (cachedTeam.getHomeLocation() == null != (databaseTeam.getHomeLocation() == null) || cachedTeam.getHomeLocation() != null && databaseTeam.getHomeLocation() != null && !cachedTeam.getHomeLocation().equals((Object)databaseTeam.getHomeLocation())) {
                    needsUpdate = true;
                }
                if (needsUpdate) {
                    this.updateCachedTeamFromDatabase(cachedTeam, databaseTeam);
                    this.plugin.getDebugLogger().log("Synced team " + cachedTeam.getName() + " with database changes");
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Error during optimized team sync for " + cachedTeam.getName() + ": " + e.getMessage());
                if (!this.plugin.getConfigManager().isDebugEnabled()) break block6;
                this.plugin.getLogger().log(Level.FINE, "Optimized team sync error details", e);
            }
        }
    }

    private void updateCachedTeamFromDatabase(Team cachedTeam, Team databaseTeam) {
        try {
            cachedTeam.setName(databaseTeam.getName());
            cachedTeam.setTag(databaseTeam.getTag());
            cachedTeam.setDescription(databaseTeam.getDescription());
            cachedTeam.setPvpEnabled(databaseTeam.isPvpEnabled());
            cachedTeam.setPublic(databaseTeam.isPublic());
            cachedTeam.setBalance(databaseTeam.getBalance());
            cachedTeam.setKills(databaseTeam.getKills());
            cachedTeam.setDeaths(databaseTeam.getDeaths());
            if (databaseTeam.getHomeLocation() != null) {
                cachedTeam.setHomeLocation(databaseTeam.getHomeLocation());
                cachedTeam.setHomeServer(databaseTeam.getHomeServer());
            }
            if (!cachedTeam.getOwnerUuid().equals(databaseTeam.getOwnerUuid())) {
                cachedTeam.setOwnerUuid(databaseTeam.getOwnerUuid());
                this.plugin.getLogger().info("Team " + cachedTeam.getName() + " ownership changed to " + String.valueOf(databaseTeam.getOwnerUuid()));
            }
            List<TeamPlayer> databaseMembers = this.storage.getTeamMembers(cachedTeam.getId());
            cachedTeam.getMembers().clear();
            for (TeamPlayer member : databaseMembers) {
                cachedTeam.addMember(member);
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Error updating cached team " + cachedTeam.getName() + " from database: " + e.getMessage());
        }
    }

    private void sendCrossServerTeamUpdate(int teamId, String updateType, UUID playerUuid) {
        if (!this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
            return;
        }
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                this.storage.addCrossServerUpdate(teamId, updateType, playerUuid.toString(), this.plugin.getConfigManager().getServerIdentifier());
                this.plugin.getLogger().fine("Sent cross-server update: " + updateType + " for team " + teamId);
            } catch (Exception e) {
                this.plugin.getLogger().warning("Failed to send cross-server update: " + e.getMessage());
            }
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void sendCrossServerTeamUpdateBatch(int teamId, String updateType, UUID playerUuid) {
        if (!this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
            return;
        }
        Object object = this.crossServerUpdateLock;
        synchronized (object) {
            this.pendingCrossServerUpdates.add(new IDataStorage.CrossServerUpdate(0, teamId, updateType, playerUuid.toString(), this.plugin.getConfigManager().getServerIdentifier(), new Timestamp(System.currentTimeMillis())));
            if (this.pendingCrossServerUpdates.size() >= this.plugin.getConfigManager().getMaxBatchSize()) {
                this.flushCrossServerUpdates();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void flushCrossServerUpdates() {
        ArrayList<IDataStorage.CrossServerUpdate> updatesToSend;
        if (this.pendingCrossServerUpdates.isEmpty()) {
            return;
        }
        Object object = this.crossServerUpdateLock;
        synchronized (object) {
            updatesToSend = new ArrayList<IDataStorage.CrossServerUpdate>(this.pendingCrossServerUpdates);
            this.pendingCrossServerUpdates.clear();
        }
        if (!updatesToSend.isEmpty()) {
            this.plugin.getTaskRunner().runAsync(() -> {
                try {
                    this.storage.addCrossServerUpdatesBatch(updatesToSend);
                    this.plugin.getLogger().fine("Sent " + updatesToSend.size() + " cross-server updates in batch");
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to send cross-server updates batch: " + e.getMessage());
                }
            });
        }
    }

    public int processCrossServerMessages() {
        if (!this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
            return 0;
        }
        try {
            List<IDataStorage.CrossServerMessage> messages = this.storage.getCrossServerMessages(this.plugin.getConfigManager().getServerIdentifier());
            int processedCount = 0;
            for (IDataStorage.CrossServerMessage msg : messages) {
                try {
                    this.processCrossServerMessage(msg);
                    this.storage.removeCrossServerMessage(msg.id());
                    ++processedCount;
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to process cross-server message " + msg.id() + ": " + e.getMessage());
                }
            }
            if (processedCount > 0) {
                this.plugin.getLogger().info("Processed " + processedCount + " cross-server team chat messages from MySQL");
            }
            return processedCount;
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to process cross-server messages: " + e.getMessage());
            return 0;
        }
    }

    private void processCrossServerMessage(IDataStorage.CrossServerMessage msg) {
        this.plugin.getTaskRunner().run(() -> {
            block8: {
                try {
                    Team team = this.teamNameCache.values().stream().filter(t -> t.getId() == msg.teamId()).findFirst().orElse(null);
                    if (team == null) {
                        Optional<Team> dbTeam = this.storage.findTeamById(msg.teamId());
                        if (dbTeam.isPresent()) {
                            team = dbTeam.get();
                        } else {
                            this.plugin.getLogger().warning("Team " + msg.teamId() + " not found for cross-server message");
                            return;
                        }
                    }
                    Team finalTeam = team;
                    UUID senderUuid = UUID.fromString(msg.playerUuid());
                    Optional<String> playerNameOpt = this.storage.getPlayerNameByUuid(senderUuid);
                    String playerName = playerNameOpt.orElse("Unknown");
                    String playerPrefix = "";
                    String playerSuffix = "";
                    Player onlineSender = this.plugin.getServer().getPlayer(senderUuid);
                    if (onlineSender != null && onlineSender.isOnline()) {
                        playerPrefix = this.plugin.getPlayerPrefix(onlineSender);
                        playerSuffix = this.plugin.getPlayerSuffix(onlineSender);
                    }
                    String format = this.messageManager.getRawMessage("team_chat_format");
                    Component formattedMessage = this.plugin.getMiniMessage().deserialize(format, new TagResolver[]{Placeholder.unparsed((String)"player", (String)playerName), Placeholder.unparsed((String)"prefix", (String)playerPrefix), Placeholder.unparsed((String)"player_prefix", (String)playerPrefix), Placeholder.unparsed((String)"suffix", (String)playerSuffix), Placeholder.unparsed((String)"player_suffix", (String)playerSuffix), Placeholder.unparsed((String)"team_name", (String)finalTeam.getName()), Placeholder.unparsed((String)"message", (String)msg.message())});
                    int recipientCount = 0;
                    for (TeamPlayer member : finalTeam.getMembers()) {
                        Player onlinePlayer = member.getBukkitPlayer();
                        if (onlinePlayer == null || !onlinePlayer.isOnline()) continue;
                        onlinePlayer.sendMessage(formattedMessage);
                        ++recipientCount;
                    }
                    if (recipientCount > 0) {
                        this.plugin.getLogger().info("Delivered cross-server chat from " + playerName + " (Server: " + msg.serverName() + ") to " + recipientCount + " players on this server");
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to process cross-server chat message: " + e.getMessage());
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block8;
                    e.printStackTrace();
                }
            }
        });
    }

    public int processCrossServerUpdates() {
        if (!this.plugin.getConfigManager().isCrossServerSyncEnabled()) {
            return 0;
        }
        try {
            List<IDataStorage.CrossServerUpdate> updates = this.storage.getCrossServerUpdates(this.plugin.getConfigManager().getServerIdentifier());
            int processedCount = 0;
            for (IDataStorage.CrossServerUpdate update : updates) {
                try {
                    this.processCrossServerUpdate(update);
                    this.storage.removeCrossServerUpdate(update.id());
                    ++processedCount;
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to process cross-server update " + update.id() + ": " + e.getMessage());
                }
            }
            return processedCount;
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to process cross-server updates: " + e.getMessage());
            return 0;
        }
    }

    private void processCrossServerUpdate(IDataStorage.CrossServerUpdate update) {
        this.plugin.getTaskRunner().run(() -> {
            block42: {
                try {
                    Team finalTeam;
                    Optional<Team> dbTeam;
                    Team team = this.teamNameCache.values().stream().filter(t -> t.getId() == update.teamId()).findFirst().orElse(null);
                    if (team == null && (dbTeam = this.storage.findTeamById(update.teamId())).isPresent()) {
                        this.loadTeamIntoCache(dbTeam.get());
                        team = dbTeam.get();
                    }
                    if ((finalTeam = team) == null) break block42;
                    switch (update.updateType()) {
                        case "PLAYER_INVITED": {
                            try {
                                Player onlinePlayer;
                                UUID invitedPlayerUuid = UUID.fromString(update.playerUuid());
                                List<String> invites = this.teamInvites.getIfPresent(invitedPlayerUuid);
                                if (invites == null) {
                                    invites = new ArrayList<String>();
                                }
                                if (!invites.contains(finalTeam.getName().toLowerCase())) {
                                    invites.add(finalTeam.getName().toLowerCase());
                                    this.teamInvites.put(invitedPlayerUuid, invites);
                                }
                                if ((onlinePlayer = Bukkit.getPlayer((UUID)invitedPlayerUuid)) != null && onlinePlayer.isOnline()) {
                                    this.plugin.getTaskRunner().runOnEntity((Entity)onlinePlayer, () -> {
                                        this.messageManager.sendRawMessage((CommandSender)onlinePlayer, this.messageManager.getRawMessage("prefix") + this.messageManager.getRawMessage("invite_received").replace("<team>", finalTeam.getName()), new TagResolver[0]);
                                        this.messageManager.sendMessage((CommandSender)onlinePlayer, "pending_invites_singular", new TagResolver[0]);
                                        EffectsUtil.playSound(onlinePlayer, EffectsUtil.SoundType.SUCCESS);
                                    });
                                }
                                this.plugin.getLogger().info("Processed cross-server invite for player " + String.valueOf(invitedPlayerUuid) + " to team: " + finalTeam.getName());
                            } catch (IllegalArgumentException e) {
                                this.plugin.getLogger().warning("Invalid player UUID in PLAYER_INVITED update: " + update.playerUuid());
                            }
                            break;
                        }
                        case "MEMBER_ADDED": {
                            this.forceTeamSync(finalTeam.getId());
                            this.plugin.getLogger().info("Processed cross-server member addition for team: " + finalTeam.getName());
                            break;
                        }
                        case "MEMBER_REMOVED": {
                            this.forceTeamSync(finalTeam.getId());
                            this.plugin.getLogger().info("Processed cross-server member removal for team: " + finalTeam.getName());
                            break;
                        }
                        case "TEAM_UPDATED": {
                            this.forceTeamSync(finalTeam.getId());
                            this.plugin.getLogger().info("Processed cross-server team update for team: " + finalTeam.getName());
                            break;
                        }
                        case "PUBLIC_STATUS_CHANGED": 
                        case "PVP_STATUS_CHANGED": {
                            this.forceTeamSync(finalTeam.getId());
                            this.plugin.getLogger().info("Processed cross-server " + update.updateType() + " for team: " + finalTeam.getName());
                            break;
                        }
                        case "ADMIN_BALANCE_SET": 
                        case "ADMIN_STATS_SET": {
                            this.forceTeamSync(finalTeam.getId());
                            this.plugin.getLogger().info("Processed cross-server admin update (" + update.updateType() + ") for team: " + finalTeam.getName());
                            break;
                        }
                        case "ADMIN_PERMISSION_UPDATE": {
                            try {
                                String[] parts = update.playerUuid().split(":");
                                if (parts.length == 3) {
                                    UUID memberUuid = UUID.fromString(parts[0]);
                                    String permission = parts[1];
                                    boolean value = Boolean.parseBoolean(parts[2]);
                                    this.forceMemberPermissionRefresh(finalTeam.getId(), memberUuid);
                                    this.plugin.getLogger().info("Processed cross-server admin permission update for member " + String.valueOf(memberUuid) + " in team: " + finalTeam.getName());
                                }
                            } catch (Exception e) {
                                this.plugin.getLogger().warning("Failed to parse ADMIN_PERMISSION_UPDATE data: " + update.playerUuid());
                            }
                            break;
                        }
                        case "ADMIN_MEMBER_KICK": {
                            try {
                                UUID memberUuid = UUID.fromString(update.playerUuid());
                                finalTeam.removeMember(memberUuid);
                                this.playerTeamCache.remove(memberUuid);
                                this.plugin.getLogger().info("Processed cross-server admin kick for member " + String.valueOf(memberUuid) + " from team: " + finalTeam.getName());
                            } catch (Exception e) {
                                this.plugin.getLogger().warning("Failed to parse ADMIN_MEMBER_KICK playerUuid: " + update.playerUuid());
                            }
                            break;
                        }
                        case "ADMIN_MEMBER_PROMOTE": 
                        case "ADMIN_MEMBER_DEMOTE": {
                            try {
                                UUID memberUuid = UUID.fromString(update.playerUuid());
                                this.forceMemberPermissionRefresh(finalTeam.getId(), memberUuid);
                                this.plugin.getLogger().info("Processed cross-server admin " + update.updateType() + " for member " + String.valueOf(memberUuid) + " in team: " + finalTeam.getName());
                            } catch (Exception e) {
                                this.plugin.getLogger().warning("Failed to parse " + update.updateType() + " playerUuid: " + update.playerUuid());
                            }
                            break;
                        }
                        case "ENDERCHEST_UPDATED": {
                            this.plugin.getTaskRunner().runAsync(() -> {
                                try {
                                    if (!finalTeam.isEnderChestLocked()) {
                                        String serializedData = this.storage.getEnderChest(finalTeam.getId());
                                        if (serializedData != null && !serializedData.isEmpty()) {
                                            Inventory enderChest = finalTeam.getEnderChest();
                                            if (enderChest == null) {
                                                enderChest = Bukkit.createInventory(null, (int)27, (String)"Team Enderchest");
                                                finalTeam.setEnderChest(enderChest);
                                            }
                                            Inventory finalEnderChest = enderChest;
                                            InventoryUtil.deserializeInventory(finalEnderChest, serializedData);
                                            this.plugin.getTaskRunner().run(() -> this.refreshEnderChestInventory(finalTeam));
                                            this.plugin.getLogger().info("\u2713 Enderchest reloaded from database (MySQL fallback) for team: " + finalTeam.getName());
                                        }
                                    } else {
                                        this.plugin.getLogger().info("Skipped enderchest update (lock held) for team: " + finalTeam.getName());
                                    }
                                } catch (Exception e) {
                                    this.plugin.getLogger().warning("Failed to process ENDERCHEST_UPDATED (MySQL fallback) for team " + finalTeam.getName() + ": " + e.getMessage());
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to process cross-server update: " + e.getMessage());
                }
            }
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void cleanupExpiredCache() {
        Object object = this.cacheLock;
        synchronized (object) {
            try {
                this.homeCooldowns.entrySet().removeIf(entry -> Instant.now().isAfter((Instant)entry.getValue()));
                this.warpCooldowns.entrySet().removeIf(entry -> Instant.now().isAfter((Instant)entry.getValue()));
                this.teamStatusCooldowns.entrySet().removeIf(entry -> Instant.now().isAfter((Instant)entry.getValue()));
                this.teleportTasks.entrySet().removeIf(entry -> {
                    CancellableTask task = (CancellableTask)entry.getValue();
                    return task == null;
                });
                this.plugin.getLogger().fine("Cache cleanup completed. Team cache size: " + this.teamNameCache.size());
            } catch (Exception e) {
                this.plugin.getLogger().warning("Error during cache cleanup: " + e.getMessage());
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void shutdown() {
        Object object = this.cacheLock;
        synchronized (object) {
            this.plugin.getLogger().info("TeamManager shutdown initiated. Saving all pending changes...");
            try {
                this.saveAllOnlineTeamEnderChests();
                this.forceSaveAllTeamData();
                this.flushCrossServerUpdates();
                this.cleanupExpiredCache();
                this.plugin.getLogger().info("TeamManager shutdown completed successfully.");
            } catch (Exception e) {
                this.plugin.getLogger().severe("Error during TeamManager shutdown: " + e.getMessage());
            }
        }
    }

    private void refreshTeamMembers(Team team) {
        ArrayList<TeamPlayer> currentMembers = new ArrayList<TeamPlayer>(team.getMembers());
        team.getMembers().clear();
        for (TeamPlayer member : currentMembers) {
            if (member.isOnline()) {
                team.addMember(member);
                continue;
            }
            this.plugin.getLogger().warning("Member " + String.valueOf(member.getPlayerUuid()) + " is offline, removing from team.");
        }
        this.plugin.getLogger().info("Refreshed team " + team.getName() + " with " + team.getMembers().size() + " online members.");
    }

    public void refreshAllTeamMemberGUIs(Team team) {
        if (team == null) {
            return;
        }
        team.getMembers().stream().filter(TeamPlayer::isOnline).forEach(member -> {
            Player player = member.getBukkitPlayer();
            if (player != null) {
                this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                        new TeamGUI(this.plugin, team, player).open();
                    }
                });
            }
        });
    }

    private void refreshTeamData(int teamId) {
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> refreshedTeam = this.storage.findTeamById(teamId);
            if (refreshedTeam.isPresent()) {
                Team team = refreshedTeam.get();
                List<TeamPlayer> members = this.storage.getTeamMembers(teamId);
                team.getMembers().clear();
                team.getMembers().addAll(members);
                this.teamNameCache.put(team.getName().toLowerCase(), team);
                for (TeamPlayer member : members) {
                    this.playerTeamCache.put(member.getPlayerUuid(), team);
                }
                this.plugin.getLogger().info("Refreshed team " + team.getName() + " with " + members.size() + " members from database");
                if (this.plugin.getConfigManager().isDebugEnabled()) {
                    for (TeamPlayer member : members) {
                        this.plugin.getLogger().info("Member " + String.valueOf(member.getPlayerUuid()) + " permissions after refresh - canUseEnderChest: " + member.canUseEnderChest());
                    }
                }
            }
        });
    }

    public void forceTeamRefresh(int teamId) {
        this.plugin.getTaskRunner().runAsync(() -> {
            Optional<Team> teamOpt = this.storage.findTeamById(teamId);
            if (teamOpt.isPresent()) {
                Team team = teamOpt.get();
                this.refreshTeamData(teamId);
                this.plugin.getTaskRunner().run(() -> this.refreshAllTeamMemberGUIs(team));
            }
        });
    }

    public void forceTeamRefreshFromDatabase(int teamId) {
        this.plugin.getTaskRunner().runAsync(() -> {
            try {
                Optional<Team> freshTeam = this.storage.findTeamById(teamId);
                if (freshTeam.isPresent()) {
                    Team team = freshTeam.get();
                    this.teamNameCache.put(team.getName().toLowerCase(), team);
                    this.plugin.getLogger().info("Successfully refreshed team " + team.getName() + " from database");
                    this.refreshAllTeamMemberGUIs(team);
                } else {
                    this.plugin.getLogger().warning("Could not find team with ID " + teamId + " in database");
                }
            } catch (Exception e) {
                this.plugin.getLogger().severe("Error refreshing team " + teamId + " from database: " + e.getMessage());
            }
        });
    }

    public void forceMemberPermissionRefresh(int teamId, UUID memberUuid) {
        try {
            List<TeamPlayer> freshMembers;
            TeamPlayer freshMember;
            Team team;
            TeamPlayer member;
            Optional<Team> teamOpt = this.storage.findTeamById(teamId);
            if (teamOpt.isPresent() && (member = (team = teamOpt.get()).getMember(memberUuid)) != null && (freshMember = (TeamPlayer)(freshMembers = this.storage.getTeamMembers(teamId)).stream().filter(m -> m != null && m.getPlayerUuid() != null && m.getPlayerUuid().equals(memberUuid)).findFirst().orElse(null)) != null) {
                member.setCanWithdraw(freshMember.canWithdraw());
                member.setCanUseEnderChest(freshMember.canUseEnderChest());
                member.setCanSetHome(freshMember.canSetHome());
                member.setCanUseHome(freshMember.canUseHome());
                this.plugin.getLogger().info("Refreshed member " + String.valueOf(memberUuid) + " permissions from database - canUseEnderChest: " + member.canUseEnderChest());
                Player player = Bukkit.getPlayer((UUID)memberUuid);
                if (player != null && player.isOnline()) {
                    this.plugin.getTaskRunner().runOnEntity((Entity)player, () -> {
                        if (player.getOpenInventory().getTopInventory().getHolder() instanceof TeamGUI) {
                            new TeamGUI(this.plugin, team, player).open();
                        }
                    });
                }
            }
        } catch (Exception e) {
            this.plugin.getLogger().severe("Error refreshing member permissions for " + String.valueOf(memberUuid) + " in team " + teamId + ": " + e.getMessage());
        }
    }

    public void cleanupEnderChestLocksOnStartup() {
        if (this.plugin.getConfigManager().isSingleServerMode()) {
            this.plugin.getLogger().info("Single-server mode detected. Cleaning up any existing enderchest locks...");
            this.plugin.getTaskRunner().runAsync(() -> {
                try {
                    this.storage.cleanupAllEnderChestLocks();
                    this.plugin.getLogger().info("Enderchest locks cleanup completed for single-server mode");
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Could not cleanup enderchest locks on startup: " + e.getMessage());
                }
            });
        }
    }

    public void forceSaveTeamData(int teamId) {
        Team team = this.teamNameCache.values().stream().filter(t -> t.getId() == teamId).findFirst().orElse(null);
        if (team == null) {
            this.plugin.getLogger().warning("Could not force save team data for team ID " + teamId + " - team not found in cache");
            return;
        }
        try {
            for (TeamPlayer member : team.getMembers()) {
                this.plugin.getLogger().info("Force saving permissions for member " + String.valueOf(member.getPlayerUuid()) + " in team " + team.getName() + " - canUseEnderChest: " + member.canUseEnderChest() + ", canEditMembers: " + member.canEditMembers());
                this.storage.updateMemberPermissions(team.getId(), member.getPlayerUuid(), member.canWithdraw(), member.canUseEnderChest(), member.canSetHome(), member.canUseHome());
                this.storage.updateMemberEditingPermissions(team.getId(), member.getPlayerUuid(), member.canEditMembers(), member.canEditCoOwners(), member.canKickMembers(), member.canPromoteMembers(), member.canDemoteMembers());
            }
            this.plugin.getLogger().info("Successfully force saved team: " + team.getName());
        } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to force save team " + team.getName() + ": " + e.getMessage());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void forceSaveAllTeamData() {
        if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getDebugLogger().log("Force saving all team data to database...");
        }
        int savedCount = 0;
        int errorCount = 0;
        Object object = this.cacheLock;
        synchronized (object) {
            for (Team team : this.teamNameCache.values()) {
                try {
                    for (TeamPlayer member : team.getMembers()) {
                        this.plugin.getLogger().info("Saving permissions for member " + String.valueOf(member.getPlayerUuid()) + " in team " + team.getName() + " - canUseEnderChest: " + member.canUseEnderChest() + ", canEditMembers: " + member.canEditMembers());
                        this.storage.updateMemberPermissions(team.getId(), member.getPlayerUuid(), member.canWithdraw(), member.canUseEnderChest(), member.canSetHome(), member.canUseHome());
                        this.storage.updateMemberEditingPermissions(team.getId(), member.getPlayerUuid(), member.canEditMembers(), member.canEditCoOwners(), member.canKickMembers(), member.canPromoteMembers(), member.canDemoteMembers());
                    }
                    ++savedCount;
                    this.plugin.getLogger().fine("Force saved team: " + team.getName());
                } catch (Exception e) {
                    ++errorCount;
                    this.plugin.getLogger().warning("Failed to force save team " + team.getName() + ": " + e.getMessage());
                }
            }
        }
        if (errorCount > 0) {
            this.plugin.getLogger().warning("Force save completed with " + errorCount + " errors out of " + (savedCount + errorCount) + " teams");
        } else if (this.plugin.getConfigManager().isDebugEnabled()) {
            this.plugin.getDebugLogger().log("Successfully force saved all " + savedCount + " teams");
        }
    }

    public Map<String, Team> getTeamNameCache() {
        return this.teamNameCache;
    }

    public Map<UUID, Team> getPlayerTeamCache() {
        return this.playerTeamCache;
    }
}

