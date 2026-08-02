package com.github.squi2rel.vp;

import com.github.squi2rel.vp.i18n.PaperTexts;
import com.github.squi2rel.vp.i18n.VpTranslation;
import com.github.squi2rel.vp.network.VideoProtocol;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientVersionTracker {
    public static final String VERSION_PERMISSION = "videoplayer.version";
    public static final String JOIN_MESSAGE_PERMISSION = "videoplayer.joinmessage";
    static final long DETECTION_TIMEOUT_TICKS = 100L;

    private static final ConcurrentHashMap<UUID, ClientSession> sessions = new ConcurrentHashMap<>();
    private static final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();
    private static volatile VideoPlayerPaperPlugin plugin;
    private static volatile NamespacedKey joinMessageKey;

    private ClientVersionTracker() {
    }

    public static synchronized void initialize(VideoPlayerPaperPlugin owner) {
        shutdown();
        plugin = owner;
        joinMessageKey = new NamespacedKey(owner, "joinmessage");
    }

    public static synchronized void shutdown() {
        plugin = null;
        for (ClientSession session : sessions.values()) {
            session.cancelTimeout();
        }
        sessions.clear();
        subscribers.clear();
        joinMessageKey = null;
    }

    public static void playerJoined(Player player) {
        VideoPlayerPaperPlugin owner = plugin;
        if (owner == null || player == null || !owner.isEnabled()) return;
        UUID uuid = player.getUniqueId();
        NamespacedKey key = joinMessageKey;
        if (key != null && player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            subscribers.add(uuid);
        } else {
            subscribers.remove(uuid);
        }
        ClientSession session = new ClientSession(uuid, player.getName());
        ClientSession previous = sessions.put(uuid, session);
        if (previous != null) previous.cancelTimeout();
        FoliaScheduler.TaskHandle timeout;
        try {
            timeout = FoliaScheduler.runAtEntityDelayed(
                    player,
                    () -> detectionTimedOut(session),
                    () -> retire(session),
                    DETECTION_TIMEOUT_TICKS
            );
        } catch (Throwable error) {
            sessions.remove(uuid, session);
            VideoPlayerMain.LOGGER.warn("Failed to schedule VideoPlayer client-version detection for {}", session.playerName, error);
            return;
        }
        session.setTimeout(timeout);
        if (sessions.get(uuid) != session) session.cancelTimeout();
    }

    public static void playerLeft(UUID uuid) {
        if (uuid == null) return;
        ClientSession session = sessions.remove(uuid);
        if (session != null) session.cancelTimeout();
        subscribers.remove(uuid);
    }

    public static void clientDetected(Player player, String remoteToken) {
        VideoPlayerPaperPlugin owner = plugin;
        if (owner == null || player == null || !owner.isEnabled()) return;
        ClientSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        String version = VideoProtocol.displayVersion(remoteToken).trim();
        if (version.isEmpty()) version = "unknown";
        boolean compatible = VideoProtocol.compatible(VideoPlayerMain.version, remoteToken);
        boolean notify;
        synchronized (session) {
            if (sessions.get(session.uuid) != session) return;
            notify = !session.versionReported;
            session.playerName = player.getName();
            session.version = version;
            session.compatible = compatible;
            session.cancelTimeout();
            session.versionReported = true;
        }
        if (notify) broadcast(detectedMessage(session.playerName, version, compatible));
    }

    public static boolean toggleSubscription(Player player) {
        NamespacedKey key = joinMessageKey;
        if (player == null || plugin == null || key == null) return false;
        UUID uuid = player.getUniqueId();
        boolean enabled;
        synchronized (subscribers) {
            enabled = !subscribers.remove(uuid);
            if (enabled) subscribers.add(uuid);
        }
        if (enabled) {
            player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(key);
        }
        return enabled;
    }

    public static List<ClientVersion> versionSnapshot() {
        ArrayList<ClientVersion> snapshot = new ArrayList<>();
        for (ClientSession session : sessions.values()) {
            synchronized (session) {
                if (session.version != null) {
                    snapshot.add(new ClientVersion(session.uuid, session.playerName, session.version, session.compatible));
                }
            }
        }
        snapshot.sort(Comparator
                .comparing(ClientVersion::version, ClientVersionTracker::compareVersions)
                .thenComparing(ClientVersion::playerName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(snapshot);
    }

    public static int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("[._+\\-]");
        String[] rightParts = normalizeVersion(right).split("[._+\\-]");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            String leftPart = index < leftParts.length ? leftParts[index] : "0";
            String rightPart = index < rightParts.length ? rightParts[index] : "0";
            boolean leftNumber = digits(leftPart);
            boolean rightNumber = digits(rightPart);
            int compared;
            if (leftNumber && rightNumber) {
                compared = new BigInteger(leftPart).compareTo(new BigInteger(rightPart));
            } else {
                compared = leftPart.compareToIgnoreCase(rightPart);
            }
            if (compared != 0) return compared;
        }
        return 0;
    }

    private static void retire(ClientSession session) {
        if (sessions.remove(session.uuid, session)) subscribers.remove(session.uuid);
    }

    private static void detectionTimedOut(ClientSession session) {
        boolean notify;
        synchronized (session) {
            if (sessions.get(session.uuid) != session || session.version != null || session.missingReported) return;
            session.missingReported = true;
            session.timeout = FoliaScheduler.TaskHandle.NONE;
            notify = true;
        }
        if (notify) broadcast(PaperTexts.text(VpTranslation.of(
                "message.videoplayer.join_client_missing",
                "No VideoPlayer client version was detected for %s",
                session.playerName
        )).color(NamedTextColor.YELLOW));
    }

    private static Component detectedMessage(String playerName, String version, boolean compatible) {
        if (compatible) {
            return PaperTexts.text(VpTranslation.of(
                    "message.videoplayer.join_client_detected",
                    "%s joined with VideoPlayer client %s",
                    playerName,
                    version
            )).color(NamedTextColor.GREEN);
        }
        return PaperTexts.text(VpTranslation.of(
                "message.videoplayer.join_client_incompatible",
                "%s joined with incompatible VideoPlayer client %s; server version is %s",
                playerName,
                version,
                VideoPlayerMain.version
        )).color(NamedTextColor.RED);
    }

    private static void broadcast(Component message) {
        VideoPlayerPaperPlugin owner = plugin;
        if (owner == null || !owner.isEnabled() || subscribers.isEmpty()) return;
        List<UUID> recipients = List.copyOf(subscribers);
        try {
            for (UUID uuid : recipients) {
                DataHolder.runForPlayer(uuid, recipient -> {
                    if (plugin == owner && owner.isEnabled() && subscribers.contains(uuid)
                            && recipient.isOnline() && recipient.hasPermission(JOIN_MESSAGE_PERMISSION)) {
                        recipient.sendMessage(message);
                    }
                });
            }
        } catch (Throwable error) {
            VideoPlayerMain.LOGGER.warn("Failed to schedule VideoPlayer join-version notification", error);
        }
    }

    private static String normalizeVersion(String value) {
        return value == null || value.isBlank() ? "0" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean digits(String value) {
        if (value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return true;
    }

    public record ClientVersion(UUID uuid, String playerName, String version, boolean compatible) {
    }

    private static final class ClientSession {
        private final UUID uuid;
        private String playerName;
        private String version;
        private boolean compatible;
        private boolean versionReported;
        private boolean missingReported;
        private FoliaScheduler.TaskHandle timeout = FoliaScheduler.TaskHandle.NONE;

        private ClientSession(UUID uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName;
        }

        private synchronized void setTimeout(FoliaScheduler.TaskHandle task) {
            if (task == null) return;
            if (versionReported || sessions.get(uuid) != this) {
                task.cancel();
                return;
            }
            timeout = task;
        }

        private synchronized void cancelTimeout() {
            timeout.cancel();
            timeout = FoliaScheduler.TaskHandle.NONE;
        }
    }

}
