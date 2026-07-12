package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
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
import java.util.SplittableRandom;


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
    private SplittableRandom splittableRandom = new SplittableRandom();
    private List<String> sellOrderName = new ArrayList<>();
    private boolean notEnoughCash = false;
    private boolean isInventoryFull = false;
    private boolean didRemoveOrder = false;



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

    public BazaarFlipper() {
        ChatHook.onMessage("filled", this::handleFilledMessage);
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

        bazaarMonitor.stop();
        isInventoryFull = false;
        didRemoveOrder = false;

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
                bazaarMonitor.start();
            }

            case IDLE -> {

                if (notEnoughCash) {
                    if (!task.isEmpty()) return;

                    clock.start(60000);
                    if (clock.shouldFire()) {
                        state = State.REPLACE_SELL;
                    }

                    return;

                }

                Book outbidBook = firstBookInState(BookState.OUTBID);
                if (outbidBook != null && !isInventoryFull) {
                    state = State.OUTBID;
                    didRemoveOrder = false;
                    return;
                }

                Book selectedBook = firstBookInState(BookState.SELECTED);
                if (selectedBook != null) {
                    activeBook = selectedBook;
                    state = State.BAZAAR_NAVIGATION;
                    return;
                }

                Book bookToStore = firstBookInState(BookState.STORE);
                if (bookToStore != null) {
                    state = State.STORE;
                    isInventoryFull = false;
                    return;
                }



                List<Book> booksToAnvil = booksInState(BookState.ANVIL);
                if (!booksToAnvil.isEmpty()) {
                    isInventoryFull = false;
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

                clock.start(GoofyConfig.INSTANCE.outbidRefreshDelay);
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
                if (!isContainerOpen()) clock.start(randomizer());
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("BAZAAR_NAVIGATION: no container open, opening bazaar for " + activeBook.name());
                    openBazaar(activeBook.name().replace("Ultimate", ""));
                }

                if (containerCheck("Bazaar")) clock.start(randomizer());
                if (containerCheck("Bazaar") && clock.shouldFire()) {
                    List<Integer> slots = inventoryScanner.findContainer(activeBook.getRomanLevel(activeBook.level()));
                    debug("BAZAAR_NAVIGATION: Bazaar open, clicking slot " + slots + " for " + activeBook.getRomanLevel(activeBook.level()));
                    InventoryUtils.clickSlot(slots.getFirst(), false);
                }

                if (containerCheck(activeBook.name())) clock.start(randomizer());
                if (containerCheck(activeBook.name()) && clock.shouldFire()) {
                    debug("BAZAAR_NAVIGATION: book container open, clicking slot 15");
                    InventoryUtils.clickSlot(15, false);
                }

                if (containerCheck("How many do you want")) clock.start(randomizer());
                if (containerCheck("How many do you want") && clock.shouldFire()) {
                    debug("BAZAAR_NAVIGATION: qty prompt open, clicking slot 16");
                    InventoryUtils.clickSlot(16, false);
                }
                if (minecraft.screen instanceof SignEditScreen) clock.start(randomizer());
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
                if (containerCheck("How much do you want to pay")) clock.start(randomizer());
                if (containerCheck("How much do you want to pay") && clock.shouldFire()) {
                    debug("PLACE_ORDER: clicking slot 12 to confirm price, book=" + activeBook);
                    bazaarMonitor.add(activeBook, inventoryScanner.getUnitPrice(12), false);
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm")) clock.start(randomizer());
                if (containerCheck("Confirm") && clock.shouldFire()) {
                    debug("PLACE_ORDER: confirming buy order for " + activeBook);
                    InventoryUtils.clickSlot(13, false);
                    if (shouldStore(activeBook)) {
                        editStateBook(activeBook, BookState.STORE);
                        state = State.IDLE;
                        return;
                    }
                    editStateBook(activeBook, BookState.BUY_ORDER);
                    state = State.IDLE;

                }
            }

            case OUTBID -> {
                if (!isContainerOpen()) clock.start(randomizer());
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("OUTBID: no container, opening bazaar for Wise");
                    openBazaar("Wise");
                }

                if (containerCheck("Wise")) clock.start(randomizer());
                if (containerCheck("Wise") && clock.shouldFire()) {
                    debug("OUTBID: Wise open, clicking slot 50");
                    InventoryUtils.clickSlot(50, false);
                }

                if (containerCheck("Bazaar")) clock.start(randomizer());
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
                        if (!task.get(bookToHandle).isCompleted() && !didRemoveOrder) return;
                        editStateBook(bookToHandle, task.get(bookToHandle).isCompleted() ? BookState.ANVIL : BookState.SELECTED);
                        didRemoveOrder = false;

                        return;

                    }


                    if (!slots.isEmpty()) {
                        int amount = inventoryScanner.checkOrder(slots.getFirst());
                        debug("OUTBID: order amount=" + amount + ", clicking slot " + slots.getFirst());
                        if (amount > inventoryScanner.getEmptyInventorySlots()) {
                            task.get(bookToHandle).setEarlyAction(true);
                            editStateBook(bookToHandle, BookState.STORE);
                            state = State.STORE;
                            isInventoryFull = true;
                            minecraft.player.closeContainer();
                            return;
                        }
                        InventoryUtils.clickSlot(slots.getFirst(), false);
                        if (amount == 0) {
                            debug("OUTBID: amount=0, returning early");
                            return;
                        }


                        task.get(bookToHandle).addInInventory(amount);
                    }
                }

                if (containerCheck("Order")) clock.start(randomizer());
                if (containerCheck("Order") && clock.shouldFire()) {
                    didRemoveOrder = true;
                    debug("OUTBID: Order screen open, clicking slot 11");
                    InventoryUtils.clickSlot(11, false);
                }
            }

            case STORE -> {
                if (!isContainerOpen()) clock.start(randomizer());
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("STORE: no container, opening ender chest");
                    openEnderChest();
                }

                if (containerCheck("Ender Chest")) clock.start(randomizer());
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
                        if (task.get(bookToHandle).isEarlyAction()) {
                            editStateBook(bookToHandle, BookState.OUTBID);
                            task.get(bookToHandle).setEarlyAction(false);
                            return;
                        }

                        editStateBook(bookToHandle, BookState.BUY_ORDER);
                        debug("STORE: slot is empty adding book to " + "BUY_ORDER");
                    }
                }
            }

            case ANVIL -> {
                if (!containerCheck("Ender Chest")) clock.start(randomizer());
                if (!containerCheck("Ender Chest") && clock.shouldFire()) {
                    debug("ANVIL: no ender chest, opening it");
                    openEnderChest();
                }

                if (containerCheck("Ender Chest")) clock.start(randomizer());
                if (containerCheck("Ender Chest") && clock.shouldFire()) {
                    List<Integer> slots = new ArrayList<>();
                    Book bookToHandle = firstBookInState(BookState.ANVIL);

                    if (bookToHandle == null) {
                        minecraft.player.closeContainer();
                        state = State.COMBINE;
                        return;
                    }

                    slots.addAll(inventoryScanner.findLoreContainer(bookToHandle.getRomanLevel(bookToHandle.level())));

                    if (slots.size() > inventoryScanner.getEmptyInventorySlots()) {
                        state = State.COMBINE;
                        minecraft.player.closeContainer();
                        return;
                    }

                    debug("ANVIL: found " + slots.size() + " book slots in ender chest");
                    if (slots.isEmpty()) {
                        editStateBook(bookToHandle, BookState.COMBINE);
                        return;
                    }
                    debug("ANVIL: pulling slot " + slots.getFirst() + " from ender chest");
                    InventoryUtils.clickSlot(slots.getFirst(), true);
                    task.get(bookToHandle).addInInventory(1);
                    task.get(bookToHandle).addInEnderChest(-1);
                }
            }

            case COMBINE -> {

                Book bookToHandle = firstBookInState(BookState.COMBINE);

                if (bookToHandle == null) {
                    state = State.SELL;
                    minecraft.player.closeContainer();
                    return;
                }

                int level = task.get(bookToHandle).getBooktoCombine();


                if (!containerCheck("Anvil")) clock.start(randomizer());
                if (!containerCheck("Anvil") && clock.shouldFire()) {
                    debug("COMBINE: no anvil open, opening it");
                    openAnvil();
                }

                if (containerCheck("Anvil") && counter < 2) clock.start(randomizer());
                if (containerCheck("Anvil") && counter < 2 && clock.shouldFire()) {
                    if (level == 0) {
                        editStateBook(bookToHandle, BookState.SELL);
                        return;
                    }

                    List<Integer> book = inventoryScanner.findLoreInv(bookToHandle.getRomanLevel(level));

                    if (!book.isEmpty()) {
                        counter++;
                        InventoryUtils.clickSlot(book.getFirst(), true);
                    }
                }

                if (counter == 2) clock.start(randomizer());
                if (counter == 2 && clock.shouldFire()) {
                    List<Integer> books = inventoryScanner.findLoreInv(bookToHandle.getRomanLevel(level + 1));
                    int counter = books.size();
                    debug("COMBINE: counter==2, clicking anvil output slot 22, clickedOnce=" + clickedOnce);
                    InventoryUtils.clickSlot(22, false);
                    if (clickedOnce) {
                        debug("COMBINE: second click done, resetting counter and clickedOnce");
                        counter = 0;
                        clickedOnce = false;
                        if (inventoryScanner.locate(bookToHandle.getRomanLevel(level + 1)).size() > counter) {
                            task.get(bookToHandle).increaseCounter();
                        }
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
                if (!isContainerOpen()) clock.start(randomizer());
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("SELL: no container, opening bazaar for tomato");
                    openBazaar("tomato");
                }

                if (containerCheck("tomato")) clock.start(randomizer());
                if (containerCheck("tomato") && clock.shouldFire()) {
                    debug("SELL: tomato bazaar open, clicking slot 50");
                    InventoryUtils.clickSlot(50, false);
                }

                if (containerCheck("Bazaar")) clock.start(randomizer());
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

                if (containerCheck("Order")) clock.start(randomizer());
                if (containerCheck("Order") && clock.shouldFire()) {
                    debug("SELL: Order screen, clicking slot 13");
                    InventoryUtils.clickSlot(13, false);
                }

                if (!bookList.isEmpty() && containerCheck(bookList.getFirst().name())) clock.start(randomizer());
                if (!bookList.isEmpty() && containerCheck(bookList.getFirst().name()) && clock.shouldFire()) {
                    debug("SELL: book screen open, clicking slot 16");
                    InventoryUtils.clickSlot(16, false);
                }

                if (containerCheck("At what price are you selling")) clock.start(randomizer());
                if (containerCheck("At what price are you selling") && clock.shouldFire()) {
                    debug("SELL: price prompt, clicking slot 12");
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm")) clock.start(randomizer());
                if (containerCheck("Confirm") && clock.shouldFire()) {
                    debug("SELL: confirm prompt, clicking slot 13 and removing " + bookList.getFirst() + " from sell list");
                    InventoryUtils.clickSlot(13, false);
                    removeDuplicateBooks(task);
                    if (task.containsKey(bookList.getFirst())) task.remove(bookList.getFirst());
                    bookList.removeFirst();

                }
            }

            case REPLACE_SELL -> {
                if (!isContainerOpen()) clock.start(randomizer());
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("SELL: no container, opening bazaar for tomato");
                    openBazaar("tomato");
                }

                if (containerCheck("tomato")) clock.start(randomizer());
                if (containerCheck("tomato") && clock.shouldFire()) {
                    debug("SELL: tomato bazaar open, clicking slot 50");
                    InventoryUtils.clickSlot(50, false);
                }

                if (containerCheck("Bazaar")) clock.start(randomizer());
                if (containerCheck("Bazaar") && clock.shouldFire()) {
                    List<Integer> slots = new ArrayList<>();

                    slots.addAll(inventoryScanner.getSellOrder());
                    if (slots.isEmpty()) {
                        List<Integer> slot = new ArrayList<>();
                        for (String string : sellOrderName) {
                            slot.addAll(inventoryScanner.findLoreInv(string));
                        }

                        if (!slot.isEmpty()) {
                            InventoryUtils.clickSlot(slot.getFirst(), false);
                            return;
                        }

                        state = State.FETCHING;
                        minecraft.player.closeContainer();
                        return;

                    }

                    sellOrderName.add(inventoryScanner.getName(slots.getFirst()).replace("SELL ", ""));

                    InventoryUtils.clickSlot(slots.getFirst(), false);

                }

                if (containerCheck("Order")) clock.start(randomizer());
                if (containerCheck("Order") && clock.shouldFire()) {
                    debug("SELL: Order screen, clicking slot 13");
                    InventoryUtils.clickSlot(13, false);
                }

                if (!sellOrderName.isEmpty() && containerCheck(sellOrderName.getFirst())) clock.start(randomizer());
                if (!sellOrderName.isEmpty() && containerCheck(sellOrderName.getFirst()) && clock.shouldFire()) {
                    debug("SELL: book screen open, clicking slot 16");
                    InventoryUtils.clickSlot(16, false);
                }

                if (containerCheck("At what price are you selling")) clock.start(randomizer());
                if (containerCheck("At what price are you selling") && clock.shouldFire()) {
                    debug("SELL: price prompt, clicking slot 12");
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm")) clock.start(randomizer());
                if (containerCheck("Confirm") && clock.shouldFire()) {
                    debug("SELL: confirm prompt, clicking slot 13 and removing " + sellOrderName.getFirst() + " from sell list");
                    InventoryUtils.clickSlot(13, false);
                    sellOrderName.clear();
                    state = State.FETCHING;

                }




            }
        }
    }


    private boolean shouldStore(Book book) {
        return task.get(book).shouldStore();
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

    private List<Book> booksInState(BookState target, BookState target2) {
        List<Book> result = new ArrayList<>();
        for (Map.Entry<Book, Task> entry : task.entrySet()) {
            if (entry.getValue().getBookState() == target) result.add(entry.getKey());
        }

        for (Map.Entry<Book, Task> entry : task.entrySet()) {
            if (entry.getValue().getBookState() == target2) result.add(entry.getKey());
        }
        return result;
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
                counts.getOrDefault(entry.getKey().name(), 0) > 1
        );
    }

    private void processData() {
        boolean wasTasksCreated = false;
        if (flipItemsList.isEmpty()) return;
        System.out.println("[BazaarFlipper] processData: item check passed");
        double purse = scoreboardUtils.getPurse();
        System.out.println("[BazaarFlipper] processData: purse = " + purse);

        for (FlipItem flipItem : flipItemsList) {
            if (purse < flipItem.totalCost()) continue;
            if (task.containsKey(flipItem.book())) continue;
            wasTasksCreated = true;
            purse -= flipItem.totalCost();
            System.out.println("[BazaarFlipper] processData: new purse = " + purse);
            task.put(flipItem.book(), new Task(flipItem.book().getQtyAmount(flipItem.book().level())));
            System.out.println("[BazaarFlipper] processData: new task created size:" + task.size());
        }

        notEnoughCash = wasTasksCreated ? false : true;
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

    private void handleFilledMessage(String string) {
        List<Book> booksInState = new ArrayList<>();
        booksInState.addAll(booksInState(BookState.BUY_ORDER, BookState.STORE));

        String stripped = string
                .replace("[Bazaar] Your Buy Order for ", "")
                .replace(" was filled!", "");

        stripped = stripped.substring(stripped.indexOf(' ') + 1);

        System.out.println(stripped);

        for (Book book : booksInState) {
            if (!stripped.equals(book.getRomanLevel(book.level()))) continue;
            editStateBook(book, BookState.OUTBID);
            bazaarMonitor.finish(book, false);
        }
    }

    private int randomizer() {
        int result = splittableRandom.nextInt(GoofyConfig.INSTANCE.minActionDelay, GoofyConfig.INSTANCE.maxActionDelay);

        if (result > 50) {
            return result;
        }

        return 500;
    }


    private class Task {
        private BookState bookState = BookState.SELECTED;
        private int amountToOrder;
        private int inEnderChest;
        private int inInventory;
        private boolean earlyAction = false;
        private int counter = 0;
        public boolean isEarlyAction() {
            return earlyAction;
        }

        public void setEarlyAction(boolean earlyAction) {
            this.earlyAction = earlyAction;
        }

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

        private int getBooktoCombine() {
            int result = 0;

            if (counter == inInventory / 2) {
                inInventory /= 2;
                counter = 0;
            }

            if (inInventory > 1) {
                int divisions = Integer.numberOfTrailingZeros(inInventory);
                 result = 5 - divisions;
            }

            return result;
        }

        private void increaseCounter() {
            counter++;
        }
    }
}