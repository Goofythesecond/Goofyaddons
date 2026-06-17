package com.goofy.goofyaddons.features.bookflipper.helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;



public class BazaarMonitor {
    private final List<BazaarMonitorItem> buyOrderList = new ArrayList<>();
    private final List<BazaarMonitorItem> sellOrderList = new ArrayList<>();
    private final List<Book> getOutbidBuyOrderBookList = new ArrayList<>();
    private final List<Book> getOutbidSellOrderBookList = new ArrayList<>();
    private boolean running = false;
    private HttpClient client = HttpClient.newHttpClient();
    private long duration = 60000;
    private long startMs;


    public void add(Book book, double price, boolean isSellOrder) {
        if (isSellOrder) sellOrderList.add(new BazaarMonitorItem(book, price));
        if (!isSellOrder) buyOrderList.add(new BazaarMonitorItem(book, price));
    }

    public void finish(Book book, boolean isSellorder) {
        if (isSellorder) sellOrderList.removeIf(item -> item.book.equals(book));
        if (!isSellorder) buyOrderList.removeIf(item -> item.book.equals(book));
    }

    public void reset(boolean sellOrder) {
        if (sellOrder) sellOrderList.clear();
        if (!sellOrder) buyOrderList.clear();

    }

    public List<Book> isOutbid(boolean isSellOrder) {
        if (isSellOrder) return getOutbidSellOrderBookList;
        return getOutbidBuyOrderBookList;
    }

    public void onTick() {
        if (!running) return;
        if (!((System.currentTimeMillis() - startMs) >= duration)) return;
        startMs = System.currentTimeMillis();
        refresh();

    }

    public void start() {
        running = true;
        startMs = System.currentTimeMillis();
    }

    public void stop() {
        running = false;
    }

    public void refresh() {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body ->
                        JsonParser.parseString(body).getAsJsonObject()
                )
                .thenAccept(root -> {

                    JsonObject products = root.getAsJsonObject("products");
                    buyOrderList.forEach(item -> outbidScanner(products, item, false));
                    sellOrderList.forEach(item -> outbidScanner(products, item, true));

                    for (BazaarMonitorItem list : buyOrderList) {
                        if (!list.getOutbid()) return;
                        buyOrderList.remove(list);
                        getOutbidBuyOrderBookList.add(list.book);
                    }

                    for (BazaarMonitorItem list : sellOrderList) {
                        if (!list.getOutbid()) return;
                        buyOrderList.remove(list);
                        getOutbidBuyOrderBookList.add(list.book);
                    }
                });

    }

    public void outbidScanner(JsonObject products, BazaarMonitorItem bazaarMonitorItem, boolean isSellOrder) {
        JsonObject productID = products.getAsJsonObject(bazaarMonitorItem.book.getLevel(bazaarMonitorItem.book.level()));
        if (isSellOrder) {
            JsonObject entry = productID.getAsJsonArray("sell_summary").get(0).getAsJsonObject();
            int orders = entry.get("orders").getAsInt();
            double price = entry.get("pricePerUnit").getAsDouble();

            if (orders > 1 || price != bazaarMonitorItem.price) bazaarMonitorItem.setOutbid(true);
        } else {
            JsonObject entry = productID.getAsJsonArray("buy_summary").get(0).getAsJsonObject();
            int orders = entry.get("orders").getAsInt();
            double price = entry.get("pricePerUnit").getAsDouble();

            if (orders > 1 || price != bazaarMonitorItem.price) bazaarMonitorItem.setOutbid(true);
        }

    }



    private class BazaarMonitorItem {
        private Book book;
        private double price;
        private boolean isOutbid = false;

        public BazaarMonitorItem(Book book, double price) {
            this.book = book;
            this.price = price;
        }

        public void setOutbid(boolean outbid) {
            isOutbid = outbid;
        }

        public boolean getOutbid() {
            return isOutbid;
        }
    }

}

