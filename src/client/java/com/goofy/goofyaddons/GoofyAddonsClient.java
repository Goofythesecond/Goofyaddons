package com.goofy.goofyaddons;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper;
import com.goofy.goofyaddons.utils.InventoryScanner;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class GoofyAddonsClient implements ClientModInitializer {
    InventoryScanner inventoryScanner = new InventoryScanner();
    BazaarFlipper bazaarFlipper = new BazaarFlipper();

    @Override
    public void onInitializeClient() {
        GoofyConfig.load();
        ChatHook.register();
        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            bazaarFlipper.onTick();
            boolean keyDown = InputConstants.isKeyDown(minecraft.getWindow(), GoofyConfig.INSTANCE.startKey);
            boolean keyDown1 = InputConstants.isKeyDown(minecraft.getWindow(), GoofyConfig.INSTANCE.stopKey);
            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_P)) bazaarFlipper.debugMode = true;

            if (InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_BACKSLASH)) GoofyConfig.save();

            if (keyDown) bazaarFlipper.start();
            if (keyDown1) bazaarFlipper.stop();

        });
    }
}