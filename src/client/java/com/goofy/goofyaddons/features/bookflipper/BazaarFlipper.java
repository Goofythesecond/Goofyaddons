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
import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.jetbrains.annotations.UnknownNullability;


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
        SELECTED,
        BUY_ORDER,
        OUTBID,
        SUB_STORE,
        STORE,
        ANVIL,
        COMBINE,
        SELL
    }

    public boolean enabled = false;


    private Clock clock = new Clock();
    private State state = State.IDLE;
    private State lastState = null;
    private List<FlipItem> flipItemsList = new ArrayList<>();
    private FlipCalculator flipCalculator = new FlipCalculator();
    private ScoreboardUtils scoreboardUtils = new ScoreboardUtils();
    private InventoryScanner inventoryScanner = new InventoryScanner();
    private Minecraft minecraft = Minecraft.getInstance();
    private BazaarMonitor bazaarMonitor = new BazaarMonitor();
    private int counter = 0;
    private boolean clickedOnce = false;
    private Book activeBook = null;

    private final Map<Book, Task> task = new LinkedHashMap<>();

    private void debug(String msg) {
        System.out.println("[BazaarFlipper] [" + state + "] " + msg);
    }

    public void start() {
        debug("STARTED");
        if (minecraft.screen != null) {
            minecraft.player.closeContainer();
        }
        enabled = true;
        state = State.START;
    }

    public void stop() {

        task.clear();
        debug("Stopping and resetting");

        enabled = false;

        // reset state
        state = State.IDLE;
        lastState = null;

        // clear data
        flipItemsList.clear();

        // reset current data
        activeBook = null;

        // reset counters / flags
        counter = 0;
        clickedOnce = false;

        // reset timers
        clock.stop();

        debug("Reset complete");
    }

    public void onTick() {
        if (!enabled) return;

        bazaarMonitor.onTick();
        bazaarMonitor.start();
        lastStateCheck();

        switch (state) {
            case START -> {
                debug("START: calling flipCalculator.Refresh()");
                flipCalculator.Refresh();
                debug("START: Switching to FETCHING");
                state = State.FETCHING;
            }

            case IDLE -> {
                Book outbidBook = firstBookInState(BookState.OUTBID);
                if (outbidBook != null) {
                    state = State.OUTBID;
                    return;
                }

                Book selectedBook = firstBookInState(BookState.SELECTED);
                if (selectedBook != null) {
                    activeBook = selectedBook;
                    state = State.BAZAAR_NAVIGATION;
                    return;
                }

                Book bookSubStore = firstBookInState(BookState.SUB_STORE);
                if (bookSubStore != null) {
                    state = State.BAZAAR_NAVIGATION;
                    activeBook = bookSubStore;
                    return;
                }

                Book bookToStore = firstBookInState(BookState.STORE);
                if (bookToStore != null) {
                    state = State.STORE;
                    return;
                }



                List<Book> booksToAnvil = booksInState(BookState.ANVIL);
                if (!booksToAnvil.isEmpty()) {
                    boolean shouldCheck = false;
                    for (Book book : booksToAnvil) {
                        if (task.get(book).shouldCheckEnderChest()) {
                            shouldCheck = true;
                            continue;
                        }

                        editStateBook(book, BookState.COMBINE);
                    }
                    if (shouldCheck) {
                        state = State.ANVIL;
                    }
                    else {
                        state = State.COMBINE;
                    }

                }

                clock.start(15000);
                if (clock.shouldFire()) {
                    debug("IDLE: Checking for outbid");
                    List<Book> books;
                    books = bazaarMonitor.isOutbid(false);

                    if (books.isEmpty()) return;
                    debug("IDLE: Found outbid:" + books.size());

                    for (Book book : books) {
                        editStateBook(book, BookState.OUTBID);
                    }
                }
            }

            case FETCHING -> {

                if (!flipItemsList.isEmpty()) {
                    processData();
                    state = State.IDLE;
                }

                clock.start(5000);
                if (clock.shouldFire()) flipItemsList = flipCalculator.getFlipItemsList();
            }

            case BAZAAR_NAVIGATION -> {
                if (!isContainerOpen()) clock.start(1000);
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("BAZAAR_NAVIGATION: no container open, opening bazaar for " + activeBook.name());
                    openBazaar(activeBook.name().replace("Ultimate", ""));
                }

                if (containerCheck("Bazaar")) clock.start(1000);
                if (containerCheck("Bazaar") && clock.shouldFire()) {
                    List<Integer> slots = inventoryScanner.findContainer(activeBook.getRomanLevel(activeBook.level()));
                    debug("BAZAAR_NAVIGATION: Bazaar open, clicking slot " + slots + " for " + activeBook.getRomanLevel(activeBook.level()));
                    InventoryUtils.clickSlot(slots.getFirst(), false);
                }

                if (containerCheck(activeBook.name())) clock.start(1000);
                if (containerCheck(activeBook.name()) && clock.shouldFire()) {
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
                    debug("PLACE_ORDER: clicking slot 12 to confirm price, book=" + activeBook);
                    bazaarMonitor.add(activeBook, inventoryScanner.getUnitPrice(12), false);
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm")) clock.start(1000);
                if (containerCheck("Confirm") && clock.shouldFire()) {
                    debug("PLACE_ORDER: confirming buy order for " + activeBook);
                    InventoryUtils.clickSlot(13, false);
                    if (editIfStateBook(activeBook, BookState.SUB_STORE, BookState.STORE)) {
                        state = State.IDLE;
                        return;
                    }
                    editStateBook(activeBook, BookState.BUY_ORDER);
                    state = State.IDLE;

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

                    Book bookToHandle = firstBookInState(BookState.OUTBID);

                    if (bookToHandle == null) {
                        minecraft.player.closeContainer();
                        state = State.IDLE;
                        return;
                    }


                    List<Integer> slots = inventoryScanner.findContainer("BUY " + bookToHandle.getRomanLevel(bookToHandle.level()));
                    debug("OUTBID: found " + slots.size() + " slots for " + bookToHandle);

                    if (slots.isEmpty()) {

                        if (task.get(bookToHandle).shouldStore()) {
                            editStateBook(bookToHandle, BookState.SUB_STORE);
                        } else {
                            editStateBook(bookToHandle, BookState.SELECTED);
                        }

                        if (!task.get(bookToHandle).isCompleted()) return;

                        editStateBook(bookToHandle, BookState.ANVIL);

                    }


                    if (!slots.isEmpty()) {
                        int amount = inventoryScanner.checkOrder(slots.getFirst());
                        debug("OUTBID: order amount=" + amount + ", clicking slot " + slots.getFirst());
                        InventoryUtils.clickSlot(slots.getFirst(), false);
                        if (amount == 0) {
                            debug("OUTBID: amount=0, returning early");
                            return;
                        }

                        task.get(bookToHandle).addInInventory(amount);
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
                    Book bookToHandle = firstBookInState(BookState.STORE);

                    if (bookToHandle == null) {
                        minecraft.player.closeContainer();
                        state = State.IDLE;
                        return;
                    }

                    List<Integer> slots = new ArrayList<>();
                    slots.addAll(inventoryScanner.findLoreInv(bookToHandle.getRomanLevel(bookToHandle.level())));
                    if (!slots.isEmpty()) {
                        InventoryUtils.clickSlot(slots.getFirst(), true);
                        debug("STORE: storing " + bookToHandle.name() + " at slot " + slots.getFirst());
                        task.get(bookToHandle).addInInventory(-1);
                        task.get(bookToHandle).addInEnderChest(1);
                    }
                    if (slots.isEmpty()) {
                        editStateBook(bookToHandle, BookState.BUY_ORDER);
                        debug("STORE: slot is empty adding book to " + "BUY_ORDER");
                    }
                }
            }

            case ANVIL -> {
                if (!containerCheck("Ender Chest")) clock.start(1000);
                if (!containerCheck("Ender Chest") && clock.shouldFire()) {
                    debug("ANVIL: no ender chest, opening it");
                    openEnderChest();
                }

                if (containerCheck("Ender Chest")) clock.start(1000);
                if (containerCheck("Ender Chest") && clock.shouldFire()) {
                    List<Integer> slots = new ArrayList<>();
                    Book bookToHandle = firstBookInState(BookState.ANVIL);

                    if (bookToHandle == null) {
                        minecraft.player.closeContainer();
                        state = State.COMBINE;
                        return;
                    }

                    slots.addAll(inventoryScanner.findLoreContainer(bookToHandle.getRomanLevel(bookToHandle.level())));

                    debug("ANVIL: found " + slots.size() + " book slots in ender chest");
                    if (slots.isEmpty()) {
                        editStateBook(bookToHandle, BookState.COMBINE);
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
                    Book bookToHandle = firstBookInState(BookState.COMBINE);

                    if (bookToHandle == null) {
                        state = State.SELL;
                        minecraft.player.closeContainer();
                        return;
                    }

                    List<Integer> stageOneBookList   = inventoryScanner.findLoreInv(bookToHandle.getRomanLevel(1));
                    List<Integer> stageTwoBookList   = inventoryScanner.findLoreInv(bookToHandle.getRomanLevel(2));
                    List<Integer> stageThreeBookList = inventoryScanner.findLoreInv(bookToHandle.getRomanLevel(3));
                    List<Integer> stageFourBookList  = inventoryScanner.findLoreInv(bookToHandle.getRomanLevel(4));
                    List<Integer> stageFiveBookList  = inventoryScanner.findLoreInv(bookToHandle.getRomanLevel(5));

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
                        editStateBook(bookToHandle, BookState.SELL);
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
                List<Book> bookList = (booksInState(BookState.SELL));
                if (bookList.isEmpty()) {
                    debug("SELL: bookstoSell empty, switching to IDLE");
                    state = State.FETCHING;
                    return;
                }
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

                    for (Book book : bookList) {
                        slots.addAll(inventoryScanner.findContainer("SELL " + book.getRomanLevel(5)));
                    }
                    debug("SELL: found " + slots.size() + " sell slots");

                    if (!slots.isEmpty()) {
                        debug("SELL: clicking sell slot " + slots.getFirst());
                        InventoryUtils.clickSlot(slots.getFirst(), false);
                    }
                    if (slots.isEmpty()) {
                        debug("SELL: no slots found, clicking on: " + bookList.getFirst().name());
                        List<Integer> slot = inventoryScanner.findLoreInv(bookList.getFirst().getRomanLevel(bookList.getFirst().sellLevel()));
                        if (slot.isEmpty()) {
                            bookList.removeFirst();
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

                if (!bookList.isEmpty() && containerCheck(bookList.getFirst().name())) clock.start(500);
                if (!bookList.isEmpty() && containerCheck(bookList.getFirst().name()) && clock.shouldFire()) {
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
                    debug("SELL: confirm prompt, clicking slot 13 and removing " + bookList.getFirst() + " from sell list");
                    InventoryUtils.clickSlot(13, false);
                    removeDuplicateBooks(task);
                    if (task.containsKey(bookList.getFirst())) task.remove(bookList.getFirst());
                    bookList.removeFirst();

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

    private List<Book> booksInState(BookState target) {
        List<Book> result = new ArrayList<>();
        for (Map.Entry<Book, Task> entry : task.entrySet()) {
            if (entry.getValue().getBookState() == target) result.add(entry.getKey());
        }
        return result;
    }

    private boolean editIfStateBook(Book book, BookState check, BookState target) {
        if (task.get(book).getBookState() != check) return false;
        task.get(book).setBookState(target);
        return true;
    }

    private void editStateBook(Book book, BookState target) {
        task.get(book).setBookState(target);
    }

    private Book firstBookInState(BookState target) {
        for (Map.Entry<Book, Task> entry : task.entrySet()) {
            if (entry.getValue().getBookState() == target) return entry.getKey();
        }
        return null;
    }

    private void removeDuplicateBooks(Map<Book, Task> tasks) {
        Map<String, Integer> counts = new HashMap<>();
        List<Book> stateBooks = new ArrayList<>();

        stateBooks.addAll(booksInState(BookState.SELL));

        for (Book book : stateBooks) {
            counts.merge(book.name(), 1, Integer::sum);
        }

        tasks.entrySet().removeIf(entry ->
                counts.get(entry.getKey().name()) > 1
        );
    }

    private void processData() {
        if (flipItemsList.isEmpty()) return;
        System.out.println("[BazaarFlipper] processData: item check passed");
        double purse = scoreboardUtils.getPurse();
        System.out.println("[BazaarFlipper] processData: purse = " + purse);

        for (FlipItem flipItem : flipItemsList) {
            if (purse < flipItem.totalCost()) continue;
            if (task.containsKey(flipItem.book())) continue;
            purse -= flipItem.totalCost();
            System.out.println("[BazaarFlipper] processData: new purse = " + purse);
            task.put(flipItem.book(), new Task(flipItem.book().getQtyAmount(flipItem.book().level())));
            System.out.println("[BazaarFlipper] processData: new task created size:" + task.size());
        }
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
        String amountToOrder = String.valueOf(task.get(activeBook).getAmountToOrder());
        if (minecraft.screen instanceof AbstractSignEditScreen signScreen) {
            System.out.println("[BazaarFlipper] handleSign: writing amount=" + amountToOrder + " for book=" + activeBook);
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
        private BookState bookState = BookState.SELECTED;
        private int amountToOrder;
        private int inEnderChest;
        private int inInventory;

        private Task(int amountToOrder) {
            this.amountToOrder = amountToOrder;
        }

        private BookState getBookState() {
            return bookState;
        }

        private void setBookState(BookState bookState) {
            this.bookState = bookState;
        }

        private void addInEnderChest(int inEnderChest) {
            this.inEnderChest += inEnderChest;
        }

        private void addInInventory(int inInventory) {
            this.inInventory += inInventory;
        }

        private int getAmountToOrder() {
            return amountToOrder - (inEnderChest + inInventory);
        }

        private boolean shouldCheckEnderChest() {
            return inEnderChest > 0;
        }

        private boolean isCompleted() { return getAmountToOrder() == 0; }

        private boolean shouldStore() { return inInventory > 0; }
    }
}