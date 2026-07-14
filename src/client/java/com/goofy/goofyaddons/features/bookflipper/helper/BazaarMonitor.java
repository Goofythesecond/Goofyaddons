package com.goofy.goofyaddons.features.bookflipper.helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public class BazaarMonitor {
    private boolean running = false;
    private HttpClient client = HttpClient.newHttpClient();
    private long duration = 15000;
    private long startMs;
    private long lastUpdated;
    private final List<BazaarMonitorItem> monitorItemList = new ArrayList<>();
    private final List<Consumer<Book>> hookList = new ArrayList<>();

    public void add(Book book, double price, boolean isSellOrder) {
        monitorItemList.add(new BazaarMonitorItem(book, price, isSellOrder));
    }

    public void finish(Book book) {
        monitorItemList.removeIf(bazaarMonitorItem -> {
            if (monitorItemList.contains(book)) return true;
            return false;
        });
    }

    public void reset() {
        monitorItemList.clear();
    }

    public void hook(Consumer<Book> hook) {
        hookList.add(hook);
    }


    public void onTick() {
        if (!running) return;
        if (!((System.currentTimeMillis() - startMs) >= duration)) return;
        if (monitorItemList.isEmpty()) return;
        startMs = System.currentTimeMillis();
        refresh();

    }

    public void start() {
        if (running) return;
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

                    long lastUpdated = root.get("lastUpdated").getAsLong();

                    if (lastUpdated == this.lastUpdated) return;

                    this.lastUpdated = lastUpdated;

                    JsonObject products = root.getAsJsonObject("products");

                    monitorItemList.forEach(bazaarMonitorItem -> outbidScanner(products, bazaarMonitorItem));

                    monitorItemList.removeIf(bazaarMonitorItem -> {
                        if (bazaarMonitorItem.getOutbid()) return true;
                        return false;
                    });
                });

    }

    private void outbidScanner(JsonObject products, BazaarMonitorItem bazaarMonitorItem) {
        if (!bazaarMonitorItem.shouldCheck()) return;
        JsonObject productID = products.getAsJsonObject(bazaarMonitorItem.book.getLevel(bazaarMonitorItem.book.level()));
        if (!bazaarMonitorItem.isSellOrder) {
            JsonObject entry = productID.getAsJsonArray("sell_summary").get(0).getAsJsonObject();
            int orders = entry.get("orders").getAsInt();
            double price = entry.get("pricePerUnit").getAsDouble();

            if (orders > 1 || price != bazaarMonitorItem.price) {
                bazaarMonitorItem.setOutbid(true);
                handleOutbid(bazaarMonitorItem);
            }
        } else {
            JsonObject entry = productID.getAsJsonArray("buy_summary").get(0).getAsJsonObject();
            int orders = entry.get("orders").getAsInt();
            double price = entry.get("pricePerUnit").getAsDouble();

            if (orders > 1 || price != bazaarMonitorItem.price) {
                bazaarMonitorItem.setOutbid(true);
                handleOutbid(bazaarMonitorItem);
            }
        }

    }

    private void handleOutbid(BazaarMonitorItem bazaarMonitorItem) {
        hookList.getFirst().accept(bazaarMonitorItem.book);
    }



    private class BazaarMonitorItem {
        private boolean isSellOrder;
        private Book book;
        private double price;
        private boolean isOutbid = false;
        private long time;

        public BazaarMonitorItem(Book book, double price, boolean isSellOrder) {
            this.book = book;
            this.price = price;
            this.isSellOrder = isSellOrder;
            time = System.currentTimeMillis();
        }

        private void setOutbid(boolean outbid) {
            isOutbid = outbid;
        }

        private boolean getOutbid() {
            return isOutbid;
        }

        private boolean shouldCheck() {
            if (!((System.currentTimeMillis() - time) >= duration)) return false;
            time = System.currentTimeMillis();
            return true;
        }
    }

}

