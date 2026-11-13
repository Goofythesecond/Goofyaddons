package com.goofy.goofyaddons.failsafes;

import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.utils.Clock;
import com.goofy.goofyaddons.utils.TabUtils;
import com.goofy.goofyaddons.utils.helpers.Islands;
import net.minecraft.client.Minecraft;

public class ServerRebootFailsafe implements Failsafe {
    enum State {
        ISLAND,
        HUB,
        COMPLETED
    }

    private boolean enabled = false;
    private State state;
    private Clock clock = new Clock();


    public ServerRebootFailsafe() {
        ChatHook.onMessage("Scheduled Reboot", this::handleMessage);
        ChatHook.onMessage("Game Update", this::handleMessage);
    }

    @Override
    public String name() {
        return "ScheduledReboot";
    }

    @Override
    public void onTick() {
        if (!enabled) return;


        switch (state) {
            case ISLAND -> {
                if (TabUtils.getIsland() == Islands.PRIVATE_ISLAND) {
                    Minecraft.getInstance().player.connection.sendCommand("hub");
                    return;
                }
                state = State.HUB;
            }

            case HUB -> {
                if (TabUtils.getIsland() == Islands.HUB) {
                    clock.start(5000);
                    if (clock.shouldFire()) {
                        Minecraft.getInstance().player.connection.sendCommand("Is");
                        state = State.COMPLETED;
                    }
                    return;
                }
                state = State.COMPLETED;
            }

            case COMPLETED -> {
                clock.start(5000);
                if (clock.shouldFire() && TabUtils.getIsland() == Islands.PRIVATE_ISLAND) {
                    FeatureManager.INSTANCE.resume();
                    enabled = false;
                }
            }
        }

    }

    private void handleMessage(String message) {
        FeatureManager.INSTANCE.pause();
        enabled = true;
        state = State.ISLAND;
    }
}
