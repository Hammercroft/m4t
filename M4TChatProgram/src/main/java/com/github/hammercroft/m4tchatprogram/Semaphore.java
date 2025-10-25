package com.github.hammercroft.m4tchatprogram;

/**
 * Defines network-level control messages used by M4T.
 * <p>
 * For detailed information on semaphore behavior and message structure, see:
 * <a href="https://github.com/Hammercroft/m4t/wiki/M4T-Messaging-Scheme">
 * M4T Messaging Scheme
 * </a>
 * </p>
 */

public enum Semaphore {
    /**
     * General Message Acknowledgement (GMA) semaphore.
     * Indicates a chat message has been received.
     */
    S_ACK("|^~ACK"),
    
    /**
     * Salve semaphore.
     * Indicates that the sender is active.
     */
    S_SALVE("|^~SALVE"),
    
    /**
     * Et Tu Salve semaphore.
     * Acknowledges the other peer’s identity. Also indicates that the sender is also active.
     * Additionally, it may or may not provide a session discriminator that the receiver may
     * require to use in all future outbound payloads.
     */
    S_E2SALVE("|^~E2SALVE"),
    
    /**
     * Keep-Alive semaphore.
     * Signals connectivity, sent periodically (ideally every 3 seconds).
     */
    S_KA("|^~KA");

    private final String token;

    Semaphore(String token) {
        this.token = token;
    }

    /**
     * Shorthand alternative for Semaphore.toString();
     * @return The semaphore message token associated with this enum.
     */
    public String token() {
        return token;
    }

    /**
     * Returns a token string that identifies messages of this semaphore.
     * @return The semaphore message token associated with this enum.
     */
    @Override
    public String toString() {
        return token;
    }
}
