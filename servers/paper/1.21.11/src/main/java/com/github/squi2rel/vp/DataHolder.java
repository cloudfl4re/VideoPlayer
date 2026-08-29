package com.github.squi2rel.vp;

import com.github.squi2rel.vp.network.ServerPacketHandler;
import com.github.squi2rel.vp.network.VideoHandshakeState;
import com.github.squi2rel.vp.network.VideoPackets;
import com.github.squi2rel.vp.network.VideoProtocol;
import com.github.squi2rel.vp.i18n.PaperTexts;
import com.github.squi2rel.vp.i18n.VpTranslation;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import com.github.squi2rel.vp.video.ScreenSurface;
import com.github.squi2rel.vp.video.ScreenKey;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class DataHolder {
    public static final Object LOCK = new Object();
    static final long PLAYER_TRACKING_PERIOD_TICKS = 5L;
    static final long RELOAD_HANDSHAKE_DELAY_TICKS = 60L;
    static final long WORLD_SAVE_DEBOUNCE_TICKS = 20L;
    static final long WORLD_SAVE_RETRY_TICKS = 100L;
    static final long STOP_PERSISTENCE_TIMEOUT_MILLIS = 15_000L;
    static final long FILE_LOCK_TIMEOUT_MILLIS = 5_000L;
    static final long FILE_LOCK_RETRY_MILLIS = 50L;
    public static ServerConfig config = new ServerConfig();
    public static final HashSet<UUID> allPlayers = new HashSet<>();
    public static final HashMap<UUID, String> playerDim = new HashMap<>();
    public static final HashMap<String, HashMap<String, VideoArea>> areas = new HashMap<>();

    private static final Gson gson = new Gson();
    private static final HashMap<String, Path> worldFiles = new HashMap<>();
    private static final HashMap<String, String> persistedWorldSnapshots = new HashMap<>();
    private static final HashMap<String, WorldPersistenceState> worldPersistence = new HashMap<>();
    private static final HashSet<String> invalidWorldConfigs = new HashSet<>();
    private static final HashMap<UUID, FoliaScheduler.TaskHandle> playerTasks = new HashMap<>();
    private static final HashSet<UUID> onlinePlayerIds = new HashSet<>();
    private static final HashMap<UUID, String> onlinePlayerNames = new HashMap<>();
    private static final HashMap<UUID, VideoHandshakeState> handshakes = new HashMap<>();
    private static final HashMap<UUID, Long> handshakeNonces = new HashMap<>();
    private static final HashMap<UUID, String> handshakeTokens = new HashMap<>();
    private static final HashMap<UUID, FoliaScheduler.TaskHandle> reloadHandshakeTasks = new HashMap<>();
    private static final HashMap<UUID, Long> reloadHandshakeRequests = new HashMap<>();
    private static final HashMap<UUID, PlayerPosition> playerPositions = new HashMap<>();
    private static final HashMap<String, Long> worldLoadRequests = new HashMap<>();
    private static final HashMap<String, FoliaScheduler.TaskHandle> worldSaveDebounceTasks = new HashMap<>();
    private static final HashMap<String, FoliaScheduler.TaskHandle> worldSaveRetryTasks = new HashMap<>();
    private static final ArrayList<VideoArea> legacyConfigAreas = new ArrayList<>();
    private static final WorldSaveQueue worldSaveQueue = new WorldSaveQueue(
            FoliaScheduler::runAsync,
            DataHolder::writeQueuedWorldSnapshot,
            DataHolder::queuedWorldSaveFailed
    );
    private static final WorldSaveQueue legacyConfigSaveQueue = new WorldSaveQueue(
            FoliaScheduler::runAsync,
            DataHolder::writeLegacyConfigSnapshot,
            DataHolder::legacyConfigSaveFailed
    );
    private static VideoPlayerPaperPlugin plugin;
    private static boolean sharedConfigLoaded;
    private static long lifecycleEpoch;
    private static long nextWorldPersistenceId;
    private static long nextWorldLoadRequestId;
    private static long nextSaveGeneration;
    private static long nextReloadHandshakeRequest;
    private static long nextLegacyConfigGeneration;
    private static boolean legacyConfigBackupCreated;
    private static boolean running;

    private DataHolder() {
    }

    public static void start(VideoPlayerPaperPlugin owner) {
        worldSaveQueue.cancelAll();
        legacyConfigSaveQueue.cancelAll();
        synchronized (LOCK) {
            plugin = owner;
            running = true;
            lifecycleEpoch++;
            config = new ServerConfig();
            nextSaveGeneration = Math.max(nextSaveGeneration + 1L, System.currentTimeMillis());
            sharedConfigLoaded = false;
            legacyConfigAreas.clear();
            handshakes.clear();
            handshakeNonces.clear();
            cancelReloadHandshakesLocked();
            handshakeTokens.clear();
            invalidWorldConfigs.clear();
            worldPersistence.clear();
            worldLoadRequests.clear();
            cancelWorldSaveDebouncesLocked();
            cancelWorldSaveRetriesLocked();
            legacyConfigBackupCreated = false;
            loadLegacyPluginConfig(owner);
            VideoPlayerMain.resetScheduler();
            long epoch = lifecycleEpoch;
            VideoPlayerMain.server = runnable -> executeState(epoch, runnable);
        }
    }

    public static void stop() {
        long deadline = System.nanoTime() + STOP_PERSISTENCE_TIMEOUT_MILLIS * 1_000_000L;
        ArrayList<WorldSaveQueue.Snapshot> finalWorldSnapshots = new ArrayList<>();
        HashSet<String> finalCaptureFailures = new HashSet<>();
        synchronized (LOCK) {
            running = false;
            for (FoliaScheduler.TaskHandle task : playerTasks.values()) {
                task.cancel();
            }
            playerTasks.clear();
            cancelReloadHandshakesLocked();
            cancelWorldSaveDebouncesLocked();
            cancelWorldSaveRetriesLocked();
            worldLoadRequests.clear();
            for (String dim : new ArrayList<>(areas.keySet())) {
                try {
                    WorldSaveQueue.Snapshot snapshot = captureWorldSaveLocked(dim, true, false);
                    if (snapshot != null) finalWorldSnapshots.add(snapshot);
                } catch (RuntimeException error) {
                    finalCaptureFailures.add(dim);
                    VideoPlayerMain.LOGGER.error("Failed to capture final VideoPlayer world save for {}", dim, error);
                }
            }
        }
        boolean worldsPersisted = finalCaptureFailures.isEmpty();
        for (WorldSaveQueue.Snapshot snapshot : finalWorldSnapshots) {
            if (remainingMillis(deadline) == 0L) {
                worldsPersisted = false;
                break;
            }
            if (!worldSaveQueue.drainInline(snapshot).successful()) worldsPersisted = false;
        }
        boolean worldQueueDrained = worldSaveQueue.flush(remainingMillis(deadline));
        worldsPersisted &= worldQueueDrained;
        boolean legacyPersisted = legacyConfigSaveQueue.flush(remainingMillis(deadline));
        HashSet<String> dirty = new HashSet<>(worldSaveQueue.failedDimensions());
        dirty.addAll(finalCaptureFailures);
        synchronized (LOCK) {
            for (Map.Entry<String, WorldPersistenceState> entry : worldPersistence.entrySet()) {
                if (entry.getValue().dirtySnapshot != null) dirty.add(entry.getKey());
            }
        }
        boolean legacyFailed = !legacyConfigSaveQueue.failedDimensions().isEmpty();
        if (!worldsPersisted || !legacyPersisted || !dirty.isEmpty() || legacyFailed) {
            VideoPlayerMain.LOGGER.warn(
                    "VideoPlayer shutdown persistence incomplete; worlds persisted: {}, legacy queue drained: {}, unsaved worlds: {}, legacy config failed: {}",
                    worldsPersisted, legacyPersisted, dirty, legacyFailed
            );
        }
        worldSaveQueue.cancelAll();
        legacyConfigSaveQueue.cancelAll();
        synchronized (LOCK) {
            lifecycleEpoch++;
            for (HashMap<String, VideoArea> map : areas.values()) {
                for (VideoArea area : map.values()) {
                    unloadAreaForPlayers(area);
                    area.remove();
                }
            }
            areas.clear();
            worldFiles.clear();
            persistedWorldSnapshots.clear();
            worldPersistence.clear();
            invalidWorldConfigs.clear();
            worldLoadRequests.clear();
            worldSaveDebounceTasks.clear();
            worldSaveRetryTasks.clear();
            allPlayers.clear();
            playerDim.clear();
            onlinePlayerIds.clear();
            onlinePlayerNames.clear();
            handshakes.clear();
            handshakeNonces.clear();
            handshakeTokens.clear();
            playerPositions.clear();
            plugin = null;
        }
    }

    private static long remainingMillis(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) return 0L;
        return Math.max(1L, remaining / 1_000_000L);
    }

    public static void executeState(Runnable runnable) {
        long epoch;
        synchronized (LOCK) {
            epoch = lifecycleEpoch;
        }
        executeState(epoch, runnable);
    }

    public static void executeState(long expectedEpoch, Runnable runnable) {
        VideoPlayerPaperPlugin owner;
        synchronized (LOCK) {
            owner = plugin;
            if (!running || lifecycleEpoch != expectedEpoch || owner == null || !owner.isEnabled() || runnable == null) return;
        }
        FoliaScheduler.runGlobal(() -> {
            synchronized (LOCK) {
                if (!running || lifecycleEpoch != expectedEpoch || plugin != owner || !owner.isEnabled()) return;
                try {
                    runnable.run();
                } catch (RuntimeException error) {
                    VideoPlayerMain.LOGGER.warn("VideoPlayer state task failed", error);
                } catch (Error error) {
                    VideoPlayerMain.LOGGER.error("VideoPlayer state task encountered an unrecoverable error", error);
                    throw error;
                }
            }
        });
    }

    public static void loadWorld(World world) {
        if (world == null) return;
        WorldDescriptor descriptor = describeWorld(world);
        if (descriptor == null) return;
        requestWorldLoad(descriptor);
    }

    private static WorldDescriptor describeWorld(World world) {
        java.io.File folder = world.getWorldFolder();
        if (folder == null) {
            VideoPlayerMain.LOGGER.warn("Skipped VideoPlayer world load for {} because world folder is unavailable", worldKey(world));
            return null;
        }
        return new WorldDescriptor(worldKey(world), folder.toPath().resolve("videoplayer.json"));
    }

    private static void requestWorldLoad(WorldDescriptor descriptor) {
        VideoPlayerPaperPlugin owner;
        long epoch;
        long requestId;
        synchronized (LOCK) {
            if (areas.containsKey(descriptor.dimension())) {
                bindWorldPersistenceLocked(descriptor.dimension(), descriptor.path());
                return;
            }
            owner = plugin;
            epoch = lifecycleEpoch;
            if (!running || owner == null || !owner.isEnabled()) return;
            if (worldLoadRequests.containsKey(descriptor.dimension())) return;
            requestId = ++nextWorldLoadRequestId;
            worldLoadRequests.put(descriptor.dimension(), requestId);
        }
        worldSaveQueue.awaitIdle(descriptor.dimension()).whenComplete((drain, barrierError) ->
                scheduleWorldRead(owner, epoch, requestId, descriptor, drain, barrierError));
    }

    private static void scheduleWorldRead(VideoPlayerPaperPlugin owner, long epoch, long requestId,
                                          WorldDescriptor descriptor, WorldSaveQueue.DrainResult drain,
                                          Throwable barrierError) {
        synchronized (LOCK) {
            Long activeRequest = worldLoadRequests.get(descriptor.dimension());
            if (!running || plugin != owner || lifecycleEpoch != epoch || !owner.isEnabled()
                    || activeRequest == null || activeRequest != requestId) {
                return;
            }
        }
        try {
            FoliaScheduler.runAsync(() -> {
                WorldLoadResult result;
                try {
                    if (barrierError != null) throw new IllegalStateException("World save barrier failed", barrierError);
                    result = readWorld(descriptor, drain);
                } catch (Throwable error) {
                    result = WorldLoadResult.failed(descriptor, error);
                }
                WorldLoadResult completed = result;
                executeState(epoch, () -> applyWorldLoad(owner, epoch, requestId, completed));
            });
        } catch (Throwable error) {
            synchronized (LOCK) {
                if (worldLoadRequests.get(descriptor.dimension()) != null
                        && worldLoadRequests.get(descriptor.dimension()) == requestId) {
                    worldLoadRequests.remove(descriptor.dimension());
                }
            }
            VideoPlayerMain.LOGGER.warn("Failed to schedule VideoPlayer world config load for {}", descriptor.path(), error);
        }
    }

    private static WorldLoadResult readWorld(WorldDescriptor descriptor, WorldSaveQueue.DrainResult drain) {
        if (drain != null && !drain.successful() && drain.snapshot() != null && !drain.cancelled()) {
            ReadResult retained = readConfig(descriptor.path(), drain.snapshot().serialized());
            return new WorldLoadResult(descriptor, true, retained, null, null, true);
        }
        return withWorldFileLock(descriptor.path(), FILE_LOCK_TIMEOUT_MILLIS, () -> readWorldFile(descriptor));
    }

    private static WorldLoadResult readWorldFile(WorldDescriptor descriptor) {
        boolean existingConfig = Files.exists(descriptor.path());
        ReadResult result = readConfig(descriptor.path());
        Path backup = null;
        if (result.migrated()) {
            try {
                backup = backup(descriptor.path(), ".1.6.5.bak");
            } catch (IOException error) {
                throw new IllegalStateException("Failed to back up legacy VideoPlayer world data " + descriptor.path(), error);
            }
        }
        return new WorldLoadResult(descriptor, existingConfig, result, backup, null, false);
    }

    private static void applyWorldLoad(VideoPlayerPaperPlugin owner, long expectedEpoch, long requestId, WorldLoadResult load) {
        String dim = load.descriptor().dimension();
        Long activeRequest = worldLoadRequests.get(dim);
        if (activeRequest == null || activeRequest != requestId || !running || lifecycleEpoch != expectedEpoch || plugin != owner) return;
        worldLoadRequests.remove(dim);
        if (!playerDim.containsValue(dim)) return;
        if (areas.containsKey(dim)) {
            bindWorldPersistenceLocked(dim, load.descriptor().path());
            return;
        }
        if (load.error() != null) {
            invalidWorldConfigs.add(dim);
            areas.put(dim, new HashMap<>());
            bindWorldPersistenceLocked(dim, load.descriptor().path());
            invalidateWorldTracking(dim);
            VideoPlayerMain.LOGGER.warn("Rejected invalid VideoPlayer world config {}; original file will not be modified", load.descriptor().path(), load.error());
            return;
        }
        ReadResult result = load.result();
        ServerConfig loaded = result.config();
        nextSaveGeneration = Math.max(nextSaveGeneration, Math.max(0L, loaded.saveGeneration));
        if (load.existingConfig() && result.hasSharedConfig() && (!sharedConfigLoaded || dim.equals("minecraft:overworld"))) {
            applySharedConfig(loaded);
            sharedConfigLoaded = true;
        }
        HashMap<String, VideoArea> map;
        try {
            map = createWorldAreas(dim, loaded);
        } catch (RuntimeException error) {
            invalidWorldConfigs.add(dim);
            areas.put(dim, new HashMap<>());
            bindWorldPersistenceLocked(dim, load.descriptor().path());
            VideoPlayerMain.LOGGER.warn("Rejected invalid VideoPlayer world config {}; original file will not be modified", load.descriptor().path(), error);
            return;
        }
        areas.put(dim, map);
        invalidWorldConfigs.remove(dim);
        invalidateWorldTracking(dim);
        bindWorldPersistenceLocked(dim, load.descriptor().path());
        rememberWorldSnapshot(dim, loaded.saveGeneration);
        int imported = importLegacyConfigAreas(dim, map);
        if (result.migrated() || imported > 0 || load.retryPersistence()) queueWorldSaveLocked(dim, false);
        if (result.migrated()) {
            VideoPlayerMain.LOGGER.info(
                    "Migrated VideoPlayer 1.6.5 world data at {}: {} areas, {} screens; backup: {}",
                    load.descriptor().path(), result.areaCount(), result.screenCount(), load.backup()
            );
        }
        if (imported > 0) persistLegacyPluginConfigAsync();
        VideoPlayerMain.LOGGER.info("Loaded {} VideoPlayer areas for world {} from {}", map.size(), dim, load.descriptor().path());
    }

    private static HashMap<String, VideoArea> createWorldAreas(String dim, ServerConfig loaded) {
        HashMap<String, VideoArea> map = new HashMap<>();
        if (loaded.areas == null) return map;
        if (loaded.areas.size() > VideoArea.MAX_AREAS_PER_WORLD) {
            throw new IllegalArgumentException("VideoPlayer world contains more than " + VideoArea.MAX_AREAS_PER_WORLD + " areas");
        }
        for (VideoArea area : loaded.areas) {
            area.dim = dim;
            if (area.screens == null) area.screens = new ArrayList<>();
            for (VideoScreen screen : area.screens) {
                if (screen.metadata == null) screen.metadata = new com.github.squi2rel.vp.video.ScreenMetadata();
                screen.metadata.ensureValid();
            }
            area.initServer();
            area.afterLoad();
            map.put(area.name, area);
        }
        return map;
    }

    public static void ensureWorldLoaded(World world) {
        loadWorld(world);
    }

    public static void unloadWorld(World world) {
        if (world == null) return;
        unloadWorld(worldKey(world));
    }

    public static void unloadWorld(String dim) {
        if (dim == null || dim.isBlank()) return;
        synchronized (LOCK) {
            unloadWorldLocked(dim);
        }
    }

    private static void unloadWorldLocked(String dim) {
        boolean loadCancelled = worldLoadRequests.remove(dim) != null;
        HashMap<String, VideoArea> map = areas.get(dim);
        if (map != null) {
            cancelWorldSaveDebounceLocked(dim);
            cancelWorldSaveRetryLocked(dim);
            if (running) submitFinalWorldSaveLocked(dim);
            for (VideoArea area : map.values()) {
                unloadAreaForPlayers(area);
                area.remove();
            }
            areas.remove(dim);
        }
        worldFiles.remove(dim);
        persistedWorldSnapshots.remove(dim);
        worldPersistence.remove(dim);
        invalidWorldConfigs.remove(dim);
        playerDim.entrySet().removeIf(entry -> dim.equals(entry.getValue()));
        playerPositions.entrySet().removeIf(entry -> dim.equals(entry.getValue().dimension()));
        if (map != null) {
            VideoPlayerMain.LOGGER.info("Unloaded VideoPlayer world {}", dim);
        } else if (loadCancelled) {
            VideoPlayerMain.LOGGER.info("Cancelled pending VideoPlayer world load for {}", dim);
        }
    }

    private static void releaseWorldIfUnusedLocked(String dim) {
        if (dim == null || playerDim.containsValue(dim)) return;
        unloadWorldLocked(dim);
    }

    public static boolean isWorldTrackingReady(String dim) {
        synchronized (LOCK) {
            return dim != null && areas.containsKey(dim) && worldFiles.containsKey(dim);
        }
    }

    private static void bindWorldPersistenceLocked(String dim, Path path) {
        if (dim == null || path == null) return;
        worldFiles.put(dim, path);
        worldPersistence.computeIfAbsent(dim, ignored -> new WorldPersistenceState(++nextWorldPersistenceId));
    }

    /**
     * Captures mutable world state under the DataHolder lock and persists only that immutable snapshot asynchronously.
     */
    public static void queueWorldSave(String dim) {
        if (dim == null || dim.isBlank()) return;
        synchronized (LOCK) {
            queueWorldSaveLocked(dim, false);
        }
    }

    private static void queueWorldSaveLocked(String dim, boolean finalSave) {
        if (invalidWorldConfigs.contains(dim)) {
            VideoPlayerMain.LOGGER.warn("Skipped saving VideoPlayer world {} because its loaded config was invalid", dim);
            return;
        }
        if (finalSave) {
            cancelWorldSaveDebounceLocked(dim);
            cancelWorldSaveRetryLocked(dim);
            submitWorldSaveLocked(dim, true);
            return;
        }
        if (!running) return;
        cancelWorldSaveRetryLocked(dim);
        cancelWorldSaveDebounceLocked(dim);
        AtomicReference<FoliaScheduler.TaskHandle> scheduled = new AtomicReference<>();
        FoliaScheduler.TaskHandle task;
        try {
            task = FoliaScheduler.runGlobalDelayed(() -> {
                synchronized (LOCK) {
                    if (worldSaveDebounceTasks.get(dim) != scheduled.get()) return;
                    worldSaveDebounceTasks.remove(dim);
                    if (running) {
                        try {
                            submitWorldSaveLocked(dim, false);
                        } catch (RuntimeException error) {
                            VideoPlayerMain.LOGGER.error("Failed to capture VideoPlayer world save for {}", dim, error);
                        }
                    }
                }
            }, WORLD_SAVE_DEBOUNCE_TICKS);
        } catch (RuntimeException error) {
            VideoPlayerMain.LOGGER.error("Failed to schedule VideoPlayer world save for {}", dim, error);
            return;
        }
        scheduled.set(task);
        worldSaveDebounceTasks.put(dim, task);
    }

    private static void submitFinalWorldSaveLocked(String dim) {
        try {
            submitWorldSaveLocked(dim, true);
        } catch (RuntimeException error) {
            VideoPlayerMain.LOGGER.error("Failed to capture final VideoPlayer world save for {}", dim, error);
        }
    }

    private static void submitWorldSaveLocked(String dim, boolean finalSave) {
        submitWorldSaveLocked(dim, finalSave, false);
    }

    private static void submitWorldSaveLocked(String dim, boolean finalSave, boolean retryAttempt) {
        WorldSaveQueue.Snapshot snapshot = captureWorldSaveLocked(dim, finalSave, retryAttempt);
        if (snapshot != null) worldSaveQueue.enqueue(snapshot);
    }

    private static WorldSaveQueue.Snapshot captureWorldSaveLocked(String dim, boolean finalSave, boolean retryAttempt) {
        if (invalidWorldConfigs.contains(dim)) return null;
        Path path = worldFiles.get(dim);
        HashMap<String, VideoArea> map = areas.get(dim);
        if (path == null || map == null) return null;
        long saveGeneration = ++nextSaveGeneration;
        WorldConfigSnapshot captured = WorldConfigSnapshot.capture(config, map.values(), saveGeneration);
        WorldPersistenceState persistence = worldPersistence.computeIfAbsent(
                dim,
                ignored -> new WorldPersistenceState(++nextWorldPersistenceId)
        );
        long version = ++persistence.latestVersion;
        persistence.latestSaveGeneration = saveGeneration;
        WorldSaveQueue.Snapshot snapshot = retryAttempt
                ? WorldSaveQueue.Snapshot.lazyRetry(
                        dim, path, lifecycleEpoch, persistence.id, version, saveGeneration,
                        () -> captured.serialize(gson)
                )
                : WorldSaveQueue.Snapshot.lazy(
                        dim, path, lifecycleEpoch, persistence.id, version, saveGeneration, finalSave,
                        () -> captured.serialize(gson)
                );
        persistence.dirtySnapshot = snapshot;
        return snapshot;
    }

    private static void cancelWorldSaveDebounceLocked(String dim) {
        FoliaScheduler.TaskHandle task = worldSaveDebounceTasks.remove(dim);
        if (task != null) task.cancel();
    }

    private static void cancelWorldSaveDebouncesLocked() {
        for (FoliaScheduler.TaskHandle task : worldSaveDebounceTasks.values()) {
            task.cancel();
        }
        worldSaveDebounceTasks.clear();
    }

    private static void cancelWorldSaveRetryLocked(String dim) {
        FoliaScheduler.TaskHandle task = worldSaveRetryTasks.remove(dim);
        if (task != null) task.cancel();
    }

    private static void cancelWorldSaveRetriesLocked() {
        for (FoliaScheduler.TaskHandle task : worldSaveRetryTasks.values()) {
            task.cancel();
        }
        worldSaveRetryTasks.clear();
    }

    private static void rememberWorldSnapshot(String dim, long saveGeneration) {
        WorldPersistenceState persistence = worldPersistence.get(dim);
        if (persistence != null) {
            persistence.persistedVersion = persistence.latestVersion;
            persistence.latestSaveGeneration = Math.max(0L, saveGeneration);
            persistence.dirtySnapshot = null;
        }
    }

    private static WorldSaveQueue.WriteOutcome writeQueuedWorldSnapshot(WorldSaveQueue.Snapshot snapshot) {
        synchronized (LOCK) {
            if (snapshot.lifecycleEpoch() != lifecycleEpoch && !snapshot.finalSave()) return WorldSaveQueue.WriteOutcome.SKIPPED;
            if (!running && !snapshot.finalSave()) return WorldSaveQueue.WriteOutcome.SKIPPED;
            WorldPersistenceState persistence = worldPersistence.get(snapshot.dimension());
            if (!snapshot.finalSave() && !matches(persistence, snapshot)) return WorldSaveQueue.WriteOutcome.SKIPPED;
            if (snapshot.finalSave() && running && persistence != null
                    && persistence.id == snapshot.persistenceId() && persistence.latestVersion > snapshot.version()) {
                return WorldSaveQueue.WriteOutcome.SKIPPED;
            }
        }
        if (!writeWorldSnapshot(snapshot)) return WorldSaveQueue.WriteOutcome.SKIPPED;
        synchronized (LOCK) {
            WorldPersistenceState persistence = worldPersistence.get(snapshot.dimension());
            if (matches(persistence, snapshot) && persistence.latestVersion == snapshot.version()) {
                persistence.persistedVersion = snapshot.version();
                persistence.dirtySnapshot = null;
                persistedWorldSnapshots.put(snapshot.dimension(), snapshot.serialized());
                persistence.latestSaveGeneration = snapshot.saveGeneration();
            }
        }
        return WorldSaveQueue.WriteOutcome.WRITTEN;
    }

    private static void queuedWorldSaveFailed(WorldSaveQueue.Snapshot snapshot, Throwable error) {
        boolean scheduleRetry = false;
        synchronized (LOCK) {
            WorldPersistenceState persistence = worldPersistence.get(snapshot.dimension());
            if (matches(persistence, snapshot) && persistence.latestVersion == snapshot.version()) {
                persistence.dirtySnapshot = snapshot;
                scheduleRetry = running
                        && lifecycleEpoch == snapshot.lifecycleEpoch()
                        && !snapshot.finalSave()
                        && !snapshot.retryAttempt()
                        && !worldSaveRetryTasks.containsKey(snapshot.dimension());
            }
            if (scheduleRetry) {
                try {
                    scheduleWorldSaveRetryLocked(snapshot);
                } catch (RuntimeException retryError) {
                    VideoPlayerMain.LOGGER.error("Failed to schedule VideoPlayer world retry for {}", snapshot.dimension(), retryError);
                }
            }
        }
        VideoPlayerMain.LOGGER.error(
                "Failed to persist VideoPlayer world {} to {}; changes remain queued for the next save",
                snapshot.dimension(), snapshot.path(), error
        );
    }

    private static void scheduleWorldSaveRetryLocked(WorldSaveQueue.Snapshot failed) {
        AtomicReference<FoliaScheduler.TaskHandle> scheduled = new AtomicReference<>();
        FoliaScheduler.TaskHandle task = FoliaScheduler.runGlobalDelayed(() -> {
            synchronized (LOCK) {
                if (worldSaveRetryTasks.get(failed.dimension()) != scheduled.get()) return;
                worldSaveRetryTasks.remove(failed.dimension());
                WorldPersistenceState persistence = worldPersistence.get(failed.dimension());
                if (!running || lifecycleEpoch != failed.lifecycleEpoch() || persistence == null
                        || persistence.dirtySnapshot != failed) {
                    return;
                }
                try {
                    submitWorldSaveLocked(failed.dimension(), false, true);
                } catch (RuntimeException error) {
                    VideoPlayerMain.LOGGER.error("Failed to capture VideoPlayer world retry for {}", failed.dimension(), error);
                }
            }
        }, WORLD_SAVE_RETRY_TICKS);
        scheduled.set(task);
        worldSaveRetryTasks.put(failed.dimension(), task);
    }

    private static boolean matches(WorldPersistenceState persistence, WorldSaveQueue.Snapshot snapshot) {
        return persistence != null
                && persistence.id == snapshot.persistenceId()
                && snapshot.version() <= persistence.latestVersion;
    }

    static boolean writeWorldSnapshot(WorldSaveQueue.Snapshot snapshot) {
        return writeWorldSnapshot(snapshot, FILE_LOCK_TIMEOUT_MILLIS);
    }

    static boolean writeWorldSnapshot(WorldSaveQueue.Snapshot snapshot, long lockTimeoutMillis) {
        Path path = snapshot.path();
        return withWorldFileLock(path, lockTimeoutMillis, () -> {
            long persistedGeneration = persistedGeneration(path);
            if (persistedGeneration > snapshot.saveGeneration()) return false;
            writeString(path, snapshot.serialized());
            return true;
        });
    }

    private static <T> T withWorldFileLock(Path path, long lockTimeoutMillis, LockedFileOperation<T> operation) {
        Path lockPath = path.resolveSibling(path.getFileName() + ".lock");
        long deadline = System.nanoTime() + Math.max(0L, lockTimeoutMillis) * 1_000_000L;
        try {
            Files.createDirectories(path.getParent());
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                while (true) {
                    FileLock fileLock = null;
                    try {
                        fileLock = channel.tryLock();
                    } catch (OverlappingFileLockException ignored) {
                    }
                    if (fileLock != null) {
                        FileLock acquired = fileLock;
                        try (acquired) {
                            return operation.run();
                        }
                    }
                    if (System.nanoTime() >= deadline) {
                        throw new IllegalStateException("Timed out acquiring VideoPlayer world lock " + lockPath);
                    }
                    try {
                        Thread.sleep(FILE_LOCK_RETRY_MILLIS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while acquiring VideoPlayer world lock " + lockPath, interrupted);
                    }
                }
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to access VideoPlayer world file " + path, error);
        }
    }

    private static long persistedGeneration(Path path) {
        try {
            if (!Files.isRegularFile(path)) return 0L;
            JsonElement root = JsonParser.parseString(Files.readString(path));
            if (!root.isJsonObject()) return 0L;
            JsonElement generation = root.getAsJsonObject().get("saveGeneration");
            if (generation == null || !generation.isJsonPrimitive()) return 0L;
            return Math.max(0L, generation.getAsLong());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static void playerJoin(Player player) {
        if (player == null) return;
        synchronized (LOCK) {
            VideoPlayerPaperPlugin owner = plugin;
            long epoch = lifecycleEpoch;
            if (!running || owner == null || !owner.isEnabled() || !player.isOnline()) return;
            UUID uuid = player.getUniqueId();
            FoliaScheduler.TaskHandle old = playerTasks.remove(uuid);
            if (old != null) old.cancel();
            cancelReloadHandshakeLocked(uuid);
            playerPositions.remove(uuid);
            FoliaScheduler.TaskHandle task;
            try {
                AtomicReference<FoliaScheduler.TaskHandle> scheduled = new AtomicReference<>(FoliaScheduler.TaskHandle.NONE);
                task = FoliaScheduler.runAtEntityFixedRate(
                        player,
                        () -> updatePlayer(player, owner, epoch, scheduled.get()),
                        () -> playerTrackingRetired(uuid, scheduled.get()),
                        1L,
                        PLAYER_TRACKING_PERIOD_TICKS
                );
                scheduled.set(task);
            } catch (Throwable error) {
                handshakes.remove(uuid);
                handshakeTokens.remove(uuid);
                onlinePlayerIds.remove(uuid);
                onlinePlayerNames.remove(uuid);
                VideoPlayerMain.LOGGER.warn("Failed to start VideoPlayer tracking for {}", player.getName(), error);
                return;
            }
            if (task == null || task == FoliaScheduler.TaskHandle.NONE) {
                handshakes.remove(uuid);
                handshakeTokens.remove(uuid);
                onlinePlayerIds.remove(uuid);
                onlinePlayerNames.remove(uuid);
                return;
            }
            VideoHandshakeState previousHandshake = handshakes.get(uuid);
            if (previousHandshake == null || previousHandshake == VideoHandshakeState.NEEDS_RESET) {
                handshakes.put(uuid, VideoHandshakeState.NEEDS_RESET);
                handshakeNonces.remove(uuid);
                handshakeTokens.remove(uuid);
            }
            onlinePlayerIds.add(uuid);
            onlinePlayerNames.put(uuid, player.getName());
            playerTasks.put(uuid, task);
        }
    }

    public static boolean sendResetHandshake(Player player) {
        if (player == null) return false;
        byte[] reset;
        synchronized (LOCK) {
            VideoPlayerPaperPlugin owner = plugin;
            UUID uuid = player.getUniqueId();
            if (!running || owner == null || !owner.isEnabled() || !player.isOnline() || !onlinePlayerIds.contains(uuid)) {
                return false;
            }
            cancelReloadHandshakeLocked(uuid);
            long nonce = nextHandshakeNonce();
            handshakes.put(uuid, VideoHandshakeState.RESET_SENT);
            handshakeNonces.put(uuid, nonce);
            allPlayers.remove(uuid);
            String token = handshakeTokens.computeIfAbsent(uuid, ignored -> VideoProtocol.legacyToken());
            reset = VideoPackets.resetClient(token, config, nonce);
        }
        sendTo(player, reset);
        return true;
    }

    public static boolean scheduleReloadHandshake(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        long expectedEpoch;
        long requestId;
        synchronized (LOCK) {
            VideoPlayerPaperPlugin owner = plugin;
            if (!running || owner == null || !owner.isEnabled() || !player.isOnline() || !onlinePlayerIds.contains(uuid)) {
                return false;
            }
            cancelReloadHandshakeLocked(uuid);
            expectedEpoch = lifecycleEpoch;
            requestId = ++nextReloadHandshakeRequest;
            reloadHandshakeRequests.put(uuid, requestId);
        }
        FoliaScheduler.TaskHandle task;
        try {
            task = FoliaScheduler.runAtEntityDelayed(
                    player,
                    () -> runScheduledReloadHandshake(player, uuid, expectedEpoch, requestId),
                    () -> retireScheduledReloadHandshake(uuid, requestId),
                    RELOAD_HANDSHAKE_DELAY_TICKS
            );
        } catch (Throwable error) {
            synchronized (LOCK) {
                if (reloadHandshakeRequests.getOrDefault(uuid, 0L) == requestId) {
                    reloadHandshakeRequests.remove(uuid);
                }
            }
            VideoPlayerMain.LOGGER.warn("Failed to schedule VideoPlayer reload handshake for {}", player.getName(), error);
            return false;
        }
        if (task == null || task == FoliaScheduler.TaskHandle.NONE) {
            synchronized (LOCK) {
                if (reloadHandshakeRequests.getOrDefault(uuid, 0L) == requestId) {
                    reloadHandshakeRequests.remove(uuid);
                }
            }
            return false;
        }
        synchronized (LOCK) {
            if (!running || lifecycleEpoch != expectedEpoch || reloadHandshakeRequests.getOrDefault(uuid, 0L) != requestId) {
                task.cancel();
                return false;
            }
            reloadHandshakeTasks.put(uuid, task);
        }
        return true;
    }

    private static void runScheduledReloadHandshake(Player player, UUID uuid, long expectedEpoch, long requestId) {
        synchronized (LOCK) {
            if (reloadHandshakeRequests.getOrDefault(uuid, 0L) != requestId) return;
            reloadHandshakeRequests.remove(uuid);
            reloadHandshakeTasks.remove(uuid);
            if (!running || lifecycleEpoch != expectedEpoch || handshakes.getOrDefault(uuid, VideoHandshakeState.NEEDS_RESET)
                    != VideoHandshakeState.NEEDS_RESET) {
                return;
            }
        }
        sendResetHandshake(player);
    }

    private static void retireScheduledReloadHandshake(UUID uuid, long requestId) {
        synchronized (LOCK) {
            if (reloadHandshakeRequests.getOrDefault(uuid, 0L) != requestId) return;
            reloadHandshakeRequests.remove(uuid);
            reloadHandshakeTasks.remove(uuid);
        }
    }

    public static void playerLeave(UUID uuid) {
        synchronized (LOCK) {
            allPlayers.remove(uuid);
            String previousDim = playerDim.remove(uuid);
            onlinePlayerIds.remove(uuid);
            onlinePlayerNames.remove(uuid);
            handshakes.remove(uuid);
            handshakeNonces.remove(uuid);
            handshakeTokens.remove(uuid);
            cancelReloadHandshakeLocked(uuid);
            playerPositions.remove(uuid);
            FoliaScheduler.TaskHandle task = playerTasks.remove(uuid);
            if (task != null) task.cancel();
            for (HashMap<String, VideoArea> map : areas.values()) {
                for (VideoArea area : map.values()) {
                    area.removePlayer(uuid);
                }
            }
            releaseWorldIfUnusedLocked(previousDim);
        }
    }

    private static void updatePlayer(Player player, VideoPlayerPaperPlugin owner, long expectedEpoch, FoliaScheduler.TaskHandle scheduled) {
        UUID uuid = player.getUniqueId();
        World world = player.getWorld();
        Location location = player.getLocation();
        String dim = worldKey(world);
        if (!isWorldTrackingReady(dim)) {
            ensureWorldLoaded(world);
        }
        PlayerPosition position = new PlayerPosition(dim, location.getX(), location.getY(), location.getZ());
        synchronized (LOCK) {
            if (playerTasks.get(uuid) != scheduled) {
                scheduled.cancel();
                return;
            }
            if (!running || plugin != owner || lifecycleEpoch != expectedEpoch || !owner.isEnabled() || !player.isOnline()) {
                scheduled.cancel();
                playerTasks.remove(uuid, scheduled);
                playerPositions.remove(uuid);
                return;
            }
            if (!allPlayers.contains(uuid)) return;
            PlayerPosition previousPosition = playerPositions.get(uuid);
            if (!shouldScan(previousPosition, position)) return;
            String previousDim = playerDim.get(uuid);
            if (previousDim != null && !previousDim.equals(dim)) {
                removeFromWorld(uuid, previousDim, player);
            }
            HashMap<String, VideoArea> all = areas.get(dim);
            if (all != null) {
                for (VideoArea area : all.values()) {
                    if (area.inBounds(location.getX(), location.getY(), location.getZ())) {
                        if (area.addPlayer(uuid)) {
                            sendAreaSnapshot(player, area);
                            actionBar(player, VpTranslation.of("message.videoplayer.area_enter", "Entered video area %s", area.name), NamedTextColor.DARK_AQUA);
                            area.playerEntered();
                        }
                    } else if (area.removePlayer(uuid)) {
                        sendTo(player, VideoPackets.unloadArea(area));
                        sendTo(player, VideoPackets.removeArea(area));
                        actionBar(player, VpTranslation.of("message.videoplayer.area_leave", "Left video area %s", area.name), NamedTextColor.DARK_AQUA);
                    }
                }
            }
            playerDim.put(uuid, dim);
            playerPositions.put(uuid, position);
            if (previousDim != null && !previousDim.equals(dim)) releaseWorldIfUnusedLocked(previousDim);
        }
    }

    private static void playerTrackingRetired(UUID uuid, FoliaScheduler.TaskHandle scheduled) {
        synchronized (LOCK) {
            if (playerTasks.get(uuid) != scheduled) return;
            playerLeave(uuid);
        }
    }

    public static void invalidateWorldTracking(String dim) {
        if (dim == null) return;
        synchronized (LOCK) {
            playerPositions.entrySet().removeIf(entry -> dim.equals(entry.getValue().dimension()));
        }
    }

    public static boolean worldConfigValid(String dim) {
        synchronized (LOCK) {
            return dim != null && !invalidWorldConfigs.contains(dim);
        }
    }

    static boolean shouldScan(PlayerPosition previous, PlayerPosition current) {
        return current != null && !current.equals(previous);
    }

    public static void runForPlayer(Player player, Runnable runnable) {
        if (player == null || runnable == null) return;
        VideoPlayerPaperPlugin owner;
        long expectedEpoch;
        synchronized (LOCK) {
            owner = plugin;
            expectedEpoch = lifecycleEpoch;
            if (!running || owner == null || !owner.isEnabled()) return;
        }
        FoliaScheduler.runAtEntity(player, () -> {
            synchronized (LOCK) {
                if (running && plugin == owner && lifecycleEpoch == expectedEpoch && owner.isEnabled()) runnable.run();
            }
        }, null);
    }

    public static void runForPlayer(UUID uuid, Consumer<Player> consumer) {
        if (uuid == null || consumer == null) return;
        VideoPlayerPaperPlugin owner;
        long expectedEpoch;
        synchronized (LOCK) {
            owner = plugin;
            expectedEpoch = lifecycleEpoch;
            if (!running || owner == null || !owner.isEnabled()) return;
        }
        FoliaScheduler.runGlobal(() -> {
            synchronized (LOCK) {
                if (!running || plugin != owner || lifecycleEpoch != expectedEpoch || !owner.isEnabled()) return;
            }
            Player player = resolveOnlinePlayer(uuid);
            if (player == null) return;
            FoliaScheduler.runAtEntity(player, () -> {
                synchronized (LOCK) {
                    if (!running || plugin != owner || lifecycleEpoch != expectedEpoch || !owner.isEnabled()) return;
                    consumer.accept(player);
                }
            }, null);
        });
    }

    public static void runStateForPlayer(Player player, Runnable runnable) {
        if (player == null || runnable == null) return;
        VideoPlayerPaperPlugin owner;
        long expectedEpoch;
        synchronized (LOCK) {
            owner = plugin;
            expectedEpoch = lifecycleEpoch;
            if (!running || owner == null || !owner.isEnabled()) return;
        }
        FoliaScheduler.runAtEntity(player, () -> {
            synchronized (LOCK) {
                if (running && plugin == owner && lifecycleEpoch == expectedEpoch && owner.isEnabled()) runnable.run();
            }
        }, null);
    }

    public static void sendTo(Player player, byte[] bytes) {
        if (player == null || bytes == null) return;
        if (bytes.length > VideoPackets.MAX_PAYLOAD_BYTES) {
            VideoPlayerMain.LOGGER.warn("Dropped oversized VideoPlayer payload: {} bytes", bytes.length);
            return;
        }
        runForPlayer(player, () -> {
            if (player.isOnline()) {
                player.sendPluginMessage(plugin, VideoPlayerPaperPlugin.CHANNEL, bytes);
            }
        });
    }

    public static void sendTo(UUID uuid, byte[] bytes) {
        if (uuid == null || bytes == null) return;
        if (!isOnlineTracked(uuid)) return;
        if (bytes.length > VideoPackets.MAX_PAYLOAD_BYTES) {
            VideoPlayerMain.LOGGER.warn("Dropped oversized VideoPlayer payload: {} bytes", bytes.length);
            return;
        }
        runForPlayer(uuid, player -> {
            if (player.isOnline()) {
                player.sendPluginMessage(plugin, VideoPlayerPaperPlugin.CHANNEL, bytes);
            }
        });
    }

    public static void sendToCurrentThread(Player player, byte[] bytes) {
        if (player == null || bytes == null || plugin == null || !plugin.isEnabled()) return;
        if (bytes.length > VideoPackets.MAX_PAYLOAD_BYTES) {
            VideoPlayerMain.LOGGER.warn("Dropped oversized VideoPlayer payload: {} bytes", bytes.length);
            return;
        }
        if (player.isOnline()) {
            player.sendPluginMessage(plugin, VideoPlayerPaperPlugin.CHANNEL, bytes);
        }
    }

    public static void message(Player player, String message) {
        if (player == null || message == null || message.isBlank()) return;
        runForPlayer(player, () -> {
            if (player.isOnline()) player.sendMessage(message);
        });
    }

    public static void message(Player player, VpTranslation message) {
        if (player == null || message == null || message.isEmpty()) return;
        runForPlayer(player, () -> {
            if (player.isOnline()) player.sendMessage(PaperTexts.text(message));
        });
    }

    public static void message(UUID uuid, long epoch, String message) {
        if (!lifecycleActive(epoch)) return;
        message(uuid, message);
    }

    public static void message(UUID uuid, long epoch, VpTranslation message) {
        if (!lifecycleActive(epoch)) return;
        message(uuid, message);
    }

    public static void message(UUID uuid, String message) {
        if (uuid == null || message == null || message.isBlank() || !isOnlineTracked(uuid)) return;
        runForPlayer(uuid, player -> {
            if (player.isOnline()) player.sendMessage(message);
        });
    }

    public static void message(UUID uuid, VpTranslation message) {
        if (uuid == null || message == null || message.isEmpty() || !isOnlineTracked(uuid)) return;
        runForPlayer(uuid, player -> {
            if (player.isOnline()) player.sendMessage(PaperTexts.text(message));
        });
    }

    private static void actionBar(Player player, VpTranslation message, NamedTextColor color) {
        if (player == null || message == null || message.isEmpty()) return;
        runForPlayer(player, () -> {
            if (player.isOnline()) player.sendActionBar(PaperTexts.text(message).color(color));
        });
    }

    public static void disconnect(Player player, String message) {
        if (player == null) return;
        runForPlayer(player, () -> player.kickPlayer(message == null ? "Disconnected" : message));
    }

    private static Player resolveOnlinePlayer(UUID uuid) {
        if (uuid == null) return null;
        try {
            org.bukkit.Server server = org.bukkit.Bukkit.getServer();
            if (server == null) return null;
            return server.getPlayer(uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isOnlineTracked(UUID uuid) {
        if (uuid == null) return false;
        synchronized (LOCK) {
            return onlinePlayerIds.contains(uuid);
        }
    }

    public static UUID onlinePlayerUuid(String name) {
        if (name == null) return null;
        synchronized (LOCK) {
            for (Map.Entry<UUID, String> entry : onlinePlayerNames.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
            }
        }
        return null;
    }

    public static long lifecycleEpoch() {
        synchronized (LOCK) {
            return lifecycleEpoch;
        }
    }

    public static boolean lifecycleActive(long epoch) {
        synchronized (LOCK) {
            return running && lifecycleEpoch == epoch;
        }
    }

    public static VideoHandshakeState handshakeState(UUID uuid) {
        synchronized (LOCK) {
            return handshakes.getOrDefault(uuid, VideoHandshakeState.NEEDS_RESET);
        }
    }

    public static VideoHandshakeState acceptHandshake(UUID uuid) {
        synchronized (LOCK) {
            VideoHandshakeState state = handshakes.getOrDefault(uuid, VideoHandshakeState.NEEDS_RESET);
            if (state == VideoHandshakeState.REJECTED) return state;
            if (state == VideoHandshakeState.NEEDS_RESET) {
                handshakes.put(uuid, VideoHandshakeState.RESET_SENT);
                return VideoHandshakeState.RESET_SENT;
            }
            if (state == VideoHandshakeState.RESET_SENT) {
                handshakes.put(uuid, VideoHandshakeState.ACTIVE);
                allPlayers.add(uuid);
                return VideoHandshakeState.ACTIVE;
            }
            return VideoHandshakeState.ACTIVE;
        }
    }

    public static boolean recordHandshakeToken(UUID uuid, String remoteToken) {
        if (uuid == null) return false;
        synchronized (LOCK) {
            String token = VideoProtocol.responseToken(VideoPlayerMain.version, remoteToken);
            String previous = handshakeTokens.put(uuid, token);
            cancelReloadHandshakeLocked(uuid);
            return previous == null || !previous.equals(token);
        }
    }

    public static String handshakeToken(UUID uuid) {
        synchronized (LOCK) {
            return handshakeTokenLocked(uuid);
        }
    }

    public static long issueHandshakeNonce(UUID uuid) {
        synchronized (LOCK) {
            long nonce = nextHandshakeNonce();
            handshakeNonces.put(uuid, nonce);
            return nonce;
        }
    }

    private static long nextHandshakeNonce() {
        long nonce;
        do {
            nonce = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        } while (nonce == 0L);
        return nonce;
    }

    public static long handshakeNonce(UUID uuid) {
        synchronized (LOCK) {
            return handshakeNonces.getOrDefault(uuid, 0L);
        }
    }

    public static boolean acceptHandshakeAck(UUID uuid, long nonce) {
        synchronized (LOCK) {
            if (nonce == 0L || handshakeNonces.getOrDefault(uuid, 0L) != nonce) return false;
            if (handshakes.get(uuid) != VideoHandshakeState.RESET_SENT) return false;
            handshakeNonces.remove(uuid);
            handshakes.put(uuid, VideoHandshakeState.ACTIVE);
            allPlayers.add(uuid);
            return true;
        }
    }

    public static boolean rejectHandshake(UUID uuid) {
        synchronized (LOCK) {
            VideoHandshakeState previous = handshakes.put(uuid, VideoHandshakeState.REJECTED);
            allPlayers.remove(uuid);
            handshakeTokens.remove(uuid);
            cancelReloadHandshakeLocked(uuid);
            return previous != VideoHandshakeState.REJECTED;
        }
    }

    private static String handshakeTokenLocked(UUID uuid) {
        return handshakeTokens.getOrDefault(uuid, VideoProtocol.token(VideoPlayerMain.version));
    }

    private static void cancelReloadHandshakeLocked(UUID uuid) {
        reloadHandshakeRequests.remove(uuid);
        FoliaScheduler.TaskHandle task = reloadHandshakeTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private static void cancelReloadHandshakesLocked() {
        for (FoliaScheduler.TaskHandle task : reloadHandshakeTasks.values()) {
            task.cancel();
        }
        reloadHandshakeTasks.clear();
        reloadHandshakeRequests.clear();
    }

    public static boolean protocolActive(UUID uuid) {
        synchronized (LOCK) {
            return handshakes.get(uuid) == VideoHandshakeState.ACTIVE;
        }
    }

    public static boolean supportsClientPlaybackReporting(UUID uuid) {
        synchronized (LOCK) {
            return handshakes.get(uuid) == VideoHandshakeState.ACTIVE
                    && VideoProtocol.supportsClientPlaybackReporting(handshakeTokenLocked(uuid));
        }
    }

    public static boolean supportsIdlePlayMutations(UUID uuid) {
        synchronized (LOCK) {
            return handshakes.get(uuid) == VideoHandshakeState.ACTIVE
                    && VideoProtocol.supportsIdlePlayMutations(handshakeTokenLocked(uuid));
        }
    }

    public static void refreshPlayerProtocol(Player player) {
        if (player == null) return;
        synchronized (LOCK) {
            UUID uuid = player.getUniqueId();
            if (handshakes.get(uuid) != VideoHandshakeState.ACTIVE) return;
            boolean mutations = VideoProtocol.supportsIdlePlayMutations(handshakeTokenLocked(uuid));
            for (HashMap<String, VideoArea> world : areas.values()) {
                for (VideoArea area : world.values()) {
                    if (!area.containsPlayer(uuid)) continue;
                    for (VideoScreen screen : area.screens) {
                        screen.addPlayer(uuid);
                        if (!screen.idlePlayEntries.isEmpty() || screen.idlePlayRandom) {
                            sendToCurrentThread(player, VideoPackets.idlePlay(screen, mutations));
                        }
                    }
                }
            }
        }
    }

    public static VideoScreen findScreen(ScreenKey key) {
        if (key == null) return null;
        synchronized (LOCK) {
            HashMap<String, VideoArea> world = areas.get(key.dimension());
            if (world == null) return null;
            VideoArea area = world.get(key.areaName());
            return area == null ? null : area.getScreen(key.screenName());
        }
    }

    public static String worldKey(World world) {
        return world.getKey().toString();
    }

    private static void removeFromWorld(UUID uuid, String dim, Player player) {
        HashMap<String, VideoArea> map = areas.get(dim);
        if (map == null) return;
        for (VideoArea area : map.values()) {
            if (area.removePlayer(uuid)) {
                sendTo(player, VideoPackets.unloadArea(area));
                sendTo(player, VideoPackets.removeArea(area));
            }
        }
    }

    private static void unloadAreaForPlayers(VideoArea area) {
        if (!area.hasPlayer()) return;
        byte[] unload = VideoPackets.unloadArea(area);
        byte[] remove = VideoPackets.removeArea(area);
        for (UUID uuid : area.playerSnapshot()) {
            sendTo(uuid, unload);
            sendTo(uuid, remove);
        }
    }

    private static void sendAreaSnapshot(Player player, VideoArea area) {
        sendToCurrentThread(player, VideoPackets.createArea(area));
        ServerPacketHandler.sendAreaPermissions(player, area);
        for (VideoScreen screen : area.screens) {
            sendToCurrentThread(player, VideoPackets.createScreen(java.util.List.of(screen)));
            for (Map.Entry<String, com.github.squi2rel.vp.video.MetaValue> entry : screen.metadata.entries().entrySet()) {
                sendToCurrentThread(player, VideoPackets.setMetadata(screen, entry.getKey(), entry.getValue()));
            }
            if (!screen.idlePlayEntries.isEmpty() || screen.idlePlayRandom) {
                sendToCurrentThread(player, VideoPackets.idlePlay(
                        screen, supportsIdlePlayMutations(player.getUniqueId())
                ));
            }
        }
        boolean loadedPlayback = false;
        for (VideoScreen screen : area.screens) {
            if (screen.currentPlayback() != null) {
                sendToCurrentThread(player, VideoPackets.loadArea(area, screen));
                loadedPlayback = true;
            }
        }
        if (!loadedPlayback) {
            sendToCurrentThread(player, VideoPackets.loadArea(area, null));
        }
        for (VideoScreen screen : area.screens) {
            sendToCurrentThread(player, VideoPackets.updatePlaylist(java.util.List.of(screen)));
        }
    }

    private static ReadResult readConfig(Path path) {
        try {
            if (!Files.exists(path)) return new ReadResult(new ServerConfig(), false, false, 0, 0);
            JsonElement root = JsonParser.parseString(Files.readString(path));
            RecoveryResult recovery = recoverIncorrectLegacyMigration(path, root);
            return parseConfig(recovery.root(), recovery.recovered());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read VideoPlayer world config " + path + "; original file was not modified", e);
        }
    }

    private static ReadResult readConfig(Path path, String serialized) {
        try {
            return parseConfig(JsonParser.parseString(serialized), false);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to restore queued VideoPlayer world config " + path, error);
        }
    }

    private static ReadResult parseConfig(JsonElement root, boolean recovered) {
        boolean hasSharedConfig = root.isJsonObject() && (
                root.getAsJsonObject().has("remoteControlName")
                        || root.getAsJsonObject().has("remoteControlId")
                        || root.getAsJsonObject().has("remoteControlRange")
                        || root.getAsJsonObject().has("noControlRange")
        );
        LegacyConfigMigrator.Result migration = LegacyConfigMigrator.migrate(root);
        ServerConfig read = gson.fromJson(migration.root(), ServerConfig.class);
        if (read == null) read = new ServerConfig();
        validateConfig(read);
        return new ReadResult(read, migration.migrated() || recovered, hasSharedConfig, migration.areas(), migration.screens());
    }

    private static RecoveryResult recoverIncorrectLegacyMigration(Path path, JsonElement currentRoot) throws IOException {
        if (!currentRoot.isJsonObject()) return new RecoveryResult(currentRoot, false);
        JsonObject current = currentRoot.getAsJsonObject();
        if (current.has("dataVersion") && current.get("dataVersion").isJsonPrimitive()
                && current.get("dataVersion").getAsInt() >= 2) {
            return new RecoveryResult(currentRoot, false);
        }
        Path backup = path.resolveSibling(path.getFileName() + ".1.6.5.bak");
        if (!Files.isRegularFile(backup)) return new RecoveryResult(currentRoot, false);
        JsonElement original = JsonParser.parseString(Files.readString(backup));
        if (!original.isJsonArray()) return new RecoveryResult(currentRoot, false);
        LegacyConfigMigrator.Result recovered = LegacyConfigMigrator.migrate(original);
        JsonObject restored = recovered.root();
        for (String key : new String[]{"remoteControlName", "remoteControlId", "remoteControlRange", "noControlRange"}) {
            if (current.has(key)) restored.add(key, current.get(key).deepCopy());
        }
        VideoPlayerMain.LOGGER.warn("Repairing incorrectly oriented legacy screens in {} from backup {}", path, backup);
        return new RecoveryResult(restored, true);
    }

    static void validateConfig(ServerConfig loaded) {
        VideoConfigValidator.validate(loaded);
        for (VideoArea area : loaded.areas) {
            for (VideoScreen screen : area.screens) {
                screen.ensureValidState();
                if (screen.surface != ScreenSurface.SPHERE_360 && screen.vertices.size() < 3) {
                    throw new IllegalArgumentException("VideoPlayer flat screen must contain at least three vertices");
                }
                for (var vertex : screen.vertices) {
                    if (vertex == null || !finite(vertex.x, vertex.y, vertex.z)) {
                        throw new IllegalArgumentException("VideoPlayer screen contains an invalid vertex");
                    }
                }
            }
        }
    }

    private static boolean finite(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
    }

    private static void loadLegacyPluginConfig(VideoPlayerPaperPlugin owner) {
        var old = owner.getConfig();
        if (old.contains("remoteControlName")) config.remoteControlName = old.getString("remoteControlName", config.remoteControlName);
        if (old.contains("remoteControlId")) config.remoteControlId = (float) old.getDouble("remoteControlId", config.remoteControlId);
        if (old.contains("remoteControlRange")) config.remoteControlRange = (float) old.getDouble("remoteControlRange", config.remoteControlRange);
        if (old.contains("noControlRange")) config.noControlRange = (float) old.getDouble("noControlRange", config.noControlRange);
        String areasJson = old.getString("areas");
        if (areasJson == null || areasJson.isBlank() || areasJson.trim().equals("[]")) return;
        LegacyConfigMigrator.Result migration = LegacyConfigMigrator.migrate(JsonParser.parseString(areasJson));
        ServerConfig legacy = gson.fromJson(migration.root(), ServerConfig.class);
        if (legacy == null) return;
        validateConfig(legacy);
        legacyConfigAreas.addAll(legacy.areas);
        VideoPlayerMain.LOGGER.info(
                "Found {} legacy VideoPlayer areas with {} screens in plugin config.yml",
                migration.areas(), migration.screens()
        );
    }

    private static int importLegacyConfigAreas(String dim, HashMap<String, VideoArea> map) {
        int imported = 0;
        var iterator = legacyConfigAreas.iterator();
        while (iterator.hasNext()) {
            VideoArea area = iterator.next();
            if (!dim.equals(area.dim)) continue;
            if (map.containsKey(area.name)) {
                VideoPlayerMain.LOGGER.warn(
                        "Kept conflicting legacy area {} in config.yml because the 2.0 world file already contains that name in {}",
                        area.name, dim
                );
                continue;
            } else if (map.size() >= VideoArea.MAX_AREAS_PER_WORLD) {
                VideoPlayerMain.LOGGER.warn(
                        "Kept legacy area {} in config.yml because world {} already contains {} areas",
                        area.name, dim, VideoArea.MAX_AREAS_PER_WORLD
                );
                continue;
            } else {
                area.dim = dim;
                area.initServer();
                area.afterLoad();
                map.put(area.name, area);
                imported++;
            }
            iterator.remove();
        }
        return imported;
    }

    private static void persistLegacyPluginConfigAsync() {
        VideoPlayerPaperPlugin owner = plugin;
        if (owner == null) return;
        Path configPath = owner.getDataFolder().toPath().resolve("config.yml");
        owner.getConfig().set("areas", gson.toJson(legacyConfigAreas));
        String serialized = owner.getConfig().saveToString();
        long generation = ++nextLegacyConfigGeneration;
        legacyConfigSaveQueue.enqueue(new WorldSaveQueue.Snapshot(
                "plugin-config",
                configPath,
                lifecycleEpoch,
                0L,
                generation,
                generation,
                false,
                serialized
        ));
    }

    private static WorldSaveQueue.WriteOutcome writeLegacyConfigSnapshot(WorldSaveQueue.Snapshot snapshot) throws IOException {
        return withWorldFileLock(snapshot.path(), FILE_LOCK_TIMEOUT_MILLIS,
                () -> writeLegacyConfigSnapshotLocked(snapshot));
    }

    private static WorldSaveQueue.WriteOutcome writeLegacyConfigSnapshotLocked(WorldSaveQueue.Snapshot snapshot) throws IOException {
        Path backup = null;
        boolean createBackup;
        synchronized (LOCK) {
            if (snapshot.lifecycleEpoch() != lifecycleEpoch) return WorldSaveQueue.WriteOutcome.SKIPPED;
            createBackup = !legacyConfigBackupCreated;
        }
        if (createBackup) backup = backup(snapshot.path(), ".1.6.5.bak");
        synchronized (LOCK) {
            if (snapshot.lifecycleEpoch() != lifecycleEpoch) return WorldSaveQueue.WriteOutcome.SKIPPED;
            if (createBackup) legacyConfigBackupCreated = true;
        }
        try {
            writeString(snapshot.path(), snapshot.serialized());
            VideoPlayerMain.LOGGER.info("Updated legacy VideoPlayer config areas; backup: {}", backup);
            return WorldSaveQueue.WriteOutcome.WRITTEN;
        } catch (Throwable error) {
            if (error instanceof IOException io) throw io;
            if (error instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(error);
        }
    }

    private static void legacyConfigSaveFailed(WorldSaveQueue.Snapshot snapshot, Throwable error) {
        VideoPlayerMain.LOGGER.error("Failed to update legacy VideoPlayer config.yml", error);
    }

    private static Path backup(Path path, String suffix) throws IOException {
        Path backup = path.resolveSibling(path.getFileName() + suffix);
        if (Files.exists(backup) && Files.mismatch(path, backup) == -1) return backup;
        int index = 1;
        while (Files.exists(backup)) {
            backup = path.resolveSibling(path.getFileName() + suffix + "." + index++);
        }
        Files.copy(path, backup);
        return backup;
    }

    private static void applySharedConfig(ServerConfig loaded) {
        if (loaded.remoteControlName != null && !loaded.remoteControlName.isBlank()) {
            config.remoteControlName = loaded.remoteControlName;
        }
        config.remoteControlId = loaded.remoteControlId;
        config.remoteControlRange = loaded.remoteControlRange;
        config.noControlRange = loaded.noControlRange;
    }

    private static void writeString(Path path, String str) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, str, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    private record ReadResult(ServerConfig config, boolean migrated, boolean hasSharedConfig, int areaCount, int screenCount) {
    }

    private record WorldDescriptor(String dimension, Path path) {
    }

    private record WorldLoadResult(WorldDescriptor descriptor, boolean existingConfig, ReadResult result, Path backup,
                                   Throwable error, boolean retryPersistence) {
        private static WorldLoadResult failed(WorldDescriptor descriptor, Throwable error) {
            return new WorldLoadResult(descriptor, false, null, null, error, false);
        }
    }

    private record RecoveryResult(JsonElement root, boolean recovered) {
    }

    @FunctionalInterface
    private interface LockedFileOperation<T> {
        T run() throws Exception;
    }

    private static final class WorldPersistenceState {
        private final long id;
        private long latestVersion;
        private long persistedVersion;
        private long latestSaveGeneration;
        private WorldSaveQueue.Snapshot dirtySnapshot;

        private WorldPersistenceState(long id) {
            this.id = id;
        }
    }

    record PlayerPosition(String dimension, double x, double y, double z) {
    }
}
