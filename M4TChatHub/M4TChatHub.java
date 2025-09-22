/*
MIT License

Copyright (c) 2025 Hammercroft

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

// Yes. We are using the default package.
// This is meant to be manually compiled via a JDK's utilities.
// To create a runnable .jar of this, do the following in your shell:
// jar cfe ./M4TChatProgram.jar M4TChatProgram ./M4TChatProgram.class
// java -jar ./M4TChatProgram.jar

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple UDP-based, two-participant chat program.
 *
 * @author org.nekoweb.hammercroft
 */
public class M4TChatProgram {

    // CONSTANTS
    final static int DEFAULT_BUFFER_SIZE = 800; // well within the average MTU of old cellular internet.
    final static String SEM_ACK = "|^~ACK "; // general ack semaphore
    final static String SEM_SALVE = "|^~SALVE";
    final static String SEM_ETTUSALVE = "|^~E2SALVE";

    // TRANSMISSION CONFIGURATION
    static DatagramSocket ourSocket = null; // Port of which we receive messages, and of which we use to send messages.
    static InetAddress theirAddress = null; // Target peer's address.
    static int theirPort = -1; // Target peer's port. This field needs re-initialization with a valid port number.

    // PROGRAM FIELDS
    static Scanner scn = new Scanner(System.in);
    static int bufferSize = DEFAULT_BUFFER_SIZE;

    public static void resolveTransmissionConfig() {
        // [ Resolve transmission config via user input ]
        System.out.println("Please enter your desired operating port (0–65535):");
        while (ourSocket == null) {
            try {
                int port = Integer.parseInt(scn.nextLine().trim());
                if (port < 0 || port > 65535) {
                    System.out.println("Port must be between 0 and 65535. Please try again.");
                    continue;
                }
                // Create the DatagramSocket on the user-specified port
                ourSocket = new DatagramSocket(port);
                if (port == 0) { // Port 0 is a request for automatic assignment, so lets print what we actually got.
                    System.out.println("Using port " + ourSocket.getLocalPort());
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a numeric port.");
            } catch (SocketException e) {
                System.out.println("Failed to bind to port. It may be in use or restricted. Please try another port.");
            }
        }
        System.out.println();

        System.out.println("Please enter your target's IP address.");
        while (theirAddress == null) {
            try {
                theirAddress = InetAddress.getByName(scn.nextLine().trim());
            } catch (UnknownHostException ex) {
                System.out.println("Unknown host / invalid IP address. Please try another hostname / valid IP address.");
            }
        }
        System.out.println();

        System.out.println("Please enter your target's recieving port (1-65535). Leave empty to reuse your operating port number.");
        while (theirPort == -1) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                // Reuse our own receiving port
                theirPort = ourSocket.getLocalPort();
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
    }

    public static void printYourAddresses() {
        try {
            // --- Local addresses ---
            System.out.println("Your Local IP addresses:");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    System.out.println(" - " + addr.getHostAddress() + " ("
                            + (addr instanceof Inet4Address ? "IPv4" : "IPv6") + ")");
                }
            }

            // --- Public addresses ---
            System.out.println("\nYour Public IP addresses as seen by ipify.org (unusable if blocked by firewall/NAT):");

            // IPv4
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(new URL("https://api.ipify.org").openStream()))) {
                String publicIPv4 = in.readLine();
                System.out.println(" - Public IPv4: " + publicIPv4);
            } catch (Exception e) {
                System.out.println(" - Public IPv4: Could not determine (" + e.getMessage() + ")");
            }

            // IPv6
            try (BufferedReader in6 = new BufferedReader(
                    new InputStreamReader(new URL("https://api6.ipify.org").openStream()))) {
                String publicIPv6 = in6.readLine();
                System.out.println(" - Public IPv6: " + publicIPv6);
            } catch (Exception e) {
                System.out.println(" - Public IPv6: Could not determine (" + e.getMessage() + ")");
            }
        } catch (SocketException e) {
            System.getLogger("M4TChatProgram").log(System.Logger.Level.ERROR, "Exception during printYourAddress() address checks", e);
        }
    }

    /**
     * Sends a message as a UDP datagram to the specified target address and
     * port.
     * <p>
     * Each datagram is wrapped in a {@link Payload} object that includes a
     * randomly generated 16-bit message ID and the UTF-8 encoded message
     * content. The message ID is returned to the caller so that retransmissions
     * or acknowledgements can reference the same identifier.
     *
     * @param message the message content to send (UTF-8 encoded in payload)
     * @param targetAddress the destination IP address
     * @param targetPort the destination port number
     * @return the randomly generated 16-bit message ID associated with this
     * payload
     * @throws IOException if an I/O error occurs while sending the datagram
     */
    public static short send(String message, InetAddress targetAddress, int targetPort) throws IOException {
        short randomId = (short) ThreadLocalRandom.current().nextInt(0, 65536);
        Payload outgoing = new Payload(randomId, message);
        byte[] bytes = outgoing.toBytes();
        DatagramPacket outPacket = new DatagramPacket(bytes, bytes.length, targetAddress, targetPort);
        ourSocket.send(outPacket);
        return randomId;
    }

    /**
     * Sends a general message acknowledgement (ACK) for a received payload.
     * The acknowledgement format is:
     * <pre>
     * |^~ACK &lt;messageId&gt; &lt;message&gt;
     * </pre> where:
     * <ul>
     * <li>{@code messageId} is the numeric string form of the signed short
     * ID.</li>
     * <li>{@code message} is the original message content (UTF-8).</li>
     * </ul>
     *
     * @param received the payload that is being acknowledged
     * @param targetAddress the destination IP address
     * @param targetPort the destination port number
     * @throws IOException if an I/O error occurs while sending the ACK datagram
     */
    public static void sendAck(Payload received, InetAddress targetAddress, int targetPort) throws IOException {
        // Build ACK string according to specification
        String ackMessage = "|^~ACK " + received.messageId + " " + received.content;
        // Send using the existing send(String, …) method
        send(ackMessage, targetAddress, targetPort);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        boolean active = true;
        printYourAddresses(); // TODO dont print on headless mode

        System.out.println(); // TODO dont print on headless mode
        resolveTransmissionConfig();

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Starting communication to " + theirAddress + ":" + theirPort);
        System.out.println("Due to the nature of delivery via UDP, the delivery of messages between you and your peer is not guaranteed.");
        System.out.println();
        System.out.println("RECEIVED MESSAGES ARE NOT GUARANTEED TO ORIGINATE FROM YOUR INTENDED COMMUNICATION TARGET.");
        System.out.println("YOUR MESSAGES ARE NOT ENCRYPTED.");
        System.out.println();
        System.out.println("To stop communication, do enter .exit");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("(Communication start.)");
        
        // Separate thread for recieving messages.
        new Thread(() -> {
            while (true) {
                byte[] buffer = new byte[bufferSize];
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    // This call blocks until a packet is received
                    ourSocket.receive(packet);

                    // Extract data
                    Payload received = Payload.fromBytes(buffer, bufferSize);

                    // Semaphore-handling conditions
                    if (received.content.startsWith(SEM_ACK)) {
                        // Strip off the prefix (everything after "|^~ACK ")
                        String ackData = received.content.substring(SEM_ACK.length());

                        // Split into two parts: messageId and acknowledged message
                        int firstSpace = ackData.indexOf(' ');
                        if (firstSpace == -1) {
                            System.err.println("Malformed ACK: missing space separator");
                            continue;
                        }

                        String idPart = ackData.substring(0, firstSpace);
                        String ackMessage = ackData.substring(firstSpace + 1);

                        try {
                            short ackMsgId = Short.parseShort(idPart);
                            // TODO: handle ACK logic (mark message as delivered, etc.)
                        } catch (NumberFormatException e) {
                            System.err.println("Malformed ACK: invalid message ID \"" + idPart + "\"");
                        }

                        continue; // do not display ACKs as normal messages
                    } else if (received.content.startsWith(SEM_SALVE)){
                        System.out.println("(Target peer is online.)");
                        send(SEM_ETTUSALVE, theirAddress, theirPort);
                        continue;
                    } else if (received.content.startsWith(SEM_ETTUSALVE)){
                        System.out.println("(Target peer is online.)");
                        continue;
                    }

                    // Display received message
                    System.out.print(" (+) ");
                    System.out.print(received.content);
                    if (!received.content.isEmpty() && received.content.charAt(received.content.length() - 1) == '\n') {
                        // if message ended with a newline, do nothing.
                    } else {
                        // if message did not end with a newline, print a newline.
                        System.out.print('\n');
                    }

                    // Transmit general acknowledgement for received message
                    sendAck(received, theirAddress, theirPort);
                } catch (IOException ex) {
                    // Crash this thread silently. Quick and dirty way to end this thread after closing ourSocket in the main thread.
                }
            }
        }).start();
        
        // Greeting semaphore
        try {
            send("|^~SALVE", theirAddress, theirPort);
        } catch (IOException ex) {
            Logger.getLogger(M4TChatProgram.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // Transmission of messages to target
        while (true) {
            String message = scn.nextLine();

            // Client command switch
            if (message.equalsIgnoreCase(".exit")) {
                active = false;
                break;
            }
            else if (message.equalsIgnoreCase(".salve")||message.equalsIgnoreCase(".poke")) {
                try {
                    send(SEM_SALVE, theirAddress, theirPort);
                } catch (IOException ex) {
                    Logger.getLogger(M4TChatProgram.class.getName()).log(Level.SEVERE, null, ex);
                }
                continue;
            }

            // Send message
            try {
                send(message, theirAddress, theirPort);
            } catch (IOException ex) {
                
            }
        }
        ourSocket.close();
        System.out.println("(Communication end).");
    }

    // UTILITY CLASSES
    public static class Payload {

        private final short messageId;
        private final String content;

        public Payload(short messageId, String content) {
            this.messageId = messageId;
            this.content = content;
        }

        public short getMessageId() {
            return messageId;
        }

        public String getContent() {
            return content;
        }

        /**
         * Parse raw bytes (from a DatagramPacket) into a Payload.
         * @param data the raw data of this payload
         * @param length the known byte length of this payload
         * @return The resulting Payload instance
         */
        public static Payload fromBytes(byte[] data, int length) {
            ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);

            // First 2 bytes = message ID
            short messageId = buffer.getShort();

            // Remaining bytes = UTF-8 content
            byte[] contentBytes = new byte[length - 2];
            buffer.get(contentBytes);

            String content = new String(contentBytes, StandardCharsets.UTF_8);

            return new Payload(messageId, content);
        }

        /**
         * Convert a Payload back into a byte array (for sending).
         * @return the raw bytes of this Payload
         */
        public byte[] toBytes() {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(2 + contentBytes.length);

            buffer.putShort(messageId);
            buffer.put(contentBytes);

            return buffer.array();
        }
    }
}
