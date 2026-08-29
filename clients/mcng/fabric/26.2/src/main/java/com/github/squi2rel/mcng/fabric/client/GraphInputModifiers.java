package com.github.squi2rel.mcng.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

final class GraphInputModifiers {
	private GraphInputModifiers() {
	}

	static boolean shiftDown() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return false;
			}
			Window window = client.getWindow();
			return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
				|| InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
		} catch (RuntimeException ignored) {
			return false;
		}
	}
}
