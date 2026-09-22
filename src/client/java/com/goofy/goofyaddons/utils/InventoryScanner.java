package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class InventoryScanner {
    private Minecraft minecraft = Minecraft.getInstance();

    public List<Integer> findInv(String name) {
        List<Integer> slots = new ArrayList<>();
        Inventory playerInv = minecraft.player.getInventory();
        AbstractContainerMenu menu = minecraft.player.containerMenu;

        for (Slot slot : menu.slots) {
            if (slot.container != playerInv) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            if (item.getCustomName() == null) continue;
            if (!item.getCustomName().getString().equals(name)) continue;
            slots.add(slot.index);
        }
        return slots;
    }

    public List<Integer> getSellOrder() {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int end = menu.slots.size() - 36;
        for (int i = 0; i < end; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (item.isEmpty()) continue;
            if (item.getCustomName() == null) continue;
            if (!item.getCustomName().getString().contains("SELL")) continue;
            slots.add(i);
        }
        return slots;
    }

    public String getName(int slot) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;

        ItemStack itemStack = menu.slots.get(slot).getItem();
        return itemStack.getCustomName().toString();
    }

    public List<Integer> findContainer(String name) {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int end = menu.slots.size() - 36;
        for (int i = 0; i < end; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (item.isEmpty()) continue;
            if (item.getCustomName() == null) continue;
            if (!item.getCustomName().getString().equals(name)) continue;
            slots.add(i);
        }
        return slots;
    }

    public List<Integer> findLoreInv(String string) {
        List<Integer> slots = new ArrayList<>();
        Inventory playerInv = minecraft.player.getInventory();
        AbstractContainerMenu menu = minecraft.player.containerMenu;

        for (Slot slot : menu.slots) {
            if (slot.container != playerInv) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null || !lore.lines().stream().anyMatch(l -> l.getString().equals(string))) continue;
            slots.add(slot.index);
        }
        return slots;
    }

    public List<Integer> findLoreContainer(String string) {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int end = menu.slots.size() - 36;
        for (int i = 0; i < end; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null || !lore.lines().stream().anyMatch(l -> l.getString().equals(string))) continue;
            slots.add(i);
        }
        return slots;
    }

    public int checkOrder(int slot) {
        int items = 0;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        ItemStack itemStack = menu.slots.get(slot).getItem();
        ItemLore lore = itemStack.get(DataComponents.LORE);
        if (lore == null) return 0;
        for (Component line : lore.lines()) {
            String text = line.getString();
            if (!text.contains("You have")) continue;
            String digits = text.replaceAll("[^0-9]", "");
            items = Integer.parseInt(digits);
        }
        return items;
    }

    public double getUnitPrice(int slot) {
        double unitPrice = 0;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        ItemStack itemStack = menu.slots.get(slot).getItem();
        ItemLore itemLore = itemStack.get(DataComponents.LORE);
        if (itemLore == null) return 0;
        for (Component line : itemLore.lines()) {
            String text = line.getString();
            if (!text.contains("Unit price:")) continue;
            String digits = text.replaceAll("[^0-9.]", "");
            unitPrice = Double.parseDouble(digits);
        }
        return unitPrice;
    }

    public int getEmptyInventorySlots() {
        int amount = 0;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        Inventory playerInv = minecraft.player.getInventory();

        for (Slot slot : menu.slots) {
            if (slot.container != playerInv) continue;

            if (slot.hasItem()) continue;
            amount++;
        }

        return amount;
    }

    public int getEmptyContainerSlots() {
        int amount = 0;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        Inventory playerInv = minecraft.player.getInventory();

        for (Slot slot : menu.slots) {
            if (slot.container == playerInv) continue;

            if (slot.hasItem()) continue;
            amount++;
        }

        return amount;
    }

    public boolean findMisMatch(String string) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (!menu.slots.get(29).hasItem() || !menu.slots.get(29).hasItem()) return false;
        ItemStack item = menu.slots.get(29).getItem();
        ItemStack item2 = menu.slots.get(33).getItem();
        ItemLore lore = item.get(DataComponents.LORE);
        ItemLore lore2 = item2.get(DataComponents.LORE);
        if (lore == null || lore2 == null) return false;
        if (lore.lines().stream().anyMatch(l -> l.getString().equals(string)) && lore2.lines().stream().anyMatch(l -> l.getString().equals(string)))
            return false;
        return true;
    }

    public List<Integer> matchingBookInContainer(Book book) {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int end = menu.slots.size() - 36;
        for (int i = 0; i < end; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null || !lore.lines().stream().anyMatch(l -> l.getString().contains(book.name()))) continue;
            slots.add(i);
        }
        return slots;
    }

    public List<Integer> matchingBookInInventory(Book book) {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        Inventory inventory = minecraft.player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container != inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null || !lore.lines().stream().anyMatch(l -> l.getString().contains(book.name()))) continue;
            slots.add(slot.index);
        }
        return slots;
    }

    public int getLevel(int slot) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        ItemStack itemStack = menu.slots.get(slot).getItem();
        if (itemStack.isEmpty()) return -1;
        CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return -1;
        CompoundTag tag = customData.copyTag().getCompound("enchantments").orElse(null);
        String id = tag.keySet().iterator().next();

        return tag.getIntOr(id, -1);
    }

    public boolean isMenuLoaded(int slot) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        return menu.slots.get(slot).hasItem();
    }
}