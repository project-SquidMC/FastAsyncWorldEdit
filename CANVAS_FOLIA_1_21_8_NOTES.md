# Canvas/Folia 1.21.8 FAWE Notes

Repository: `C:\Users\werfr\IdeaProjects\FastAsyncWorldEdit`

Goal: make this FAWE checkout work on CanvasMC/Folia 1.21.8, especially the Paper jar from `worldedit-bukkit`.

## Build

Use:

```powershell
.\gradlew.bat --no-configure-on-demand :worldedit-bukkit:shadowJar
```

Useful targeted build:

```powershell
.\gradlew.bat --no-configure-on-demand :worldedit-core:compileJava :worldedit-bukkit:compileJava :worldedit-bukkit:adapters:adapter-1_21_6:compileJava :worldedit-bukkit:shadowJar
```

Output jar:

```text
worldedit-bukkit/build/libs/FastAsyncWorldEdit-Paper-2.15.3-SNAPSHOT.jar
```

`--no-configure-on-demand` matters. Configure-on-demand caused adapter/build issues.

## Important Existing Changes

- `worldedit-bukkit/src/main/resources/plugin.yml`
  - Added `folia-supported: true`.

- `worldedit-bukkit/src/main/java/com/fastasyncworldedit/bukkit/util/BukkitSchedulerAdapter.java`
  - New scheduler bridge.
  - Detects Folia by reflection through `Bukkit.getServer().getClass().getMethods()` looking for `getGlobalRegionScheduler`.
  - Replaces direct Bukkit scheduler calls with:
    - global scheduler for sync-like tasks,
    - async scheduler for async tasks,
    - entity scheduler for player-bound command execution.
  - Wraps Folia global/entity callbacks with `Fawe.runAsMainThread(...)`.
  - Does not wrap async scheduler callbacks as main thread.

- `worldedit-bukkit/src/main/java/com/fastasyncworldedit/bukkit/util/BukkitTaskManager.java`
  - Delegates scheduling to `BukkitSchedulerAdapter`.

- `worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/BukkitServerInterface.java`
  - Uses `BukkitSchedulerAdapter.repeat(...)`.
  - `getTickCount()` catches `IllegalStateException` from `Bukkit.getCurrentTick()` and falls back to `super.getTickCount()`.

- `worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/WorldEditPlugin.java`
  - Cancels tasks through `BukkitSchedulerAdapter.cancelPluginTasks(...)`.

- `worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/BukkitBlockCommandSender.java`
  - Replaced direct `Bukkit.getScheduler().callSyncMethod(...)` usage with FAWE `TaskManager`.

- `worldedit-core/src/main/java/com/fastasyncworldedit/core/Fawe.java`
  - Added thread-local main-thread override:
    - `MAIN_THREAD_OVERRIDES`
    - `runAsMainThread(Runnable)`
  - `isMainThread()` now returns true if this override is active.
  - Needed because Folia global region callbacks are not the old Bukkit main thread, but FAWE queue code expects a main-thread context.

- `worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/BukkitPlayer.java`
  - Added Folia player action routing through entity scheduler.
  - Avoids async command handling for player commands such as `//wand`.
  - `giveItem(...)` avoids blocking `TaskManager.sync(...).get()` on Folia and runs directly when already in the entity/region context.
  - `trySetPosition(...)` avoids blocking sync on Folia and uses `teleportAsync(...)`, because Canvas rejects synchronous teleport calls while in region threading.

- `worldedit-bukkit/adapters/adapter-1_21_6/src/main/java/com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_21_6/PaperweightFaweWorldNativeAccess.java`
  - Removed dependency on `MinecraftServer.currentTick`, which does not exist on Canvas 1.21.8.
  - Uses a local `currentTick()` fallback based on `System.currentTimeMillis() / 50L`.

- `worldedit-bukkit/adapters/adapter-1_21_6/src/main/java/com/sk89q/worldedit/bukkit/adapter/impl/fawe/v1_21_6/PaperweightPlatformAdapter.java`
  - `sendChunk(...)` no longer calls `MinecraftServer.getServer().execute(...)` on Folia.
  - On Folia it sends the chunk packet directly in the scheduled FAWE context.
  - This fixed blocks being placed server-side but not visible client-side until relog.

## Java/Version Build Changes

- `build-logic/src/main/kotlin/buildlogic.common.gradle.kts`
  - Java toolchain changed from 25 to 21.

- `worldedit-core/src/main/java/com/fastasyncworldedit/core/internal/simd/SimdSupport.java`
  - Replaced `VectorOperators.UGT` with `VectorOperators.GT` for Java 21 compatibility.

- Adapter 26.1 excluded because it requires Java 25 and is not needed for CanvasMC 1.21.8:
  - `settings.gradle.kts`
  - root `build.gradle.kts`
  - `worldedit-bukkit/build.gradle.kts`

## Runtime Issues Already Fixed

1. `//wand` on old `2.13.2`
   - Error: `IllegalStateException: No currently ticking region`
   - Cause: command work ran through async scheduler and touched region-only API.
   - Fix: entity scheduler/player action routing.

2. `//wand` on `2.15.3-SNAPSHOT`
   - Error: `NoSuchFieldError: net.minecraft.server.MinecraftServer.currentTick`
   - Cause: Canvas 1.21.8 does not have that field.
   - Fix: adapter 1.21.6 local `currentTick()` fallback.

3. `//wand` watchdog/deadlock
   - Stack: `TaskManager.sync(TaskManager.java:370)` from `BukkitPlayer.giveItem(...)`
   - Cause: region thread waited for a sync task that needed the same effective context.
   - Fix: Folia direct execution in `giveItem(...)` and similar handling in teleport path.

4. `//set stone`
   - Error: `IllegalStateException: Not main thread` from `QueueHandler.run(...)`
   - Cause: FAWE queue ran in Folia global scheduler, not old Bukkit main thread.
   - Fix: `Fawe.runAsMainThread(...)` around Folia global/entity scheduler callbacks.

5. `//set stone` placed blocks but client did not see them until relog
   - Error: `UnsupportedOperationException` from `MinecraftServer.execute(...)`
   - Stack: `PaperweightPlatformAdapter.sendChunk(...)`
   - Cause: Canvas/Folia rejects old main executor path.
   - Fix: on Folia, send chunk packet directly from scheduled context.

6. `/up 1`
   - Error: `UnsupportedOperationException: Must use teleportAsync while in region threading`
   - Stack: `BukkitPlayer.trySetPosition(...)` from `AsyncPlayer.floatAt(...)`
   - Cause: Canvas/Folia rejects `Player#teleport(...)` even when the command is running in the player entity scheduler context.
   - Fix: on Folia, `trySetPosition(...)` starts `Player#teleportAsync(...)` and returns without blocking the region thread.

## Current Status

Last known successful build:

```powershell
.\gradlew.bat --no-configure-on-demand :worldedit-bukkit:adapters:adapter-1_21_6:compileJava :worldedit-bukkit:shadowJar
```

Build result: successful.

Last user-visible state:

- `//wand` works.
- `//set stone` places blocks.
- Latest patch should make placed blocks visible immediately without relog.

## If More Errors Appear

Ask for the full stack trace and inspect the exact method.

Likely remaining issue categories:

- More `TaskManager.sync(...).get()` calls reached from a Folia region/global thread.
- More calls to old Bukkit/Paper main-thread executors, especially:
  - `MinecraftServer.execute(...)`
  - `io.papermc.paper.util.MCUtil.MAIN_EXECUTOR.execute(...)`
  - direct `Bukkit.getScheduler()` usage
- More adapter 1.21.6 methods that assume one global main thread.

Be careful not to claim full Folia compatibility. This is an incremental compatibility patch for CanvasMC/Folia 1.21.8 using the 1.21.6 adapter path.
