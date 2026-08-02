package com.github.squi2rel.vp;

import com.github.squi2rel.vp.network.ServerPacketHandler;
import com.github.squi2rel.vp.network.VideoHandshakeState;
import com.github.squi2rel.vp.network.VideoPackets;
import com.github.squi2rel.vp.i18n.MinecraftTexts;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import com.github.squi2rel.vp.video.ScreenKey;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.Formatting;
import net.minecraft.world.dimension.DimensionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class DataHolder {
    static final long WORLD_SAVE_DEBOUNCE_MILLIS = 1_000L;
    static final long WORLD_SAVE_RETRY_MILLIS = 5_000L;
    static final long STOP_PERSISTENCE_TIMEOUT_MILLIS = 15_000L;
    static final long FILE_LOCK_TIMEOUT_MILLIS = 5_000L;
    public static ServerConfig config = new ServerConfig();
    public static HashSet<UUID> allPlayers = new HashSet<>();
    public static HashMap<UUID, String> playerDim = new HashMap<>();

    public static HashMap<String, HashMap<String, VideoArea>> areas = new HashMap<>();

    private static final Gson gson = new Gson();
    private static final HashMap<String, Path> worldFiles = new HashMap<>();
    private static final HashSet<String> invalidWorldConfigs = new HashSet<>();
    private static final HashMap<UUID, VideoHandshakeState> handshakes = new HashMap<>();
    private static final HashMap<UUID, Long> handshakeNonces = new HashMap<>();
    private static final HashMap<UUID, String> handshakeTokens = new HashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, UUID> onlinePlayerNames = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Object persistenceLock = new Object();
    private static final HashMap<String, ScheduledSave> scheduledWorldSaves = new HashMap<>();
    private static final HashMap<CompletableFuture<Void>, String> pendingWorldWrites = new HashMap<>();
    private static final HashMap<String, Long> failedWorldWrites = new HashMap<>();
    private static final ReentrantLock worldFileLock = new ReentrantLock();
    private static final AtomicInteger persistenceThreadIds = new AtomicInteger();
    private static ScheduledExecutorService persistenceExecutor;
    private static long nextSaveGeneration;
    private static volatile long lifecycleEpoch;
    private static volatile boolean running;

    public static volatile MinecraftServer server;

    public static void update() {
        MinecraftServer current = server;
        if (!running || current == null) return;
        PlayerManager pm = current.getPlayerManager();
        ArrayList<Runnable> notifications = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : new ArrayList<>(playerDim.entrySet())) {
            ServerPlayerEntity player = pm.getPlayer(entry.getKey());
            if (player == null) continue;
            String dim = player.getEntityWorld().getRegistryKey().getValue().toString();
            if (dim.equals(entry.getValue())) continue;
            HashMap<String, VideoArea> map = areas.get(entry.getValue());
            if (map == null) continue;
            for (VideoArea area : map.values()) {
                if (area.removePlayer(player.getUuid())) {
                    ServerPacketHandler.sendTo(player, VideoPackets.unloadArea(area));
                    ServerPacketHandler.sendTo(player, VideoPackets.removeArea(area));
                }
            }
        }
        for (UUID uuid : allPlayers) {
            ServerPlayerEntity player = pm.getPlayer(uuid);
            if (player == null) continue;
            String dim = player.getEntityWorld().getRegistryKey().getValue().toString();
            HashMap<String, VideoArea> all = areas.get(dim);
            if (all == null) {
                loadWorld(current, player.getEntityWorld());
                all = areas.get(dim);
            }
            if (all == null || all.isEmpty()) continue;
            for (VideoArea area : all.values()) {
                if (area.inBounds(player.getEntityPos())) {
                    if (area.addPlayer(player.getUuid())) {
                        sendAreaSnapshot(player, area);
                        area.playerEntered();
                        notifications.add(() -> player.sendMessage(MinecraftTexts.tr(
                                "message.videoplayer.area_enter",
                                "Entered video area %s",
                                area.name
                        ).formatted(Formatting.DARK_AQUA), true));
                    }
                } else {
                    if (area.removePlayer(player.getUuid())) {
                        notifications.add(() -> ServerPacketHandler.sendTo(player, VideoPackets.unloadArea(area)));
                        notifications.add(() -> ServerPacketHandler.sendTo(player, VideoPackets.removeArea(area)));
                        notifications.add(() -> player.sendMessage(MinecraftTexts.tr(
                                "message.videoplayer.area_leave",
                                "Left video area %s",
                                area.name
                        ).formatted(Formatting.DARK_AQUA), true));
                    }
                }
            }
        }
        for (ServerPlayerEntity player : PlayerLookup.all(current)) {
            playerDim.put(player.getUuid(), player.getEntityWorld().getRegistryKey().getValue().toString());
        }
        notifications.forEach(Runnable::run);
    }

    public static void unload(MinecraftServer s) {
        PlayerManager pm = s.getPlayerManager();
        for (HashMap<String, VideoArea> map : areas.values()) {
            for (VideoArea area : map.values()) {
                unloadArea(pm, area);
                area.remove();
            }
        }
    }

    public static void playerJoin(ServerPlayerEntity player) {
        if (!running || player == null) return;
        handshakes.put(player.getUuid(), VideoHandshakeState.NEEDS_RESET);
        handshakeNonces.remove(player.getUuid());
        handshakeTokens.remove(player.getUuid());
        onlinePlayerNames.put(player.getGameProfile().name().toLowerCase(Locale.ROOT), player.getUuid());
    }

    public static void playerLeave(UUID uuid) {
        allPlayers.remove(uuid);
        playerDim.remove(uuid);
        handshakes.remove(uuid);
        handshakeNonces.remove(uuid);
        handshakeTokens.remove(uuid);
        onlinePlayerNames.entrySet().removeIf(entry -> entry.getValue().equals(uuid));
        if (server != null) {
            server.execute(() -> {
                for (HashMap<String, VideoArea> value : areas.values()) {
                    for (VideoArea area : value.values()) {
                        area.removePlayer(uuid);
                    }
                }
            });
        }
    }

    public static void stop(MinecraftServer server) {
        running = false;
        lifecycleEpoch++;
        cancelScheduledWorldSaves();
        for (String dim : new ArrayList<>(areas.keySet())) {
            submitFinalWorldSave(dim);
        }
        boolean flushed = flushWorldWrites(STOP_PERSISTENCE_TIMEOUT_MILLIS);
        Set<String> unsaved = unsavedWorlds();
        if (!flushed || !unsaved.isEmpty()) {
            VideoPlayerMain.LOGGER.warn("Fabric VideoPlayer shutdown left unsaved worlds: {}", unsaved);
        }
        shutdownPersistenceExecutor();
        unload(server);
        areas.clear();
        worldFiles.clear();
        invalidWorldConfigs.clear();
        allPlayers.clear();
        playerDim.clear();
        handshakes.clear();
        handshakeNonces.clear();
        handshakeTokens.clear();
        onlinePlayerNames.clear();
        if (DataHolder.server == server) DataHolder.server = null;
        if (VideoPlayerMain.server == server) VideoPlayerMain.server = null;
    }

    public static void save() {
        for (String dim : new ArrayList<>(areas.keySet())) {
            try {
                saveWorld(dim);
            } catch (RuntimeException error) {
                recordFailedWorld(dim, nextSaveGeneration);
                VideoPlayerMain.LOGGER.error("Failed to capture VideoPlayer world save for {}", dim, error);
            }
        }
    }

    public static void load(MinecraftServer server) {
        resetPersistenceExecutor();
        DataHolder.server = server;
        running = true;
        lifecycleEpoch++;
        nextSaveGeneration = Math.max(nextSaveGeneration + 1L, System.currentTimeMillis());
        config = new ServerConfig();
        areas.clear();
        worldFiles.clear();
        invalidWorldConfigs.clear();
        allPlayers.clear();
        playerDim.clear();
        handshakes.clear();
        handshakeNonces.clear();
        handshakeTokens.clear();
        onlinePlayerNames.clear();
        for (ServerWorld world : server.getWorlds()) {
            loadWorld(server, world);
        }
    }

    public static void loadWorld(MinecraftServer server, ServerWorld world) {
        if (!running || server == null || world == null || DataHolder.server != server) return;
        String dim = world.getRegistryKey().getValue().toString();
        if (areas.containsKey(dim)) return;

        Path path = worldDirectory(server, world).resolve("videoplayer.json");
        worldFiles.put(dim, path);

        boolean existingConfig = Files.exists(path);
        ServerConfig loaded;
        try {
            loaded = readConfig(path);
            invalidWorldConfigs.remove(dim);
        } catch (RuntimeException error) {
            invalidWorldConfigs.add(dim);
            areas.put(dim, new HashMap<>());
            VideoPlayerMain.LOGGER.warn("Rejected invalid VideoPlayer world config {}; original file will not be modified", path, error);
            return;
        }
        if (existingConfig) applySharedConfig(loaded);
        nextSaveGeneration = Math.max(nextSaveGeneration, Math.max(0L, loaded.saveGeneration));

        HashMap<String, VideoArea> map = new HashMap<>();
        if (loaded.areas != null) {
            for (VideoArea area : loaded.areas) {
                area.dim = dim;
                if (area.screens == null) area.screens = new ArrayList<>();
                prepareArea(area);
                map.put(area.name, area);
            }
        }
        areas.put(dim, map);
        VideoPlayerMain.LOGGER.info("Loaded {} VideoPlayer areas for world {} from {}", map.size(), dim, path);
    }

    public static void unloadWorld(MinecraftServer server, ServerWorld world) {
        if (world == null) return;
        String dim = world.getRegistryKey().getValue().toString();
        try {
            saveWorld(dim);
        } catch (RuntimeException error) {
            recordFailedWorld(dim, nextSaveGeneration);
            VideoPlayerMain.LOGGER.error("Failed to capture final VideoPlayer world save for {}", dim, error);
        }
        HashMap<String, VideoArea> map = areas.remove(dim);
        if (map != null) {
            PlayerManager pm = server == null ? null : server.getPlayerManager();
            for (VideoArea area : map.values()) {
                unloadArea(pm, area);
                area.remove();
            }
        }
        worldFiles.remove(dim);
        invalidWorldConfigs.remove(dim);
        playerDim.entrySet().removeIf(entry -> dim.equals(entry.getValue()));
        VideoPlayerMain.LOGGER.info("Unloaded VideoPlayer world {}", dim);
    }

    public static void saveWorld(String dim) {
        cancelScheduledWorldSave(dim);
        submitWorldSave(dim, true, false);
    }

    private static void submitWorldSave(String dim, boolean allowRetry, boolean allowStopped) {
        if (!allowStopped && !running) return;
        if (invalidWorldConfigs.contains(dim)) {
            VideoPlayerMain.LOGGER.warn("Skipped saving VideoPlayer world {} because its loaded config was invalid", dim);
            return;
        }
        Path path = worldFiles.get(dim);
        HashMap<String, VideoArea> map = areas.get(dim);
        if (path == null || map == null) return;
        long generation = ++nextSaveGeneration;
        long epoch = lifecycleEpoch;
        WorldConfigSnapshot snapshot = WorldConfigSnapshot.capture(config, map.values(), generation);
        submitWorldWrite(dim, path, generation, epoch, snapshot, allowRetry);
    }

    private static void submitFinalWorldSave(String dim) {
        try {
            submitWorldSave(dim, false, true);
        } catch (RuntimeException error) {
            recordFailedWorld(dim, nextSaveGeneration);
            VideoPlayerMain.LOGGER.error("Failed to capture final VideoPlayer world save for {}", dim, error);
        }
    }

    public static void queueWorldSave(String dim) {
        scheduleWorldSave(dim, WORLD_SAVE_DEBOUNCE_MILLIS, true, true);
    }

    private static void scheduleWorldSave(String dim, long delayMillis, boolean allowRetry, boolean replaceExisting) {
        if (!running || dim == null || dim.isBlank()) return;
        synchronized (persistenceLock) {
            if (persistenceExecutor == null || persistenceExecutor.isShutdown()) return;
            ScheduledSave previous = scheduledWorldSaves.remove(dim);
            if (previous != null && !replaceExisting) {
                scheduledWorldSaves.put(dim, previous);
                return;
            }
            if (previous != null && previous.future != null) previous.future.cancel(false);
            ScheduledSave scheduled = new ScheduledSave(lifecycleEpoch, allowRetry);
            scheduledWorldSaves.put(dim, scheduled);
            try {
                scheduled.future = persistenceExecutor.schedule(
                        () -> dispatchScheduledWorldSave(dim, scheduled),
                        Math.max(0L, delayMillis),
                        TimeUnit.MILLISECONDS
                );
            } catch (RuntimeException error) {
                scheduledWorldSaves.remove(dim, scheduled);
                VideoPlayerMain.LOGGER.error("Failed to schedule VideoPlayer world save for {}", dim, error);
            }
        }
    }

    private static void dispatchScheduledWorldSave(String dim, ScheduledSave scheduled) {
        synchronized (persistenceLock) {
            if (scheduledWorldSaves.get(dim) != scheduled) return;
            scheduledWorldSaves.remove(dim);
        }
        MinecraftServer target = server;
        if (target == null) return;
        target.execute(() -> {
            if (!running || server != target || lifecycleEpoch != scheduled.epoch) return;
            try {
                submitWorldSave(dim, scheduled.allowRetry, false);
            } catch (RuntimeException error) {
                recordFailedWorld(dim, nextSaveGeneration);
                VideoPlayerMain.LOGGER.error("Failed to capture VideoPlayer world save for {}", dim, error);
            }
        });
    }

    private static void cancelScheduledWorldSave(String dim) {
        if (dim == null) return;
        ScheduledSave scheduled;
        synchronized (persistenceLock) {
            scheduled = scheduledWorldSaves.remove(dim);
        }
        if (scheduled != null && scheduled.future != null) scheduled.future.cancel(false);
    }

    private static void cancelScheduledWorldSaves() {
        ArrayList<ScheduledFuture<?>> tasks = new ArrayList<>();
        synchronized (persistenceLock) {
            for (ScheduledSave scheduled : scheduledWorldSaves.values()) {
                if (scheduled.future != null) tasks.add(scheduled.future);
            }
            scheduledWorldSaves.clear();
        }
        for (ScheduledFuture<?> task : tasks) task.cancel(false);
    }

    private static void submitWorldWrite(String dim, Path path, long generation, long epoch,
                                         WorldConfigSnapshot snapshot, boolean allowRetry) {
        CompletableFuture<Void> write;
        ScheduledExecutorService executor;
        synchronized (persistenceLock) {
            if (persistenceExecutor == null || persistenceExecutor.isShutdown()) {
                failedWorldWrites.merge(dim, generation, Math::max);
                return;
            }
            executor = persistenceExecutor;
            try {
                write = CompletableFuture.runAsync(
                        () -> writeWorldSnapshot(path, generation, snapshot.serialize(gson)),
                        executor
                );
            } catch (RuntimeException error) {
                failedWorldWrites.merge(dim, generation, Math::max);
                VideoPlayerMain.LOGGER.error("Failed to submit VideoPlayer world save for {}", dim, error);
                return;
            }
            pendingWorldWrites.put(write, dim);
        }
        write.whenComplete((ignored, error) -> {
            boolean active;
            synchronized (persistenceLock) {
                pendingWorldWrites.remove(write);
                active = persistenceExecutor == executor && lifecycleEpoch == epoch;
                if (active) {
                    if (error == null) {
                        Long failedGeneration = failedWorldWrites.get(dim);
                        if (failedGeneration != null && failedGeneration <= generation) failedWorldWrites.remove(dim);
                    } else {
                        failedWorldWrites.merge(dim, generation, Math::max);
                    }
                }
                persistenceLock.notifyAll();
            }
            if (error == null) return;
            VideoPlayerMain.LOGGER.error("Failed to persist VideoPlayer world {} to {}", dim, path, error);
            if (!allowRetry || !active) return;
            MinecraftServer target = server;
            if (target == null) return;
            target.execute(() -> {
                if (!running || server != target || lifecycleEpoch != epoch || !areas.containsKey(dim)) return;
                scheduleWorldSave(dim, WORLD_SAVE_RETRY_MILLIS, false, false);
            });
        });
    }

    private static boolean flushWorldWrites(long timeoutMillis) {
        long deadline = System.nanoTime() + Math.max(0L, timeoutMillis) * 1_000_000L;
        synchronized (persistenceLock) {
            while (!pendingWorldWrites.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return false;
                try {
                    long millis = Math.max(1L, remaining / 1_000_000L);
                    persistenceLock.wait(millis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    private static Set<String> unsavedWorlds() {
        synchronized (persistenceLock) {
            HashSet<String> worlds = new HashSet<>(pendingWorldWrites.values());
            worlds.addAll(failedWorldWrites.keySet());
            return Set.copyOf(worlds);
        }
    }

    private static void recordFailedWorld(String dim, long generation) {
        if (dim == null) return;
        synchronized (persistenceLock) {
            failedWorldWrites.merge(dim, generation, Math::max);
        }
    }

    private static void resetPersistenceExecutor() {
        shutdownPersistenceExecutor();
        synchronized (persistenceLock) {
            persistenceExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "VideoPlayer-world-save-" + persistenceThreadIds.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    private static void shutdownPersistenceExecutor() {
        cancelScheduledWorldSaves();
        ScheduledExecutorService executor;
        synchronized (persistenceLock) {
            executor = persistenceExecutor;
            persistenceExecutor = null;
            pendingWorldWrites.clear();
            failedWorldWrites.clear();
            persistenceLock.notifyAll();
        }
        if (executor != null) executor.shutdownNow();
    }

    private static void writeWorldSnapshot(Path path, long generation, String serialized) {
        boolean acquired;
        try {
            acquired = worldFileLock.tryLock(FILE_LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring Fabric VideoPlayer world lock", interrupted);
        }
        if (!acquired) throw new IllegalStateException("Timed out acquiring Fabric VideoPlayer world lock");
        try {
            if (persistedGeneration(path) > generation) return;
            writeString(path, serialized);
        } finally {
            worldFileLock.unlock();
        }
    }

    private static long persistedGeneration(Path path) {
        try {
            if (!Files.isRegularFile(path)) return 0L;
            ServerConfig persisted = gson.fromJson(Files.readString(path), ServerConfig.class);
            return persisted == null ? 0L : Math.max(0L, persisted.saveGeneration);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static boolean worldConfigValid(String dim) {
        return dim != null && !invalidWorldConfigs.contains(dim);
    }

    private static void prepareArea(VideoArea area) {
        VideoConfigValidator.validateArea(area);
        for (VideoScreen screen : area.screens) {
            screen.ensureValidState();
        }
        area.initServer();
        area.afterLoad();
    }

    private static void unloadArea(PlayerManager pm, VideoArea area) {
        if (pm == null || area == null || !area.hasPlayer()) return;
        byte[] unload = VideoPackets.unloadArea(area);
        byte[] remove = VideoPackets.removeArea(area);
        for (UUID uuid : area.playerSnapshot()) {
            ServerPlayerEntity player = pm.getPlayer(uuid);
            ServerPacketHandler.sendTo(player, unload);
            ServerPacketHandler.sendTo(player, remove);
        }
    }

    private static void sendAreaSnapshot(ServerPlayerEntity player, VideoArea area) {
        ServerPacketHandler.sendTo(player, VideoPackets.createArea(area));
        ServerPacketHandler.sendAreaPermissions(player, area);
        for (VideoScreen screen : area.screens) {
            ServerPacketHandler.sendTo(player, VideoPackets.createScreen(List.of(screen)));
            for (Map.Entry<String, com.github.squi2rel.vp.video.MetaValue> entry : screen.metadata.entries().entrySet()) {
                ServerPacketHandler.sendTo(player, VideoPackets.setMetadata(screen, entry.getKey(), entry.getValue()));
            }
            if (!screen.idlePlayEntries.isEmpty() || screen.idlePlayRandom) {
                ServerPacketHandler.sendTo(player, VideoPackets.idlePlay(screen));
            }
        }
        boolean loadedPlayback = false;
        for (VideoScreen screen : area.screens) {
            if (screen.currentPlayback() != null) {
                ServerPacketHandler.sendTo(player, VideoPackets.loadArea(area, screen));
                loadedPlayback = true;
            }
        }
        if (!loadedPlayback) {
            ServerPacketHandler.sendTo(player, VideoPackets.loadArea(area, null));
        }
        for (VideoScreen screen : area.screens) {
            ServerPacketHandler.sendTo(player, VideoPackets.updatePlaylist(List.of(screen)));
        }
    }

    private static ServerConfig readConfig(Path path) {
        try {
            if (!Files.exists(path)) return new ServerConfig();
            ServerConfig read = gson.fromJson(Files.readString(path), ServerConfig.class);
            if (read == null) read = new ServerConfig();
            VideoConfigValidator.validate(read);
            return read;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read VideoPlayer world config " + path, e);
        }
    }

    private static void applySharedConfig(ServerConfig loaded) {
        if (loaded == null) return;
        if (loaded.remoteControlName != null && !loaded.remoteControlName.isBlank()) {
            config.remoteControlName = loaded.remoteControlName;
        }
        config.remoteControlId = loaded.remoteControlId;
        config.remoteControlRange = loaded.remoteControlRange;
        config.noControlRange = loaded.noControlRange;
    }

    private static Path worldDirectory(MinecraftServer server, ServerWorld world) {
        return DimensionType.getSaveDirectory(world.getRegistryKey(), server.getSavePath(WorldSavePath.ROOT));
    }

    public static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeString(Path path, String str) {
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, str, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
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

    public static VideoHandshakeState acceptHandshake(UUID uuid) {
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

    public static long issueHandshakeNonce(UUID uuid) {
        long nonce;
        do {
            nonce = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        } while (nonce == 0L);
        handshakeNonces.put(uuid, nonce);
        return nonce;
    }

    public static long handshakeNonce(UUID uuid) {
        return handshakeNonces.getOrDefault(uuid, 0L);
    }

    public static void recordHandshakeToken(UUID uuid, String remoteToken) {
        if (uuid == null) return;
        handshakeTokens.put(uuid, com.github.squi2rel.vp.network.VideoProtocol.responseToken(VideoPlayerMain.version, remoteToken));
    }

    public static String handshakeToken(UUID uuid) {
        return handshakeTokens.getOrDefault(uuid, com.github.squi2rel.vp.network.VideoProtocol.token(VideoPlayerMain.version));
    }

    public static boolean acceptHandshakeAck(UUID uuid, long nonce) {
        if (nonce == 0L || handshakeNonces.getOrDefault(uuid, 0L) != nonce) return false;
        if (handshakes.get(uuid) != VideoHandshakeState.RESET_SENT) return false;
        handshakeNonces.remove(uuid);
        handshakes.put(uuid, VideoHandshakeState.ACTIVE);
        allPlayers.add(uuid);
        return true;
    }

    public static VideoHandshakeState handshakeState(UUID uuid) {
        return handshakes.getOrDefault(uuid, VideoHandshakeState.NEEDS_RESET);
    }

    public static boolean rejectHandshake(UUID uuid) {
        VideoHandshakeState previous = handshakes.put(uuid, VideoHandshakeState.REJECTED);
        allPlayers.remove(uuid);
        handshakeTokens.remove(uuid);
        return previous != VideoHandshakeState.REJECTED;
    }

    public static boolean protocolActive(UUID uuid) {
        return handshakes.get(uuid) == VideoHandshakeState.ACTIVE;
    }

    public static long lifecycleEpoch() {
        return lifecycleEpoch;
    }

    public static boolean lifecycleActive(long epoch) {
        return running && lifecycleEpoch == epoch;
    }

    public static void executeState(long epoch, Runnable runnable) {
        MinecraftServer current = server;
        if (current == null || runnable == null) return;
        current.execute(() -> {
            if (lifecycleActive(epoch)) runnable.run();
        });
    }

    public static UUID onlinePlayerUuid(String name) {
        if (name == null) return null;
        return onlinePlayerNames.get(name.toLowerCase(Locale.ROOT));
    }

    public static void message(UUID uuid, long epoch, String message) {
        MinecraftServer current = server;
        if (current == null || message == null) return;
        current.execute(() -> {
            if (!lifecycleActive(epoch)) return;
            ServerPlayerEntity player = current.getPlayerManager().getPlayer(uuid);
            if (player != null) player.sendMessage(net.minecraft.text.Text.of(message));
        });
    }

    public static void message(UUID uuid, long epoch, com.github.squi2rel.vp.i18n.VpTranslation message) {
        MinecraftServer current = server;
        if (current == null || message == null) return;
        current.execute(() -> {
            if (!lifecycleActive(epoch)) return;
            ServerPlayerEntity player = current.getPlayerManager().getPlayer(uuid);
            if (player != null) player.sendMessage(MinecraftTexts.text(message));
        });
    }

    public static VideoScreen findScreen(ScreenKey key) {
        if (key == null) return null;
        HashMap<String, VideoArea> world = areas.get(key.dimension());
        if (world == null) return null;
        VideoArea area = world.get(key.areaName());
        return area == null ? null : area.getScreen(key.screenName());
    }

    private static final class ScheduledSave {
        private final long epoch;
        private final boolean allowRetry;
        private ScheduledFuture<?> future;

        private ScheduledSave(long epoch, boolean allowRetry) {
            this.epoch = epoch;
            this.allowRetry = allowRetry;
        }
    }
}
