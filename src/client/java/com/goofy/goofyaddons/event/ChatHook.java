package com.goofy.goofyaddons.event;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

public class ChatHook {
    public static void register() {
        ClientReceiveMessageEvents.GAME.register(ChatHook::onChatMessage);
    }

    private static void onChatMessage(Component message, boolean overlay) {
        if (overlay == true) return;
        String text = message.getString().replaceAll("§.", "");
    }


}
