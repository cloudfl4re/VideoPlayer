package com.github.squi2rel.vp.i18n;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

public final class VpInputTexts {
    private VpInputTexts() {
    }

    public static Component key(int keyCode) {
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName();
    }

    public static Component mouseButton(int button) {
        return InputConstants.Type.MOUSE.getOrCreate(button).getDisplayName();
    }
}
