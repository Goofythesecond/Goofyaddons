package com.goofy.goofyaddons;

import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper;
import com.goofy.goofyaddons.utils.InventoryScanner;
import com.goofy.goofyaddons.utils.InventoryUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class GoofyAddonsClient implements ClientModInitializer {
    InventoryScanner inventoryScanner = new InventoryScanner();


    BazaarFlipper bazaarFlipper = new BazaarFlipper();

    @Override
    public void onInitializeClient() {

        final Minecraft minecraft = Minecraft.getInstance();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            bazaarFlipper.onTick();
            boolean keyDown = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_J);
            boolean keyDown1 = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_K);
            boolean keyDown3 = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_L);
            if (keyDown3) {
                List<Integer> stageOneBookList   = inventoryScanner.findLoreInv("Ultimate Wise I");
                System.out.println(stageOneBookList);
                AbstractContainerMenu menu = minecraft.player.containerMenu;
                System.out.println(menu.slots.size());
                Inventory inventory = minecraft.player.getInventory();
                System.out.println(inventory.getContainerSize() - menu.slots.size());
                InventoryUtils.clickSlot((10 + 45), true);
            }
            if (keyDown) bazaarFlipper.start();
            if (keyDown1) bazaarFlipper.stop();

        });
    }
}