package com.github.hammercroft.m4tchatprogram;

import java.io.IOException;
import java.util.Map;

/**
 * Handles dot-prefixed local commands for an {@link M4TChatProgram} instance.
 * <p>
 * This class delegates execution of commands such as <code>.EXIT</code>,
 * <code>.PROGRAMSTATE</code>, <code>.SALVE</code>, and <code>.POKE</code>
 * to the appropriate methods of the {@link M4TChatProgram}.
 * </p>
 */
public class LocalCommandHandler {

    private final M4TChatProgram chatProgram;

    /**
     * Constructs a new handler for the given chat program instance.
     *
     * @param chatProgram the chat program whose commands will be handled
     */
    public LocalCommandHandler(M4TChatProgram chatProgram) {
        this.chatProgram = chatProgram;
    }

    /**
     * Processes a single local command.
     * <p>
     * Recognized commands include:
     * <ul>
     *   <li><code>.EXIT</code> – shuts down the program.</li>
     *   <li><code>.PROGRAMSTATE</code> – prints the current program state to the user interface.</li>
     *   <li><code>.SALVE</code>, <code>.POKE</code> – sends a salve message via {@link M4TChatProgram#sendSalve()}.</li>
     *   <li>Unknown commands – triggers a notification for <code>UNKNOWN_LOCAL_COMMAND</code>.</li>
     * </ul>
     * </p>
     *
     * @param command the dot-prefixed local command to handle
     */
    void handle(String command) {
        if (command == null) return;

        switch (command.toUpperCase()) {
            case ".EXIT":
                chatProgram.shutdown(0);
                break;

            case ".PROGRAMSTATE":
                chatProgram.userInterface.handleProgramNotification(
                        Map.of("Topic", "PUSH_TEXT", "Text", chatProgram.state.toString())
                );
                break;

            case ".SALVE":
            case ".POKE":
                try {
                    chatProgram.sendSalve();
                } catch (IOException ex) {
                    System.getLogger(M4TChatProgram.class.getName())
                          .log(System.Logger.Level.ERROR, (String) null, ex);
                }
                break;

            default:
                chatProgram.userInterface.handleProgramNotification(
                        Map.of("Topic", "UNKNOWN_LOCAL_COMMAND")
                );
                break;
        }
    }
}
