package com.goofy.goofyaddons;

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
        ChatHook.register();

        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            bazaarFlipper.onTick();
            boolean keyDown = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_J);
            boolean keyDown1 = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_K);
            boolean keyDown3 = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_L);
            if (keyDown3) {
            }
            if (keyDown) bazaarFlipper.start();
            if (keyDown1) bazaarFlipper.stop();
        });
    }
}