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
        STORE,
        ANVIL,
        COMBINE,
        SELL,
        REPLACE_SELL
    }

    private enum BookState {
        NOT_ENOUGH_CASH,
        NONE,
        BUY_ORDER,

    }

    public boolean enabled = false;


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
    private List<Book> bookstoSell = new ArrayList<>();
    private int counter = 0;
    private boolean clickedOnce = false;
    private List<Book> booksToRestart = new ArrayList<>();
    private boolean notEnoughMoney;


    private void debug(String msg) {
        System.out.println("[BazaarFlipper] [" + state + "] " + msg);
    }

    public void start() {
        debug("STARTED");
        enabled = true;
        state = State.START;
    }

    public void stop() {
        debug("Stopping and resetting");


        enabled = false;


        // reset state
        state = State.IDLE;
        lastState = null;


        // clear data lists
        flipItemsList.clear();
        buyOrderBook.clear();
        outbidBuyOrderBook.clear();
        booksToStore.clear();
        completedList.clear();
        bookstoSell.clear();


        // clear queue
        queue.clear();


        // clear map
        bookIntegerMap.clear();


        // reset current data
        currentBook = null;
        booksToRestart = new ArrayList<>();


        // reset counters / flags
        counter = 0;
        clickedOnce = false;
        notEnoughMoney = false;


        // reset timers
        clock.stop();


        debug("Reset complete");
    }

    public void onTick() {
        if (!enabled) return;

        bazaarMonitor.onTick();
        lastStateCheck();

        switch (state) {
            case START -> {
                debug("START: calling flipCalculator.Refresh()");
                flipCalculator.Refresh();
                debug("START: Switching to FETCHING");
                state = State.FETCHING;
            }

            case IDLE -> {

                if (!booksToStore.isEmpty()) {
                    debug("IDLE: booksToStore not empty, switching to STORE");
                    state = State.STORE;
                    return;
                }
                if (!completedList.isEmpty()) {
                    debug("IDLE: completedList not empty, switching to ANVIL");
                    state = State.ANVIL;
                    return;
                }
                if (!queue.isEmpty()) {
                    debug("IDLE: queue not empty, switching to Bazaar and polling book from queue");
                    state = State.BAZAAR_NAVIGATION;
                    currentBook = queue.poll();
                }

                clock.start(10000);
                if (clock.shouldFire()) {
                    bazaarMonitor.start();
                    debug("IDLE: 15s clock fired, checking for outbids");
                    List<Book> outbidBookList = bazaarMonitor.isOutbid(false);

                    buyOrderBook.removeIf(book -> {
                        if (outbidBookList.stream().anyMatch(outbid ->
                                outbid.id().equals(book.id()) && outbid.level() == book.level())) {

                            outbidBuyOrderBook.add(book);
                            return true;
                        }

                        return false;
                    });
                    if (!outbidBuyOrderBook.isEmpty()) {
                        debug("IDLE: outbid books found, returning");
                        state = State.OUTBID;
                        return;
                    }
                }
                if (notEnoughMoney) {
                    double purse = scoreboardUtils.getPurse();
                    debug("IDLE: notEnoughMoney=true, purse=" + purse);
                    if (purse >= 2500000) {
                        debug("IDLE: purse sufficient, switching to FETCHING");
                        state = State.FETCHING;
                    }
                    notEnoughMoney = false;
                }
                if (!booksToRestart.isEmpty()) {
                    debug("IDLE: booksToRestart isn't empty, switching to FETCHING");
                    state = State.FETCHING;
                    return;
                }
            }

            case FETCHING -> {
                if (!queue.isEmpty()) {
                    currentBook = queue.poll();
                    debug("FETCHING: polled book=" + currentBook + ", switching to BAZAAR_NAVIGATION");
                    state = State.BAZAAR_NAVIGATION;
                }

                if (!flipItemsList.isEmpty()) {
                    debug("FETCHING: flipItemsList not empty, calling processData");
                    processData(booksToRestart);
                    return;
                }

                clock.start(5000);
                if (clock.shouldFire() && flipItemsList.isEmpty()) {
                    debug("FETCHING: 5s clock fired and flipItemsList empty, fetching flip items");
                    flipItemsList = flipCalculator.getFlipItemsList();
                    for (FlipItem flipItem : flipItemsList) debug("Book: " + flipItem.book());
                    debug("FETCHING: got " + flipItemsList.size() + " flip items");
                }
            }

            case BAZAAR_NAVIGATION -> {
                if (!isContainerOpen()) clock.start(1000);
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("BAZAAR_NAVIGATION: no container open, opening bazaar for " + currentBook.name());
                    openBazaar(currentBook.name().replace("Ultimate", ""));
                }

                if (containerCheck("Bazaar")) clock.start(1000);
                if (containerCheck("Bazaar") && clock.shouldFire()) {
                    List<Integer> slots = inventoryScanner.findContainer(currentBook.getRomanLevel(currentBook.level()));
                    debug("BAZAAR_NAVIGATION: Bazaar open, clicking slot " + slots + " for " + currentBook.getRomanLevel(currentBook.level()));
                    InventoryUtils.clickSlot(slots.getFirst(), false);
                }

                if (containerCheck(currentBook.name())) clock.start(1000);
                if (containerCheck(currentBook.name()) && clock.shouldFire()) {
                    debug("BAZAAR_NAVIGATION: book container open, clicking slot 15");
                    InventoryUtils.clickSlot(15, false);
                }

                if (containerCheck("How many do you want")) clock.start(1000);
                if (containerCheck("How many do you want") && clock.shouldFire()) {
                    debug("BAZAAR_NAVIGATION: qty prompt open, clicking slot 16");
                    InventoryUtils.clickSlot(16, false);
                }
                if (minecraft.screen instanceof SignEditScreen) clock.start(2000);
                if (minecraft.screen instanceof SignEditScreen && clock.shouldFire()) {
                    debug("BAZAAR_NAVIGATION: sign screen detected, handling sign");
                    handleSign();
                }

                if (containerCheck("How much do you want to pay")) {
                    debug("BAZAAR_NAVIGATION: price prompt open, switching to PLACE_ORDER");
                    state = State.PLACE_ORDER;
                }
            }

            case PLACE_ORDER -> {
                if (containerCheck("How much do you want to pay")) clock.start(1000);
                if (containerCheck("How much do you want to pay") && clock.shouldFire()) {
                    debug("PLACE_ORDER: clicking slot 12 to confirm price, book=" + currentBook);
                    bazaarMonitor.add(currentBook, inventoryScanner.getUnitPrice(12), false);
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm")) clock.start(1000);
                if (containerCheck("Confirm") && clock.shouldFire()) {
                    debug("PLACE_ORDER: confirming buy order for " + currentBook + ", queue remaining=" + queue.size());
                    InventoryUtils.clickSlot(13, false);
                    buyOrderBook.add(currentBook);
                    if (queue.isEmpty()) {
                        debug("PLACE_ORDER: queue empty, switching to IDLE");
                        state = State.IDLE;
                        return;
                    }
                    currentBook = queue.poll();
                    debug("PLACE_ORDER: next book=" + currentBook + ", switching to BAZAAR_NAVIGATION");
                    state = State.BAZAAR_NAVIGATION;
                }
            }

            case OUTBID -> {
                if (!isContainerOpen()) clock.start(1000);
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("OUTBID: no container, opening bazaar for Wise");
                    openBazaar("Wise");
                }

                if (containerCheck("Wise")) clock.start(500);
                if (containerCheck("Wise") && clock.shouldFire()) {
                    debug("OUTBID: Wise open, clicking slot 50");
                    InventoryUtils.clickSlot(50, false);
                }

                if (containerCheck("Bazaar")) clock.start(1000);
                if (containerCheck("Bazaar") && clock.shouldFire()) {
                    if (outbidBuyOrderBook.isEmpty()) {
                        debug("OUTBID: outbidBuyOrderBook empty, closing container and switching to IDLE");
                        minecraft.player.closeContainer();
                        state = State.IDLE;
                        return;
                    }

                    List<Integer> slots = inventoryScanner.findContainer("BUY " + outbidBuyOrderBook.getFirst()
                            .getRomanLevel(outbidBuyOrderBook.getFirst().level()));
                    debug("OUTBID: found " + slots.size() + " slots for " + outbidBuyOrderBook.getFirst());

                    if (slots.isEmpty()) {
                        if (bookIntegerMap.containsKey(outbidBuyOrderBook.getFirst()) && bookIntegerMap.get(outbidBuyOrderBook.getFirst())
                                == outbidBuyOrderBook.getFirst().getQtyAmount(outbidBuyOrderBook.getFirst().level())) {
                            debug("OUTBID: order complete for " + outbidBuyOrderBook.getFirst() + ", moving to completedList");
                            completedList.add(outbidBuyOrderBook.getFirst());
                            booksToStore.remove(outbidBuyOrderBook.getFirst());
                            bookIntegerMap.remove(outbidBuyOrderBook.getFirst());
                            outbidBuyOrderBook.removeFirst();
                        } else {
                            debug("OUTBID: slots empty and not complete, re-queuing " + outbidBuyOrderBook.getFirst());
                            queue.add(outbidBuyOrderBook.getFirst());
                            outbidBuyOrderBook.removeFirst();
                        }
                    }
                    if (!slots.isEmpty()) {
                        int amount = inventoryScanner.checkOrder(slots.getFirst());
                        debug("OUTBID: order amount=" + amount + ", clicking slot " + slots.getFirst());
                        InventoryUtils.clickSlot(slots.getFirst(), false);
                        if (amount == 0) {
                            debug("OUTBID: amount=0, returning early");
                            return;
                        }
                        bookIntegerMap.merge(outbidBuyOrderBook.getFirst(), amount, Integer::sum);
                        booksToStore.add(outbidBuyOrderBook.getFirst());
                    }
                }

                if (containerCheck("Order")) clock.start(1000);
                if (containerCheck("Order") && clock.shouldFire()) {
                    debug("OUTBID: Order screen open, clicking slot 11");
                    InventoryUtils.clickSlot(11, false);
                }
            }

            case STORE -> {
                if (!isContainerOpen()) clock.start(1000);
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("STORE: no container, opening ender chest");
                    openEnderChest();
                }

                if (containerCheck("Ender Chest")) clock.start(1000);
                if (containerCheck("Ender Chest") && clock.shouldFire()) {
                    if (booksToStore.isEmpty()) {
                        debug("STORE: booksToStore empty, closing container and switching to IDLE");
                        minecraft.player.closeContainer();
                        state = State.IDLE;
                        return;
                    }
                    List<Integer> slots = new ArrayList<>();
                    slots.addAll(inventoryScanner.findLoreInv(booksToStore.getFirst().getRomanLevel(booksToStore.getFirst().level())));
                    if (!slots.isEmpty()) {
                        InventoryUtils.clickSlot(slots.getFirst(), true);
                        debug("STORE: storing " + booksToStore.getFirst() + " at slot " + slots.getFirst());
                    }
                    if (slots.isEmpty()) {
                        booksToStore.removeFirst();
                        debug("STORE: slot is empty removing book from booksToStore");
                    }
                }
            }

            case ANVIL -> {
                List<Integer> slots = new ArrayList<>();
                if (!containerCheck("Ender Chest")) clock.start(1000);
                if (!containerCheck("Ender Chest") && clock.shouldFire()) {
                    debug("ANVIL: no ender chest, opening it");
                    openEnderChest();
                }

                if (containerCheck("Ender Chest")) clock.start(1000);
                if (containerCheck("Ender Chest") && clock.shouldFire()) {
                    for (Book book : completedList) {
                        slots.addAll(inventoryScanner.findLoreContainer(book.getRomanLevel(book.level())));
                    }
                    debug("ANVIL: found " + slots.size() + " book slots in ender chest");
                    if (slots.isEmpty()) {
                        debug("ANVIL: no slots found, switching to COMBINE");
                        state = State.COMBINE;
                        minecraft.player.closeContainer();
                        return;
                    }
                    debug("ANVIL: pulling slot " + slots.getFirst() + " from ender chest");
                    InventoryUtils.clickSlot(slots.getFirst(), true);
                }
            }

            case COMBINE -> {
                if (!containerCheck("Anvil")) clock.start(1000);
                if (!containerCheck("Anvil") && clock.shouldFire()) {
                    debug("COMBINE: no anvil open, opening it");
                    openAnvil();
                }

                if (containerCheck("Anvil") && counter < 2) clock.start(1000);
                if (containerCheck("Anvil") && counter < 2 && clock.shouldFire()) {
                    if (completedList.isEmpty()) {
                        debug("COMBINE: completedList empty, switching to SELL");
                        state = State.SELL;
                        minecraft.player.closeContainer();
                        return;
                    }

                    List<Integer> stageOneBookList   = inventoryScanner.findLoreInv(completedList.getFirst().getRomanLevel(1));
                    List<Integer> stageTwoBookList   = inventoryScanner.findLoreInv(completedList.getFirst().getRomanLevel(2));
                    List<Integer> stageThreeBookList = inventoryScanner.findLoreInv(completedList.getFirst().getRomanLevel(3));
                    List<Integer> stageFourBookList  = inventoryScanner.findLoreInv(completedList.getFirst().getRomanLevel(4));
                    List<Integer> stageFiveBookList  = inventoryScanner.findLoreInv(completedList.getFirst().getRomanLevel(5));

                    debug("COMBINE: stage counts - 1:" + stageOneBookList.size() + " 2:" + stageTwoBookList.size()
                            + " 3:" + stageThreeBookList.size() + " 4:" + stageFourBookList.size() + " 5:" + stageFiveBookList.size()
                            + " counter=" + counter);

                    if (!stageOneBookList.isEmpty()) {
                        debug("COMBINE: placing stage 1 book at slot " + stageOneBookList.getFirst());
                        InventoryUtils.clickSlot(stageOneBookList.getFirst(), true);
                        counter++;
                        return;
                    }
                    if (!stageTwoBookList.isEmpty()) {
                        debug("COMBINE: placing stage 2 book at slot " + stageTwoBookList.getFirst());
                        InventoryUtils.clickSlot(stageTwoBookList.getFirst(), true);
                        counter++;
                        return;
                    }
                    if (!stageThreeBookList.isEmpty()) {
                        debug("COMBINE: placing stage 3 book at slot " + stageThreeBookList.getFirst());
                        InventoryUtils.clickSlot(stageThreeBookList.getFirst(), true);
                        counter++;
                        return;
                    }
                    if (!stageFourBookList.isEmpty()) {
                        debug("COMBINE: placing stage 4 book at slot " + stageFourBookList.getFirst());
                        InventoryUtils.clickSlot(stageFourBookList.getFirst(), true);
                        counter++;
                        return;
                    }
                    if (!stageFiveBookList.isEmpty()) {
                        debug("COMBINE: Completed final book, adding to bookstoSell");
                        bookstoSell.add(completedList.getFirst());
                        completedList.removeFirst();
                        return;
                    }
                }

                if (counter == 2) clock.start(1000);
                if (counter == 2 && clock.shouldFire()) {
                    debug("COMBINE: counter==2, clicking anvil output slot 22, clickedOnce=" + clickedOnce);
                    InventoryUtils.clickSlot(22, false);
                    if (clickedOnce) {
                        debug("COMBINE: second click done, resetting counter and clickedOnce");
                        counter = 0;
                        clickedOnce = false;
                        return;
                    }
                    clickedOnce = true;
                }
            }

            case SELL -> {
                List<Integer> slots = new ArrayList<>();
                if (!isContainerOpen()) clock.start(1000);
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("SELL: no container, opening bazaar for tomato");
                    openBazaar("tomato");
                }

                if (containerCheck("tomato")) clock.start(500);
                if (containerCheck("tomato") && clock.shouldFire()) {
                    debug("SELL: tomato bazaar open, clicking slot 50");
                    InventoryUtils.clickSlot(50, false);
                }

                if (containerCheck("Bazaar")) clock.start(1000);
                if (containerCheck("Bazaar") && clock.shouldFire()) {
                    if (bookstoSell.isEmpty()) {
                        debug("SELL: bookstoSell empty, switching to IDLE");
                        state = State.IDLE;
                        minecraft.player.closeContainer();
                        return;
                    }
                    for (Book book : bookstoSell) {
                        slots.addAll(inventoryScanner.findContainer("SELL " + book.getRomanLevel(5)));
                    }
                    debug("SELL: found " + slots.size() + " sell slots");

                    if (!slots.isEmpty()) {
                        debug("SELL: clicking sell slot " + slots.getFirst());
                        InventoryUtils.clickSlot(slots.getFirst(), false);
                    }
                    if (slots.isEmpty()) {
                        debug("SELL: no slots found, clicking on: " + bookstoSell.getFirst().name());
                        List<Integer> slot = inventoryScanner.findLoreInv(bookstoSell.getFirst().getRomanLevel(bookstoSell.getFirst().sellLevel()));
                        if (slot.isEmpty()) {
                            bookstoSell.removeFirst();
                            debug("SELL: slot is empty, removed book from booksToSell and return" );
                            return;
                        }
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                    }
                }

                if (containerCheck("Order")) clock.start(1000);
                if (containerCheck("Order") && clock.shouldFire()) {
                    debug("SELL: Order screen, clicking slot 13");
                    InventoryUtils.clickSlot(13, false);
                }

                if (!bookstoSell.isEmpty() && containerCheck(bookstoSell.getFirst().name())) clock.start(500);
                if (!bookstoSell.isEmpty() && containerCheck(bookstoSell.getFirst().name()) && clock.shouldFire()) {
                    debug("SELL: book screen open, clicking slot 16");
                    InventoryUtils.clickSlot(16, false);
                }

                if (containerCheck("At what price are you selling")) clock.start(1000);
                if (containerCheck("At what price are you selling") && clock.shouldFire()) {
                    debug("SELL: price prompt, clicking slot 12");
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm")) clock.start(1000);
                if (containerCheck("Confirm") && clock.shouldFire()) {
                    debug("SELL: confirm prompt, clicking slot 13 and removing " + bookstoSell.getFirst() + " from sell list");
                    InventoryUtils.clickSlot(13, false);
                    booksToRestart.add(bookstoSell.getFirst());
                    bookstoSell.removeFirst();

                }
            }
        }
    }

    private void lastStateCheck() {
        if (state != lastState) {
            System.out.println("[BazaarFlipper] state changed: " + lastState + " -> " + state);
            clock.stop();
            lastState = state;
        }
    }

    private void processData(List<Book> list) {
        if (notEnoughMoney) return;
        double purse = scoreboardUtils.getPurse();
        System.out.println("[BazaarFlipper] processData: purse=" + purse + " booksToRestart=" + (list == null ? "null" : list.size()));
        if (booksToRestart.isEmpty()) {
            for (FlipItem flipItems : flipItemsList) {
                if (purse < flipItems.totalCost()) continue;
                queue.add(flipItems.book());
            }
            if (!queue.isEmpty()) {
                System.out.println("[BazaarFlipper] processData: queued " + queue.size() + " books, switching to BAZAAR_NAVIGATION");
                currentBook = queue.poll();
                state = State.BAZAAR_NAVIGATION;
            } else {
                System.out.println("[BazaarFlipper] processData: no affordable books, switching to IDLE, notEnoughMoney=true");
                state = State.IDLE;
                notEnoughMoney = true;
            }
        } else {
            for (FlipItem flipItems : flipItemsList) {
                if (!list.contains(flipItems.book())) continue;
                if (purse < flipItems.totalCost()) continue;
                queue.add(flipItems.book());
            }
            if (!queue.isEmpty()) {
                System.out.println("[BazaarFlipper] processData: restart path, queued " + queue.size() + " books, switching to FETCHING");
                state = State.FETCHING;
            } else {
                System.out.println("[BazaarFlipper] processData: restart path, no books, notEnoughMoney=true");
                state = State.IDLE;
                notEnoughMoney = true;
            }
        }
        if (!queue.isEmpty()) booksToRestart.clear();
    }

    private void openBazaar(String name) {
        if (containerCheck("bazaar")) return;
        System.out.println("[BazaarFlipper] openBazaar: sending command for " + name);
        minecraft.player.connection.sendCommand("bz " + name);
    }

    private void openAnvil() {
        if (containerCheck("Anvil")) return;
        System.out.println("[BazaarFlipper] openAnvil");
        minecraft.player.connection.sendCommand("Anvil");
    }

    private void openEnderChest() {
        if (containerCheck("Ender Chest")) return;
        System.out.println("[BazaarFlipper] openEnderChest");
        minecraft.player.connection.sendCommand("ec");
    }

    private void handleSign() {
        String amountToOrder = String.valueOf(currentBook.getQtyAmount(currentBook.level()));
        if (minecraft.screen instanceof AbstractSignEditScreen signScreen) {
            if (bookIntegerMap.containsKey(currentBook)) {
                int calcAmount = currentBook.getQtyAmount(currentBook.level()) - bookIntegerMap.get(currentBook);
                amountToOrder = String.valueOf(calcAmount);
            }
            System.out.println("[BazaarFlipper] handleSign: writing amount=" + amountToOrder + " for book=" + currentBook);
            try {
                Field messagesField = AbstractSignEditScreen.class.getDeclaredField("messages");
                messagesField.setAccessible(true);
                String[] messages = (String[]) messagesField.get(signScreen);
                messages[0] = amountToOrder;
                minecraft.setScreen(null);
            } catch (Exception e) {
                System.out.println("[BazaarFlipper] handleSign: reflection failed - " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private boolean containerCheck(String name) {
        if (minecraft.screen == null) return false;
        return minecraft.screen.getTitle().toString().contains(name);
    }

    private boolean isContainerOpen() {
        if (minecraft.screen == null) return false;
        return true;
    }


    private class Task {
        private BookState bookState;
        private int amountToOrder;
        private int inEnderChest;
        private int inInventory;

        private BookState getBookState() {
            return bookState;
        }

        private void setBookState(BookState bookState) {
            this.bookState = bookState;
        }

        private void setAmountToOrder(int amountToOrder) {
            this.amountToOrder = amountToOrder;
        }

        private void setInEnderChest(int inEnderChest) {
            this.inEnderChest = inEnderChest;
        }

        private void setInInventory(int inInventory) {
            this.inInventory = inInventory;
        }

        private int getAmountToOrder() {
            return amountToOrder - (inEnderChest + inInventory);
        }

        private boolean shouldCheckInventory() {
            return inEnderChest > 0;
        }
    }
}
