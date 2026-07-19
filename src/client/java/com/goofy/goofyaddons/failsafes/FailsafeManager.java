package com.goofy.goofyaddons.failsafes;

import com.goofy.goofyaddons.features.FeatureManager;

import java.util.ArrayList;
import java.util.List;

public class FailsafeManager {
    List<Failsafe> failsafes = new ArrayList<>();

    public static FailsafeManager INSTANCE = new FailsafeManager();

    private FailsafeManager() {
        failsafes.add(new ScheduledReboot());
    }

    public void onTick() {
        if (!FeatureManager.INSTANCE.isMacroRunning()) return;
        failsafes.stream().forEach(failsafe -> failsafe.onTick());
    }


}
