package com.fastasyncworldedit.bukkit.util;

import com.fastasyncworldedit.core.util.TaskManager;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;

public class BukkitTaskManager extends TaskManager {

    private final Plugin plugin;

    public BukkitTaskManager(final Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int repeat(@Nonnull final Runnable runnable, final int interval) {
        return BukkitSchedulerAdapter.repeat(this.plugin, runnable, interval, interval);
    }

    @Override
    public int repeatAsync(@Nonnull final Runnable runnable, final int interval) {
        return BukkitSchedulerAdapter.repeatAsync(this.plugin, runnable, interval, interval);
    }

    @Override
    public void async(@Nonnull final Runnable runnable) {
        BukkitSchedulerAdapter.async(this.plugin, runnable);
    }

    @Override
    public void task(@Nonnull final Runnable runnable) {
        BukkitSchedulerAdapter.task(this.plugin, runnable);
    }

    @Override
    public void later(@Nonnull final Runnable runnable, final int delay) {
        BukkitSchedulerAdapter.later(this.plugin, runnable, delay);
    }

    @Override
    public void laterAsync(@Nonnull final Runnable runnable, final int delay) {
        BukkitSchedulerAdapter.laterAsync(this.plugin, runnable, delay);
    }

    @Override
    public void cancel(final int task) {
        BukkitSchedulerAdapter.cancel(task);
    }

}
