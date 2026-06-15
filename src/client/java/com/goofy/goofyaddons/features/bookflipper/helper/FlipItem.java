package com.goofy.goofyaddons.features.bookflipper.helper;

public record FlipItem(Book book, double totalCost, double score) {
    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        book = this.book;
    }
}
