package com.goofy.goofyaddons.features;

import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper;

import java.util.ArrayList;
import java.util.List;


public class FeatureManager {
    List<Feature> featureList = new ArrayList<>();
    Feature currentFeature = null;


    public static final FeatureManager INSTANCE = new FeatureManager();

    private FeatureManager() {
        featureList.add(new BazaarFlipper());
    }

    public void onTick() {
        if (currentFeature == null) return;
        currentFeature.onTick();
    }

    public void start(String name) {
        currentFeature = featureList.stream().filter(feature -> feature.name().equals(name)).findFirst().orElse(null);
        if (currentFeature == null) return;
        currentFeature.start();
    }

    public void stop() {
        if (currentFeature == null) return;
        currentFeature.stop();
        currentFeature = null;
    }

    public void pause() {
        if (currentFeature == null) return;
        currentFeature.pause();

    }

    public void resume() {
        if (currentFeature == null) return;
        currentFeature.resume();
    }

    public boolean isMacroRunning() {
        return currentFeature != null;
    }

}
