package com.github.hammercroft.m4tchatprogram;

import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;

/**
 * An implementation with all necessary callbacks that allow the M4TChatProgram to interact with a user interface.
 * <p>
 * <strong>Important:</strong> The user interface must be fully initialized and
 * ready to handle these callbacks before the chat program itself is started.
 * <p>
 * Handles message delivery, program lifecycle events, and user prompts for
 * network configuration.
 */
public interface UserInterface {

    /**
     * Called when a message is received from the remote peer.
     *
     * @param messageId the ID of the received message
     * @param message the UTF-8 content of the message
     */
    void handleRecievedMessage(int messageId, String message);

    /**
     * Called once when the chat program starts and is ready.
     */
    void handleChatProgramStart();

    /**
     * Called to notify the UI of program events or state changes.
     *
     * @param notificationData a map containing event information (keys and values)
     */
    void handleProgramNotification(Map<String,Object> notificationData);

    /**
     * Called when the program is shutting down.
     */
    void handleShutdown();

    /**
     * Prompts the user for the local port to use.
     *
     * @return an Optional containing the chosen port, or empty if the user interface cannot facilitate this prompt.
     */
    Optional<Integer> resolveOurPortFromUser();

    /**
     * Prompts the user for the remote peer address and port.
     *
     * @return an Optional containing the chosen address and port pair, or empty if the user interface cannot facilitate this prompt.
     */
    Optional<Pair<InetAddress,Integer>> resolveTargetfromUser();
}
