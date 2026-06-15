package com.goofy.goofyaddons.utils;

import java.util.ArrayList;
import java.util.List;

public class Scheduler {
    private int ticks = 0;
    private boolean firedThisTick = false;
    final private List<ScheduledAction> actions = new ArrayList<>();

    public void at(int ticks, Runnable action) {
        this.actions.add(new ScheduledAction(ticks, -1, action));
    }

    public void every(int ticks, int interval, Runnable action) {
        this.actions.add(new ScheduledAction(ticks, interval, action));
    }

    public void tick() {
        for (ScheduledAction action : new ArrayList<>(actions)) {
            firedThisTick = false;
            if (action.shouldFire(ticks) && !firedThisTick) {
                firedThisTick = true;
                action.action.run();
            }
        }
        ticks++;
    }

    public void reset() {
        ticks = 0;
        firedThisTick = false;
        actions.clear();
    }

    public int getTicks() {
        return ticks;
    }



    private class ScheduledAction {
        int ticks;
        int loop;
        Runnable action;

        public ScheduledAction(int ticks, int loop, Runnable action) {
            this.ticks = ticks;
            this.loop = loop;
            this.action = action;
        }

        boolean shouldFire(int ticks) {
            if (ticks < this.ticks) return false;
            if (loop == -1) return ticks == this.ticks;
            if (loop == 0) return false;
            return (ticks - this.ticks) % loop == 0;
        }



    }

}
