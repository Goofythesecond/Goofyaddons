package com.goofy.goofyaddons.features.bookflipper.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Task {
    public enum BookState {
        BAZAAR_ORDER_CHECK,
        SELECTED,
        IN_BUY_ORDER,
        OUTBID,
        STORE,
        ANVIL,
        COMBINE,
        SELL,
        SELL_ORDER,
        REPLACE_SELL
    }

    public enum ActionSchedule {
        NONE,
        SELECTED_COMBINE_STORE_BUYORDER,
        SELECTED_STORE_BUYORDER,
        ANVIL_SELL
    }

    public boolean instaSell = false;
    public boolean instaBuy = false;
    public ActionSchedule actionSchedule = ActionSchedule.NONE;
    private Book book;
    private int amountToOrder;
    private BookState bookState;
    // book location will be represented in integars, 0 = Inventory, 1 = EnderChest, 2 = EnderChestPage2
    public List<BookList> bookList = new ArrayList<>();


    public Task(Book book, boolean instaBuy, boolean instaSell) {
        this.book = book;
        this.instaBuy = instaBuy;
        this.instaSell = instaSell;
        amountToOrder = book.getQtyAmount(book.level());
    }

    public Book getBook() {
        return book;
    }

    public BookState getBookState() {
        return bookState;
    }

    public void setBookState(BookState bookState) {
        this.bookState = bookState;
    }

    // -1 will indicate failure, 0 will indicate success
    public int assignBook(Book book, int level, int location, int amountOfBook) {
        if (amountOfBook == 0) return 0;
        if (book != this.book) return -1;
        if (book.level() > level) return -1;
        int amount = parseBookLevel(level);
        int totalAmount = amount * amountOfBook;

        if (totalAmount > amountToOrder) return -1;

        amountToOrder -= totalAmount;

        for (int i = 0; i < amountOfBook; i++) {
            bookList.add(new BookList(book, level, location));
        }
        // we sort the list here by location
        bookList.sort(Comparator.comparingInt(bookList -> bookList.location));

        return 0;
    }

    public int getAmountToOrder() {
        return amountToOrder;
    }

    public boolean isCombinable() {
        Set<Integer> seen = new HashSet<>();
        return bookList.stream().anyMatch(book -> !seen.add(book.level));
    }


    private int parseBookLevel(int level) {
        if (level == book.level()) return 1;
        return 1 << (level - book.level());
    }

}