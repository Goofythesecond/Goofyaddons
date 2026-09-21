package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.Feature;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarMonitor;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.BookList;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipCalculator;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipItem;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.goofy.goofyaddons.utils.ChatUtils;
import com.goofy.goofyaddons.utils.Clock;
import com.goofy.goofyaddons.utils.InventoryScanner;
import com.goofy.goofyaddons.utils.InventoryUtils;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

public class BazaarFlipper implements Feature {
    private enum State {
        START,
        FETCHING,
        STARTUP_CHECK,
        STARTUP_BAZAAR_CHECK,
        IDLE,
        OUTBID,
        BAZAAR_NAVIGATION,
        STORE,
        ANVIL,
        COMBINE,
        SELL,
        REPLACE_SELL,

    }

    private State state = State.START;
    private State lastState = null;
    private Clock clock = new Clock();
    private FlipCalculator flipCalculator = new FlipCalculator();
    private ScoreboardUtils scoreboardUtils = new ScoreboardUtils();
    private SplittableRandom splittableRandom = new SplittableRandom();
    private InventoryScanner inventoryScanner = new InventoryScanner();
    private BazaarMonitor bazaarMonitor = new BazaarMonitor();
    private boolean running = false;
    private List<FlipItem> flipItemList = new ArrayList<>();
    private boolean notEnoughCash = false;
    private boolean needToStoreExcessBook = false;
    private boolean usingSecondPage = false;
    private boolean isStartUpCheckCompleted = false;
    private boolean inventoryIsFull = false;
    private Minecraft minecraft = Minecraft.getInstance();
    private boolean checkedFirstPage = false;
    private int store_Counter = -1;
    private int store_Counter_2 = -1;
    private int combine_Counter_2 = 0;
    private boolean attemptedToClaim = false;
    private boolean didReceiveItems = false;
    private int anvil_Counter = -1;
    private int anvil_Counter_2 = -1;
    private int combine_Counter = -1;
    private boolean overFlowProt = false;

    private Task activeTask = null;
    private Set<Task> listOfTaskToChange = new HashSet<>();
    private List<BookList> bookLists = new ArrayList<>();
    private List<Task> taskList = new ArrayList<>();

    private static final Map<Task.BookState, Integer> STATE_PRIORITY = Map.of(
            Task.BookState.REPLACE_SELL, 1,
            Task.BookState.BAZAAR_ORDER_CHECK, 2,
            Task.BookState.ANVIL, 3,
            Task.BookState.COMBINE, 4,
            Task.BookState.SELL, 5,
            Task.BookState.STORE, 6,
            Task.BookState.SELECTED, 7,
            Task.BookState.OUTBID, 8
    );


    public BazaarFlipper() {
        ChatHook.onMessage("filled", this::handleFilledMessage);
        ChatHook.onMessage("Claimed", this::handleClaimedMessage);
        bazaarMonitor.hook(this::handleOutbid);
    }

    @Override
    public String name() {
        return "BazaarFlipper";
    }

    @Override
    public void stop() {
        debug("[BazaarFlipper] stop: resetting state, was " + state + " with " + taskList.size() + " active task(s)");
        combine_Counter_2 = 0;
        store_Counter = -1;
        store_Counter_2 = -1;
        anvil_Counter = -1;
        anvil_Counter_2 = -1;
        combine_Counter = -1;
        checkedFirstPage = false;
        isStartUpCheckCompleted = false;
        didReceiveItems = false;
        state = State.START;
        taskList.clear();
        listOfTaskToChange.clear();
        running = false;
        bazaarMonitor.stop();
        bazaarMonitor.reset();
        ChatUtils.clientMessage("BazaarFlipper: Stopped");
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void onTick() {
        if (!running) return;
        handleTaskStateChange();
        lastStateCheck();
        bazaarMonitor.onTick();

        switch (state) {
            case START -> {
                bazaarMonitor.start();
                ChatUtils.clientMessage("BazaarFlipper: Started");
                state = State.FETCHING;
                debug("[BazaarFlipper] START: going from start to fetching");
            }

            case FETCHING -> {
                flipItemList.addAll(flipCalculator.getFlipItemsList());
                if (flipItemList.isEmpty()) return;
                debug("[BazaarFlipper] FETCHING: list wasn't empty, printing the list and going into processData");
                flipItemList.forEach(flipItem -> {
                    debug("[BazaarFlipper] FETCHING: " + flipItem.book() + " | Cost: " + flipItem.totalCost() + " | Buy: " + flipItem.instaBuy() + " | Sell: " + flipItem.instaSell());
                });
                processData();
            }

            case STARTUP_CHECK -> {
                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand(checkedFirstPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage);
                }

                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack"))
                    clock.start(randomizer());
                if ((containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack")) && inventoryScanner.isMenuLoaded(8) && clock.shouldFire()) {
                    Set<Integer> counter = new HashSet<>();
                    // in here we check both pages
                    for (Task task : taskList) {
                        List<Integer> slots = inventoryScanner.matchingBookInContainer(task.getBook());
                        if (slots.isEmpty()) continue;
                        debug("[BazaarFlipper] STARTUP_CHECK: found " + slots.size() + " container slot(s) matching " + task.getBook() + " on page " + (checkedFirstPage ? 2 : 1));
                        for (Integer i : slots) {
                            if (counter.contains(i)) continue;

                            int level = inventoryScanner.getLevel(i);

                            int attempt = task.assignBook(task.getBook(), level, checkedFirstPage ? 2 : 1, 1);

                            if (attempt == 0) {
                                debug("[BazaarFlipper] STARTUP_CHECK: slot " + i + " level " + level + " accepted by assignBook (attempt=0)");
                                counter.add(i);
                                continue;
                            }

                            if (!taskList.stream()
                                    .filter(task1 -> task1.getBook().equals(task.getBook())).skip(1).findAny().isPresent()) {
                                debug("[BazaarFlipper] STARTUP_CHECK: slot " + i + " level " + level + " is excess for " + task.getBook() + " (no other task needs it), marking for store");
                                handleBookList(task.getBook(), checkedFirstPage ? 2 : 1, level, 1);
                                counter.add(i);
                            }
                        }
                    }

                    if (!checkedFirstPage) {
                        // in here we check inventory
                        for (Task task : taskList) {
                            task.setBookState(Task.BookState.BAZAAR_ORDER_CHECK);
                            List<Integer> slots = inventoryScanner.matchingBookInInventory(task.getBook());
                            if (slots.isEmpty()) continue;
                            debug("[BazaarFlipper] STARTUP_CHECK: found " + slots.size() + " inventory slot(s) matching " + task.getBook());
                            for (Integer i : slots) {
                                if (counter.contains(i)) continue;

                                int level = inventoryScanner.getLevel(i);

                                int attempt = task.assignBook(task.getBook(), level, 0, 1);

                                if (attempt == 0) {
                                    debug("[BazaarFlipper] STARTUP_CHECK: inventory slot " + i + " level " + level + " accepted by assignBook (attempt=0)");
                                    counter.add(i);
                                    continue;
                                }

                                if (!taskList.stream().skip(taskList.indexOf(task) + 1).filter(task1 -> task1.getBook().equals(task.getBook())).findAny().isPresent()) {
                                    debug("[BazaarFlipper] STARTUP_CHECK: inventory slot " + i + " level " + level + " is excess for " + task.getBook() + ", marking for store");
                                    handleBookList(task.getBook(), 0, level, 1);
                                    counter.add(i);
                                }
                            }
                        }

                        checkedFirstPage = true;
                        debug("[BazaarFlipper] STARTUP_CHECK: finished first page (" + bookLists.size() + " book(s) queued for store), moving to second page");
                        minecraft.player.closeContainer();
                        return;
                    }

                    debug("[BazaarFlipper] STARTUP_CHECK: finished both pages, going to STARTUP_BAZAAR_CHECK");
                    minecraft.player.closeContainer();
                    state = State.STARTUP_BAZAAR_CHECK;
                }
            }

            case STARTUP_BAZAAR_CHECK -> {
                Task task = taskInState(Task.BookState.BAZAAR_ORDER_CHECK);
                if (task == null) {
                    debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: no task left in BAZAAR_ORDER_CHECK, startup complete, going to IDLE");
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    isStartUpCheckCompleted = true;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand("managebazaarorders");
                }

                if (containerNameCheck("Bazaar")) clock.start(randomizer());
                if (containerNameCheck("Bazaar") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    // Waiting for chat message to appear here
                    if (attemptedToClaim) {
                        if (!didReceiveItems) return;
                        attemptedToClaim = false;
                        didReceiveItems = false;
                    }

                    List<Integer> slot = inventoryScanner.findContainer("BUY " + task.getBook().getRomanLevel(task.getBook().level()));
                    if (slot.isEmpty()) {
                        // first we check if we have all the required books
                        if (task.getAmountToOrder() == 0) {
                            debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: no BUY order and amount requirement already met, going to ANVIL for " + task.getBook());
                            task.setBookState(Task.BookState.ANVIL);
                            return;
                        }

                        // we check if we can combine the books
                        if (task.isCombinable()) {
                            debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: task is combinable, scheduling SELECTED_COMBINE_STORE_BUYORDER for " + task.getBook());
                            task.actionSchedule = Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER;
                            task.setBookState(Task.BookState.SELECTED);
                            return;
                        }
                        // if we cannot we check if we have any book in our inventory
                        if (!task.bookList.isEmpty() && task.bookList.getFirst().location == 0) {
                            debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: book found in inventory, scheduling SELECTED_STORE_BUYORDER for " + task.getBook());
                            task.actionSchedule = Task.ActionSchedule.SELECTED_STORE_BUYORDER;
                            task.setBookState(Task.BookState.SELECTED);
                            return;
                        }
                        debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: no order and no books, placing new buy order for " + task.getBook());
                        activeTask = task;
                        task.setBookState(Task.BookState.SELECTED);
                        return;
                    }

                    int amount = inventoryScanner.checkOrder(slot.getFirst());
                    if (amount > inventoryScanner.getEmptyInventorySlots()) {
                        debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: not enough empty inventory slots to claim " + amount + " items, going to IDLE");
                        overFlowProt = true;
                        state = State.IDLE;
                        return;
                    }
                    InventoryUtils.clickSlot(slot.getFirst(), false);

                    if (amount > 0) {
                        debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: claiming " + amount + " of " + task.getBook());
                        handleItemAssigning(task, amount);
                    }

                }

                if (containerNameCheck("Order")) clock.start(randomizer());
                if (containerNameCheck("Order") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = inventoryScanner.findContainer("Cancel Order");
                    if (slot.isEmpty()) return;
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                }
            }

            case IDLE -> {
                if (needToStoreExcessBook) {
                    state = State.STORE;
                    return;
                }

                Task taskToHandle = null;

                // here we loop through every task and pick based of priority
                for (Task task : taskList) {
                    if (!isStartUpCheckCompleted && task.getBookState().equals(Task.BookState.OUTBID) || inventoryIsFull)
                        continue;
                    Integer rank = STATE_PRIORITY.get(task.getBookState());
                    if (rank == null) continue;

                    if (taskToHandle == null || rank > STATE_PRIORITY.get(taskToHandle.getBookState())) {
                        taskToHandle = task;
                    }
                }

                if (taskToHandle == null) return;
                debug("[BazaarFlipper] IDLE: picked task " + taskToHandle.getBook() + " in state " + taskToHandle.getBookState());
                switch (taskToHandle.getBookState()) {

                    case OUTBID -> state = State.OUTBID;

                    case SELECTED -> {
                        activeTask = taskToHandle;
                        state = State.BAZAAR_NAVIGATION;
                    }

                    case STORE -> state = State.STORE;

                    case ANVIL -> {
                        if (!taskToHandle.bookList.isEmpty() && taskToHandle.bookList.getFirst().level == taskToHandle.getBook().sellLevel()) {
                            if (taskToHandle.bookList.getFirst().location == 0) {
                                debug("[BazaarFlipper] IDLE: " + taskToHandle.getBook() + " already at sell level in container, going to ANVIL to pull out then sell");
                                taskToHandle.actionSchedule = Task.ActionSchedule.ANVIL_SELL;
                                taskToHandle.setBookState(Task.BookState.ANVIL);
                                state = State.ANVIL;
                                return;
                            }
                            debug("[BazaarFlipper] IDLE: " + taskToHandle.getBook() + " already at sell level in inventory, going to SELL");
                            taskToHandle.setBookState(Task.BookState.SELL);
                            state = State.SELL;
                            return;
                        }

                        if (!taskToHandle.bookList.isEmpty() && taskToHandle.bookList.getLast().location == 0) {
                            debug("[BazaarFlipper] IDLE: highest level book for " + taskToHandle.getBook() + " already in inventory, going to COMBINE");
                            taskToHandle.setBookState(Task.BookState.COMBINE);
                            state = State.COMBINE;
                            taskToHandle.bookList.sort(Comparator.comparingInt(bookList -> bookList.level));
                            return;
                        }
                        state = State.ANVIL;
                    }
                    case SELL -> state = State.SELL;
                    case BAZAAR_ORDER_CHECK -> state = State.STARTUP_BAZAAR_CHECK;

                    case REPLACE_SELL -> state = State.REPLACE_SELL;
                    case COMBINE -> {
                        state = State.COMBINE;
                        taskToHandle.bookList.sort(Comparator.comparingInt(bookList -> bookList.level));
                    }
                }
            }

            case BAZAAR_NAVIGATION -> {
                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand("bz " + activeTask.getBook().name().replace("Ultimate", ""));
                }

                if (containerNameCheck("Bazaar")) clock.start(randomizer());
                if (containerNameCheck("Bazaar") && inventoryScanner.isMenuLoaded(53) && clock.shouldFire()) {
                    List<Integer> slots = inventoryScanner.findContainer(activeTask.getBook().getRomanLevel(activeTask.getBook().level()));
                    if (slots.isEmpty()) return;
                    InventoryUtils.clickSlot(slots.getFirst(), false);
                }

                if (containerNameCheck(activeTask.getBook().name())) clock.start(randomizer());
                if (containerNameCheck(activeTask.getBook().name()) && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    InventoryUtils.clickSlot(activeTask.instaBuy ? 10 : 15, false);
                }

                if (containerNameCheck("How many do you want")) clock.start(randomizer());
                if (containerNameCheck("How many do you want") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    InventoryUtils.clickSlot(16, false);
                }

                if (minecraft.screen instanceof SignEditScreen) clock.start(randomizer());
                if (minecraft.screen instanceof SignEditScreen && clock.shouldFire()) {
                    handleSign();
                }

                if (containerNameCheck("How much do you want to pay")) clock.start(randomizer());
                if (containerNameCheck("How much do you want to pay") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    bazaarMonitor.add(activeTask.getBook(), inventoryScanner.getUnitPrice(12), false);
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerNameCheck("Confirm")) clock.start(randomizer());
                if (containerNameCheck("Confirm") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    InventoryUtils.clickSlot(13, false);
                    // first we check if the order was an insta buy
                    if (activeTask.instaBuy) {
                        debug("[BazaarFlipper] BAZAAR_NAVIGATION: insta bought " + activeTask.getBook() + ", going to " + (activeTask.bookList.getLast().location != 0 ? "ANVIL" : "COMBINE"));
                        activeTask.setBookState(activeTask.bookList.getLast().location != 0 ? Task.BookState.ANVIL : Task.BookState.COMBINE);
                        return;
                    }

                    debug("[BazaarFlipper] BAZAAR_NAVIGATION: confirmed buy order for " + activeTask.getBook() + ", schedule was " + activeTask.actionSchedule);
                    switch (activeTask.actionSchedule) {
                        case SELECTED_COMBINE_STORE_BUYORDER -> activeTask.setBookState(Task.BookState.ANVIL);

                        case SELECTED_STORE_BUYORDER -> activeTask.setBookState(Task.BookState.STORE);

                        case NONE -> activeTask.setBookState(Task.BookState.IN_BUY_ORDER);
                    }
                    state = State.IDLE;
                }
            }

            case OUTBID -> {
                Task task = taskInState(Task.BookState.OUTBID);
                if (task == null) {
                    debug("[BazaarFlipper] OUTBID: no task left in OUTBID, going to IDLE");
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand("managebazaarorders");
                }

                if (containerNameCheck("Bazaar")) clock.start(randomizer());
                if (containerNameCheck("Bazaar") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    // Waiting for chat message to appear here
                    if (attemptedToClaim) {
                        if (!didReceiveItems) return;
                        attemptedToClaim = false;
                        didReceiveItems = false;
                    }

                    List<Integer> slot = inventoryScanner.findContainer("BUY " + task.getBook().getRomanLevel(task.getBook().level()));

                    if (slot.isEmpty()) {
                        // first we check if we have all the required books
                        if (task.getAmountToOrder() == 0) {
                            debug("[BazaarFlipper] OUTBID: no BUY order and amount requirement already met, going to ANVIL for " + task.getBook());
                            task.setBookState(Task.BookState.ANVIL);
                            return;
                        }

                        // we check if we can combine the books
                        if (task.isCombinable()) {
                            debug("[BazaarFlipper] OUTBID: task is combinable, scheduling SELECTED_COMBINE_STORE_BUYORDER for " + task.getBook());
                            task.actionSchedule = Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER;
                            task.setBookState(Task.BookState.SELECTED);
                            return;
                        }
                        // if we cannot we check if we have any book in our inventory
                        if (!task.bookList.isEmpty() && task.bookList.getFirst().location == 0) {
                            debug("[BazaarFlipper] OUTBID: book found in inventory, scheduling SELECTED_STORE_BUYORDER for " + task.getBook());
                            task.actionSchedule = Task.ActionSchedule.SELECTED_STORE_BUYORDER;
                            task.setBookState(Task.BookState.SELECTED);
                            return;
                        }
                        debug("[BazaarFlipper] OUTBID: re-placing buy order for " + task.getBook());
                        activeTask = task;
                        task.setBookState(Task.BookState.SELECTED);
                        return;
                    }

                    int amount = inventoryScanner.checkOrder(slot.getFirst());
                    if (amount > inventoryScanner.getEmptyInventorySlots()) {
                        debug("[BazaarFlipper] OUTBID: not enough empty inventory slots to claim " + amount + " items, going to IDLE and marking inventory full");
                        state = State.IDLE;
                        inventoryIsFull = true;
                        return;
                    }
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                    if (amount > 0) {
                        debug("[BazaarFlipper] OUTBID: claiming " + amount + " of " + task.getBook());
                        handleItemAssigning(task, amount);
                    }
                }

                if (containerNameCheck("Order")) clock.start(randomizer());
                if (containerNameCheck("Order") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = inventoryScanner.findContainer("Cancel Order");
                    if (slot.isEmpty()) return;
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                }
            }

            case STORE -> {
                Task task = taskInState(Task.BookState.STORE);
                if (task == null && !needToStoreExcessBook) {
                    debug("[BazaarFlipper] STORE: no task left in STORE and nothing excess to store, going to IDLE");
                    store_Counter = -1;
                    store_Counter_2 = -1;
                    usingSecondPage = false;
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand(usingSecondPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage);
                }

                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack"))
                    clock.start(randomizer());
                if ((containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack")) && inventoryScanner.isMenuLoaded(8) && clock.shouldFire()) {
                    BookList bookList = null;
                    if (needToStoreExcessBook) {
                        for (BookList book : bookLists) {
                            if (book.location != 0) continue;
                            bookList = book;
                            break;
                        }
                    } else {
                        for (BookList book : task.bookList) {
                            if (book.location != 0) continue;
                            bookList = book;
                            break;
                        }
                    }

                    if (bookList != null)
                        debug("[BazaarFlipper] STORE: handling level " + bookList.level + " " + bookList.book + " (excess=" + needToStoreExcessBook + ")");

                    if (bookList == null) {
                        if (needToStoreExcessBook) {
                            debug("[BazaarFlipper] STORE: finished storing excess books");
                            needToStoreExcessBook = false;
                            return;
                        }

                        debug("[BazaarFlipper] STORE: finished storing for " + task.getBook() + ", schedule was " + task.actionSchedule);
                        switch (task.actionSchedule) {
                            case SELECTED_STORE_BUYORDER, SELECTED_COMBINE_STORE_BUYORDER -> {
                                task.setBookState(Task.BookState.IN_BUY_ORDER);
                                task.actionSchedule = Task.ActionSchedule.NONE;
                            }
                        }
                        return;
                    }

                    List<Integer> slot = inventoryScanner.findLoreInv(bookList.book.getRomanLevel(bookList.level));

                    // item move check
                    if (slot.isEmpty()) {
                        debug("[BazaarFlipper] STORE: level " + bookList.level + " " + bookList.book + " no longer in inventory, marking moved to page " + (usingSecondPage ? 2 : 1));
                        bookList.location = usingSecondPage ? 2 : 1;
                        store_Counter = inventoryScanner.findLoreInv(bookList.book.getRomanLevel(bookList.level)).size();
                        store_Counter_2 = inventoryScanner.findLoreContainer(bookList.book.getRomanLevel(bookList.level)).size();
                        return;
                    }

                    // compares how many items it had before and how many items it has now to label them as moved or just labeling them once empty
                    if (store_Counter != -1 && store_Counter_2 != -1 && store_Counter > slot.size() && store_Counter_2 < inventoryScanner.findLoreContainer(bookList.book.getRomanLevel(bookList.level)).size()) {
                        debug("[BazaarFlipper] STORE: detected move for level " + bookList.level + " (inventory " + store_Counter + "->" + slot.size() + ", container " + store_Counter_2 + "->" + inventoryScanner.findLoreContainer(bookList.book.getRomanLevel(bookList.level)).size() + ")");
                        bookList.location = usingSecondPage ? 2 : 1;
                        store_Counter = inventoryScanner.findLoreInv(bookList.book.getRomanLevel(bookList.level)).size();
                        store_Counter_2 = inventoryScanner.findLoreContainer(bookList.book.getRomanLevel(bookList.level)).size();
                        return;
                    }

                    store_Counter_2 = inventoryScanner.findLoreContainer(bookList.book.getRomanLevel(bookList.level)).size();
                    store_Counter = slot.size();

                    if (inventoryScanner.getEmptyContainerSlots() == 0) {
                        debug("[BazaarFlipper] STORE: first page container full, switching to second page");
                        usingSecondPage = true;
                        store_Counter = -1;
                        store_Counter_2 = -1;
                        minecraft.player.closeContainer();
                        return;
                    }
                    InventoryUtils.clickSlot(slot.getFirst(), true);
                }
            }

            case ANVIL -> {
                Task task = taskInState(Task.BookState.ANVIL);
                if (task == null) {
                    debug("[BazaarFlipper] ANVIL: no task left in ANVIL, going to IDLE");
                    anvil_Counter = -1;
                    anvil_Counter_2 = -1;
                    usingSecondPage = false;
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand(usingSecondPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage);
                }

                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack"))
                    clock.start(randomizer());
                if ((containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack")) && inventoryScanner.isMenuLoaded(8) && clock.shouldFire()) {

                    // if we have all the amount we pull out everything
                    if (task.getAmountToOrder() == 0) {
                        List<Integer> slot = new ArrayList<>();

                        // here we check the amount we'll pull out and assign book one by one
                        BookList bookToHandle = null;
                        for (int i = 0; i < task.bookList.size(); i++) {
                            BookList bookList = task.bookList.get(i);
                            if (bookList.location == 0) continue;
                            int remaining = task.bookList.size() - i;
                            if (remaining > inventoryScanner.getEmptyInventorySlots()) {
                                debug("[BazaarFlipper] ANVIL: need " + remaining + " inventory slot(s) to pull remaining books for " + task.getBook() + " but not enough free, going to IDLE");
                                state = State.IDLE;
                                return;
                            }
                            // here we check if we should move to the second page
                            boolean needsSecondPage = bookList.location == 2;
                            if (needsSecondPage != usingSecondPage) {
                                debug("[BazaarFlipper] ANVIL: level " + bookList.level + " is on page " + bookList.location + ", switching usingSecondPage from " + usingSecondPage + " to " + needsSecondPage);
                                usingSecondPage = needsSecondPage;
                                minecraft.player.closeContainer();
                                return;
                            }

                            slot.addAll(inventoryScanner.findLoreContainer(bookList.book.getRomanLevel(bookList.level)));

                            bookToHandle = bookList;
                            break;
                        }

                        // first we handle if we have no books to pull out
                        if (bookToHandle == null) {
                            debug("[BazaarFlipper] ANVIL: nothing left to pull out for " + task.getBook() + ", schedule was " + task.actionSchedule);
                            switch (task.actionSchedule) {
                                case ANVIL_SELL -> task.setBookState(Task.BookState.SELL);
                                case NONE -> task.setBookState(Task.BookState.COMBINE);
                            }
                            usingSecondPage = false;
                            return;
                        }

                        if (slot.isEmpty()) {
                            debug("[BazaarFlipper] ANVIL: level " + bookToHandle.level + " no longer in container, marking moved to inventory (location=0)");
                            bookToHandle.location = 0;
                            return;
                        }

                        InventoryUtils.clickSlot(slot.getFirst(), true);
                        return;
                    }

                    List<Integer> slot = new ArrayList<>();
                    List<Integer> slot_2 = new ArrayList<>();


                    HashMap<Integer, Integer> futureItem = new HashMap<>();
                    BookList bookList = null;

                    // this is loop to handle what item to pick
                    for (int i = 0; i < task.bookList.size(); i++) {
                        BookList bookList1 = task.bookList.get(i);
                        BookList bookList2 = i + 1 >= task.bookList.size() ? null : task.bookList.get(i + 1);

                        if (bookList2 == null || bookList1.level != bookList2.level) {
                            if (futureItem.getOrDefault(bookList1.level, 0) >= 1) {
                                int newCount = futureItem.merge(bookList1.level + 1, futureItem.getOrDefault(bookList1.level + 1, 0) == 2 ? -2 : 1, Integer::sum);
                                if (newCount == 0) futureItem.merge(bookList1.level + 1, 1, Integer::sum);
                                debug("[BazaarFlipper] ANVIL lookahead: level " + bookList1.level + " has a pending partner from a prior merge, projected level " + (bookList1.level + 1) + " count now " + futureItem.getOrDefault(bookList1.level + 1, 0));

                                if (bookList1.location == 0) {
                                    debug("[BazaarFlipper] ANVIL lookahead: level " + bookList1.level + " already in inventory, nothing to pull, continuing scan");
                                    continue;
                                }
                                slot.addAll(inventoryScanner.findLoreContainer(task.getBook().getRomanLevel(bookList1.level)));
                                slot_2.addAll(inventoryScanner.findLoreInv(task.getBook().getRomanLevel(bookList1.level)));
                                bookList = bookList1;
                                debug("[BazaarFlipper] ANVIL lookahead: picked unpaired level " + bookList1.level + " (location=" + bookList1.location + ") to pull for merge partner");
                                break;
                            }
                            debug("[BazaarFlipper] ANVIL lookahead: level " + bookList1.level + " unpaired and no pending partner, skipping");
                            continue;
                        }
                        int newCount = futureItem.merge(bookList1.level + 1, futureItem.getOrDefault(bookList1.level + 1, 0) == 1 ? -1 : 1, Integer::sum);
                        if (newCount == 0) futureItem.merge(bookList1.level + 2, 1, Integer::sum);
                        debug("[BazaarFlipper] ANVIL lookahead: matched pair at level " + bookList1.level + ", projected level " + (bookList1.level + 1) + " count now " + newCount + (newCount == 0 ? " (rolled over to level " + (bookList1.level + 2) + ")" : ""));

                        i++;

                        if (bookList1.location != 0) {
                            slot.addAll(inventoryScanner.findLoreContainer(task.getBook().getRomanLevel(bookList1.level)));
                            slot_2.addAll(inventoryScanner.findLoreInv(task.getBook().getRomanLevel(bookList1.level)));
                            bookList = bookList1;
                            debug("[BazaarFlipper] ANVIL lookahead: picked first of pair, level " + bookList1.level + " (location=" + bookList1.location + ") to pull");
                            break;
                        }
                        if (bookList2.location != 0) {
                            slot.addAll(inventoryScanner.findLoreContainer(task.getBook().getRomanLevel(bookList2.level)));
                            slot_2.addAll(inventoryScanner.findLoreInv(task.getBook().getRomanLevel(bookList2.level)));
                            bookList = bookList2;
                            debug("[BazaarFlipper] ANVIL lookahead: picked second of pair, level " + bookList2.level + " (location=" + bookList2.location + ") to pull");
                            break;
                        }
                        debug("[BazaarFlipper] ANVIL lookahead: matched pair at level " + bookList1.level + " already fully in inventory, continuing scan");
                    }

                    if (bookList == null) {
                        debug("[BazaarFlipper] ANVIL: no more books to pull for merging on " + task.getBook() + ", schedule was " + task.actionSchedule);
                        switch (task.actionSchedule) {
                            case ANVIL_SELL -> task.setBookState(Task.BookState.SELL);
                            case SELECTED_COMBINE_STORE_BUYORDER, NONE -> task.setBookState(Task.BookState.COMBINE);
                        }
                        usingSecondPage = false;
                        return;
                    }

                    debug("[BazaarFlipper] ANVIL: merge lookahead picked level " + bookList.level + " (location=" + bookList.location + ") to pull, projected future counts=" + futureItem);

                    // here we check if we should move to the second page
                    boolean needsSecondPage = bookList.location == 2;
                    if (needsSecondPage != usingSecondPage) {
                        debug("[BazaarFlipper] ANVIL: level " + bookList.level + " is on page " + bookList.location + ", switching usingSecondPage from " + usingSecondPage + " to " + needsSecondPage);
                        usingSecondPage = needsSecondPage;
                        minecraft.player.closeContainer();
                        return;
                    }

                    if (slot.size() > inventoryScanner.getEmptyInventorySlots()) {
                        debug("[BazaarFlipper] ANVIL: need " + slot.size() + " inventory slot(s) but only " + inventoryScanner.getEmptyInventorySlots() + " empty, going to IDLE");
                        state = State.IDLE;
                        return;
                    }

                    // Item move check
                    if (slot.isEmpty()) {
                        debug("[BazaarFlipper] ANVIL: level " + bookList.level + " no longer in container, marking moved to inventory (location=0)");
                        bookList.location = 0;
                        anvil_Counter = inventoryScanner.findLoreContainer(task.getBook().getRomanLevel(bookList.level)).size();
                        anvil_Counter_2 = inventoryScanner.findLoreInv(task.getBook().getRomanLevel(bookList.level)).size();
                        return;
                    }

                    // compares how many items it had before and how many items it has now to label them as moved or just labeling them once empty
                    if (anvil_Counter != -1 && anvil_Counter > slot.size() && anvil_Counter_2 > inventoryScanner.findLoreInv(task.getBook().getRomanLevel(bookList.level)).size()) {
                        debug("[BazaarFlipper] ANVIL: detected move for level " + bookList.level + " (container " + anvil_Counter + "->" + slot.size() + ", inventory " + anvil_Counter_2 + "->" + inventoryScanner.findLoreInv(task.getBook().getRomanLevel(bookList.level)).size() + ")");
                        bookList.location = 0;
                        anvil_Counter = inventoryScanner.findLoreContainer(task.getBook().getRomanLevel(bookList.level)).size();
                        anvil_Counter_2 = inventoryScanner.findLoreInv(task.getBook().getRomanLevel(bookList.level)).size();
                        return;
                    }

                    anvil_Counter_2 = slot_2.size();
                    anvil_Counter = slot.size();

                    InventoryUtils.clickSlot(slot.getFirst(), true);
                }
            }

            case COMBINE -> {
                Task task = taskInState(Task.BookState.COMBINE);
                if (task == null) {
                    debug("[BazaarFlipper] COMBINE: no task left in COMBINE, going to IDLE");
                    combine_Counter_2 = 0;
                    combine_Counter = -1;
                    usingSecondPage = false;
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand("anvil");
                }

                if (containerNameCheck("Anvil")) clock.start(randomizer());
                if (containerNameCheck("Anvil") && inventoryScanner.isMenuLoaded(8) && clock.shouldFire()) {
                    List<Integer> slot = new ArrayList<>();
                    BookList firstBook = null;
                    BookList secondBook = null;

                    for (int i = 0; i < task.bookList.size(); i++) {
                        BookList bookList1 = task.bookList.get(i);
                        BookList bookList2 = i + 1 >= task.bookList.size() ? null : task.bookList.get(i + 1);

                        if (bookList2 == null) continue;
                        if (bookList1.level != bookList2.level || bookList1.location != 0 || bookList2.location != 0)
                            continue;

                        i++;
                        firstBook = bookList1;
                        secondBook = bookList2;
                        debug("[BazaarFlipper] COMBINE: found pair of level " + firstBook.level + " " + firstBook.book + " both in inventory, will merge");
                        slot.addAll(inventoryScanner.findLoreInv(firstBook.book.getRomanLevel(firstBook.level)));
                        break;
                    }

                    if (firstBook == null) {
                        debug("[BazaarFlipper] COMBINE: no matching pair left for " + task.getBook() + ", schedule was " + task.actionSchedule);
                        switch (task.actionSchedule) {
                            case NONE -> task.setBookState(Task.BookState.SELL);
                            case SELECTED_COMBINE_STORE_BUYORDER -> task.setBookState(Task.BookState.STORE);
                        }
                        return;
                    }

                    if (inventoryScanner.getEmptyContainerSlots() == 0 || combine_Counter_2 != 0 || inventoryScanner.findLoreContainer(firstBook.book.getRomanLevel(firstBook.level + 1)).size() == 1) {
                        debug("[BazaarFlipper] COMBINE: toggling anvil output slot for level " + (firstBook.level + 1) + " " + firstBook.book);
                        InventoryUtils.clickSlot(22, false);
                        combine_Counter_2 = combine_Counter_2 == 0 ? 1 : 0;
                        return;
                    }

                    if (inventoryScanner.findMisMatch(firstBook.book.getRomanLevel(firstBook.level))) {
                        minecraft.player.closeContainer();
                        debug("[BazaarFlipper] COMBINE: found mismatch attempting self repair");
                        return;
                    }

                    if (combine_Counter != -1 && combine_Counter < inventoryScanner.findLoreInv(firstBook.book.getRomanLevel(firstBook.level + 1)).size() || slot.isEmpty()) {
                        debug("[BazaarFlipper] COMBINE: combined into level " + (firstBook.level + 1) + " " + firstBook.book);
                        task.bookList.add(new BookList(task.getBook(), firstBook.level + 1, 0));
                        task.bookList.removeAll(List.of(firstBook, secondBook));
                        combine_Counter = inventoryScanner.findLoreInv(firstBook.book.getRomanLevel(firstBook.level + 1)).size();
                        task.bookList.sort(Comparator.comparingInt(bookList -> bookList.level));
                        return;
                    }


                    combine_Counter = inventoryScanner.findLoreInv(firstBook.book.getRomanLevel(firstBook.level + 1)).size();

                    InventoryUtils.clickSlot(slot.getFirst(), true);
                }
            }

            case SELL -> {
                Task task = taskInState(Task.BookState.SELL);
                if (task == null) {
                    debug("[BazaarFlipper] SELL: no task left in SELL, going to FETCHING");
                    minecraft.player.closeContainer();
                    state = State.FETCHING;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand("managebazaarorders");
                }

                if (containerNameCheck("Bazaar")) clock.start(randomizer());
                if (containerNameCheck("Bazaar") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = new ArrayList<>();

                    slot.addAll(inventoryScanner.findContainer("SELL " + task.getBook().getRomanLevel(task.getBook().sellLevel())));

                    if (!slot.isEmpty() && !task.instaSell) {
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                        return;
                    }

                    slot.addAll(inventoryScanner.findLoreInv(task.getBook().getRomanLevel(task.getBook().sellLevel())));

                    if (!slot.isEmpty()) {
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                        return;
                    }
                }

                if (containerNameCheck("Order")) clock.start(randomizer());
                if (containerNameCheck("Order") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = inventoryScanner.findContainer("Cancel Order");
                    if (slot.isEmpty()) return;
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                }

                if (containerNameCheck(task.getBook().name())) clock.start(randomizer());
                if (containerNameCheck(task.getBook().name()) && clock.shouldFire()) {
                    if (task.instaSell) {
                        debug("[BazaarFlipper] SELL: insta selling " + task.getBook());
                        InventoryUtils.clickSlot(10, false);
                        taskList.remove(task);
                        return;
                    }
                    InventoryUtils.clickSlot(16, false);
                }

                if (containerNameCheck("At what price are you selling")) clock.start(randomizer());
                if (containerNameCheck("At what price are you selling") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    bazaarMonitor.add(task.getBook(), inventoryScanner.getUnitPrice(12), true);
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerNameCheck("Confirm")) clock.start(randomizer());
                if (containerNameCheck("Confirm") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    InventoryUtils.clickSlot(13, false);
                    debug("[BazaarFlipper] SELL: placed sell order for " + task.getBook());
                    task.setBookState(Task.BookState.SELL_ORDER);

                    // Removing duplicates
                    Set<String> seen = new HashSet<>();

                    taskList.removeIf(task1 -> {
                        if (task1.getBookState() != Task.BookState.REPLACE_SELL
                                && task1.getBookState() != Task.BookState.SELL_ORDER
                                && task1.getBookState() != Task.BookState.SELL) {
                            return false;
                        }

                        return !seen.add(task1.getBook().name());
                    });
                    debug("[BazaarFlipper] SELL: TaskSize:" + taskList.size());
                }
            }

            case REPLACE_SELL -> {
                Task task = taskInState(Task.BookState.REPLACE_SELL);
                if (task == null) {
                    debug("[BazaarFlipper] REPLACE_SELL: no task left in REPLACE_SELL, going to IDLE");
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    minecraft.player.connection.sendCommand("managebazaarorders");
                }

                if (containerNameCheck("Bazaar")) clock.start(randomizer());
                if (containerNameCheck("Bazaar") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = new ArrayList<>();

                    slot.addAll(inventoryScanner.findContainer("SELL " + task.getBook().getRomanLevel(task.getBook().sellLevel())));

                    if (!slot.isEmpty()) {
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                        return;
                    }

                    slot.addAll(inventoryScanner.findLoreInv(task.getBook().getRomanLevel(task.getBook().sellLevel())));

                    if (!slot.isEmpty()) {
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                        return;
                    }

                    debug("[BazaarFlipper] REPLACE_SELL: no book or existing sell order found for " + task.getBook() + ", dropping task");
                    taskList.remove(task);
                    return;
                }

                if (containerNameCheck("Order")) clock.start(randomizer());
                if (containerNameCheck("Order") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = inventoryScanner.findContainer("Cancel Order");
                    if (slot.isEmpty()) return;
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                }

                if (containerNameCheck(task.getBook().name())) clock.start(randomizer());
                if (containerNameCheck(task.getBook().name()) && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    InventoryUtils.clickSlot(16, false);
                }

                if (containerNameCheck("At what price are you selling")) clock.start(randomizer());
                if (containerNameCheck("At what price are you selling") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    bazaarMonitor.add(activeTask.getBook(), inventoryScanner.getUnitPrice(12), true);
                    InventoryUtils.clickSlot(12, false);
                }

                if (containerNameCheck("Confirm")) clock.start(randomizer());
                if (containerNameCheck("Confirm") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    InventoryUtils.clickSlot(13, false);
                    debug("[BazaarFlipper] REPLACE_SELL: replaced sell order for " + task.getBook());
                    task.setBookState(Task.BookState.SELL_ORDER);
                    debug("[BazaarFlipper] REPLACE_SELL: TaskSize:" + taskList.size());
                }
            }
        }

    }

    private boolean containerNameCheck(String name) {
        if (minecraft.screen == null) return false;
        return minecraft.screen.getTitle().toString().contains(name);
    }


    private void lastStateCheck() {
        if (state == lastState) return;
        ChatUtils.clientMessage("State switched from: " + lastState + " to: " + state);
        clock.stop();
        if (lastState == State.IDLE) {
            inventoryIsFull = false;
        }
        lastState = state;

        if (state == State.FETCHING) {
            flipItemList.clear();
            flipCalculator.Refresh();
        }
    }

    private void processData() {
        double purse = scoreboardUtils.getPurse();
        // Money Check
        debug("[BazaarFlipper] PROCESSDATA: purse=" + purse + ", flipItemList size=" + flipItemList.size());
        double cost = flipItemList.stream().mapToDouble(FlipItem::totalCost).min().orElse(-1);

        if (cost != -1) {
            debug("[BazaarFlipper] PROCESSDATA: cheapest flip costs " + cost);
            if (cost > purse) {
                debug("[BazaarFlipper] PROCESSDATA: cheapest flip (" + cost + ") exceeds purse (" + purse + "), going to IDLE");
                if (!notEnoughCash)
                    ChatUtils.clientMessage("BazaarFlipper: Not enough cash for the cheapest flip (" + cost + " needed)");
                notEnoughCash = true;
                state = State.IDLE;
                return;
            }
        }

        for (FlipItem flipItem : flipItemList) {
            if (flipItem.totalCost() > purse) continue;
            if (taskList.stream().anyMatch(task ->
                    task.getBook().equals(flipItem.book())
                            && task.getBookState() != Task.BookState.SELL_ORDER
                            && task.getBookState() != Task.BookState.REPLACE_SELL
            )) continue;
            debug("[BazaarFlipper] PROCESSDATA: creating task for " + flipItem.book().getRomanLevel(flipItem.book().level()) + " (cost=" + flipItem.totalCost() + ", instaBuy=" + flipItem.instaBuy() + ", instaSell=" + flipItem.instaSell() + ")");
            Task task = new Task(flipItem.book(), flipItem.instaBuy(), flipItem.instaSell());
            taskList.add(task);

            Iterator<BookList> iterator = bookLists.iterator();

            while (iterator.hasNext()) {
                BookList bookList = iterator.next();

                if (!bookList.book.equals(flipItem.book())) continue;

                int attempt = task.assignBook(bookList.book, bookList.level, bookList.location, 1);

                if (attempt != -1) {
                    debug("[BazaarFlipper] PROCESSDATA: pre-existing book level " + bookList.level + " (location=" + bookList.location + ") folded into new task for " + task.getBook());
                    iterator.remove();
                }
            }

            if (task.getAmountToOrder() == 0) {
                task.setBookState(Task.BookState.ANVIL);
                continue;
            }

            if (task.isCombinable()) {
                task.setBookState(Task.BookState.SELECTED);
                task.actionSchedule = Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER;
                continue;
            }
            task.setBookState(Task.BookState.SELECTED);
        }

        if (overFlowProt) {
            overFlowProt = false;
            state = State.IDLE;
            return;
        }
        state = isStartUpCheckCompleted ? State.IDLE : State.STARTUP_CHECK;
    }

    private int randomizer() {
        int result = splittableRandom.nextInt(GoofyConfig.INSTANCE.minActionDelay, GoofyConfig.INSTANCE.maxActionDelay);
        return result > 50 ? result : 500;
    }

    private Task taskInState(Task.BookState bookState) {
        return taskList.stream().filter(task -> task.getBookState() == bookState).findFirst().orElse(null);
    }

    private void handleBookList(Book book, int location, int level, int amount) {
        debug("[BazaarFlipper] handleBookList: queuing " + amount + "x level " + level + " " + book + " at location " + location + (location == 0 ? " (will need storing)" : ""));
        if (location == 0) needToStoreExcessBook = true;
        for (int i = 0; i < amount; i++) {
            bookLists.add(new BookList(book, level, location));
        }
        bookLists.sort(Comparator.comparingInt(bookList -> bookList.location));
    }

    private void handleClaimedMessage(String string) {
        if (!didReceiveItems) {
            debug("[BazaarFlipper] onClaimNotice: received item pickup confirmation");
            didReceiveItems = true;
        }
    }

    private void handleItemAssigning(Task task, int amount) {

        if (amount > task.getAmountToOrder()) {
            int requiredAmount = task.getAmountToOrder();
            int remainder = amount - requiredAmount;
            debug("[BazaarFlipper] handleItemAssigning: received " + amount + " of " + task.getBook() + " vs amountToOrder=" + task.getAmountToOrder() + " -> assignBook(newAmount=" + requiredAmount + "), handleBookList(amount=" + remainder + ")");
            task.assignBook(task.getBook(), task.getBook().level(), 0, requiredAmount);
            handleBookList(task.getBook(), 0, task.getBook().level(), remainder);
            return;
        }
        task.assignBook(task.getBook(), task.getBook().level(), 0, amount);
    }

    private void handleSign() {
        String amountToOrder = String.valueOf(activeTask.getAmountToOrder());
        if (minecraft.screen instanceof AbstractSignEditScreen signScreen) {
            try {
                Field messagesField = AbstractSignEditScreen.class.getDeclaredField("messages");
                messagesField.setAccessible(true);
                String[] messages = (String[]) messagesField.get(signScreen);
                messages[0] = amountToOrder;
                debug("[BazaarFlipper] handleSign: wrote \"" + amountToOrder + "\" onto sign for " + activeTask.getBook());
                minecraft.setScreen(null);
            } catch (Exception e) {
                debug("[BazaarFlipper] handleSign: reflection write failed for " + activeTask.getBook() + " - " + e);
                e.printStackTrace();
            }
        }
    }

    private void handleFilledMessage(String string) {
        String stripped;
        boolean isSellOffer = false;

        if (string.contains("Buy Order")) {
            stripped = string.replace("[Bazaar] Your Buy Order for ", "").replace(" was filled!", "");
            stripped = stripped.substring(stripped.indexOf(' ') + 1);
            ChatUtils.clientMessage("BazaarFlipper: Buy order complete for " + stripped);
        } else {
            stripped = string.replace("[Bazaar] Your Sell Offer for ", "").replace(" was filled!", "");
            stripped = stripped.substring(stripped.indexOf(' ') + 1);
            ChatUtils.clientMessage("BazaarFlipper: Sell offer complete for " + stripped);
            isSellOffer = true;
        }

        debug("[BazaarFlipper] onOrderNotice: parsed \"" + stripped + "\" isSellOffer=" + isSellOffer);

        for (Task task : taskList) {
            if (!stripped.equals(task.getBook().getRomanLevel(task.getBook().level())) && !isSellOffer) continue;
            if (!stripped.equals(task.getBook().getRomanLevel(task.getBook().sellLevel())) && isSellOffer) continue;

            debug("[BazaarFlipper] onOrderNotice: matched task " + task.getBook() + ", queuing state change");
            listOfTaskToChange.add(task);
            bazaarMonitor.finish(task.getBook(), isSellOffer);
        }
    }

    private void handleTaskStateChange() {
        if (listOfTaskToChange.isEmpty()) return;

        for (Task task : new HashSet<>(listOfTaskToChange)) {
            switch (task.getBookState()) {
                case SELL_ORDER -> {
                    debug("[BazaarFlipper] handleTaskStateChange: " + task.getBook()
                            + " sell order complete, moving to REPLACE_SELL");

                    task.setBookState(Task.BookState.REPLACE_SELL);
                    listOfTaskToChange.remove(task);
                }

                case IN_BUY_ORDER -> {
                    debug("[BazaarFlipper] handleTaskStateChange: " + task.getBook()
                            + " outbid, moving to OUTBID");

                    task.setBookState(Task.BookState.OUTBID);
                    listOfTaskToChange.remove(task);
                }
            }
        }
    }

    private void handleOutbid(BazaarMonitor.BazaarMonitorItem book) {
        for (Task task : taskList) {
            if (!book.book.equals(task.getBook())) continue;

            if (book.isSellOrder && (task.getBookState() == Task.BookState.REPLACE_SELL || task.getBookState() == Task.BookState.SELL_ORDER)) {
                debug("[BazaarFlipper] handleOutbid: sell order for " + task.getBook() + " was outbid/undercut, queuing state change");
                listOfTaskToChange.add(task);
            } else {
                debug("[BazaarFlipper] handleOutbid: buy order for " + task.getBook() + " was outbid, queuing state change");
                listOfTaskToChange.add(task);
            }
        }
    }

    private void debug(String string) {
        ChatUtils.debugMessage(string);
    }

}