package com.goofy.goofyaddons.config;

import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GoofyConfig {
    public List<Book> books = new ArrayList<>();


    public GoofyConfig() {
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISE", 1, 5, "Ultimate Wise"));
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISE", 2, 5, "Ultimate Wise"));
        // books.add(new Book("ENCHANTMENT_ULTIMATE_WISDOM", 1, 5, "Wisdom"));
        // books.add(new Book("ENCHANTMENT_ULTIMATE_WISDOM", 2, 5, "Wisdom"));
        // books.add(new Book("ENCHANTMENT_ULTIMATE_LAST_STAND", 1, 5, "Last Stand"));
        // books.add(new Book("ENCHANTMENT_ULTIMATE_LAST_STAND", 2, 5, "Last Stand"));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("goofyaddons.json");

    public static GoofyConfig INSTANCE;


    public int startKey = GLFW.GLFW_KEY_J;
    public int stopKey = GLFW.GLFW_KEY_K;
    public int outbidRefreshDelay = 15000;
    public int minActionDelay = 100;
    public int maxActionDelay = 500;

    // When true, uses Insta-Buy/Insta-Sell instead of buy orders and sell offers.
    // Faster cycles (no waiting for orders to fill), but pays the bazaar spread twice,
    // so profit per flip is lower. FlipCalculator prices flips accordingly.
    public boolean instantMode = false;


    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(
                        Files.readString(CONFIG_PATH),
                        GoofyConfig.class
                );
            } else {
                INSTANCE = new GoofyConfig();
                save();
            }
        } catch (Exception e) {
            e.printStackTrace();
            INSTANCE = new GoofyConfig();
        }
    }

    public static void save() {
        try {
            Files.writeString(
                    CONFIG_PATH,
                    GSON.toJson(INSTANCE)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




}
