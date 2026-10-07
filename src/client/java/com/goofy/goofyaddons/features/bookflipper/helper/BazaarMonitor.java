package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.utils.ChatUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;


public class BazaarMonitor {
    private boolean running = false;
    private HttpClient client = HttpClient.newHttpClient();
    private long duration = 20000;
    private long startMs;
    private long lastUpdated;
    private List<Task> taskList;
    private Set<Task> listOfTaskToChange;

    public BazaarMonitor(List<Task> taskList, Set<Task> listOfTaskToChange) {
        this.taskList = taskList;
        this.listOfTaskToChange = listOfTaskToChange;
    }

    public void add(Task task, double price, boolean isSellOrder) {
        if (task.inBuyOrder && task.inSellOffer) {
            ChatUtils.debugMessage("[BazaarMonitor] Book was rejected " + task.getBook().name() + " " + price + "sellorder=" + isSellOrder);
            return;
        }
        ChatUtils.debugMessage("[BazaarMonitor] Book was added " + task.getBook().name() + " " + price + "sellorder=" + isSellOrder);
        task.timeCounter = System.currentTimeMillis();
        task.priceUnit = price;
        if (isSellOrder) {
            task.inSellOffer = true;
        } else {
            task.inBuyOrder = true;
        }
    }

    public void finish(Task task, boolean isSellOrder) {
        System.out.println("[BazaarMonitor] Removing book " + task.getBook().name());
        if (isSellOrder) {
            task.inSellOffer = false;
        } else {
            task.inBuyOrder = false;
        }
    }

    public void reset() {
        for (Task task : taskList) {
            task.inBuyOrder = false;
            task.inSellOffer = false;
        }
    }


    public void onTick() {
        if (!running) return;
        if (!((System.currentTimeMillis() - startMs) >= duration)) return;
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

                    for (Task task : taskList) {
                        if (!task.inSellOffer && !task.inBuyOrder) continue;
                        outbidScanner(products, task);
                    }
                });

    }

    private void outbidScanner(JsonObject products, Task task) {
        if (!shouldCheck(task)) return;
        JsonObject productID = products.getAsJsonObject(task.inSellOffer ? task.getBook().getLevel(task.getBook().sellLevel()) : task.getBook().getLevel(task.getBook().level()));
        if (!task.inSellOffer) {
            JsonObject entry = productID.getAsJsonArray("sell_summary").get(0).getAsJsonObject();
            int orders = entry.get("orders").getAsInt();
            double price = entry.get("pricePerUnit").getAsDouble();

            if (orders > 1 || price != task.priceUnit) {
                handleOutbid(task);
            }
        } else {
            JsonObject entry = productID.getAsJsonArray("buy_summary").get(0).getAsJsonObject();
            int orders = entry.get("orders").getAsInt();
            double price = entry.get("pricePerUnit").getAsDouble();

            if (orders > 1 || price != task.priceUnit) {
                handleOutbid(task);
            }
        }

    }

    private void handleOutbid(Task task) {
        ChatUtils.debugMessage("[BazaarMonitor] outbidding book " + task.getBook().name());
        if (!task.inSellOffer && !task.inBuyOrder) {
            ChatUtils.debugMessage("[BazaarMonitor] book is already outbid " + task.getBook().name());
            return;
        }
        task.inSellOffer = false;
        task.inBuyOrder = false;
        listOfTaskToChange.add(task);
    }

    private boolean shouldCheck(Task task) {
        if (!((System.currentTimeMillis() - task.timeCounter) >= duration)) return false;
        task.timeCounter = System.currentTimeMillis();
        return true;
    }


}

