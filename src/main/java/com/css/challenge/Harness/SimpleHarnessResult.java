package com.css.challenge.Harness;

import com.css.challenge.Kitchen;
import com.css.challenge.client.Action;

import java.util.List;

public class SimpleHarnessResult {
    private final Kitchen kitchen;
    private final List<Action> actions;
    private final long startTimeMillis;
    private final long endTimeMillis;

    public SimpleHarnessResult(
            Kitchen kitchen,
            List<Action> actions,
            long startTimeMillis,
            long endTimeMillis) {
        this.kitchen = kitchen;
        this.actions = actions;
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
    }

    public List<Action> getActions() {
        return actions;
    }

    public int getActionsCount(){
        return actions.size();
    }

    public long getDurationMillis(){
        return endTimeMillis - startTimeMillis;
    }
}
