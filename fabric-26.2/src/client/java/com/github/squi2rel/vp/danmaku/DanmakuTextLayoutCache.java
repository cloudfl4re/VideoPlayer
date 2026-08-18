package com.github.squi2rel.vp.danmaku;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class DanmakuTextLayoutCache {
    private static final int MAX_ENTRIES = 2048;
    private static final int WHITE = 0xFFFFFFFF;
    private static final Map<String, CachedLayout> CACHE = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true);

    private DanmakuTextLayoutCache() {
    }

    static float measureWidth(String text, float scale) {
        Font textRenderer = Minecraft.getInstance().font;
        return Math.max(1.0f, textRenderer.width(safeText(text)) * Math.max(0.01f, scale));
    }

    static float measureHeight(float scale) {
        Font textRenderer = Minecraft.getInstance().font;
        return Math.max(1.0f, textRenderer.lineHeight * Math.max(0.01f, scale));
    }

    static FormattedCharSequence orderedText(String text) {
        return Component.literal(safeText(text)).getVisualOrderText();
    }

    static void prepare(List<ClientDanmakuController.RenderableDanmaku> items) {
        if (items == null || items.isEmpty()) return;
        for (ClientDanmakuController.RenderableDanmaku item : items) {
            if (item != null) get(item.text());
        }
    }

    static CachedLayout get(String text) {
        String safe = safeText(text);
        CachedLayout cached = CACHE.get(safe);
        if (cached != null) return cached;

        Font textRenderer = Minecraft.getInstance().font;
        FormattedCharSequence ordered = orderedText(safe);
        ArrayList<Font.PreparedText> outlines = new ArrayList<>(8);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox == 0 && oy == 0) continue;
                outlines.add(textRenderer.prepareText(ordered, ox, oy, WHITE, false, true, 0));
            }
        }
        CachedLayout created = new CachedLayout(
                List.copyOf(outlines),
                textRenderer.prepareText(ordered, 0, 0, WHITE, false, true, 0),
                textRenderer.width(ordered),
                textRenderer.lineHeight
        );
        CACHE.put(safe, created);
        evictOverflow();
        return created;
    }

    static void clear() {
        CACHE.clear();
    }

    private static void evictOverflow() {
        Iterator<String> iterator = CACHE.keySet().iterator();
        while (CACHE.size() > MAX_ENTRIES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    record CachedLayout(List<Font.PreparedText> outlines,
                        Font.PreparedText body,
                        int width,
                        int height) {
    }
}
