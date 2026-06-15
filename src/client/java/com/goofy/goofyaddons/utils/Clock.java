package com.goofy.goofyaddons.utils;

public class Clock {
    private long duration;
    private long startMs;
    private boolean running = false;

    public void start(long ms) {
        this.duration = ms;
        this.startMs = System.currentTimeMillis();
        this.running = true;
    }

    public boolean shouldFire() {
        if (!running) return false;
        return System.currentTimeMillis() - startMs >= duration;
    }

    public void reset() {
        startMs = System.currentTimeMillis();
    }

    public void stop() {
        running = false;
    }
}