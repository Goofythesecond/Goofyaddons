package com.goofy.goofyaddons;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.keybinds.GoofyKeybinds;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class GoofyAddonsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GoofyConfig.load();
        ChatHook.register();
        GoofyKeybinds.register();
        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FailsafeManager.INSTANCE.onTick();
            FeatureManager.INSTANCE.onTick();

            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_BACKSLASH)) GoofyConfig.save();

            while (GoofyKeybinds.startKey.consumeClick()) {
                FeatureManager.INSTANCE.start("BazaarFlipper");
            }
            while (GoofyKeybinds.stopKey.consumeClick()) {
                FeatureManager.INSTANCE.stop();
            }
        });
    }
}
