package net.aquiles.neochat.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class FoliaSupport {

    private static final ScheduledTaskHandle NOOP_TASK_HANDLE = () -> {
    };
    private static final Method FOLIA_SCHEDULED_TASK_CANCEL_METHOD = findFoliaScheduledTaskCancelMethod();

    private final Plugin plugin;
    private final boolean folia;
    private final Method getGlobalRegionSchedulerMethod;
    private final Method getAsyncSchedulerMethod;
    private final Method isOwnedByCurrentRegionMethod;

    public FoliaSupport(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
        Object server = Bukkit.getServer();
        this.getGlobalRegionSchedulerMethod = findMethod(server.getClass(), "getGlobalRegionScheduler");
        this.getAsyncSchedulerMethod = findMethod(server.getClass(), "getAsyncScheduler");
        this.isOwnedByCurrentRegionMethod = findMethod(server.getClass(), "isOwnedByCurrentRegion", Entity.class);
    }

    public boolean isFolia() {
        return folia;
    }

    public void runGlobal(Runnable task) {
        if (canRunGlobalNow()) {
            task.run();
            return;
        }

        if (getGlobalRegionSchedulerMethod == null) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        try {
            Object scheduler = getGlobalRegionSchedulerMethod.invoke(Bukkit.getServer());
            Method executeMethod = scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
            executeMethod.invoke(scheduler, plugin, task);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to schedule task on the global region.", exception);
        }
    }

    public ScheduledTaskHandle runGlobalLater(long delayTicks, Runnable task) {
        long safeDelay = Math.max(0L, delayTicks);
        if (safeDelay <= 0L) {
            runGlobal(task);
            return NOOP_TASK_HANDLE;
        }

        if (getGlobalRegionSchedulerMethod == null) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, safeDelay);
            return bukkitTask::cancel;
        }

        try {
            Object scheduler = getGlobalRegionSchedulerMethod.invoke(Bukkit.getServer());
            Method runDelayedMethod = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Object scheduledTask = runDelayedMethod.invoke(scheduler, plugin, consumer, safeDelay);
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to schedule delayed task on the global region.", exception);
        }
    }

    public ScheduledTaskHandle runGlobalTimer(long delayTicks, long periodTicks, Runnable task) {
        long safeDelay = Math.max(1L, delayTicks);
        long safePeriod = Math.max(1L, periodTicks);

        if (getGlobalRegionSchedulerMethod == null) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, safeDelay, safePeriod);
            return bukkitTask::cancel;
        }

        try {
            Object scheduler = getGlobalRegionSchedulerMethod.invoke(Bukkit.getServer());
            Method runAtFixedRateMethod = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Object scheduledTask = runAtFixedRateMethod.invoke(scheduler, plugin, consumer, safeDelay, safePeriod);
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to schedule repeating task on the global region.", exception);
        }
    }

    public <T> T callGlobal(Callable<T> task) {
        if (canRunGlobalNow()) {
            return callUnchecked(task);
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        runGlobal(() -> completeFuture(task, future));
        return waitFor(future);
    }

    public void runAsync(Runnable task) {
        if (getAsyncSchedulerMethod == null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }

        try {
            Object scheduler = getAsyncSchedulerMethod.invoke(Bukkit.getServer());
            Method runNowMethod = scheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            Consumer<Object> consumer = scheduledTask -> task.run();
            runNowMethod.invoke(scheduler, plugin, consumer);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to schedule async task.", exception);
        }
    }

    public void runForEntity(Entity entity, Runnable task) {
        if (entity == null) {
            runGlobal(task);
            return;
        }

        if (!folia) {
            runGlobal(task);
            return;
        }

        if (isOwnedByCurrentRegion(entity)) {
            task.run();
            return;
        }

        boolean scheduled = scheduleEntity(entity, task, null);
        if (!scheduled) {
            throw new IllegalStateException("Unable to schedule task for entity " + entity.getUniqueId() + ".");
        }
    }

    public <T> T callForEntity(Entity entity, Callable<T> task) {
        return callForEntity(entity, task, null, false);
    }

    public <T> T callForEntityOrDefault(Entity entity, Callable<T> task, T fallback) {
        return callForEntity(entity, task, fallback, true);
    }

    private <T> T callForEntity(Entity entity, Callable<T> task, T fallback, boolean useFallback) {
        if (entity == null) {
            return useFallback ? fallback : callGlobal(task);
        }

        if (!folia) {
            return callGlobal(task);
        }

        if (isOwnedByCurrentRegion(entity)) {
            return callUnchecked(task);
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        boolean scheduled = scheduleEntity(
                entity,
                () -> completeFuture(task, future),
                () -> {
                    if (useFallback) {
                        future.complete(fallback);
                    } else {
                        future.completeExceptionally(new IllegalStateException("Entity scheduler retired before task execution."));
                    }
                }
        );

        if (!scheduled) {
            if (useFallback) {
                return fallback;
            }
            throw new IllegalStateException("Unable to schedule task for entity " + entity.getUniqueId() + ".");
        }

        return waitFor(future);
    }

    private boolean scheduleEntity(Entity entity, Runnable task, Runnable retired) {
        try {
            Method getSchedulerMethod = entity.getClass().getMethod("getScheduler");
            Object scheduler = getSchedulerMethod.invoke(entity);
            Method executeMethod = scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
            Object result = executeMethod.invoke(scheduler, plugin, task, retired, 1L);
            return !(result instanceof Boolean scheduled) || scheduled;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to schedule entity task.", exception);
        }
    }

    private boolean canRunGlobalNow() {
        return Bukkit.isPrimaryThread() && !folia;
    }

    private boolean isOwnedByCurrentRegion(Entity entity) {
        if (isOwnedByCurrentRegionMethod == null) {
            return false;
        }

        try {
            Object result = isOwnedByCurrentRegionMethod.invoke(Bukkit.getServer(), entity);
            return result instanceof Boolean owned && owned;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Method findFoliaScheduledTaskCancelMethod() {
        try {
            Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            return scheduledTaskClass.getMethod("cancel");
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private static <T> T callUnchecked(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception exception) {
            throw new IllegalStateException("Scheduled task execution failed.", exception);
        }
    }

    private static <T> void completeFuture(Callable<T> task, CompletableFuture<T> future) {
        try {
            future.complete(task.call());
        } catch (Exception exception) {
            future.completeExceptionally(exception);
        }
    }

    private static <T> T waitFor(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for scheduled task completion.", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Scheduled task execution failed.", exception.getCause());
        }
    }

    public interface ScheduledTaskHandle {
        void cancel();
    }

    private record ReflectiveTaskHandle(Object scheduledTask) implements ScheduledTaskHandle {

        @Override
        public void cancel() {
            if (scheduledTask == null) {
                return;
            }

            try {
                if (FOLIA_SCHEDULED_TASK_CANCEL_METHOD != null && FOLIA_SCHEDULED_TASK_CANCEL_METHOD.getDeclaringClass().isInstance(scheduledTask)) {
                    FOLIA_SCHEDULED_TASK_CANCEL_METHOD.invoke(scheduledTask);
                    return;
                }

                Method cancelMethod = scheduledTask.getClass().getMethod("cancel");
                cancelMethod.invoke(scheduledTask);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to cancel scheduled task.", exception);
            }
        }
    }
}
