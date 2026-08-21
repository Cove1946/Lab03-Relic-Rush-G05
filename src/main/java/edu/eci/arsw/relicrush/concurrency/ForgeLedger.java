package edu.eci.arsw.relicrush.concurrency;

import edu.eci.arsw.relicrush.model.ForgeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Global match ledger.
 *
 * Starter implementation is intentionally NOT thread-safe.
 */
public final class ForgeLedger {
    private final Object lock = new Object();
    private int totalCrafted = 0;
    private final List<ForgeEvent> events = new ArrayList<>();

    public void record(ForgeEvent event) {
        synchronized (lock) {
            totalCrafted++;
            events.add(event);
        }
    }

    public int totalCrafted() {
        synchronized (lock) {
            return totalCrafted;
        }
    }

    public int eventCount() {
        synchronized (lock) {
            return events.size();
        }
    }

    public List<ForgeEvent> snapshot() {
        synchronized (lock) {
            return List.copyOf(events);
        }
    }
}
