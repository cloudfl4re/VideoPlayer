package com.github.squi2rel.vp.i18n;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class MinecraftTexts {
    private MinecraftTexts() {
    }

    public static MutableComponent tr(String key, String fallback, Object... args) {
        return text(VpTranslation.of(key, fallback, args));
    }

    public static MutableComponent text(VpTranslation translation) {
        if (translation == null || translation.isEmpty()) {
            return Component.empty();
        }
        if (translation.isLiteral()) {
            return Component.literal(translation.fallback());
        }
        return Component.translatableWithFallback(translation.key(), translation.fallback(), translation.argumentArray());
    }
}
