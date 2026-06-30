package com.goofy.goofyaddons.utils;

public class Clock {
    private long duration;
    private long startMs;
    private boolean running = false;

    public void start(long ms) {
        if (running) return;
        this.duration = ms;
        this.startMs = System.currentTimeMillis();
        this.running = true;
    }

    public boolean shouldFire() {
        if (!running) return false;
        if (System.currentTimeMillis() - startMs >= duration) {
            stop();
            return true;
        }
        return false;
    }

    public void reset() {
        startMs = System.currentTimeMillis();
    }

    public void stop() {
        running = false;
    }

    public boolean returnState() {
        return running;
    }
}