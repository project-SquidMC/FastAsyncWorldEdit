package com.fastasyncworldedit.bukkit.util;

import com.fastasyncworldedit.core.Fawe;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class BukkitSchedulerAdapter {

    private static final long TICK_MILLIS = 50L;
    private static final boolean FOLIA = hasMethod(Bukkit.getServer().getClass(), "getGlobalRegionScheduler");
    private static final AtomicInteger TASK_IDS = new AtomicInteger();
    private static final Map<Integer, Object> FOLIA_TASKS = new ConcurrentHashMap<>();

    private BukkitSchedulerAdapter() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static int repeat(@Nonnull Plugin plugin, @Nonnull Runnable runnable, long delay, long period) {
        if (!FOLIA) {
            return Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, runnable, delay, period);
        }
        return store(invokeScheduler(
                globalScheduler(),
                "runAtFixedRate",
                new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class},
                plugin,
                taskConsumer(runnable, true),
                Math.max(1L, delay),
                Math.max(1L, period)
        ));
    }

    public static int repeatAsync(@Nonnull Plugin plugin, @Nonnull Runnable runnable, long delay, long period) {
        if (!FOLIA) {
            return Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, runnable, delay, period);
        }
        return store(invokeScheduler(
                asyncScheduler(),
                "runAtFixedRate",
                new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class},
                plugin,
                taskConsumer(runnable, false),
                ticksToMillis(Math.max(1L, delay)),
                ticksToMillis(Math.max(1L, period)),
                TimeUnit.MILLISECONDS
        ));
    }

    public static void task(@Nonnull Plugin plugin, @Nonnull Runnable runnable) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, runnable).getTaskId();
            return;
        }
        store(invokeScheduler(
                globalScheduler(),
                "run",
                new Class<?>[]{Plugin.class, Consumer.class},
                plugin,
                taskConsumer(runnable, true)
        ));
    }

    public static void async(@Nonnull Plugin plugin, @Nonnull Runnable runnable) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable).getTaskId();
            return;
        }
        store(invokeScheduler(
                asyncScheduler(),
                "runNow",
                new Class<?>[]{Plugin.class, Consumer.class},
                plugin,
                taskConsumer(runnable, false)
        ));
    }

    public static void later(@Nonnull Plugin plugin, @Nonnull Runnable runnable, long delay) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delay).getTaskId();
            return;
        }
        store(invokeScheduler(
                globalScheduler(),
                "runDelayed",
                new Class<?>[]{Plugin.class, Consumer.class, long.class},
                plugin,
                taskConsumer(runnable, true),
                Math.max(1L, delay)
        ));
    }

    public static void laterAsync(@Nonnull Plugin plugin, @Nonnull Runnable runnable, long delay) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delay);
            return;
        }
        store(invokeScheduler(
                asyncScheduler(),
                "runDelayed",
                new Class<?>[]{Plugin.class, Consumer.class, long.class, TimeUnit.class},
                plugin,
                taskConsumer(runnable, false),
                ticksToMillis(Math.max(1L, delay)),
                TimeUnit.MILLISECONDS
        ));
    }

    public static void cancel(int taskId) {
        if (taskId == -1) {
            return;
        }
        if (!FOLIA) {
            Bukkit.getScheduler().cancelTask(taskId);
            return;
        }
        Object task = FOLIA_TASKS.remove(taskId);
        if (task != null) {
            invoke(task, "cancel", new Class<?>[0]);
        }
    }

    public static void cancelPluginTasks(@Nonnull Plugin plugin) {
        if (!FOLIA) {
            Bukkit.getScheduler().cancelTasks(plugin);
            return;
        }
        invoke(globalScheduler(), "cancelTasks", new Class<?>[]{Plugin.class}, plugin);
        invoke(asyncScheduler(), "cancelTasks", new Class<?>[]{Plugin.class}, plugin);
        FOLIA_TASKS.clear();
    }

    public static void entityTask(@Nonnull Plugin plugin, @Nonnull Player player, @Nonnull Runnable runnable) {
        if (!FOLIA) {
            task(plugin, runnable);
            return;
        }
        Object scheduler = invoke(player, "getScheduler", new Class<?>[0]);
        store(invokeScheduler(
                scheduler,
                "run",
                new Class<?>[]{Plugin.class, Consumer.class, Runnable.class},
                plugin,
                taskConsumer(runnable, true),
                null
        ));
    }

    private static Consumer<Object> taskConsumer(Runnable runnable, boolean mainThread) {
        if (!mainThread) {
            return ignored -> runnable.run();
        }
        return ignored -> Fawe.runAsMainThread(runnable);
    }

    private static int store(Object task) {
        int id = TASK_IDS.incrementAndGet();
        FOLIA_TASKS.put(id, task);
        return id;
    }

    private static long ticksToMillis(long ticks) {
        return ticks * TICK_MILLIS;
    }

    private static Object globalScheduler() {
        return invoke(Bukkit.getServer(), "getGlobalRegionScheduler", new Class<?>[0]);
    }

    private static Object asyncScheduler() {
        return invoke(Bukkit.getServer(), "getAsyncScheduler", new Class<?>[0]);
    }

    private static Object invokeScheduler(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        return invoke(target, name, parameterTypes, args);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to invoke Folia scheduler method " + name, e);
        }
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

}
