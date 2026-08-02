package com.github.squi2rel.vp.i18n;

import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public final class VpInputTexts {
    private VpInputTexts() {
    }

    public static Text key(int keyCode) {
        return InputUtil.Type.KEYSYM.createFromCode(keyCode).getLocalizedText();
    }

    public static Text mouseButton(int button) {
        return InputUtil.Type.MOUSE.createFromCode(button).getLocalizedText();
    }
}
