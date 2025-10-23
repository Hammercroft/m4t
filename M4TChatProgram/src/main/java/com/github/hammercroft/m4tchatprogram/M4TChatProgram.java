package com.github.hammercroft.m4tchatprogram;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An instance of the Messenger For Tinkerers Chat Program (M4TChatProgram).
 * <p>
 * A UDP-based, two-participant chat program.
 */
public class M4TChatProgram implements Runnable {

    public static final int DEFAULT_BUFFER_SIZE = 800;
    private static final String SEM_ACK = "|^~ACK";
    private static final String SEM_SALVE = "|^~SALVE";
    private static final String SEM_ETTUSALVE = "|^~E2SALVE";
    private static final String SEM_KA = "|^~KA";
    private static final String LCOM_EXIT = ".EXIT";
    private static final String LCOM_PROGRAM_STATE = ".PROGRAMSTATE";
    private static final String LCOM_SALVE = ".SALVE";
    private static final String LCOM_POKE = ".POKE";
    public volatile boolean running = true;
    public Thread kaThread;

    /**
     * Current program state; may be modified or replaced at runtime.
     *
     * @see ProgramState
     */
    public ProgramState state;

    /**
     * The user interface this ChatProgram interacts with.
     */
    public UserInterface userInterface;

    /**
     * Storage for message IDs for received messages. IDs are retained for at
     * least 30 seconds.
     */
    public TransientMessageStore msgIdStore = new TransientMessageStore();

    /**
     * Blocks the calling thread until this chat program has stopped running.
     * <p>
     * This method repeatedly checks the {@link #running} flag and sleeps
     * briefly between checks. It returns only after {@link #running} becomes
     * {@code false}, which typically occurs when {@link #shutdown(int)} is
     * called.
     * <p>
     * Note: This is a simple busy-wait loop with sleep; it is not intended for
     * high-performance timing or precise event waiting.
     */
    @SuppressWarnings("SleepWhileInLoop") //intentional
    public void waitUntilStopped() {
        while (running) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Starts this chat program. {@code state} and {@code userInterface} must be
     * initialized beforehand.
     */
    @Override
    public void run() {
        if (state == null) {
            System.err.println("(PROGRAMMING BUG. REPORT TO https://github.com/Hammercroft/M4T ) State is null!. Shutting down...");
            shutdown(3);
        }

        if (state.bufferSize == 0) {
            state.bufferSize = DEFAULT_BUFFER_SIZE;
        }
        if (state.ourSocket == null) {
            Optional<Integer> resolvedHomePort = userInterface.resolveOurPortFromUser();
            if (resolvedHomePort.isEmpty()) {
                System.err.println("No valid operating port provided. Shutting down...");
                shutdown(1);
            }
            int ourPort = resolvedHomePort.get();
            try {
                state.ourSocket = new DatagramSocket(ourPort);
            } catch (SocketException ex) {
                System.err.println("No valid operating port provided. Shutting down..."); // intentional duplicate.
                shutdown(1); // intentional duplicate.
            }
            if (ourPort == 0) { // Port 0 is a request for automatic assignment, so lets print what we actually got.
                userInterface.handleProgramNotification(Map.of("Topic", "AUTOMATIC_PORT_ASSIGNMENT", "Port", state.ourSocket.getLocalPort()));
            }

        }
        if ((state.theirAddress == null) || (state.theirPort == 0)) {
            Optional<Pair<InetAddress, Integer>> resolvedTargetSocket = userInterface.resolveTargetfromUser();
            if (resolvedTargetSocket.isEmpty()) {
                System.err.println("No valid target address & port provided. Shutting down...");
                shutdown(2);
            } else {
                Pair<InetAddress, Integer> target = resolvedTargetSocket.get();
                if (target.getFirst() == null || target.getSecond() == null || target.getSecond() <= 0) {
                    System.err.println("No valid target address & port provided. Shutting down...");
                    shutdown(2);
                }
                state.theirAddress = target.getFirst();
                state.theirPort = target.getSecond();
            }
        }

        userInterface.handleProgramNotification(Map.of("Topic", "STARTUP_NOTICE"));

        userInterface.handleChatProgramStart();

        startReceiverThread();
    }

    /**
     * Sends a UTF-8 encoded message to this M4TChatProgram's target peer.
     *
     * @param message the message content to send
     * @return the randomly generated message ID
     * @throws IOException if an I/O error occurs while sending
     * @see Payload
     */
    public short sendMessage(String message) throws IOException {
        short randomId = (short) ThreadLocalRandom.current().nextInt(0, 65536);
        Payload outgoing = new Payload(randomId, state.sessionDiscriminator, message);
        byte[] bytes = outgoing.toBytes();
        DatagramPacket outPacket = new DatagramPacket(bytes, bytes.length, state.theirAddress, state.theirPort);
        state.ourSocket.send(outPacket);
        return randomId;
    }

    /**
     * Sends a general message acknowledgment (|^~ACK) for a received payload.
     *
     * @param received the payload being acknowledged
     * @throws IOException if an I/O error occurs while sending
     */
    public void sendAck(Payload received) throws IOException {
        String ackMessage = SEM_ACK + " " + received.getMessageId() + " " + received.getSessionDiscriminator() + " " + received.getContent();
        sendMessage(ackMessage);
    }

    /**
     * Sends a salve semaphore (|^~SALVE) along with an identity token.
     *
     * @throws IOException if an I/O error occurs while sending
     */
    public void sendSalve() throws IOException {
        String message = SEM_SALVE + state.ourIdentityToken;
        sendMessage(message);
    }

    @SuppressWarnings("CallToPrintStackTrace")
    private void startReceiverThread() {
        running = true;
        new Thread(() -> {
            byte[] buffer = new byte[state.bufferSize];
            try {
                sendSalve();
                kaThread = startKeepAliveTransmitter();
                while (running) {
                    Arrays.fill(buffer, (byte) 0); // clear previous data
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    state.ourSocket.receive(packet);

                    Payload received = Payload.fromBytes(buffer, state.bufferSize);
                    state.lastRecievedTransmissionTime = System.currentTimeMillis();
                    if (msgIdStore.contains(received.getMessageId())) {
                        continue;
                    } else {
                        msgIdStore.push(received.getMessageId());
                    }
                    boolean shouldDisplayMessage = handleSemaphores(received);
                    if (shouldDisplayMessage) {
                        sendAck(received);
                        displayMessage(received);
                    }
                }
            } catch (IOException ex) {
                if (running) {
                    System.err.println("Receiver thread encountered an IO error: " + ex.getMessage());
                    ex.printStackTrace();
                }
                // otherwise, expected socket closure on shutdown
            } finally {
                System.out.println("(Communication end).");
            }
        }).start();
    }

    public Thread startKeepAliveTransmitter() {
        Thread keepAliveThread = new Thread(() -> {
            final long intervalNs = 3_000_000_000L; // 3 seconds in nanoseconds
            long nextSendTime = System.nanoTime() + intervalNs;

            while (!Thread.currentThread().isInterrupted()) {
                long now = System.nanoTime();

                if (now >= nextSendTime) {
                    try {
                        sendMessage(SEM_KA);    // send keep-alive
                        nextSendTime += intervalNs;        // schedule next send
                    } catch (IOException ex) {
                        System.getLogger(M4TChatProgram.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                    }
                } else {
                    long sleepNs = nextSendTime - now;
                    try {
                        long sleepMs = sleepNs / 1_000_000;
                        int sleepNano = (int) (sleepNs % 1_000_000);
                        Thread.sleep(sleepMs, sleepNano);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "KeepAliveThread");

        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
        return keepAliveThread; // returned thread allows graceful interruption
    }

    /**
     * Handles special semaphore messages like ACKs and SALVE notifications.
     *
     * @param received the payload to examine
     * @return {@code true} if the message should be displayed normally,
     * {@code false} if it is a semaphore message that should be suppressed
     * @throws IOException if sending a SALVE response fails
     */
    private boolean handleSemaphores(Payload received) throws IOException {
        String content = received.getContent();

        if (content.startsWith(SEM_ACK)) {
            String ackData = content.substring(SEM_ACK.length()).trim();

            // Split the ACK payload into parts: <msg_id> <session> <acknowledged_msg>
            int firstSpace = ackData.indexOf(' ');
            if (firstSpace == -1) {
                userInterface.handleProgramNotification(Map.of(
                        "Topic", "MALFORMED_ACK_MISSING_SPACE_SEPARATOR"
                ));
                return false;
            }

            String idPart = ackData.substring(0, firstSpace);

            int secondSpace = ackData.indexOf(' ', firstSpace + 1);
            if (secondSpace == -1) {
                userInterface.handleProgramNotification(Map.of(
                        "Topic", "MALFORMED_ACK_MISSING_SESSION_OR_MESSAGE",
                        "MessageId", idPart
                ));
                return false;
            }

            String sessionPart = ackData.substring(firstSpace + 1, secondSpace);
            @SuppressWarnings("unused") // TODO: handle ACK logic
            String acknowledgedMsg = ackData.substring(secondSpace + 1);

            try {
                @SuppressWarnings("unused") // TODO: handle ACK logic
                short msgId = Short.parseShort(idPart);          // transient message ID
                @SuppressWarnings("unused") // TODO: handle ACK logic
                short session = Short.parseShort(sessionPart);   // session discriminator

                // TODO: handle ACK logic here
                // e.g., mark message with msgId and session as delivered, update local state, log, etc.
            } catch (NumberFormatException e) {
                userInterface.handleProgramNotification(Map.of(
                        "Topic", "MALFORMED_ACK_INVALID_ID_OR_SESSION",
                        "MessageId", idPart,
                        "Session", sessionPart
                ));
                return false;
            }

            return false;
        }
        if (content.startsWith(SEM_SALVE)) {
            userInterface.handleProgramNotification(Map.of("Topic", "TARGET_PEER_ONLINE"));
            String incomingIdentity = content.substring(SEM_SALVE.length()).trim();
            String ackMessage = String.format("%s %s %s",
                    SEM_ETTUSALVE, // e.g., "|^~E2SALVE"
                    incomingIdentity, // identity from incoming message
                    state.ourIdentityToken);
            sendMessage(ackMessage);
            return false;
        }

        if (content.startsWith(SEM_ETTUSALVE)) {
            String data = content.substring(SEM_ETTUSALVE.length()).trim();
            String[] parts = data.split(" ");

            if (parts.length < 2) {
                userInterface.handleProgramNotification(Map.of(
                        "Topic", "MALFORMED_ETTUSALVE",
                        "Content", data
                ));
                return false;
            }

            @SuppressWarnings("unused") //might be useful later
            String acknowledgedToken = parts[0]; // peer's identity token
            @SuppressWarnings("unused") //might be useful later
            String selfToken = parts[1];         // our token as seen by peer

            if (parts.length >= 3) {
                try {
                    short session = Short.parseShort(parts[2]);
                    state.sessionDiscriminator = session; // set session discriminator
                } catch (NumberFormatException e) {
                    userInterface.handleProgramNotification(Map.of(
                            "Topic", "MALFORMED_ETTUSALVE_INVALID_SESSION",
                            "SessionPart", parts[2]
                    ));
                    return false;
                }
            }

            // TODO validate acknowledgedToken/selfToken 
            return false;
        }

        if (content.startsWith(SEM_KA)) {
            return false;
        }
        return true;
    }

    /**
     * Stops the program and closes all resources.
     *
     * @param status The resulting exit code of the shutdown.
     */
    public void shutdown(int status) {
        running = false;
        if (kaThread != null)
            kaThread.interrupt();
        try {
            if (state.ourSocket != null && !state.ourSocket.isClosed()) {
                state.ourSocket.close(); // this unblocks receive()
            }
        } catch (Exception ignored) {
        }
        userInterface.handleShutdown();
        System.exit(status);
    }

    /**
     * Processes a local dot-command issued by the user.
     *
     * @param dotCommand the local command string entered by the user
     */
    public void handleLocalCommand(String dotCommand) {
        switch (dotCommand.toUpperCase()) {
            case LCOM_EXIT:
                shutdown(0); // exit code 0 - graceful shutdown
                break;
            case LCOM_PROGRAM_STATE:
                userInterface.handleProgramNotification(Map.of("Topic", "PUSH_TEXT", "Text", state.toString()));
                break;
            case LCOM_SALVE:
            case LCOM_POKE:
            {
                try {
                    sendSalve();
                } catch (IOException ex) {
                    System.getLogger(M4TChatProgram.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
            }
                break;

            default:
                userInterface.handleProgramNotification(Map.of("Topic", "UNKNOWN_LOCAL_COMMAND"));
                break;
        }
    }

    /**
     * Sends a received message to the user interface for display.
     */
    private void displayMessage(Payload received) {
        String msg = received.getContent();
        if (msg.isEmpty()) {
            msg = "\n";
        } else if (msg.charAt(msg.length() - 1) != '\n') {
            msg += '\n';
        }

        userInterface.handleRecievedMessage(received.getMessageId(), msg);
    }
}
