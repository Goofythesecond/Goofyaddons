package com.goofy.goofyaddons.features.bookflipper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class FlipCalculator {
    private HttpClient client = HttpClient.newHttpClient();
    private final List<Book> books = new ArrayList<>();
    private final Map<String, BazaarData> bazaar = new HashMap<>();
    public final List<FlipItem> flipItemsList = new ArrayList<>();

    public FlipCalculator() {
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISE", 1, 5, "Ultimate Wise"));
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISE", 2, 5, "Ultimate Wise"));
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISDOM", 1, 5, "Wisdom"));
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISDOM", 2, 5, "Wisdom"));
        books.add(new Book("ENCHANTMENT_ULTIMATE_LAST_STAND", 1, 5, "Last Stand"));
        books.add(new Book("ENCHANTMENT_ULTIMATE_LAST_STAND", 2, 5, "Last Stand"));
    }


    public void Refresh() {
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

                    for (Book book : this.books) {

                        loadProduct(products, book.getLevel(book.level()));
                        loadProduct(products, book.getLevel(book.sellLevel()));


                    }
                    processData();
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

        for (Book book : books) {

            BazaarData buyData = bazaar.get(book.getLevel(book.level()));
            BazaarData sellData = bazaar.get(book.getLevel(book.sellLevel()));

            if (buyData == null || sellData == null) continue;
            int qty = book.getQtyAmount(book.level());
            double cost = buyData.buyPrice() * qty;

            double revenue = sellData.sellPrice();

            double profit = revenue - cost;
            if (profit <= 0 || cost <= 0) continue;
            double score = profit * Math.log10(sellData.sellVolume() + 1) / Math.sqrt(cost);

            flipItemsList.add(new FlipItem(book, cost, score));
        }
        flipItemsList.sort(Comparator.comparingDouble(FlipItem::score).reversed());

    }

}
