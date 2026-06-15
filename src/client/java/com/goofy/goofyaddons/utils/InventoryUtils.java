package com.goofy.goofyaddons.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

public class InventoryUtils {

    public static void clickSlot(int slot, boolean shift) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerMenu menu = minecraft.player.containerMenu;

        ContainerInput input = shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;

        minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, input, minecraft.player);
    }
}