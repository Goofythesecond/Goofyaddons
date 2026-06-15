package com.goofy.goofyaddons.features.bookflipper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ItemMonitor {
    private String itemId;
    private double myPrice;
    private volatile boolean outbid = false;
    private boolean running = false;
    private Thread monitorThread;

    public ItemMonitor(String itemId, double myPrice) {
        this.itemId = itemId;
        this.myPrice = myPrice;
        start();
    }

    public void start() {
        running = true;
        outbid = false;
        monitorThread = new Thread(() -> {
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                return;
            }
            while (running) {
                try {
                    JsonObject data = fetchBazaar();
                    JsonObject product = data.getAsJsonObject("products")
                            .getAsJsonObject(itemId);
                    boolean foundMyOrder = false;
                    for (int j = 0; j < product.getAsJsonArray("buy_summary").size(); j++) {
                        JsonObject entry = product.getAsJsonArray("buy_summary")
                                .get(j).getAsJsonObject();
                        double price = entry.get("pricePerUnit").getAsDouble();
                        if (price == myPrice) {
                            foundMyOrder = true;
                            int orders = entry.get("orders").getAsInt();
                            if (orders > 1) {
                                outbid = true;
                                running = false;
                            }
                            break;
                        }
                    }
                    if (!foundMyOrder) {
                        outbid = true;
                        running = false;
                    }
                    if (running) {
                        Thread.sleep(60000);
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public void setItem(String itemId, double myPrice) {
        this.itemId = itemId;
        this.myPrice = myPrice;
        this.outbid = false;
        finish();
        start();
    }

    public void finish() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }

    public boolean isOutbid() {
        return outbid;
    }

    private static JsonObject fetchBazaar() throws Exception {
        URL url = new URL("https://api.hypixel.net/v2/skyblock/bazaar");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return JsonParser.parseString(response.toString()).getAsJsonObject();
    }
}