package com.github.squi2rel.mcng.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;

final class GraphInputText {
    private GraphInputText() {
    }

    static String key(int keyCode) {
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
    }

    static String mouse(int button) {
        return InputConstants.Type.MOUSE.getOrCreate(button).getDisplayName().getString();
    }

    static String shortcut(String modifier, int keyCode) {
        return modifier + "+" + key(keyCode);
    }
}
