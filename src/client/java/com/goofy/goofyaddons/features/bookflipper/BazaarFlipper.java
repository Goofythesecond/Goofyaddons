package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipCalculator;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipItem;
import com.goofy.goofyaddons.utils.*;

import java.lang.reflect.Field;
import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;

public class BazaarFlipper {
    private enum State {
        START,
        IDLE,
        FETCHING,
        BAZAAR_NAVIGATION,
        PLACE_ORDER
    }

    private Scheduler scheduler = new Scheduler();
    private State state = State.IDLE;
    private State lastState = null;
    private List<FlipItem> flipItemsList = new ArrayList<>();
    private FlipCalculator flipCalculator = new FlipCalculator();
    private Scoreboard scoreboard = new Scoreboard();
    private final Queue<FlipItem> queue = new LinkedList<>();
    InventoryScanner inventoryScanner = new InventoryScanner();
    Minecraft minecraft = Minecraft.getInstance();
    private Book currentBook = null;
    private Map<Book, ItemMonitor> itemOrder = new HashMap<>();

    public void onTick() {
        lastStateCheck();
        scheduler.tick();

        switch (state) {
            case START -> {
                flipCalculator.Refresh();
            }

            case FETCHING -> {
                if (!flipItemsList.isEmpty()) processData();
                scheduler.every(20, 10, () -> flipItemsList = flipCalculator.getFlipItemsList());
            }

            case BAZAAR_NAVIGATION -> {
                if (!queue.isEmpty()) {
                    queue.poll().setBook(currentBook);
                }
                openBazaar(currentBook.name());
                if (minecraft.screen.getTitle().toString().contains("Bazaar")) {
                    scheduler.at(5, () -> {
                        int slot = inventoryScanner.findContainer(currentBook.getRomanLevel(currentBook.level())).getFirst();
                        InventoryUtils.clickSlot(slot, false);
                    });
                }

                if (minecraft.screen.getTitle().toString().contains(currentBook.name())) {
                    scheduler.at(5, () -> InventoryUtils.clickSlot(15, false));
                }

                if (minecraft.screen.getTitle().toString().contains("How many do you want")) {
                    scheduler.at(5, () -> InventoryUtils.clickSlot(16, false));
                }

                scheduler.every(25, 1, () -> {
                    if (minecraft.screen instanceof SignEditScreen) {
                        handleSign();
                    }
                });
                scheduler.every(35, 1, () -> {
                    if (minecraft.screen instanceof SignEditScreen) return;
                    List<Integer> fixup = inventoryScanner.findLoreContainer("Click to fixup!");
                    if (!fixup.isEmpty()) {
                        InventoryUtils.clickSlot(fixup.get(0), false);
                    } else if (minecraft.screen.getTitle().toString().contains("How much do you want to pay")) state = State.PLACE_ORDER;
                });
            }

            case PLACE_ORDER -> {

            }
        }

    }



    private void lastStateCheck() {
        if (state != lastState) {
            scheduler.reset();
            lastState = state;
        }
    }

    private void processData() {
        double purse = scoreboard.getPurse();
        for (FlipItem flipItems : flipItemsList) {
            if (purse < flipItems.totalCost()) continue;
            queue.add(flipItems);
        }
        if (!queue.isEmpty()) {
            state = State.BAZAAR_NAVIGATION;
        } else {
            state = State.IDLE;
        }
    }

    private void openBazaar(String name) {
        minecraft.player.connection.sendCommand(name);
    }

    private void handleSign() {
        if (minecraft.screen instanceof AbstractSignEditScreen signScreen) {
            try {
                Field messagesField = AbstractSignEditScreen.class.getDeclaredField("messages");
                messagesField.setAccessible(true);
                String[] messages = (String[]) messagesField.get(signScreen);
                messages[0] = String.valueOf(currentBook.getQtyAmount(currentBook.level()));
                minecraft.setScreen(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}


