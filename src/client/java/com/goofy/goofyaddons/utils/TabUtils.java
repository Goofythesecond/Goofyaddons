package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.utils.helpers.Islands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

public class TabUtils {

    public static Islands getIsland() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) return Islands.UNKNOWN;

        for (PlayerInfo info : connection.getOnlinePlayers()) {
            if (info.getTabListDisplayName() == null) continue;
            if (info.getTabListDisplayName().toString().isBlank() || info.getTabListDisplayName().toString() == null)
                continue;
            if (!info.getTabListDisplayName().toString().contains("Area")) continue;
            return parseString(info.getTabListDisplayName().getString());
        }
        return Islands.UNKNOWN;
    }

    private static Islands parseString(String string) {
        string = string.toLowerCase();
        string = string.replace("area: ", "");

        switch (string) {
            case "hub" -> {
                return Islands.HUB;
            }

            case "private island" -> {
                return Islands.PRIVATE_ISLAND;
            }

            default -> {
                return Islands.UNKNOWN;
            }
        }
    }
}
