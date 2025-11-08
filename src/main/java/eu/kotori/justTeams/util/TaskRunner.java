package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.util.CancellableTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class TaskRunner {
    private final JustTeams plugin;
    private final boolean isFolia;
    private final boolean isPaper;
    private final Map<UUID, CancellableTask> activeTasks = new ConcurrentHashMap<UUID, CancellableTask>();

    public TaskRunner(JustTeams plugin) {
        this.plugin = plugin;
        String serverName = plugin.getServer().getName();
        String serverNameLower = serverName.toLowerCase();
        this.isFolia = serverName.equals("Folia") || serverNameLower.contains("folia") || serverNameLower.equals("canvas") || serverNameLower.equals("petal") || serverNameLower.equals("leaf");
        this.isPaper = serverName.equals("Paper") || serverNameLower.contains("paper") || serverName.equals("Purpur") || serverName.equals("Airplane") || serverName.equals("Pufferfish") || serverNameLower.contains("universespigot") || serverNameLower.equals("plazma") || serverNameLower.equals("mirai");
    }

    public void run(Runnable task) {
        if (this.isFolia) {
            this.plugin.getServer().getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> task.run());
        } else {
            this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, task);
        }
    }

    public void runAsync(Runnable task) {
        if (task == null) {
            return;
        }
        if (!this.plugin.isEnabled()) {
            this.runTaskLater(() -> this.runAsyncInternal(task), 1L);
            return;
        }
        this.runAsyncInternal(task);
    }

    private void runAsyncInternal(Runnable task) {
        if (this.isFolia) {
            this.plugin.getServer().getAsyncScheduler().runNow((Plugin)this.plugin, scheduledTask -> {
                block2: {
                    try {
                        task.run();
                    } catch (Exception e) {
                        this.plugin.getLogger().severe("Error in async task: " + e.getMessage());
                        if (!this.plugin.getConfigManager().isDebugEnabled()) break block2;
                        e.printStackTrace();
                    }
                }
            });
        } else {
            this.plugin.getServer().getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
                block2: {
                    try {
                        task.run();
                    } catch (Exception e) {
                        this.plugin.getLogger().severe("Error in async task: " + e.getMessage());
                        if (!this.plugin.getConfigManager().isDebugEnabled()) break block2;
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public void runAtLocation(Location location, Runnable task) {
        if (this.isFolia) {
            this.plugin.getServer().getRegionScheduler().run((Plugin)this.plugin, location, scheduledTask -> task.run());
        } else {
            this.run(task);
        }
    }

    public void runOnEntity(Entity entity, Runnable task) {
        if (this.isFolia) {
            entity.getScheduler().run((Plugin)this.plugin, scheduledTask -> task.run(), null);
        } else {
            this.run(task);
        }
    }

    public CancellableTask runEntityTaskLater(Entity entity, Runnable task, long delay) {
        if (this.isFolia) {
            ScheduledTask scheduledTask = entity.getScheduler().runDelayed((Plugin)this.plugin, scheduledTask1 -> task.run(), null, delay);
            return () -> ((ScheduledTask)scheduledTask).cancel();
        }
        BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, task, delay);
        return () -> ((BukkitTask)bukkitTask).cancel();
    }

    public CancellableTask runEntityTaskTimer(Entity entity, Runnable task, long delay, long period) {
        if (this.isFolia) {
            ScheduledTask scheduledTask = entity.getScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask1 -> task.run(), null, delay, period);
            return () -> ((ScheduledTask)scheduledTask).cancel();
        }
        BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskTimer((Plugin)this.plugin, task, delay, period);
        return () -> ((BukkitTask)bukkitTask).cancel();
    }

    public CancellableTask runTimer(Runnable task, long delay, long period) {
        return this.runTaskTimer(task, delay, period);
    }

    public CancellableTask runLater(Runnable task, long delay) {
        return this.runTaskLater(task, delay);
    }

    public CancellableTask runTaskLater(Runnable task, long delay) {
        if (this.isFolia) {
            ScheduledTask scheduledTask = this.plugin.getServer().getGlobalRegionScheduler().runDelayed((Plugin)this.plugin, scheduledTask1 -> task.run(), delay);
            return () -> ((ScheduledTask)scheduledTask).cancel();
        }
        BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskLater((Plugin)this.plugin, task, delay);
        return () -> ((BukkitTask)bukkitTask).cancel();
    }

    public CancellableTask runTaskTimer(Runnable task, long delay, long period) {
        if (this.isFolia) {
            ScheduledTask scheduledTask = this.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask1 -> task.run(), delay, period);
            return () -> ((ScheduledTask)scheduledTask).cancel();
        }
        BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskTimer((Plugin)this.plugin, task, delay, period);
        return () -> ((BukkitTask)bukkitTask).cancel();
    }

    public CancellableTask runAsyncTaskLater(Runnable task, long delay) {
        if (this.isFolia) {
            long delayMs = delay * 50L;
            ScheduledTask scheduledTask = this.plugin.getServer().getAsyncScheduler().runDelayed((Plugin)this.plugin, scheduledTask1 -> task.run(), delayMs, TimeUnit.MILLISECONDS);
            return () -> ((ScheduledTask)scheduledTask).cancel();
        }
        BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskLaterAsynchronously((Plugin)this.plugin, task, delay);
        return () -> ((BukkitTask)bukkitTask).cancel();
    }

    public CancellableTask runAsyncTaskTimer(Runnable task, long delay, long period) {
        if (this.isFolia) {
            long delayMs = delay * 50L;
            long periodMs = period * 50L;
            ScheduledTask scheduledTask = this.plugin.getServer().getAsyncScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask1 -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
            return () -> ((ScheduledTask)scheduledTask).cancel();
        }
        BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously((Plugin)this.plugin, task, delay, period);
        return () -> ((BukkitTask)bukkitTask).cancel();
    }

    public void addActiveTask(UUID taskId, CancellableTask task) {
        this.activeTasks.put(taskId, task);
    }

    public void removeActiveTask(UUID taskId) {
        CancellableTask task = this.activeTasks.remove(taskId);
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelAllTasks() {
        this.activeTasks.values().forEach(CancellableTask::cancel);
        this.activeTasks.clear();
    }

    public boolean hasActiveTask(UUID taskId) {
        return this.activeTasks.containsKey(taskId);
    }

    public boolean isFolia() {
        return this.isFolia;
    }

    public boolean isPaper() {
        return this.isPaper;
    }

    public int getActiveTaskCount() {
        return this.activeTasks.size();
    }

    public void runAsyncWithCatch(Runnable task, String taskName) {
        if (task == null) {
            this.plugin.getLogger().warning("Attempted to run null task: " + taskName);
            return;
        }
        this.runAsync(() -> {
            block3: {
                try {
                    long startTime = System.currentTimeMillis();
                    task.run();
                    long duration = System.currentTimeMillis() - startTime;
                    if (duration > 100L && this.plugin.getConfigManager().isSlowQueryLoggingEnabled()) {
                        this.plugin.getLogger().warning("Slow async task '" + taskName + "' took " + duration + "ms");
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().severe("Error in async task '" + taskName + "': " + e.getMessage());
                    if (!this.plugin.getConfigManager().isDebugEnabled()) break block3;
                    e.printStackTrace();
                }
            }
        });
    }
}

