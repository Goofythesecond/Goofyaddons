package com.goofy.goofyaddons.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class GoofyKeybinds {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("goofyaddons", "category")
    );

    public static KeyMapping startKey;
    public static KeyMapping stopKey;

    public static void register() {
        startKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.start",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));

        stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.stop",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CATEGORY
        ));
    }
}
