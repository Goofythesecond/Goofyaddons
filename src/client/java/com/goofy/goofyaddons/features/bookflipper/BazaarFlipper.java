package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.bookflipper.helper.BazaarMonitor;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipCalculator;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipItem;
import com.goofy.goofyaddons.utils.Clock;
import com.goofy.goofyaddons.utils.InventoryScanner;
import com.goofy.goofyaddons.utils.InventoryUtils;
import com.goofy.goofyaddons.utils.ScoreboardUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;



public class BazaarFlipper {
    private enum State {
        START,
        IDLE,
        FETCHING,
        BAZAAR_NAVIGATION,
        PLACE_ORDER,
        OUTBID,
        STORE
    }
    private Clock clock = new Clock();
    private State state = State.IDLE;
    private State lastState = null;
    private List<FlipItem> flipItemsList = new ArrayList<>();
    private FlipCalculator flipCalculator = new FlipCalculator();
    private ScoreboardUtils scoreboardUtils = new ScoreboardUtils();
    private final Queue<Book> queue = new LinkedList<>();
    private InventoryScanner inventoryScanner = new InventoryScanner();
    private Minecraft minecraft = Minecraft.getInstance();
    private Book currentBook = null;
    private BazaarMonitor bazaarMonitor = new BazaarMonitor();
    private List<Book> buyOrderBook = new ArrayList<>();
    private Map<Book, Integer> bookIntegerMap = new HashMap<>();
    private List<Book> outbidBuyOrderBook = new ArrayList<>();
    private List<Book> booksToStore = new ArrayList<>();
    private List<Book> completedList = new ArrayList<>();



    public void onTick() {
        lastStateCheck();

        switch (state) {
            case START -> {
                flipCalculator.Refresh();
            }

            case IDLE -> {


                if (!booksToStore.isEmpty()) state = State.STORE;
                clock.start(15000);
                if (!clock.shouldFire()) {
                    for (Book book : buyOrderBook) {
                        List<Book> outbidBookList = bazaarMonitor.isOutbid(false);
                        if (outbidBookList.isEmpty()) continue;
                        buyOrderBook.remove(book);
                        outbidBuyOrderBook.add(book);
                    }
                    if (!outbidBuyOrderBook.isEmpty()) return;
                    state = State.OUTBID;
                }

            }

            case FETCHING -> {
                if (!booksToStore.isEmpty()) state = State.STORE;

                if (!queue.isEmpty()) {
                    currentBook = queue.poll();
                    state = State.BAZAAR_NAVIGATION;
                }

                if (!flipItemsList.isEmpty()) processData();

                clock.start(5000);
                if (clock.shouldFire() & flipItemsList.isEmpty()) flipItemsList = flipCalculator.getFlipItemsList();

            }

            case BAZAAR_NAVIGATION -> {
                if (!containerCheck("Bazaar")) clock.start(250);
                if (!containerCheck("Bazaar") & clock.shouldFire()) openBazaar(currentBook.name().replace("Ultimate", ""));

                if (containerCheck("Bazaar")) clock.start(500);
                if (containerCheck("Bazaar") & clock.shouldFire()) {
                    int slot = inventoryScanner.findContainer(currentBook.getRomanLevel(currentBook.level())).getFirst();
                    InventoryUtils.clickSlot(slot, false);
                }

                if (containerCheck(currentBook.name())) clock.start(500);
                if (containerCheck(currentBook.name()) & clock.shouldFire()) InventoryUtils.clickSlot(15, false);

                if (containerCheck("How many do you want")) clock.start(500);
                if (containerCheck("How many do you want") & clock.shouldFire()) {
                    InventoryUtils.clickSlot(16, false);
                }

                if (minecraft.screen instanceof SignEditScreen) {
                    handleSign();
                };

                if (containerCheck("How much do you want to pay")) state = State.PLACE_ORDER;
            }

            case PLACE_ORDER -> {
                if (containerCheck("How much do you want to pay")) clock.start(250);
                if (containerCheck("How much do you want to pay") & clock.shouldFire()) {
                    bazaarMonitor.add(currentBook, inventoryScanner.getUnitPrice(12), false);
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm buy order")) clock.start(250);
                if (containerCheck("Confirm buy order") & clock.shouldFire()) {
                    InventoryUtils.clickSlot(13, false);
                    buyOrderBook.add(currentBook);

                }
            }

            case OUTBID -> {
                    if (!containerCheck("Bazaar")) clock.start(250);
                    if (!containerCheck("Bazaar") & clock.shouldFire()) openBazaar("Wise");

                    if (containerCheck("Wise")) clock.start(500);
                    if (containerCheck("Wise") & clock.shouldFire()) InventoryUtils.clickSlot(50, false);

                    if (containerCheck("Bazaar")) clock.start(250);
                    if (containerCheck("Bazaar") & clock.shouldFire()) {
                        if (outbidBuyOrderBook.isEmpty()) {
                            minecraft.player.closeContainer();
                            state = State.IDLE;
                        }


                        List<Integer> slots = inventoryScanner.findContainer("BUY " + outbidBuyOrderBook.getFirst()
                                .getRomanLevel(outbidBuyOrderBook.getFirst().level()));
                        if (slots.isEmpty()) {
                            if (bookIntegerMap.containsKey(outbidBuyOrderBook.getFirst()) & bookIntegerMap.get(outbidBuyOrderBook.getFirst())
                            == outbidBuyOrderBook.getFirst().getQtyAmount(outbidBuyOrderBook.getFirst().level())) {
                                completedList.add(outbidBuyOrderBook.getFirst());
                                booksToStore.remove(outbidBuyOrderBook.getFirst());
                                bookIntegerMap.remove(outbidBuyOrderBook.getFirst());
                                outbidBuyOrderBook.removeFirst();
                            } else {
                                queue.add(outbidBuyOrderBook.getFirst());
                                outbidBuyOrderBook.removeFirst();
                            }
                        }
                        if (!slots.isEmpty()) {
                            int amount = inventoryScanner.checkOrder(slots.getFirst());
                            InventoryUtils.clickSlot(slots.getFirst(), false);
                            if (amount == 0) return;
                            bookIntegerMap.merge(outbidBuyOrderBook.getFirst(), amount, Integer::sum);
                            booksToStore.add(outbidBuyOrderBook.getFirst());
                        }

                    }

                    if (containerCheck("Order")) clock.start(250);
                    if (containerCheck("Order") & clock.shouldFire()) InventoryUtils.clickSlot(11, false);
            }

            case STORE -> {
                if (!containerCheck("Ender Chest")) clock.start(150);
                if (!containerCheck("Ender Chest") & clock.shouldFire()) openEnderChest();

                if (containerCheck("Ender Chest")) clock.start(250);
                if (containerCheck("Ender Chest") & clock.shouldFire()) {
                    if (booksToStore.isEmpty()) {
                        minecraft.player.closeContainer();
                        state = State.IDLE;
                    }
                    int slot = inventoryScanner.findLoreInv(booksToStore.getFirst().getRomanLevel(booksToStore.getFirst().level())).getFirst();
                    InventoryUtils.clickSlot(slot, true);
                    booksToStore.removeFirst();
                }
            }


        }

    }



    private void lastStateCheck() {
        if (state != lastState) {
            clock.stop();
            lastState = state;
        }
    }

    private void processData() {
        double purse = scoreboardUtils.getPurse();
        for (FlipItem flipItems : flipItemsList) {
            if (purse < flipItems.totalCost()) continue;
            queue.add(flipItems.book());
        }
        if (!queue.isEmpty()) {
            state = State.BAZAAR_NAVIGATION;
        } else {
            state = State.IDLE;
        }
    }

    private void openBazaar(String name) {
        if (containerCheck("bazaar")) return;
        minecraft.player.connection.sendCommand(name);
    }

    private void openAnvil() {
        if (containerCheck("Anvil")) return;
        minecraft.player.connection.sendCommand("Anvil");
    }

    private void openEnderChest() {
        if (containerCheck("Ender Chest")) return;
        minecraft.player.connection.sendCommand("ec");
    }

    private void handleSign() {
        String amountToOrder = String.valueOf(currentBook.getQtyAmount(currentBook.level()));
        if (minecraft.screen instanceof AbstractSignEditScreen signScreen) {
            if (bookIntegerMap.containsKey(currentBook)) {
                int calcAmount = bookIntegerMap.get(currentBook) - currentBook.getQtyAmount(currentBook.level());
                amountToOrder = String.valueOf(calcAmount);
            }

            try {
                Field messagesField = AbstractSignEditScreen.class.getDeclaredField("messages");
                messagesField.setAccessible(true);
                String[] messages = (String[]) messagesField.get(signScreen);
                messages[0] = amountToOrder;
                minecraft.setScreen(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean containerCheck(String name) {
        if (minecraft.screen == null) return false;
        return minecraft.screen.getTitle().toString().contains(name);
    }
}


