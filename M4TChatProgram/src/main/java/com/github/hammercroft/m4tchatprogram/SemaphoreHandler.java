package com.github.hammercroft.m4tchatprogram;

import static com.github.hammercroft.m4tchatprogram.Semaphore.*;

import java.io.IOException;
import java.util.Map;

/**
 * Handles network-level semaphore and control messages for the M4TChatProgram.
 *
 * <p>This class is responsible for parsing incoming semaphore messages,
 * performing validation, notifying the user interface about malformed messages,
 * updating session state, and sending responses back to the peer as needed.
 *
 * <p>Messages not matching any known prefix may be normal chat messages
 * and should be handled separately.
 */
public class SemaphoreHandler {

    private final M4TChatProgram chatProgram;

    /**
     * Creates a new SemaphoreHandler for a given chat program instance.
     *
     * @param chatProgram the M4TChatProgram instance this handler belongs to
     */
    public SemaphoreHandler(M4TChatProgram chatProgram) {
        this.chatProgram = chatProgram;
    }

    /**
     * Handles a received semaphore/control message.
     *
     * <p>This method parses known prefixes, validates message content, updates
     * session state, notifies the user interface of malformed messages, and
     * sends any required responses.
     *
     * @param received the payload received from a peer
     * @return {@code true} if the message should be processed as a normal chat message,
     *         {@code false} if it was handled as a semaphore/control message
     * @throws IOException if sending a response message fails
     */
    public boolean handle(Payload received) throws IOException {
        String content = received.getContent();

        // S_ACK handling
        if (content.startsWith(S_ACK.token())) {
            String ackData = content.substring(S_ACK.token().length()).trim();

            int firstSpace = ackData.indexOf(' ');
            if (firstSpace == -1) {
                chatProgram.userInterface.handleProgramNotification(Map.of(
                        "Topic", "MALFORMED_ACK_MISSING_SPACE_SEPARATOR"
                ));
                return false;
            }

            String idPart = ackData.substring(0, firstSpace);

            int secondSpace = ackData.indexOf(' ', firstSpace + 1);
            if (secondSpace == -1) {
                chatProgram.userInterface.handleProgramNotification(Map.of(
                        "Topic", "MALFORMED_ACK_MISSING_SESSION_OR_MESSAGE",
                        "MessageId", idPart
                ));
                return false;
            }

            String sessionPart = ackData.substring(firstSpace + 1, secondSpace);
            @SuppressWarnings("unused")
            String acknowledgedMsg = ackData.substring(secondSpace + 1);

            try {
                @SuppressWarnings("unused")
                short msgId = Short.parseShort(idPart);
                @SuppressWarnings("unused")
                short session = Short.parseShort(sessionPart);
            } catch (NumberFormatException e) {
                chatProgram.userInterface.handleProgramNotification(Map.of(
                        "Topic", "MALFORMED_ACK_INVALID_ID_OR_SESSION",
                        "MessageId", idPart,
                        "Session", sessionPart
                ));
                return false;
            }

            return false;
        }

        // SEM_SALVE handling
        if (content.startsWith(S_SALVE.token())) {
            chatProgram.userInterface.handleProgramNotification(Map.of("Topic", "TARGET_PEER_ONLINE"));
            String incomingIdentity = content.substring(S_SALVE.token().length()).trim();
            String ackMessage = String.format("%s %s %s",
                    S_E2SALVE.token(),
                    incomingIdentity,
                    chatProgram.state.getOurIdentityToken());
            chatProgram.sendMessage(ackMessage);
            return false;
        }

        // SEM_ETTUSALVE handling
        if (content.startsWith(S_E2SALVE.token())) {
            String data = content.substring(S_E2SALVE.token().length()).trim();
            String[] parts = data.split(" ");

            if (parts.length < 2) {
                chatProgram.userInterface.handleProgramNotification(Map.of(
                        "Topic", "MALFORMED_ETTUSALVE",
                        "Content", data
                ));
                return false;
            }

            @SuppressWarnings("unused")
            String acknowledgedToken = parts[0];
            @SuppressWarnings("unused")
            String selfToken = parts[1];

            if (parts.length >= 3) {
                try {
                    short session = Short.parseShort(parts[2]);
                    chatProgram.state.setSessionDiscriminator(session);
                } catch (NumberFormatException e) {
                    chatProgram.userInterface.handleProgramNotification(Map.of(
                            "Topic", "MALFORMED_ETTUSALVE_INVALID_SESSION",
                            "SessionPart", parts[2]
                    ));
                    return false;
                }
            }

            return false;
        }

        // SEM_KA (keep-alive)
        if (content.startsWith(S_KA.token())) {
            return false;
        }

        // Default: normal message
        return true;
    }
}