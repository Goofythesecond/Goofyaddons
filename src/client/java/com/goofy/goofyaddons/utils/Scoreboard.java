package com.goofy.goofyaddons.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;

public class Scoreboard {

    public double getPurse() {
        Double purse = (double) -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return -1;
        if (minecraft.level == null) return -1;

        net.minecraft.world.scores.Scoreboard scoreboard = minecraft.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

        if (sidebar == null) return -1;

        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            String fakePlayer = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(fakePlayer);

            if (team == null) continue;
            String line =
                    team.getPlayerPrefix().getString()
                            + fakePlayer
                            + team.getPlayerSuffix().getString();
            if (!line.contains("Purse")) continue;
                purse = Double.parseDouble(
                        line.replace("Purse:", "")
                                .replaceAll("§.", "")
                                .replaceAll("[^0-9.]", "")
                                .trim()
                );
        }
        return purse;
    }
}
