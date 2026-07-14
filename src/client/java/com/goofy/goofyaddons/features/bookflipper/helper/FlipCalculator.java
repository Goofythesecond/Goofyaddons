package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class FlipCalculator {
    private boolean running = false;
    private HttpClient client = HttpClient.newHttpClient();
    private final Map<String, BazaarData> bazaar = new HashMap<>();
    public final List<FlipItem> flipItemsList = new ArrayList<>();



    public void Refresh() {
        if (running) return;
        running = true;
        bazaar.clear();
        flipItemsList.clear();

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

                    JsonObject products =
                            root.getAsJsonObject("products");

                    for (Book book : GoofyConfig.INSTANCE.books) {

                        loadProduct(products, book.getLevel(book.level()));
                        loadProduct(products, book.getLevel(book.sellLevel()));

                    }
                    processData();
                    running = false;
                });

    }

    private void loadProduct(JsonObject products, String productId) {
        JsonObject product = products.getAsJsonObject(productId);
        if (product == null) return;

        JsonObject quick = product.getAsJsonObject("quick_status");

        bazaar.put(productId, new BazaarData(
                productId,
                quick.get("sellPrice").getAsDouble(),
                quick.get("sellVolume").getAsInt(),
                quick.get("buyPrice").getAsDouble()
        ));
    }

    private void processData() {

        flipItemsList.clear();

        for (Book book : GoofyConfig.INSTANCE.books) {

            BazaarData buyData = bazaar.get(book.getLevel(book.level()));
            BazaarData sellData = bazaar.get(book.getLevel(book.sellLevel()));

            if (buyData == null || sellData == null) continue;

            int qty = book.getQtyAmount(book.level());

            // Order mode buys at the top buy-order price (sellPrice) and sells at the top
            // sell-offer price (buyPrice). Instant mode pays the instant-buy price (buyPrice)
            // and receives the instant-sell price (sellPrice), so both sides of the spread apply.
            boolean instant = GoofyConfig.INSTANCE.instantMode;
            double cost = (instant ? buyData.buyPrice() : buyData.sellPrice()) * qty;
            double revenue = instant ? sellData.sellPrice() : sellData.buyPrice();

            double profit = revenue - cost;

            if (profit <= 0 || cost <= 0) continue;

            double score = profit * Math.log10(sellData.sellVolume() + 1) / Math.sqrt(cost);

            flipItemsList.add(new FlipItem(book, cost, score));
        }

        flipItemsList.sort(Comparator.comparingDouble(FlipItem::score).reversed());
    }

    public List<FlipItem> getFlipItemsList() {
        return flipItemsList;
    }

}
