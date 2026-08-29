package com.github.squi2rel.vp.danmaku;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

final class DanmakuTextLayoutCache {
    private DanmakuTextLayoutCache() {
    }

    static float measureWidth(String text, float scale) {
        Font font = Minecraft.getInstance().font;
        return Math.max(1.0f, font.width(safeText(text)) * Math.max(0.01f, scale));
    }

    static float measureHeight(float scale) {
        Font font = Minecraft.getInstance().font;
        return Math.max(1.0f, font.lineHeight * Math.max(0.01f, scale));
    }

    static FormattedCharSequence orderedText(String text) {
        return Component.literal(safeText(text)).getVisualOrderText();
    }

    static void prepare(List<ClientDanmakuController.RenderableDanmaku> items) {
    }

    static void clear() {
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }
}
