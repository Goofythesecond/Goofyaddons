package com.goofy.goofyaddons.failsafes;

import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.utils.Clock;
import net.minecraft.client.Minecraft;

public class ScheduledReboot implements Failsafe {
    enum State {
        ISLAND,
        HUB,
        COMPLETED
    }

    private boolean enabled = false;
    private State state;
    private Clock clock = new Clock();


    public ScheduledReboot() {
        ChatHook.onMessage("Scheduled Reboot", this::handleMessage);
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
                Minecraft.getInstance().player.connection.sendCommand("Hub");
                state = State.HUB;
            }

            case HUB -> {
                clock.start(10000);
                if (clock.shouldFire()) {
                    Minecraft.getInstance().player.connection.sendCommand("Is");
                    state = State.COMPLETED;
                }
            }

            case COMPLETED -> {
                clock.start(5000);
                if (clock.shouldFire()) {
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
