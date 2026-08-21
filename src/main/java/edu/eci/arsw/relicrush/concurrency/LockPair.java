package edu.eci.arsw.relicrush.concurrency;

import edu.eci.arsw.relicrush.model.ForgeStation;

/**
 * Acquires the two station monitors required by a craft operation.
 *
 * Deadlock prevention: the circular-wait Coffman condition is broken by imposing a
 * total order on the resources. Both monitors are always acquired from the lower
 * {@link ForgeStation#id()} to the higher one, regardless of the order in which the
 * caller passed them. A wait cycle would require some thread to block on a station
 * ranked below one it already holds, which this ordering makes impossible.
 *
 * Concurrency is preserved: locking stays per station, so craft operations on
 * disjoint station pairs still run fully in parallel. No global lock is introduced.
 *
 * Precondition: station ids must be unique. {@code GameEngine.createStations} assigns
 * 1..N, so the ordering is total. Duplicated ids would break the total order and
 * reintroduce the possibility of a cycle.
 */
public final class LockPair {

    private LockPair() {
    }

    public static void withBoth(ForgeStation first, ForgeStation second, Runnable action) {
        boolean firstIsLower = first.id() <= second.id();
        ForgeStation lower = firstIsLower ? first : second;
        ForgeStation higher = firstIsLower ? second : first;

        synchronized (lower) {
            // This small delay makes the deadlock easier to reproduce in the starter.
            sleepQuietly(2);
            synchronized (higher) {
                action.run();
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
