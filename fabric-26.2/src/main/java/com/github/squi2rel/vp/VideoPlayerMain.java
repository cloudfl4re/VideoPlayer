package com.github.squi2rel.vp;

import com.github.squi2rel.vp.network.ServerPacketHandler;
import com.github.squi2rel.vp.network.VideoPackets;
import com.github.squi2rel.vp.network.VideoPayload;
import com.github.squi2rel.vp.provider.VideoProviders;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class VideoPlayerMain implements ModInitializer {
    public static final String MOD_ID = "videoplayer";
    public static final String version = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion().toString();
    public static Throwable error = null;
    public static MinecraftServer server;

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final boolean android = Files.exists(Path.of("/system/build.prop"));

    public static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, VideoPlayerMain::newDaemon);

    @SuppressWarnings("resource")
    @Override
    public void onInitialize() {
        if (android) {
            LOGGER.info("Android device detected; native libraries will use the Android ABI package");
        }
        System.setProperty("videoplayer.version", version);
        VideoProviders.register();
        VideoPayload.register();
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            DataHolder.load(s);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(DataHolder::stop);
        ServerLifecycleEvents.BEFORE_SAVE.register((s, flush, force) -> DataHolder.save());
        ServerLevelEvents.LOAD.register(DataHolder::loadWorld);
        ServerLevelEvents.UNLOAD.register(DataHolder::unloadWorld);
        ServerTickEvents.START_SERVER_TICK.register(ignored -> DataHolder.update());
        ServerPlayConnectionEvents.JOIN.register((e, p, s) -> DataHolder.playerJoin(e.player));
        ServerPlayConnectionEvents.DISCONNECT.register((e, s) -> DataHolder.playerLeave(e.player.getUUID()));
        ServerPlayNetworking.registerGlobalReceiver(VideoPayload.ID, (p, c) -> {
            long receivedAt = System.currentTimeMillis();
            byte[] copy = p.data().clone();
            if (copy.length > VideoPackets.MAX_PAYLOAD_BYTES) {
                c.player().connection.disconnect(Component.nullToEmpty("VideoPlayer payload is too large"));
                return;
            }
            c.server().execute(() -> {
                ByteBuf buf = Unpooled.wrappedBuffer(copy);
                try {
                    ServerPacketHandler.handle(c.player(), buf, receivedAt);
                } catch (Exception e) {
                    c.player().connection.disconnect(Component.nullToEmpty(e.toString()));
                } finally {
                    buf.release();
                }
            });
        });
        CommandRegistrationCallback.EVENT.register((d, c, e) -> d.register(Commands.literal("").then(Commands.argument("command", StringArgumentType.greedyString()).executes(s -> {
            if (!s.getSource().isPlayer()) return 0;
            ServerPacketHandler.sendTo(s.getSource().getPlayer(), VideoPackets.execute(s.getArgument("command", String.class)));
            return 1;
        }))));
    }

    private static Thread newDaemon(Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        return t;
    }
}
