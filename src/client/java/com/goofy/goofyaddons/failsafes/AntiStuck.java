package com.goofy.goofyaddons.failsafes;

public class AntiStuck implements Failsafe {
    @Override
    public String name() {
        return "AntiStuck";
    }

    @Override
    public void onTick() {
        
    }
}
