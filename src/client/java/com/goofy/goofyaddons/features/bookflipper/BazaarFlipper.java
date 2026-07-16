package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarMonitor;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipCalculator;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipItem;
import com.goofy.goofyaddons.utils.*;

import java.lang.reflect.Field;
import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import java.util.SplittableRandom;


public class BazaarFlipper {
    private enum State {
        START,
        STARTUP_CHECK,
        IDLE,
        FETCHING,
        BAZAAR_NAVIGATION,
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
    private boolean claimedItems = false;
    private boolean didReceiveItems = false;
    public boolean debugMode = false;
    private boolean firstStartUp = false;
    private int counterBazaar = 0;


    private final Map<Book, Task> task = new LinkedHashMap<>();

    private void debug(String msg) {
        if (!debugMode) return;
        ChatUtils.debugMessage("[" + state + "] " + msg);
    }

    private void dumpTasks() {
        debug("----- TASK DUMP -----");
        for (Map.Entry<Book, Task> e : task.entrySet()) {
            Task t = e.getValue();
            debug(e.getKey().getRomanLevel(e.getKey().level())
                    + " state=" + t.getBookState()
                    + " remaining=" + t.getAmountToOrder()
                    + " inv=" + t.inInventory
                    + " ec=" + t.inEnderChest
                    + " early=" + t.earlyAction);
        }
        debug("---------------------");
    }

    public void start() {
        ChatUtils.clientMessage("BazaarFlipper: Started");
        if (minecraft.screen != null) {
            minecraft.player.closeContainer();
            debug("Container is open, closing");
        }
        firstStartUp = true;
        enabled = true;
        state = State.START;
    }

    public BazaarFlipper() {
        ChatHook.onMessage("filled", this::handleFilledMessage);
        ChatHook.onMessage("Claimed", this::handleClaimedMessage);
        bazaarMonitor.hook(this::handleOutbid);
    }

    public void stop() {

        ChatUtils.clientMessage("BazaarFlipper: Stopped");

        task.clear();
        enabled = false;
        state = State.IDLE;
        lastState = null;
        flipItemsList.clear();
        activeBook = null;
        counter = 0;
        clickedOnce = false;
        clock.stop();
        bazaarMonitor.stop();
        bazaarMonitor.reset();
        isInventoryFull = false;
        didRemoveOrder = false;

    }

    public void onTick() {

        if (!enabled) return;

        bazaarMonitor.onTick();
        lastStateCheck();

        switch (state) {
            case START -> {
                debug("[START] Refreshing flipCalculator");
                flipCalculator.Refresh();
                ChatUtils.clientMessage("BazaarFlipper: [START] Switching to FETCHING");
                state = State.FETCHING;
                bazaarMonitor.start();
            }

            case STARTUP_CHECK -> {
                if (!isContainerOpen()) clock.start(randomizer());
                if (!isContainerOpen() && clock.shouldFire()) {
                    openEnderChest();
                    ChatUtils.clientMessage("BazaarFlipper: [STARTUP_CHECK] Started checks");
                }

                if (containerCheck("Ender")) clock.start(randomizer());
                if (containerCheck("Ender") && clock.shouldFire()) {
                    List<Book> bookList = new ArrayList<>();
                    bookList.addAll(booksInState(BookState.SELECTED));

                    for (Book book : bookList) {
                        debug("BazaarFlipper: [STARTUP_CHECK] book: " + book.name());
                        List<Integer> size = inventoryScanner.findLoreContainer(book.getRomanLevel(book.level()));
                        debug("BazaarFlipper: [STARTUP_CHECK] Found book: " + book.name() + " Amount: " + size.size() + "In Container");
                        task.get(book).addInEnderChest(size.size());
                        size = inventoryScanner.findLoreInv(book.getRomanLevel(book.level()));
                        debug("BazaarFlipper: [STARTUP_CHECK] Found book: " + book.name() + " Amount: " + size.size() + "In Inventory");
                        task.get(book).addInInventory(size.size());



                        if (task.get(book).getAmountToOrder() == 0) {
                            editStateBook(book, BookState.ANVIL);
                            continue;
                        }

                        if (task.get(book).shouldStore()) {
                            editStateBook(book, BookState.STORE);
                            task.get(book).setEarlyStore(true);
                        }
                    }

                    debug("BazaarFlipper: [STARTUP_CHECK] Switching to IDLE, firstStartup = false");
                    firstStartUp = false;
                    state = State.IDLE;
                    minecraft.player.closeContainer();

                }
            }

            case IDLE -> {

                if (firstStartUp) {
                    debug("BazaarFlipper: [IDLE] switching to Startup checks");
                    state = State.STARTUP_CHECK;
                    return;
                }

                if (notEnoughCash) {
                    debug("notEnoughCash is true");
                    if (!task.isEmpty()) {
                        debug("BazaarFlipper: [IDLE] task isn't empty");
                        notEnoughCash = false;
                        return;
                    }
                    debug("Starting clock");
                    clock.start(60000);
                    if (clock.shouldFire()) {
                        debug("1 Minute clock ended, switching to REPLACE_SELL");
                        state = State.REPLACE_SELL;
                    }
                    return;
                }

                Book outbidBook = firstBookInState(BookState.OUTBID);
                if (outbidBook != null && !isInventoryFull) {
                    debug("Found outbid books, switching to OUTBID");
                    state = State.OUTBID;
                    didRemoveOrder = false;
                    didReceiveItems = false;
                    claimedItems = false;
                    counterBazaar = 0;
                    return;
                }

                Book selectedBook = firstBookInState(BookState.SELECTED);
                if (selectedBook != null) {
                    debug("Found selected books, switching to BAZAAR_NAVIGATION");
                    activeBook = selectedBook;
                    debug("Active book set to: " + activeBook);
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
                    debug("no container open, opening bazaar for " + activeBook.name());
                    openBazaar(activeBook.name().replace("Ultimate", ""));
                }

                if (containerCheck("Bazaar")) clock.start(randomizer());
                if (containerCheck("Bazaar") && clock.shouldFire()) {
                    List<Integer> slots = inventoryScanner.findContainer(activeBook.getRomanLevel(activeBook.level()));
                    debug("Bazaar open, clicking slot " + slots + " for " + activeBook.getRomanLevel(activeBook.level()));
                    if (slots.isEmpty()) return;
                    InventoryUtils.clickSlot(slots.getFirst(), false);
                }

                if (containerCheck(activeBook.name())) clock.start(randomizer());
                if (containerCheck(activeBook.name()) && clock.shouldFire()) {
                    debug("book container open, clicking slot 15");
                    InventoryUtils.clickSlot(15, false);
                }

                if (containerCheck("How many do you want")) clock.start(randomizer());
                if (containerCheck("How many do you want") && clock.shouldFire()) {
                    debug("qty prompt open, clicking slot 16");
                    InventoryUtils.clickSlot(16, false);
                }
                if (minecraft.screen instanceof SignEditScreen) clock.start(randomizer());
                if (minecraft.screen instanceof SignEditScreen && clock.shouldFire()) {
                    debug("sign screen detected, handling sign");
                    handleSign();
                }

                if (containerCheck("How much do you want to pay")) clock.start(randomizer());
                if (containerCheck("How much do you want to pay") && clock.shouldFire()) {
                    debug("clicking slot 12 to confirm price, book=" + activeBook);
                    bazaarMonitor.add(activeBook, inventoryScanner.getUnitPrice(12), false);
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm")) clock.start(randomizer());
                if (containerCheck("Confirm") && clock.shouldFire()) {
                    debug("confirming buy order for " + activeBook);
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
                    debug("no container, opening bazaar for Wise");
                    openBazaar("Wise");
                }

                if (containerCheck("Wise")) clock.start(randomizer());
                if (containerCheck("Wise") && clock.shouldFire()) {
                    debug("Wise open, clicking slot 50");
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

                    if (claimedItems) {
                        if (didReceiveItems) {
                            claimedItems = false;
                            didReceiveItems = false;
                            return;
                        }
                        return;
                    }


                    List<Integer> slots = inventoryScanner.findContainer("BUY " + bookToHandle.getRomanLevel(bookToHandle.level()));
                    debug("found " + slots.size() + " slots for " + bookToHandle);

                    if (slots.isEmpty()) {
                        if (!task.get(bookToHandle).isCompleted() && !didRemoveOrder && counterBazaar < 3) {
                            counterBazaar++;
                            return;
                        }


                        editStateBook(bookToHandle, task.get(bookToHandle).isCompleted() ? BookState.ANVIL : BookState.SELECTED);
                        didRemoveOrder = false;
                        counterBazaar = 0;
                        return;

                    }


                    if (!slots.isEmpty()) {
                        int amount = inventoryScanner.checkOrder(slots.getFirst());
                        debug("order amount=" + amount + ", clicking slot " + slots.getFirst());
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
                            debug("amount=0, returning early");
                            return;
                        }

                        claimedItems = true;


                        task.get(bookToHandle).addInInventory(amount);
                    }
                }

                if (containerCheck("Order")) clock.start(randomizer());
                if (containerCheck("Order") && clock.shouldFire()) {
                    didRemoveOrder = true;
                    debug("Order screen open, clicking slot 11");
                    InventoryUtils.clickSlot(11, false);
                }
            }

            case STORE -> {
                if (!isContainerOpen()) clock.start(randomizer());
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("no container, opening ender chest");
                    openEnderChest();
                }

                if (containerCheck("Ender Chest")) clock.start(speedMode());
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
                        debug("storing " + bookToHandle.name() + " at slot " + slots.getFirst());
                        task.get(bookToHandle).addInInventory(-1);
                        task.get(bookToHandle).addInEnderChest(1);
                    }
                    if (slots.isEmpty()) {
                        if (task.get(bookToHandle).isEarlyAction()) {
                            editStateBook(bookToHandle, BookState.OUTBID);
                            task.get(bookToHandle).setEarlyAction(false);
                            return;
                        }

                        if (task.get(bookToHandle).isEarlyStore()) {
                            editStateBook(bookToHandle, BookState.SELECTED);
                            task.get(bookToHandle).setEarlyStore(false);
                            return;
                        }

                        editStateBook(bookToHandle, BookState.BUY_ORDER);
                        debug("slot is empty adding book to " + "BUY_ORDER");
                    }
                }
            }

            case ANVIL -> {
                if (!containerCheck("Ender Chest")) clock.start(randomizer());
                if (!containerCheck("Ender Chest") && clock.shouldFire()) {
                    debug("no ender chest, opening it");
                    openEnderChest();
                }

                if (containerCheck("Ender Chest")) clock.start(speedMode());
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

                    debug("found " + slots.size() + " book slots in ender chest");
                    if (slots.isEmpty()) {
                        editStateBook(bookToHandle, BookState.COMBINE);
                        return;
                    }
                    debug("pulling slot " + slots.getFirst() + " from ender chest");
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

                int level = 0;
                for (int i = bookToHandle.level(); i < bookToHandle.sellLevel(); i++) {
                    if (inventoryScanner.locate(bookToHandle.getRomanLevel(i)).size() >= 2) {
                        level = i;
                        break;
                    }
                }

                if (!containerCheck("Anvil")) clock.start(randomizer());
                if (!containerCheck("Anvil") && clock.shouldFire()) {
                    debug("no anvil open, opening it");
                    openAnvil();
                }

                if (containerCheck("Anvil") && counter < 2) clock.start(speedMode());
                if (containerCheck("Anvil") && counter < 2 && clock.shouldFire()) {
                    if (level == 0) {
                        editStateBook(bookToHandle, BookState.SELL);
                        return;
                    }


                    List<Integer> book = inventoryScanner.findLoreInv(bookToHandle.getRomanLevel(level));

                    if (!book.isEmpty()) {
                        if (inventoryScanner.findMisMatch(bookToHandle.getRomanLevel(level))) {
                            minecraft.player.closeContainer();
                            return;
                        }
                        counter++;
                        InventoryUtils.clickSlot(book.getFirst(), true);
                        return;
                    } else {
                        List<Integer> bookInContainer = inventoryScanner.findLoreContainer(bookToHandle.getRomanLevel(level));
                        if (bookInContainer.size() >= 2) counter++;
                    }
                }

                if (counter == 2) clock.start(speedMode());
                if (counter == 2 && clock.shouldFire()) {
                    debug("counter==2, clicking anvil output slot 22 with normal click");
                    InventoryUtils.clickSlot(22, false);
                    if (clickedOnce) {
                        clickedOnce = false;
                        counter = 0;
                        return;
                    }
                    clickedOnce = true;
                }
            }

            case SELL -> {
                List<Integer> slots = new ArrayList<>();
                List<Book> bookList = (booksInState(BookState.SELL));
                if (bookList.isEmpty()) {
                    debug("bookstoSell empty, switching to IDLE");
                    state = State.FETCHING;
                    return;
                }
                if (!isContainerOpen()) clock.start(randomizer());
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("no container, opening bazaar for tomato");
                    openBazaar("tomato");
                }

                if (containerCheck("tomato")) clock.start(randomizer());
                if (containerCheck("tomato") && clock.shouldFire()) {
                    debug("tomato bazaar open, clicking slot 50");
                    InventoryUtils.clickSlot(50, false);
                }

                if (containerCheck("Bazaar")) clock.start(randomizer());
                if (containerCheck("Bazaar") && clock.shouldFire()) {

                    for (Book book : bookList) {
                        slots.addAll(inventoryScanner.findContainer("SELL " + book.getRomanLevel(5)));
                    }
                    debug("found " + slots.size() + " sell slots");

                    if (!slots.isEmpty()) {
                        debug("clicking sell slot " + slots.getFirst());
                        InventoryUtils.clickSlot(slots.getFirst(), false);
                    }
                    if (slots.isEmpty()) {
                        debug("no slots found, clicking on: " + bookList.getFirst().name());
                        List<Integer> slot = inventoryScanner.findLoreInv(bookList.getFirst().getRomanLevel(bookList.getFirst().sellLevel()));
                        if (slot.isEmpty()) {
                            bookList.removeFirst();
                            debug("slot is empty, removed book from booksToSell and return" );
                            return;
                        }
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                    }
                }

                if (containerCheck("Order")) clock.start(randomizer());
                if (containerCheck("Order") && clock.shouldFire()) {
                    debug("Order screen, clicking slot 13");
                    InventoryUtils.clickSlot(13, false);
                }

                if (!bookList.isEmpty() && containerCheck(bookList.getFirst().name())) clock.start(randomizer());
                if (!bookList.isEmpty() && containerCheck(bookList.getFirst().name()) && clock.shouldFire()) {
                    debug("book screen open, clicking slot 16");
                    InventoryUtils.clickSlot(16, false);
                }

                if (containerCheck("At what price are you selling")) clock.start(randomizer());
                if (containerCheck("At what price are you selling") && clock.shouldFire()) {
                    debug("price prompt, clicking slot 12");
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm")) clock.start(randomizer());
                if (containerCheck("Confirm") && clock.shouldFire()) {
                    debug("confirm prompt, clicking slot 13 and removing " + bookList.getFirst() + " from sell list");
                    InventoryUtils.clickSlot(13, false);
                    removeDuplicateBooks(task);
                    if (task.containsKey(bookList.getFirst())) task.remove(bookList.getFirst());
                    bookList.removeFirst();

                }
            }

            case REPLACE_SELL -> {
                if (!isContainerOpen()) clock.start(randomizer());
                if (!isContainerOpen() && clock.shouldFire()) {
                    debug("no container, opening bazaar for tomato");
                    openBazaar("tomato");
                }

                if (containerCheck("tomato")) clock.start(randomizer());
                if (containerCheck("tomato") && clock.shouldFire()) {
                    debug("tomato bazaar open, clicking slot 50");
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
                    debug("Order screen, clicking slot 13");
                    InventoryUtils.clickSlot(13, false);
                }

                if (!sellOrderName.isEmpty() && containerCheck(sellOrderName.getFirst())) clock.start(randomizer());
                if (!sellOrderName.isEmpty() && containerCheck(sellOrderName.getFirst()) && clock.shouldFire()) {
                    debug("book screen open, clicking slot 16");
                    InventoryUtils.clickSlot(16, false);
                }

                if (containerCheck("At what price are you selling")) clock.start(randomizer());
                if (containerCheck("At what price are you selling") && clock.shouldFire()) {
                    debug("price prompt, clicking slot 12");
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerCheck("Confirm")) clock.start(randomizer());
                if (containerCheck("Confirm") && clock.shouldFire()) {
                    debug("confirm prompt, clicking slot 13 and removing " + sellOrderName.getFirst() + " from sell list");
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
            debug("state changed: " + lastState + " -> " + state);
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
        Task t = task.get(book);
        if (t == null) {
            debug("Attempted state change for missing task: " + book);
            return;
        }
        BookState old = t.getBookState();
        t.setBookState(target);
        debug("Book state changed: " + book + " | " + old + " -> " + target
                + " remaining=" + t.getAmountToOrder()
                + " inv=" + t.inInventory
                + " ec=" + t.inEnderChest);
        dumpTasks();
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
        if (flipItemsList.isEmpty()) return;
        debug("item check passed");
        double purse = scoreboardUtils.getPurse();
        debug("purse = " + purse);

        double cost = flipItemsList.stream().mapToDouble(FlipItem::totalCost).min().orElse(-1);

        if (cost != -1) {
            if (cost > purse) {
                notEnoughCash = true;
            }
        }

        for (FlipItem flipItem : flipItemsList) {
            debug("Checking Flipitem " + flipItem.book().name());
            if (purse < flipItem.totalCost()) continue;
            debug("User has enough money " + flipItem.book().name());
            if (task.containsKey(flipItem.book())) continue;
            debug("Not in task " + flipItem.book().name());
            purse -= flipItem.totalCost();
            debug("new purse = " + purse);
            task.put(flipItem.book(), new Task(flipItem.book().getQtyAmount(flipItem.book().level())));
            debug("new task created size:" + task.size());
        }

    }

    private void openBazaar(String name) {
        if (containerCheck("bazaar")) return;
        debug("sending command for " + name);
        minecraft.player.connection.sendCommand("bz " + name);
    }

    private void openAnvil() {
        if (containerCheck("Anvil")) return;
        debug("openAnvil");
        minecraft.player.connection.sendCommand("Anvil");
    }

    private void openEnderChest() {
        if (containerCheck("Ender Chest")) return;
        debug("openEnderChest");
        minecraft.player.connection.sendCommand("ec");
    }

    private void handleSign() {
        String amountToOrder = String.valueOf(task.get(activeBook).getAmountToOrder());
        if (minecraft.screen instanceof AbstractSignEditScreen signScreen) {
            debug("writing amount=" + amountToOrder + " for book=" + activeBook);
            try {
                Field messagesField = AbstractSignEditScreen.class.getDeclaredField("messages");
                messagesField.setAccessible(true);
                String[] messages = (String[]) messagesField.get(signScreen);
                messages[0] = amountToOrder;
                minecraft.setScreen(null);
            } catch (Exception e) {
                debug("reflection failed - " + e.getMessage());
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

    private void handleClaimedMessage(String string) {
        if (!didReceiveItems) {
            didReceiveItems = true;
        }
    }

    private void handleOutbid(Book book) {
        debug("Found outbid:" + book.getRomanLevel(book.level()));
        editStateBook(book, BookState.OUTBID);
    }


    private void handleFilledMessage(String string) {
        List<Book> booksInState = new ArrayList<>();
        booksInState.addAll(booksInState(BookState.BUY_ORDER, BookState.STORE));

        String stripped = string
                .replace("[Bazaar] Your Buy Order for ", "")
                .replace(" was filled!", "");

        stripped = stripped.substring(stripped.indexOf(' ') + 1);

        debug("stripped=" + stripped);

        for (Book book : booksInState) {
            if (!stripped.equals(book.getRomanLevel(book.level()))) continue;
            editStateBook(book, BookState.OUTBID);
            bazaarMonitor.finish(book);
        }
    }

    private int randomizer() {
        int result = splittableRandom.nextInt(GoofyConfig.INSTANCE.minActionDelay, GoofyConfig.INSTANCE.maxActionDelay);

        if (result > 50) {
            return result;
        }

        return 500;
    }


    private int speedMode() {
        if (GoofyConfig.INSTANCE.speedMode) return GoofyConfig.INSTANCE.speedModeDelay;
        return randomizer();
    }


    private class Task {
        private BookState bookState = BookState.SELECTED;
        private int amountToOrder;
        private int inEnderChest;
        private int inInventory;
        private boolean earlyAction = false;
        private boolean earlyStore = false;

        private boolean isEarlyAction() {
            return earlyAction;
        }

        private void setEarlyAction(boolean earlyAction) {
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

        private boolean isEarlyStore() {
            return earlyStore;
        }

        private void setEarlyStore(boolean earlyStore) {
            this.earlyStore = earlyStore;
        }
    }
}