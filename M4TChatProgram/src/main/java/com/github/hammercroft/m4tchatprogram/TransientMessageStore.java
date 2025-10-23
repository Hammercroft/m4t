package com.github.hammercroft.m4tchatprogram;

/**
 * A fixed-capacity, time-aware storage for transient message IDs with monotonic
 * timestamps.
 * <p>
 * Each entry is a {@link Pair} of a monotonic timestamp (nanoseconds) and a
 * transient message ID (short). The buffer behaves as a circular array with a
 * maximum capacity of 256 entries.
 * </p>
 * <p>
 * Behavior rules:
 * <ul>
 * <li>When the buffer exceeds 64 entries, entries older than 30 seconds (based
 * on monotonic time) are removed from the front.</li>
 * <li>When the buffer reaches 256 entries, new entries overwrite the oldest
 * entries.</li>
 * <li>Supports checking if a transient message ID exists in the current
 * buffer.</li>
 * </ul>
 */
@SuppressWarnings("unchecked")
public class TransientMessageStore {

    private final Pair<Long, Short>[] buffer = new Pair[256]; // fixed capacity
    private int head = 0;  // oldest element
    private int tail = 0;  // next free slot
    private int size = 0;

    private static final int AGE_CHECK_THRESHOLD = 64;
    private static final long AGE_LIMIT_NS = 30_000_000_000L; // 30 seconds in nanoseconds

    /**
     * Pushes a new transient message ID into the buffer.
     * <p>
     * Internally, the method records a monotonic timestamp using
     * {@link System#nanoTime()}. Old entries are pruned if they exceed the age
     * limit and the size threshold.
     * </p>
     *
     * @param messageId The transient message ID to store.
     */
    public void push(short messageId) {
        long now = System.nanoTime();
        Pair<Long, Short> newPair = new Pair<>(now, messageId);

        buffer[tail] = newPair;
        tail = (tail + 1) & 0xFF; // wrap around 256
        if (size < 256) {
            size++;
        } else {
            head = tail; // overwrite oldest
        }

        // Step 1: prune old pairs if size > 64
        if (size > AGE_CHECK_THRESHOLD) {
            while (size > 0) {
                Pair<Long, Short> oldest = buffer[head];
                if (now - oldest.getFirst() > AGE_LIMIT_NS) {
                    head = (head + 1) & 0xFF;
                    size--;
                } else {
                    break;
                }
            }
        }

        // Step 2: hard max size enforced automatically by overwrite
    }

    /**
     * Checks if the given transient message ID exists in the buffer.
     *
     * @param messageId The transient message ID to check for.
     * @return {@code true} if the message ID exists in the buffer,
     * {@code false} otherwise.
     */
    public boolean contains(short messageId) {
        int idx = head;
        for (int i = 0; i < size; i++) {
            if (buffer[idx].getSecond() == messageId) {
                return true;
            }
            idx = (idx + 1) & 0xFF;
        }
        return false;
    }

    /**
     * Returns the current number of entries stored in the buffer.
     *
     * @return The number of stored pairs.
     */
    public int size() {
        return size;
    }
}
