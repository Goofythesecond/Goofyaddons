package com.goofy.goofyaddons.features.bookflipper.helper;

public class BookList {
    public final Book book;
    public final int level;
    public int location;

    public BookList(Book book, int level, int location) {
        this.book = book;
        this.level = level;
        this.location = location;
    }
}