package com.github.hammercroft.m4tchatprogram;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.Terminal;

/**
 * A JLine-based terminal user interface for {@link M4TChatProgram}.
 * <p>
 * Handles input, output, and local command processing, as well as
 * resolving ports and target addresses from the user.
 * </p>
 * 
 * @author hammercroft
 */
public class JLineUserInterface implements UserInterface {

    M4TChatProgram chatProgram;
    Scanner scanner = new Scanner(System.in); // only for user port & address resolution
    Terminal terminal;
    LineReader reader;

    /**
     * Constructs a new JLine user interface for the given chat program.
     *
     * @param chatProgram the chat program instance to attach this UI to
     */
    public JLineUserInterface(M4TChatProgram chatProgram) {
        this.chatProgram = chatProgram;
    }

    // -----------------------------
    // Private helper methods
    // -----------------------------

    /**
     * Sends a user-inputted message through the chat program.
     *
     * @param message the message to send
     */
    private void sendInputtedMessage(String message) {
        try {
            chatProgram.sendMessage(message);
        } catch (IOException ex) {
            System.getLogger(JLineUserInterface.class.getName())
                  .log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

    /**
     * Delegates a user-inputted local command to the {@link LocalCommandHandler}.
     *
     * @param command the dot-prefixed local command to handle
     */
    private void handleInputtedLocalCommand(String command) {
        chatProgram.localCommandHandler.handle(command);
    }

    /**
     * Displays a notification message to the terminal or reader.
     *
     * @param message the message to display
     */
    private void displayNotification(String message) {
        if (reader == null) {
            System.out.println(message);
        } else {
            reader.printAbove(message);
        }
    }

    /**
     * Displays a map of key-value notification data as a fallback.
     *
     * @param notificationData the data to display
     */
    private void fallbackDisplay(Map<String, Object> notificationData) {
        if (reader == null) {
            for (Map.Entry<String, Object> entry : notificationData.entrySet()) {
                System.out.println(entry.getKey() + " -> " + entry.getValue());
            }
        } else {
            for (Map.Entry<String, Object> entry : notificationData.entrySet()) {
                reader.printAbove(entry.getKey() + " -> " + entry.getValue());
            }
        }
    }

    // -----------------------------
    // Lifecycle / event handlers
    // -----------------------------

    /**
     * Initializes the terminal and starts reading input from the user.
     * <p>
     * Handles both regular messages and dot-prefixed local commands.
     * </p>
     */
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void handleChatProgramStart() {
        try {
            this.terminal = TerminalBuilder.builder().system(true).build();
        } catch (IOException ex) {
            System.getLogger(JLineUserInterface.class.getName())
                  .log(System.Logger.Level.ERROR, (String) null, ex);
        }

        if (terminal == null || "dumb".equalsIgnoreCase(terminal.getType())) {
            System.out.println("WARNING: Using a dumb terminal.\nOutput may be broken, some features may not work, or this program may not work at all..\n"
                    + "For full functionality, run this program in a standard terminal\n(e.g., xterm, GNOME Terminal, PowerShell, Windows Terminal).\n");
        }

        this.scanner = null;
        this.reader = LineReaderBuilder.builder().terminal(this.terminal).build();

        new Thread(() -> {
            try {
                terminal.writer().println("(Communication Start.)");
                terminal.flush();
                while (chatProgram.running) {
                    String line = reader.readLine();
                    if (line.startsWith(".")) {
                        this.handleInputtedLocalCommand(line);
                    } else {
                        this.sendInputtedMessage(line);
                    }
                }
            } catch (EndOfFileException | UserInterruptException e) {
                System.out.println("Input terminated. Shutting down...");
                chatProgram.shutdown(0);
            }
        }).start();
    }

    /**
     * Displays a received message in the terminal.
     *
     * @param messageId the ID of the message
     * @param message the message content
     */
    @Override
    public void handleRecievedMessage(int messageId, String message) {
        reader.printAbove(" (+) " + message);
    }

    /**
     * Handles program notifications and displays appropriate messages to the
     * user.
     *
     * @param notificationData a map containing notification topic and
     * additional data
     * <p><b>Note:</b> This method calls the .toString() methods of the map values.
     * Ensure that the .toString() methods produce useful text for printing.
     */
    @Override
    public void handleProgramNotification(Map<String, Object> notificationData) {
        Object topicObj = notificationData.get("Topic");
        if (topicObj != null && topicObj instanceof String) {
            String topic = (String) topicObj; // explicit cast for Java 11
            switch (topic) {
                case "PUSH_TEXT":
                    displayNotification((String) notificationData.get("Text"));
                    break;
                case "UNKNOWN_LOCAL_COMMAND":
                    displayNotification("Unknown local command.");
                    break;
                case "AUTOMATIC_PORT_ASSIGNMENT":
                    displayNotification("Automatic port assigned: " + notificationData.get("Port"));
                    break;
                case "STARTUP_NOTICE":
                    String addr = chatProgram.state.getTheirAddress().getHostAddress();
                    int port = chatProgram.state.getTheirPort();
                    StringBuilder sb = new StringBuilder();
                    sb.append("--------------------------------------------------------------------------------\n");
                    sb.append("Starting communication to ").append(addr).append(":").append(port).append("\n");
                    sb.append("Due to the nature of delivery via UDP, the delivery of messages between you and your peer is not guaranteed.\n\n");
                    sb.append("RECEIVED MESSAGES ARE NOT GUARANTEED TO ORIGINATE FROM YOUR INTENDED COMMUNICATION TARGET.\n");
                    sb.append("YOUR MESSAGES ARE NOT ENCRYPTED.\n\n");
                    sb.append("To stop communication, do enter .exit\n");
                    sb.append("--------------------------------------------------------------------------------\n");
                    displayNotification(sb.toString());
                    break;
                case "MALFORMED_ACK_MISSING_SPACE_SEPARATOR":
                    displayNotification("Received malformed ACK: missing space separator.");
                    break;
                case "MALFORMED_ACK_INVALID_ID":
                    displayNotification("Received malformed ACK: invalid message ID " + notificationData.get("MessageId"));
                    break;
                case "TARGET_PEER_ONLINE":
                    displayNotification("Peer is online!");
                    break;
                default:
                    fallbackDisplay(notificationData);
                    break;
            }
        } else {
            fallbackDisplay(notificationData);
        }
    }


    /**
     * Handles shutdown of the user interface.
     * <p>
     * Closes the terminal and prints a goodbye message.
     * </p>
     */
    @Override
    public void handleShutdown() {
        terminal.writer().println("Goodbye!");
        try {
            terminal.close();
        } catch (IOException ex) {
            System.getLogger(JLineUserInterface.class.getName())
                  .log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

    // -----------------------------
    // User input resolvers
    // -----------------------------

    /**
     * Prompts the user to provide a local operating port.
     *
     * @return the resolved port as an {@link Optional} containing an integer between 0 and 65535
     */
    @Override
    public Optional<Integer> resolveOurPortFromUser() {
        System.out.println("Please enter your desired operating port (0-65535):");
        Integer port = null;
        while (port == null) {
            try {
                int testPort = Integer.parseInt(scanner.nextLine().trim());
                if (testPort < 0 || testPort > 65535) {
                    System.out.println("Port must be between 0 and 65535. Please try again.");
                    continue;
                }

                // Check if port is available for UDP
                try (DatagramSocket ds = new DatagramSocket(testPort)) {
                    ds.disconnect();
                    port = testPort;
                } catch (SocketException e) {
                    System.out.println("Port " + testPort + " is already in use. Please choose another.");
                }

            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a numeric port.");
            }
        }
        System.out.println();
        return Optional.of(port);
    }

    /**
     * Prompts the user to provide a target IP address and receiving port.
     *
     * @return an {@link Optional} containing a {@link Pair} of {@link InetAddress} and port
     */
    @Override
    public Optional<Pair<InetAddress, Integer>> resolveTargetfromUser() {
        System.out.println();
        System.out.println("Please enter your target's IP address.");
        InetAddress theirAddress = null;
        while (theirAddress == null) {
            try {
                theirAddress = InetAddress.getByName(scanner.nextLine().trim());
            } catch (UnknownHostException ex) {
                System.out.println("Unknown host / invalid IP address. Please try another hostname / valid IP address.");
            }
        }
        System.out.println();

        Integer theirPort = null;
        System.out.println("Please enter your target's recieving port (1-65535). Leave empty to reuse your operating port number.");
        while (theirPort == null) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                theirPort = chatProgram.state.getOurSocket().getLocalPort();
                System.out.println("Targeting " + theirPort + "");
                break;
            }
            try {
                int port = Integer.parseInt(line);
                if (port < 1 || port > 65535) {
                    System.out.println("Port must be between 1 and 65535. Please try again.");
                    continue;
                }
                theirPort = port;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a numeric port.");
            }
        }
        System.out.println();

        return Optional.of(new Pair<>(theirAddress, theirPort));
    }
}