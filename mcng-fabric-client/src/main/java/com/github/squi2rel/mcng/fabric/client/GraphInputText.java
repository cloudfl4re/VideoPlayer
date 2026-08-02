package com.github.squi2rel.mcng.fabric.client;

import net.minecraft.client.util.InputUtil;

final class GraphInputText {
    private GraphInputText() {
    }

    static String key(int keyCode) {
        return InputUtil.Type.KEYSYM.createFromCode(keyCode).getLocalizedText().getString();
    }

    static String mouse(int button) {
        return InputUtil.Type.MOUSE.createFromCode(button).getLocalizedText().getString();
    }

    static String shortcut(String modifier, int keyCode) {
        return modifier + "+" + key(keyCode);
    }
}
