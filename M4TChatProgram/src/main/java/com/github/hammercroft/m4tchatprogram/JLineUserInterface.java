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
 *
 * @author hammercroft
 */
public class JLineUserInterface implements UserInterface {

    M4TChatProgram chatProgram;
    Scanner scanner = new Scanner(System.in); //only for user port & address resolution
    Terminal terminal;
    LineReader reader;

    /**
     * Creates and starts a new instance of this user interface.
     *
     * @param chatProgram
     */
    public JLineUserInterface(M4TChatProgram chatProgram) {
        this.chatProgram = chatProgram;
    }

    public void sendInputtedMessage(String string) {
        try {
            chatProgram.sendMessage(string);
        } catch (IOException ex) {
            System.getLogger(JLineUserInterface.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

    public void handleInputtedLocalCommand(String string) {
        chatProgram.handleLocalCommand(string);
    }

    @Override
    public void handleRecievedMessage(int messageId, String message) {
        //terminal.writer().println(" (+) " + message);
        //terminal.flush();
        reader.printAbove(" (+) " + message);
    }

    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void handleChatProgramStart() {
        try {
            this.terminal = TerminalBuilder.builder().system(true).build();
        } catch (IOException ex) {
            System.getLogger(JLineUserInterface.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        if (terminal == null || "dumb".equalsIgnoreCase(terminal.getType())) {
            System.out.println("WARNING: Using a dumb terminal.\nOutput may be broken, some features may not work, or this program may not work at all..\n"
                    + "For full functionality, run this program in a standard terminal\n(e.g., xterm, GNOME Terminal, PowerShell, Windows Terminal).");
        }
        this.scanner = null;
        this.reader = LineReaderBuilder.builder().terminal(this.terminal).build();
        new Thread(() -> {
            try {
                terminal.writer().println("(Communication Start.)");
                terminal.flush();
                while (chatProgram.running == true) {
                    String line = reader.readLine();

                    this.sendInputtedMessage(line);
                }
            } catch (EndOfFileException | UserInterruptException e) {
                // We encountered CTRL+C (interruption) or CTRL+D/EOL (end of input)
                System.out.println("Input terminated. Shutting down...");
                chatProgram.shutdown(0);
            }
        }).start();
    }

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
                    ds.disconnect(); //to prevent IDE nagging
                    // Port is available
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
                // Reuse our own receiving port
                // This is assuming that the operating / home port was provided before this prompt.
                theirPort = chatProgram.state.ourSocket.getLocalPort();
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

    @Override
    public void handleProgramNotification(Map<String, Object> notificationData) {
        Object topicObj = notificationData.get("Topic");
        if (topicObj != null && topicObj instanceof String) {
            String topic = (String) topicObj;
            switch (topic) {
                case "AUTOMATIC_PORT_ASSIGNMENT":
                    displayNotification("Automatic port assigned: " + notificationData.get("Port"));
                    break;
                case "STARTUP_NOTICE":
                    String addr = chatProgram.state.theirAddress.getHostAddress();
                    int port = chatProgram.state.theirPort;
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

    private void displayNotification(String message) {
        if (reader == null) {
            System.out.println(message);
        } else {
            reader.printAbove(message);
        }
    }

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

    @Override
    public void handleShutdown() {
        terminal.writer().println("Goodbye!");
        try {
            terminal.close();
        } catch (IOException ex) {
            System.getLogger(JLineUserInterface.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

}
