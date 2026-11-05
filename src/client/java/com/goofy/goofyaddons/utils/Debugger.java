package com.goofy.goofyaddons.utils;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Debugger {
    static Path goofyaddons;
    static String timestamp;

    public static void load() {
        try {
            goofyaddons = FabricLoader.getInstance().getGameDir().resolve("goofyaddons");
            Files.createDirectories(goofyaddons);
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_hh-mm-ss-a"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void write(String featureName, String line) {
        String lineToWrite = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")) + "] " + line + System.lineSeparator();
        try {
            Path debugFilePath = goofyaddons.resolve(featureName);
            Files.createDirectories(debugFilePath);

            Files.writeString(debugFilePath.resolve("debug-" + timestamp + ".txt"), lineToWrite, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
