package com.goofy.goofyaddons;

import com.goofy.goofyaddons.utils.InventoryScanner;
import com.goofy.goofyaddons.features.bookflipper.helper.ItemMonitor;
import com.goofy.goofyaddons.features.bookflipper.ItemPicker;
import com.goofy.goofyaddons.utils.Scheduler;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class GoofyAddonsClient implements ClientModInitializer {

    private enum State {
        IDLE,
        FETCHING,
        OPENING_BAZAAR,
        CLICKING_BOOK,
        FILLING_SIGN,
        PLACING_ORDER,
        DONE,
        OUTBID,
        SCAN,
        REPLACE_ORDER,
        INVSCAN,
        ANVIL,
        COMBINE,
        SELL
    }

    private volatile State state = State.IDLE;
    private int booksClaimed = 0;
    private String booksToOrder = "8";
    private volatile String itemFlip = null;
    private boolean keyWasDown = false;
    private int ticks = 0;
    private ItemMonitor itemtoMonitor = new ItemMonitor("ENCHANTMENT_ULTIMATE_WISE_2", 1);
    private int counter = 0;
    boolean wiseBook2Found = false;
    boolean wiseBook3Found = false;
    boolean wiseBook4Found = false;
    Queue<Integer> slotsToClick = new LinkedList<>();
    boolean keepRunning = false;
    private InventoryScanner invscanner = new InventoryScanner();
    private State lastState = null;
    private Scheduler scheduler = new Scheduler();
    private boolean once = false;

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            if (text.contains("Buy Order") && text.contains("Ultimate Wise II") && text.contains("was filled") && state == state.DONE) {
                itemtoMonitor.finish();
                state = state.OUTBID;
            }
            if (text.contains("You have goods to claim")) {
                state = state.OUTBID;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.pauseOnLostFocus = false;
            if (minecraft.player == null) return;

            boolean keyDown = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_J);
            boolean test = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_K);
            if (test) {
                if (!once) {
                    itemtoMonitor.setItem("ENCHANTMENT_ULTIMATE_WISE_2", invscanner.getUnitPrice(12));
                    itemtoMonitor.start();
                    once = true;
                }
                System.out.println(itemtoMonitor.isOutbid());
            }
            if (state != lastState) {
                scheduler.reset();
                lastState = state;
                System.out.println("State changed to: " + state);
            }
            scheduler.tick();

            switch (state) {
                case IDLE -> {
                    if (keyDown && !keyWasDown || keepRunning) {

                        booksClaimed = 0;
                        booksToOrder = "8";
                        itemFlip = null;
                        state = State.FETCHING;
                        new Thread(() -> {
                            itemFlip = ItemPicker.getItem();
                            if (itemFlip == null || itemFlip.contains("Not enough")) {
                                state = State.IDLE;
                            } else {
                                state = State.OPENING_BAZAAR;
                            }
                        }).start();
                    }
                }

                case FETCHING -> {
                }

                case OPENING_BAZAAR -> {
                    openBazaar("Wise");
                    state = State.CLICKING_BOOK;
                }

                case CLICKING_BOOK -> {
                    scheduler.at(10, () -> {
                        clickMenuSlot(13);
                        state = State.FILLING_SIGN;
                    });
                }

                case FILLING_SIGN -> {
                    scheduler.at(10, () -> clickMenuSlot(15));
                    scheduler.at(20, () -> clickMenuSlot(16));
                    scheduler.every(25, 1, () -> {
                        if (minecraft.screen instanceof SignEditScreen) {
                            editSign();
                        }
                    });
                    scheduler.every(35, 1, () -> {
                        if (minecraft.screen instanceof SignEditScreen) return;
                        List<Integer> fixup = invscanner.findLoreContainer("Click to fixup!");
                        if (!fixup.isEmpty()) {
                            clickMenuSlot(fixup.get(0));
                        } else if (minecraft.screen == null) {
                            state = State.PLACING_ORDER;
                        }
                    });
                }

                case PLACING_ORDER -> {
                    scheduler.at(20, () -> {
                        itemtoMonitor.setItem("ENCHANTMENT_ULTIMATE_WISE_2", invscanner.getUnitPrice(12));
                        clickMenuSlot(12);
                    });
                    scheduler.at(40, () -> {
                        clickMenuSlot(13);
                        state = State.DONE;
                    });

                }

                case DONE -> {
                    itemFlip = null;
                    scheduler.every(40, 20, () -> {
                        if (itemtoMonitor.isOutbid()) state = State.OUTBID;
                    });
                }

                case OUTBID -> {
                    scheduler.at(10, () -> openBazaar("Wise"));
                    scheduler.at(30, () -> clickMenuSlot(50));
                    scheduler.at(50, () -> state = State.SCAN);
                }

                case SCAN -> {
                    scheduler.every(20, 20, () -> {
                        List<Integer> slots = invscanner.findContainer("BUY Ultimate Wise II");
                        if (slots.isEmpty()) {
                            return;
                        }

                        int slot = slots.get(0);
                        booksClaimed = invscanner.checkOrder(slot);
                        int targetBooks = Integer.parseInt(booksToOrder);

                        if (booksClaimed == 0) {
                            state = State.REPLACE_ORDER;
                        } else if (booksClaimed >= targetBooks) {
                            clickMenuSlot(slot);
                            itemtoMonitor.finish();
                            state = State.ANVIL;
                        } else {
                            booksToOrder = String.valueOf(targetBooks - booksClaimed);
                            clickMenuSlot(slot);
                            state = State.REPLACE_ORDER;
                        }
                    });
                }

                case REPLACE_ORDER -> {

                    scheduler.every(10, 20, () -> {
                        List<Integer> slots = invscanner.findContainer("BUY Ultimate Wise II");
                        if (slots.isEmpty()) {
                            return;
                        }

                        int slot = slots.get(0);
                        clickMenuSlot(slot);
                        scheduler.at(20, () -> {
                            clickMenuSlot(11);
                            state = State.INVSCAN;
                        });
                    });
                }

                case INVSCAN -> {
                    scheduler.at(10, () -> minecraft.player.connection.sendCommand("ec 1"));
                    scheduler.every(20, 20, () -> {
                        List<Integer> list = invscanner.findLoreInv("Ultimate Wise II");
                        if (list.isEmpty()) {
                            state = State.OPENING_BAZAAR;
                            minecraft.player.closeContainer();
                            return;
                        }
                        clickMenuSlot(list.get(0));
                    });
                }

                case ANVIL -> {
                    scheduler.at(10, () -> minecraft.player.connection.sendCommand("ec 1"));
                    scheduler.every(20, 20, () -> {
                        List<Integer> list = invscanner.findLoreContainer("Ultimate Wise II");
                        if (list.isEmpty()) {
                            state = State.COMBINE;
                            minecraft.player.closeContainer();
                            return;
                        }
                        clickMenuSlot(list.get(0));
                    });
                }

                case COMBINE -> {
                    ticks++;
                    if (ticks == 10) {
                        minecraft.player.connection.sendCommand("anvil");
                    }
                    AbstractContainerMenu menu = minecraft.player.containerMenu;
                    int totalSlots = menu.slots.size();
                    if (ticks >= 30 && ticks % 20 == 0 && counter < 2) {
                        for (int i = 53; i < totalSlots; i++) {
                            ItemStack itemStack = menu.slots.get(i).getItem();
                            if (itemStack.isEmpty()) continue;
                            ItemLore lore = itemStack.get(DataComponents.LORE);
                            if (lore != null && lore.lines().stream().anyMatch(l -> l.getString().equals("Ultimate Wise II"))) {
                                shiftClickMenu(i);
                                wiseBook2Found = true;
                                slotsToClick.add(i);
                                ticks = 11;
                                counter++;
                                break;
                            }
                        }
                    }
                    if (ticks >= 30 && ticks % 20 == 0 && counter < 2 && !wiseBook2Found) {
                        for (int i = 53; i < totalSlots; i++)  {
                            ItemStack itemStack = menu.slots.get(i).getItem();
                            if (itemStack.isEmpty()) continue;
                            ItemLore lore = itemStack.get(DataComponents.LORE);
                            if (lore != null && lore.lines().stream().anyMatch(l -> l.getString().equals("Ultimate Wise III"))) {
                                shiftClickMenu(i);
                                wiseBook3Found = true;
                                slotsToClick.add(i);
                                ticks = 11;
                                counter++;
                                break;
                            }
                        }
                    }
                    if (ticks >= 30 && ticks % 20 == 0 && counter < 2 && !wiseBook3Found && !wiseBook2Found) {
                        for (int i = 53; i < totalSlots; i++)  {
                            ItemStack itemStack = menu.slots.get(i).getItem();
                            if (itemStack.isEmpty()) continue;
                            ItemLore lore = itemStack.get(DataComponents.LORE);
                            if (lore != null && lore.lines().stream().anyMatch(l -> l.getString().equals("Ultimate Wise IV"))) {
                                shiftClickMenu(i);
                                wiseBook4Found = true;
                                slotsToClick.add(i);
                                ticks = 11;
                                counter++;
                                break;
                            }
                        }
                    }
                    if (ticks >= 30 && ticks % 20 == 0) {
                        for (int i = 53; i < totalSlots; i++)  {
                            ItemStack itemStack = menu.slots.get(i).getItem();
                            if (itemStack.isEmpty()) continue;
                            ItemLore lore = itemStack.get(DataComponents.LORE);
                            if (lore != null && lore.lines().stream().anyMatch(l -> l.getString().equals("Ultimate Wise V"))) {
                                minecraft.player.closeContainer();
                                ticks = 0;
                                state = state.SELL;
                            }
                        }
                    }
                    if (counter == 2 && ticks == 50) {
                        shiftClickMenu(slotsToClick.poll());
                    }
                    if (counter == 2 && ticks == 60) {
                        shiftClickMenu(slotsToClick.poll());
                    }
                    if (counter == 2 && ticks == 70) {
                        clickMenuSlot(22);
                    }
                    if (counter == 2 && ticks == 80) {
                        clickMenuSlot(22);
                        ticks = 11;
                        counter = 0;
                        wiseBook2Found = false;
                        wiseBook3Found = false;
                        wiseBook4Found = false;
                    }
                }
                case SELL -> {
                    ticks++;
                    if (ticks == 10) {
                        openBazaar("wise");
                    }
                    AbstractContainerMenu menu = minecraft.player.containerMenu;
                    int totalSlots = menu.slots.size();
                    if (ticks == 20) {
                        for (int i = 53; i < totalSlots; i++) {
                            ItemStack itemStack = menu.slots.get(i).getItem();
                            if (itemStack.isEmpty()) continue;
                            ItemLore lore = itemStack.get(DataComponents.LORE);
                            if (lore != null && lore.lines().stream().anyMatch(l -> l.getString().equals("Ultimate Wise V"))) {
                                clickMenuSlot(i);
                            }
                        }
                    }
                    if (ticks == 40) {
                        clickMenuSlot(16);
                    }
                    if (ticks == 70) {
                        clickMenuSlot(12);
                    }
                    if (ticks == 120) {
                        clickMenuSlot(13);
                    }
                    if (ticks == 130) {
                        minecraft.player.closeContainer();
                    }
                    if (ticks == 140) {
                        state = state.IDLE;
                        keepRunning = true;
                    }
                }
            }

            keyWasDown = keyDown;
        });
    }

    private void openBazaar(String item) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.player.connection.sendCommand("Bz " + item);
    }

    private void clickMenuSlot(int slot) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, minecraft.player);
    }

    private void shiftClickMenu(int slot) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, minecraft.player);
    }

    private void editSign() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SignEditScreen signScreen) {
            try {
                Field lineField = AbstractSignEditScreen.class.getDeclaredField("line");
                lineField.setAccessible(true);
                lineField.set(signScreen, 0);

                java.lang.reflect.Method setMessage = AbstractSignEditScreen.class.getDeclaredMethod("setMessage", String.class);
                setMessage.setAccessible(true);
                setMessage.invoke(signScreen, booksToOrder);

                minecraft.setScreen(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}