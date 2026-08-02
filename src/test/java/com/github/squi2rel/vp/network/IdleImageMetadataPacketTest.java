package com.github.squi2rel.vp.network;

import com.github.squi2rel.vp.video.MetaType;
import com.github.squi2rel.vp.video.MetaValue;
import com.github.squi2rel.vp.video.ScreenMetadata;
import com.github.squi2rel.vp.video.VideoArea;
import com.github.squi2rel.vp.video.VideoScreen;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdleImageMetadataPacketTest {
    @Test
    void setMetadataPacketCarriesBooleanValue() {
        VideoScreen screen = screen();
        ByteBuf buf = Unpooled.wrappedBuffer(VideoPackets.setMetadata(
                screen,
                ScreenMetadata.KEY_SHOW_IDLE_IMAGE,
                MetaValue.ofBool(false)
        ));
        try {
            assertEquals(VideoPacketType.SET_SCREEN_METADATA, VideoPackets.readType(buf));
            assertEquals("area", VideoPackets.readName(buf));
            assertEquals("screen", VideoPackets.readName(buf));
            assertEquals(ScreenMetadata.KEY_SHOW_IDLE_IMAGE, ByteBufUtils.readString(buf, 64));
            assertFalse(buf.readBoolean());
            MetaValue value = VideoPackets.readMetaValue(buf);
            assertEquals(MetaType.BOOL, value.type);
            assertFalse(value.bool(true));
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test
    void idleImageIsUserMetadataAndRequiresBooleanType() throws Exception {
        Method isUserOption = ServerPacketHandler.class.getDeclaredMethod("isUserMetadataOption", String.class);
        isUserOption.setAccessible(true);
        assertTrue((boolean) isUserOption.invoke(null, ScreenMetadata.KEY_SHOW_IDLE_IMAGE));

        Method validate = ServerPacketHandler.class.getDeclaredMethod(
                "validateBuiltInMetadata",
                VideoScreen.class,
                String.class,
                MetaValue.class
        );
        validate.setAccessible(true);
        assertDoesNotThrow(() -> validate.invoke(
                null,
                screen(),
                ScreenMetadata.KEY_SHOW_IDLE_IMAGE,
                MetaValue.ofBool(true)
        ));

        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> validate.invoke(
                null,
                screen(),
                ScreenMetadata.KEY_SHOW_IDLE_IMAGE,
                MetaValue.ofInt(1)
        ));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    private static VideoScreen screen() {
        VideoArea area = new VideoArea(new Vector3f(), new Vector3f(1), "area", "world");
        return new VideoScreen(
                area,
                "screen",
                new Vector3f(),
                new Vector3f(1, 0, 0),
                new Vector3f(1, 1, 0),
                new Vector3f(0, 1, 0),
                ""
        );
    }
}
