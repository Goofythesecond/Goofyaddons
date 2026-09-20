package com.goofy.goofyaddons.event;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatHook {
    private static List<HOOK> hookList = new ArrayList<>();

    public static void register() {
        ClientReceiveMessageEvents.GAME.register(ChatHook::onChatMessage);
    }

    public static void onMessage(String pattern, Consumer<String> string) {
        hookList.add(new HOOK(pattern, string));
    }



    private static void onChatMessage(Component message, boolean overlay) {
        if (overlay == true) return;
        String text = message.getString().replaceAll("§.", "");
        for (HOOK hook : hookList) {
            if (!text.contains(hook.pattern)) continue;
            hook.string.accept(text);
        }
    }

    record HOOK(String pattern, Consumer<String> string) {}
}
