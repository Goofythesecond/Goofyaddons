package com.goofy.goofyaddons.features.bookflipper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;


public class ItemPicker {

    private final List<Book> bookList = new ArrayList<>();

    public ItemPicker() {
        bookList.add(new Book("ENCHANTMENT_ULTIMATE_WISE", 2, 5, "Ultimate Wise"));
        bookList.add(new Book("ENCHANTMENT_ULTIMATE_WISDOM", 2, 5, "Wisdom"));
        bookList.add(new Book("ENCHANTMENT_ULTIMATE_LAST_STAND", 2, 5, "Last Stand"));
    }

    private static double purse;




    public static String getItem() {
        String item = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Scoreboard scoreboard = minecraft.level.getScoreboard();
            Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null) {

                for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
                    String fakePlayer = entry.owner();

                    PlayerTeam team = scoreboard.getPlayersTeam(fakePlayer);

                    if (team != null) {
                        String line =
                                team.getPlayerPrefix().getString()
                                        + fakePlayer
                                        + team.getPlayerSuffix().getString();
                        if (line.contains("Purse")) {
                            purse = Double.parseDouble(
                                    line.replace("Purse:", "")
                                            .replaceAll("§.", "")  // strips ALL formatting codes like §q, §i etc
                                            .replaceAll("[^0-9.]", "")  // strips everything except digits and decimal point
                                            .trim()
                            );
                            Double price = getSellPrice("ENCHANTMENT_ULTIMATE_WISE_2")*8;
                            if (purse >= price) {
                                item = "WISE 2";
                            }
                            else {
                                item = "Not Enough";
                            }
                        }
                    }
                }
            }
        }
        return item;
    };


    private static double getSellPrice(String itemId) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonObject root =
                    JsonParser.parseString(response.body()).getAsJsonObject();

            JsonObject products =
                    root.getAsJsonObject("products");

            JsonObject item =
                    products.getAsJsonObject(itemId);

            if (item == null) {
                return -1;
            }

            return item
                    .getAsJsonObject("quick_status")
                    .get("sellPrice")
                    .getAsDouble();

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }

    }

}
