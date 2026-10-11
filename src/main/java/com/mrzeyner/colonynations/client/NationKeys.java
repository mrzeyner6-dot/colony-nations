package com.mrzeyner.colonynations.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class NationKeys {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.colony_nations.menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.colony_nations");

    private NationKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }
}
