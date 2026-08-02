package com.github.squi2rel.vp.network;

public final class VideoProtocol {
    public static final String WIRE_REVISION = "vp5";

    private VideoProtocol() {
    }

    public static String token(String version) {
        return safe(version) + "|" + WIRE_REVISION;
    }

    // Compatibility intentionally compares release versions only. Do not require the wire revision here.
    public static boolean compatible(String localVersion, String remoteToken) {
        String localRelease = releaseVersion(localVersion);
        String remoteRelease = releaseVersion(remoteToken);
        return !localRelease.isEmpty() && localRelease.equals(remoteRelease);
    }

    public static String responseToken(String localVersion, String remoteToken) {
        String normalized = normalize(remoteToken);
        return compatible(localVersion, normalized) && !normalized.isEmpty() ? normalized : token(localVersion);
    }

    public static boolean allowedForRejectedClient(VideoPacketType type) {
        return type == VideoPacketType.PROTOCOL_REJECT
                || type == VideoPacketType.RESET_CLIENT
                || type == VideoPacketType.CONFIG;
    }

    public static String displayVersion(String token) {
        return releaseVersion(token);
    }

    private static String releaseVersion(String token) {
        String normalized = normalize(token);
        int separator = normalized.indexOf('|');
        return (separator < 0 ? normalized : normalized.substring(0, separator)).trim();
    }

    private static String normalize(String value) {
        return safe(value).trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
