package com.github.squi2rel.vp.provider;

import com.github.squi2rel.vp.video.VideoParams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeProviderTest {
    @AfterEach
    void resetConfiguration() {
        YouTubeProvider.configureProxy("");
        YouTubeProvider.configureYtdlPath("");
        YouTubeProvider.configureCookies("", "");
        YouTubeProvider.configureMissingYtdlHandler(null);
    }

    @Test
    void recognizesSupportedYouTubeVideoUrls() {
        assertTrue(YouTubeProvider.isYouTubeRawPath("https://www.youtube.com/watch?v=_e4jNyZV748&list=RD_e4jNyZV748"));
        assertTrue(YouTubeProvider.isYouTubeRawPath("youtu.be/_e4jNyZV748?t=10"));
        assertTrue(YouTubeProvider.isYouTubeRawPath("https://youtube.com/shorts/_e4jNyZV748"));
        assertFalse(YouTubeProvider.isYouTubeRawPath("https://youtube.com/@channel"));
        assertFalse(YouTubeProvider.isYouTubeRawPath("https://example.com/watch?v=_e4jNyZV748"));
    }

    @Test
    void buildsCodecPreferredSelectorWithGenericFallback() {
        String limited = YouTubeProvider.formatSelector(1080);
        String automatic = YouTubeProvider.formatSelector(-1);

        assertTrue(limited.startsWith("bestvideo[height<=1080][vcodec^=avc1]+bestaudio[acodec^=mp4a]"));
        assertTrue(limited.endsWith("/bestvideo[height<=1080]+bestaudio/best[height<=1080]"));
        assertFalse(automatic.contains("height<="));
        assertTrue(automatic.endsWith("/bestvideo+bestaudio/best"));
    }

    @Test
    void detectsAndCachesActualSupportedHeights() {
        JsonObject metadata = JsonParser.parseString("""
                {
                  "formats": [
                    {"height": 360, "vcodec": "avc1.42001E", "acodec": "none"},
                    {"height": 720, "vcodec": "vp09.00.31.08", "acodec": "none"},
                    {"height": 1080, "vcodec": "av01.0.08M.08", "acodec": "none"},
                    {"height": 240, "vcodec": "avc1.42001E", "acodec": "none"},
                    {"height": 4320, "vcodec": "none", "acodec": "mp4a.40.2"}
                  ],
                  "requested_formats": [
                    {"height": 1440, "vcodec": "av01.0.12M.08", "acodec": "none"},
                    {"vcodec": "none", "acodec": "mp4a.40.2"}
                  ]
                }
                """).getAsJsonObject();
        String rawPath = "https://youtu.be/vp-quality-test";

        assertEquals(List.of(1440, 1080, 720, 360), YouTubeProvider.detectedQualities(metadata));
        YouTubeProvider.updateAvailableQualities(rawPath, metadata);
        assertEquals(List.of(1440, 1080, 720, 360), YouTubeProvider.availableQualities("https://www.youtube.com/watch?v=vp-quality-test"));
    }

    @Test
    void usesEarlierExpiryFromSeparatedVideoAndAudioUrls() {
        String video = "https://video.example/play?expire=4102444800";
        String audio = "https://audio.example/play?expire=4000000000";

        assertEquals(4_000_000_000_000L, YouTubeProvider.mediaExpiry(video, audio));
        assertEquals(4_102_444_800_000L, YouTubeProvider.mediaExpiry(video, "https://audio.example/play"));
    }

    @Test
    void preservesExplicitExpiredMediaTime() {
        String expired = "https://video.example/play?expire=1600000000";
        String withoutExpiry = "https://audio.example/play";

        assertEquals(1_600_000_000_000L, YouTubeProvider.mediaExpiry(expired, withoutExpiry));
        assertEquals(1_600_000_000_000L, YouTubeProvider.mediaExpiry(
                "https://video.example/play?expire=4102444800", expired));
    }

    @Test
    void defaultsMediaWithoutExpiryToTwentyMinutes() {
        long before = System.currentTimeMillis();
        long expiry = YouTubeProvider.mediaExpiry(
                "https://video.example/play", "https://audio.example/play");
        long after = System.currentTimeMillis();
        long twentyMinutes = TimeUnit.MINUTES.toMillis(20);

        assertTrue(expiry >= before + twentyMinutes);
        assertTrue(expiry <= after + twentyMinutes);
    }

    @Test
    void acceptsLiveMetadataWithoutDurationAsNonSeekable() {
        JsonObject metadata = JsonParser.parseString("""
                {
                  "id": "vp-live-test",
                  "title": "Live test",
                  "live_status": "is_live",
                  "duration": 0,
                  "requested_formats": [
                    {
                      "url": "https://video.example/live",
                      "vcodec": "avc1.640028",
                      "acodec": "none"
                    },
                    {
                      "url": "https://audio.example/live",
                      "vcodec": "none",
                      "acodec": "mp4a.40.2"
                    }
                  ]
                }
                """).getAsJsonObject();
        List<String> replies = new ArrayList<>();

        VideoInfo info = YouTubeProvider.videoInfo(metadata,
                "https://www.youtube.com/watch?v=vp-live-test", source(replies));

        assertNotNull(info);
        assertEquals(0L, info.durationMs());
        assertFalse(info.seekable());
        assertTrue(VideoParams.hasYouTubeLiveChat(info.params()));
        assertTrue(replies.isEmpty());
    }

    @Test
    void marksOnlyActiveLiveStreamsForChat() {
        JsonObject upcoming = new JsonObject();
        upcoming.addProperty("live_status", "is_upcoming");
        JsonObject active = new JsonObject();
        active.addProperty("live_status", "is_live");
        JsonObject booleanLive = new JsonObject();
        booleanLive.addProperty("is_live", true);

        assertFalse(YouTubeProvider.hasLiveChat(upcoming));
        assertTrue(YouTubeProvider.hasLiveChat(active));
        assertTrue(YouTubeProvider.hasLiveChat(booleanLive));
    }

    @Test
    void excludesLiveChatFromSubtitleParameters() {
        JsonObject metadata = JsonParser.parseString("""
                {
                  "id": "vp-live-chat-subtitle-test",
                  "duration": 60,
                  "requested_formats": [
                    {
                      "url": "https://video.example/test",
                      "vcodec": "avc1",
                      "acodec": "mp4a"
                    }
                  ],
                  "subtitles": {
                    "en": [
                      {
                        "ext": "vtt",
                        "name": "English",
                        "url": "https://subtitle.example/en.vtt"
                      }
                    ],
                    "live_chat": [
                      {
                        "ext": "json",
                        "protocol": "youtube_live_chat",
                        "url": "https://www.youtube.com/watch?v=vp-live-chat-subtitle-test"
                      }
                    ]
                  },
                  "automatic_captions": {
                    "zh-Hans": [
                      {
                        "ext": "vtt",
                        "name": "Chinese",
                        "url": "https://subtitle.example/zh.vtt"
                      }
                    ],
                    "live_chat": [
                      {
                        "ext": "json",
                        "protocol": "youtube_live_chat",
                        "url": "https://www.youtube.com/watch?v=vp-live-chat-subtitle-test"
                      }
                    ]
                  }
                }
                """).getAsJsonObject();

        VideoInfo info = YouTubeProvider.videoInfo(metadata,
                "https://www.youtube.com/watch?v=vp-live-chat-subtitle-test", source(new ArrayList<>()));
        List<VideoParams.SubtitleParam> subtitles = VideoParams.subtitleParams(info.params());

        assertEquals(2, subtitles.size());
        assertTrue(subtitles.stream().anyMatch(option -> option.language().equals("en") && !option.automatic()));
        assertTrue(subtitles.stream().anyMatch(option -> option.language().equals("zh-Hans") && option.automatic()));
        assertFalse(subtitles.stream().anyMatch(option -> option.language().equalsIgnoreCase("live_chat")));
    }

    @Test
    void returnsClientFallbackWhenMetadataHasNoDurationOrStream() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("id", "vp-fallback-test");
        metadata.addProperty("title", "Fallback test");
        metadata.addProperty("duration", 0);
        List<String> replies = new ArrayList<>();

        VideoInfo info = YouTubeProvider.videoInfo(metadata,
                "https://www.youtube.com/watch?v=vp-fallback-test", source(replies));

        assertNotNull(info);
        assertEquals("", info.path());
        assertEquals("https://www.youtube.com/watch?v=vp-fallback-test", info.rawPath());
        assertTrue(info.seekable());
        assertTrue(replies.stream().anyMatch(value -> value.contains("duration")));
    }

    @Test
    void usesCookieFileBeforeBrowserProfile() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        YouTubeProvider.configureYtdlPath("yt-dlp");
        YouTubeProvider.configureCookies("C:\\private\\cookies.txt", "chrome:Default");
        YouTubeProvider provider = jsonProvider(commands);

        VideoInfo info = provider.from("https://www.youtube.com/watch?v=vp-cookie-file", source(new ArrayList<>()))
                .get(5, TimeUnit.SECONDS);

        assertNotNull(info);
        assertEquals(1, commands.size());
        List<String> command = commands.get(0);
        int cookieFlag = command.indexOf("--cookies");
        assertTrue(cookieFlag >= 0);
        assertEquals("C:\\private\\cookies.txt", command.get(cookieFlag + 1));
        assertFalse(command.contains("--cookies-from-browser"));
    }

    @Test
    void usesBrowserCookiesWhenFileIsNotConfigured() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        YouTubeProvider.configureYtdlPath("yt-dlp");
        YouTubeProvider.configureCookies("", "firefox:default-release");
        YouTubeProvider provider = jsonProvider(commands);

        assertNotNull(provider.from("https://www.youtube.com/watch?v=vp-cookie-browser", source(new ArrayList<>()))
                .get(5, TimeUnit.SECONDS));
        List<String> command = commands.get(0);
        int cookieFlag = command.indexOf("--cookies-from-browser");
        assertTrue(cookieFlag >= 0);
        assertEquals("firefox:default-release", command.get(cookieFlag + 1));
        assertFalse(command.contains("--cookies"));
    }

    @Test
    void retriesOnceAfterMissingYtdlpUsingInstallerCallback() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger installs = new AtomicInteger();
        YouTubeProvider.configureMissingYtdlHandler(() -> {
            installs.incrementAndGet();
            return "installed-yt-dlp";
        });
        YouTubeProvider provider = new YouTubeProvider(command -> {
            int attempt = starts.incrementAndGet();
            if (attempt < 3) throw new java.io.IOException("missing");
            return jsonProcess();
        });

        VideoInfo info = provider.from("https://www.youtube.com/watch?v=vp-installer", source(new ArrayList<>()))
                .get(5, TimeUnit.SECONDS);

        assertNotNull(info);
        assertEquals(1, installs.get());
        assertEquals(3, starts.get());
    }

    @Test
    void keepsVideoParametersWithinProtocolBudgetWhenMetadataHasManySubtitles() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("id", "vp-parameter-limit-test");
        metadata.addProperty("duration", 30);

        JsonArray requestedFormats = new JsonArray();
        JsonObject video = new JsonObject();
        video.addProperty("url", "https://video.example/video");
        video.addProperty("vcodec", "avc1.640028");
        video.addProperty("acodec", "none");
        requestedFormats.add(video);
        JsonObject audio = new JsonObject();
        audio.addProperty("url", "https://audio.example/audio");
        audio.addProperty("vcodec", "none");
        audio.addProperty("acodec", "mp4a.40.2");
        requestedFormats.add(audio);
        metadata.add("requested_formats", requestedFormats);

        JsonObject subtitles = new JsonObject();
        for (int i = 0; i < 24; i++) {
            JsonArray formats = new JsonArray();
            JsonObject format = new JsonObject();
            format.addProperty("ext", "vtt");
            format.addProperty("name", "Subtitle " + i);
            format.addProperty("url", "https://subtitle.example/" + "x".repeat(500) + "/" + i);
            formats.add(format);
            subtitles.add("lang-" + i, formats);
        }
        metadata.add("subtitles", subtitles);

        VideoInfo info = YouTubeProvider.videoInfo(metadata,
                "https://www.youtube.com/watch?v=vp-parameter-limit-test", source(new ArrayList<>()));

        assertNotNull(info);
        assertTrue(info.params().length <= VideoInfo.MAX_PARAMS);
        int totalBytes = java.util.Arrays.stream(info.params())
                .mapToInt(value -> value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .sum();
        assertTrue(totalBytes <= VideoInfo.MAX_TOTAL_PARAM_BYTES);
    }

    @Test
    void cancellingReturnedFutureStopsProcessWorkerAndReaders() throws Exception {
        AtomicReference<Process> launched = new AtomicReference<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        String helperClasspath = Path.of(YtDlpBlockingProcess.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        YouTubeProvider provider = new YouTubeProvider(command -> {
            Process process = new ProcessBuilder(javaExecutable(), "-cp",
                    helperClasspath, YtDlpBlockingProcess.class.getName()).start();
            launched.set(process);
            worker.set(Thread.currentThread());
            started.countDown();
            return process;
        });
        List<String> replies = new ArrayList<>();
        CompletableFuture<VideoInfo> future = provider.from(
                "https://www.youtube.com/watch?v=vp-cancel-test", source(replies));
        assertNotNull(future);

        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Process process = launched.get();
            Thread workerThread = worker.get();
            assertNotNull(process);
            assertNotNull(workerThread);
            assertTrue(process.isAlive());
            assertTrue(awaitCondition(() -> activeThreadCount(workerThread.getName()) >= 3, Duration.ofSeconds(5)));

            assertTrue(future.cancel(false));
            assertTrue(future.isCancelled());
            assertThrows(CancellationException.class, future::join);
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            assertFalse(process.isAlive());
            assertTrue(awaitCondition(() -> activeThreadCount(workerThread.getName()) == 0, Duration.ofSeconds(5)));
            assertTrue(workerThread.isInterrupted());
            assertTrue(replies.isEmpty());
        } finally {
            future.cancel(true);
            Process process = launched.get();
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static IProviderSource source(List<String> replies) {
        return new IProviderSource() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public void reply(String text) {
                replies.add(text);
            }
        };
    }

    private static YouTubeProvider jsonProvider(List<List<String>> commands) {
        return new YouTubeProvider(command -> {
            commands.add(List.copyOf(command));
            return jsonProcess();
        });
    }

    private static Process jsonProcess() throws java.io.IOException {
        try {
            String helperClasspath = Path.of(YtDlpJsonProcess.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toString();
            return new ProcessBuilder(javaExecutable(), "-cp", helperClasspath, YtDlpJsonProcess.class.getName()).start();
        } catch (java.net.URISyntaxException error) {
            throw new java.io.IOException(error);
        }
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static long activeThreadCount(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> thread.getName().equals(prefix) || thread.getName().startsWith(prefix + "-"))
                .count();
    }

    private static boolean awaitCondition(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(10L);
        }
        return condition.getAsBoolean();
    }

    public static final class YtDlpJsonProcess {
        public static void main(String[] args) {
            System.out.println("{\"id\":\"vp-test\",\"title\":\"Test video\",\"duration\":1,\"requested_formats\":[{\"url\":\"https://video.example/test\",\"vcodec\":\"avc1\",\"acodec\":\"mp4a\"}]}" );
        }
    }
}
