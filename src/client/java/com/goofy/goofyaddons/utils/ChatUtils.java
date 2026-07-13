package com.goofy.goofyaddons.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ChatUtils {
    private static final Minecraft minecraft = Minecraft.getInstance();
    private static final Component PREFIX = Component.empty()
            .append(Component.literal("[").withStyle(s -> s.withColor(0x8099FF)))
            .append(Component.literal("G").withStyle(s -> s.withColor(0x8099FF)))
            .append(Component.literal("o").withStyle(s -> s.withColor(0x8099FF)))
            .append(Component.literal("o").withStyle(s -> s.withColor(0x88B0FF)))
            .append(Component.literal("f").withStyle(s -> s.withColor(0x88B0FF)))
            .append(Component.literal("y").withStyle(s -> s.withColor(0x88B0FF)))
            .append(Component.literal("A").withStyle(s -> s.withColor(0x91C7FF)))
            .append(Component.literal("d").withStyle(s -> s.withColor(0x91C7FF)))
            .append(Component.literal("d").withStyle(s -> s.withColor(0x8CE0FD)))
            .append(Component.literal("o").withStyle(s -> s.withColor(0x8CE0FD)))
            .append(Component.literal("n").withStyle(s -> s.withColor(0x8CE0FD)))
            .append(Component.literal("s").withStyle(s -> s.withColor(0xA2F5FF)))
            .append(Component.literal("]").withStyle(s -> s.withColor(0xA2F5FF)));


    public static void clientMessage(String message) {
        if (minecraft.player == null) return;

        minecraft.player.sendSystemMessage(PREFIX.copy().append(" " + message));
    }

    public static void debugMessage(String message) {
        if (minecraft.player == null) return;

        minecraft.player.sendSystemMessage(PREFIX.copy().append(" ").append(Component.literal(message).withStyle(ChatFormatting.GRAY)));

    }



}
