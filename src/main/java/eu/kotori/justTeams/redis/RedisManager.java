package eu.kotori.justTeams.redis;

import eu.kotori.justTeams.JustTeams;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import eu.kotori.justTeams.redis.TeamMessageSubscriber;
import eu.kotori.justTeams.redis.TeamUpdateSubscriber;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisManager {
    private final JustTeams plugin;
    private JedisPool jedisPool;
    private ExecutorService executorService;
    private TeamMessageSubscriber messageSubscriber;
    private TeamUpdateSubscriber updateSubscriber;
    private volatile boolean enabled = false;
    private volatile boolean connected = false;
    private static final String CHANNEL_TEAM_CHAT = "justteams:chat";
    private static final String CHANNEL_TEAM_UPDATES = "justteams:updates";
    private static final String CHANNEL_TEAM_MESSAGES = "justteams:messages";

    public RedisManager(JustTeams plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        block11: {
            if (!this.plugin.getConfigManager().isRedisEnabled()) {
                this.plugin.getLogger().info("Redis is disabled in configuration");
                this.enabled = false;
                return;
            }
            try {
                block10: {
                    String host = this.plugin.getConfigManager().getRedisHost();
                    int port = this.plugin.getConfigManager().getRedisPort();
                    String password = this.plugin.getConfigManager().getRedisPassword();
                    boolean useSSL = this.plugin.getConfigManager().isRedisSslEnabled();
                    int timeout = this.plugin.getConfigManager().getRedisTimeout();
                    this.plugin.getLogger().info("Initializing Redis connection to " + host + ":" + port + "...");
                    JedisPoolConfig poolConfig = new JedisPoolConfig();
                    poolConfig.setMaxTotal(20);
                    poolConfig.setMaxIdle(10);
                    poolConfig.setMinIdle(2);
                    poolConfig.setTestOnBorrow(true);
                    poolConfig.setTestOnReturn(true);
                    poolConfig.setTestWhileIdle(true);
                    poolConfig.setMinEvictableIdleTime(Duration.ofSeconds(60L));
                    poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30L));
                    poolConfig.setBlockWhenExhausted(true);
                    poolConfig.setMaxWait(Duration.ofSeconds(2L));
                    this.jedisPool = password != null && !password.isEmpty() ? new JedisPool((GenericObjectPoolConfig<Jedis>)poolConfig, host, port, timeout, password, useSSL) : new JedisPool((GenericObjectPoolConfig<Jedis>)poolConfig, host, port, timeout, useSSL);
                    try (Jedis jedis = this.jedisPool.getResource();){
                        String pong = jedis.ping();
                        if ("PONG".equals(pong)) {
                            this.connected = true;
                            this.enabled = true;
                            this.plugin.getLogger().info("\u2713 Redis connection successful! PING returned PONG");
                            break block10;
                        }
                        throw new JedisException("Unexpected PING response: " + pong);
                    }
                }
                this.executorService = Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "JustTeams-Redis-Subscriber");
                    t.setDaemon(true);
                    return t;
                });
                this.startSubscribers();
                this.plugin.getLogger().info("\u2713 Redis Pub/Sub initialized successfully");
                this.plugin.getLogger().info("  Channels: justteams:chat, justteams:updates, justteams:messages");
                this.plugin.getLogger().info("  Mode: INSTANT (< 100ms delivery)");
            } catch (Exception e) {
                this.plugin.getLogger().severe("\u2717 Failed to initialize Redis connection: " + e.getMessage());
                this.plugin.getLogger().severe("  Will fall back to MySQL polling mode");
                this.enabled = false;
                this.connected = false;
                if (this.jedisPool == null) break block11;
                this.jedisPool.close();
                this.jedisPool = null;
            }
        }
    }

    private void startSubscribers() {
        this.messageSubscriber = new TeamMessageSubscriber(this.plugin);
        this.executorService.submit(() -> {
            while (this.enabled && !Thread.currentThread().isInterrupted()) {
                try {
                    Jedis jedis = this.jedisPool.getResource();
                    try {
                        this.plugin.getLogger().info("Starting Redis message subscriber...");
                        jedis.subscribe(this.messageSubscriber, CHANNEL_TEAM_CHAT, CHANNEL_TEAM_MESSAGES);
                    } finally {
                        if (jedis == null) continue;
                        jedis.close();
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Redis message subscriber disconnected: " + e.getMessage());
                    if (!this.enabled) continue;
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }
        });
        this.updateSubscriber = new TeamUpdateSubscriber(this.plugin);
        this.executorService.submit(() -> {
            while (this.enabled && !Thread.currentThread().isInterrupted()) {
                try {
                    Jedis jedis = this.jedisPool.getResource();
                    try {
                        this.plugin.getLogger().info("Starting Redis update subscriber...");
                        jedis.subscribe(this.updateSubscriber, CHANNEL_TEAM_UPDATES);
                    } finally {
                        if (jedis == null) continue;
                        jedis.close();
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Redis update subscriber disconnected: " + e.getMessage());
                    if (!this.enabled) continue;
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }
        });
    }

    public CompletableFuture<Boolean> publishTeamChat(int teamId, String playerUuid, String playerName, String message) {
        if (!this.isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            Jedis jedis = this.jedisPool.getResource();
            try {
                String payload = String.format("%d|%s|%s|%s|%d", teamId, playerUuid, playerName, message, System.currentTimeMillis());
                long subscribers = jedis.publish(CHANNEL_TEAM_CHAT, payload);
                this.plugin.getLogger().info("Published team chat to " + subscribers + " servers (Redis)");
                Boolean bl = subscribers > 0L;
                if (jedis != null) {
                    jedis.close();
                }
                return bl;
            } catch (Throwable t$) {
                try {
                    if (jedis != null) {
                        try {
                            jedis.close();
                        } catch (Throwable x2) {
                            t$.addSuppressed(x2);
                        }
                    }
                    throw t$;
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to publish team chat via Redis: " + e.getMessage());
                    return false;
                }
            }
        });
    }

    public CompletableFuture<Boolean> publishTeamMessage(int teamId, String playerUuid, String playerName, String message) {
        if (!this.isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            Jedis jedis = this.jedisPool.getResource();
            try {
                String payload = String.format("%d|%s|%s|%s|%d", teamId, playerUuid, playerName, message, System.currentTimeMillis());
                long subscribers = jedis.publish(CHANNEL_TEAM_MESSAGES, payload);
                this.plugin.getLogger().info("Published team message to " + subscribers + " servers (Redis)");
                Boolean bl = subscribers > 0L;
                if (jedis != null) {
                    jedis.close();
                }
                return bl;
            } catch (Throwable t$) {
                try {
                    if (jedis != null) {
                        try {
                            jedis.close();
                        } catch (Throwable x2) {
                            t$.addSuppressed(x2);
                        }
                    }
                    throw t$;
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to publish team message via Redis: " + e.getMessage());
                    return false;
                }
            }
        });
    }

    public CompletableFuture<Boolean> publishTeamUpdate(int teamId, String updateType, String playerUuid, String data) {
        if (!this.isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            Jedis jedis = this.jedisPool.getResource();
            try {
                String payload = String.format("%d|%s|%s|%s|%d", teamId, updateType, playerUuid, data, System.currentTimeMillis());
                long subscribers = jedis.publish(CHANNEL_TEAM_UPDATES, payload);
                this.plugin.getLogger().info("Published " + updateType + " to " + subscribers + " servers (Redis)");
                Boolean bl = subscribers > 0L;
                if (jedis != null) {
                    jedis.close();
                }
                return bl;
            } catch (Throwable t$) {
                try {
                    if (jedis != null) {
                        try {
                            jedis.close();
                        } catch (Throwable x2) {
                            t$.addSuppressed(x2);
                        }
                    }
                    throw t$;
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Failed to publish team update via Redis: " + e.getMessage());
                    return false;
                }
            }
        });
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isAvailable() {
        return this.enabled && this.connected && this.jedisPool != null && !this.jedisPool.isClosed();
    }

    public String getConnectionStatus() {
        if (!this.enabled) {
            return "DISABLED";
        }
        if (!this.connected) {
            return "DISCONNECTED";
        }
        if (this.jedisPool == null || this.jedisPool.isClosed()) {
            return "CLOSED";
        }
        Jedis jedis = this.jedisPool.getResource();
        try {
            jedis.ping();
            String string = "CONNECTED";
            if (jedis != null) {
                jedis.close();
            }
            return string;
        } catch (Throwable throwable) {
            try {
                if (jedis != null) {
                    try {
                        jedis.close();
                    } catch (Throwable throwable2) {
                        throwable.addSuppressed(throwable2);
                    }
                }
                throw throwable;
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }
    }

    public void shutdown() {
        this.plugin.getLogger().info("Shutting down Redis connection...");
        this.enabled = false;
        this.connected = false;
        if (this.messageSubscriber != null) {
            try {
                this.messageSubscriber.unsubscribe();
            } catch (Exception e) {
                this.plugin.getLogger().warning("Error unsubscribing message subscriber: " + e.getMessage());
            }
        }
        if (this.updateSubscriber != null) {
            try {
                this.updateSubscriber.unsubscribe();
            } catch (Exception e) {
                this.plugin.getLogger().warning("Error unsubscribing update subscriber: " + e.getMessage());
            }
        }
        if (this.executorService != null) {
            this.executorService.shutdownNow();
        }
        if (this.jedisPool != null && !this.jedisPool.isClosed()) {
            this.jedisPool.close();
        }
        this.plugin.getLogger().info("Redis connection closed");
    }

    public String getPoolStats() {
        if (this.jedisPool == null || this.jedisPool.isClosed()) {
            return "Pool: CLOSED";
        }
        return String.format("Pool: %d active, %d idle, %d waiters", this.jedisPool.getNumActive(), this.jedisPool.getNumIdle(), this.jedisPool.getNumWaiters());
    }
}

