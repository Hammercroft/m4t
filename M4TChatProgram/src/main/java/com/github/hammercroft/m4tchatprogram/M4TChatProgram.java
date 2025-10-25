package com.github.hammercroft.m4tchatprogram;

import static com.github.hammercroft.m4tchatprogram.Semaphore.*;

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

    // --- FIELDS ---

    /**
     * Standard buffer size as defined by the M4T messaging scheme.
     * https://github.com/Hammercroft/m4t/wiki/M4T-Messaging-Scheme
     */
    public static final int DEFAULT_BUFFER_SIZE = 800;

    /**
     * Whether or not this instance is actively carrying out its function.
     */
    public volatile boolean running = true;

    /**
     * A dedicated worker for transmitting |^~KA every 3 seconds.
     *
     * @see KeepAliveTransmitter
     */
    public Thread kaThread;

    /**
     * Current program state; may be modified or replaced at runtime.
     * Shared across threads.
     *
     * @see ProgramState
     */
    public ProgramState state;

    /**
     * The user interface that this ChatProgram interacts with.
     */
    public UserInterface userInterface;

    /**
     * All incoming messages are fed to this in order to handle them when those
     * messages are semaphores.
     */
    public SemaphoreHandler semaphoreHandler;

    /**
     * Handles local commands entered by the user. The userInterface should
     * interact with this after in order to handle local commands.
     */
    public LocalCommandHandler localCommandHandler;

    /**
     * Storage for message IDs for received messages. IDs are retained for at
     * least 30 seconds.
     */
    public TransientMessageStore msgIdStore = new TransientMessageStore();

    // --- PUBLIC METHODS ---

    /**
     * Yields execution on M4TChatProgramMain to prevent early exits.
     *
     * @see M4TChatProgramMain
     */
    @SuppressWarnings("SleepWhileInLoop") // intentional
    public void waitUntilStopped() {
        while (running) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Runs this logical program. This instance's state must be non-null before
     * calling this method.
     */
    @Override
    public void run() {
        if (state == null) {
            System.err.println("(PROGRAMMING BUG. REPORT TO https://github.com/Hammercroft/M4T ) State is null!. Shutting down...");
            shutdown(3);
        }
        if (state.getBufferSize() == 0) {
            state.setBufferSize(DEFAULT_BUFFER_SIZE);
        }
        if (state.getOurSocket() == null) {
            Optional<Integer> resolvedHomePort = userInterface.resolveOurPortFromUser();
            if (resolvedHomePort.isEmpty()) {
                System.err.println("No valid operating port provided. Shutting down...");
                shutdown(1);
            }
            int ourPort = resolvedHomePort.get();
            try {
                state.setOurSocket(new DatagramSocket(ourPort));
            } catch (SocketException ex) {
                System.err.println("No valid operating port provided. Shutting down...");
                shutdown(1);
            }
            if (ourPort == 0) {
                userInterface.handleProgramNotification(Map.of("Topic", "AUTOMATIC_PORT_ASSIGNMENT", "Port", state.getOurSocket().getLocalPort()));
            }
        }
        if ((state.getTheirAddress() == null) || (state.getTheirPort() == 0)) {
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
                state.setTheirAddress(target.getFirst());
                state.setTheirPort(target.getSecond());
            }
        }

        semaphoreHandler = new SemaphoreHandler(this);
        localCommandHandler = new LocalCommandHandler(this);

        userInterface.handleProgramNotification(Map.of("Topic", "STARTUP_NOTICE"));

        userInterface.handleChatProgramStart();

        startReceiverThread();
    }

    /**
     * Sends a message to the currently configured messaging target.
     * <p>
     * The target is determined by {@code this.state.theirAddress} and
     * {@code this.state.theirPort}. The transmitted payload will include the
     * session discriminator from {@code this.state.sessionDiscriminator}.
     * </p>
     *
     * @param message the message to send
     * @return the transient message ID assigned to the on-air payload, which can
     *         be used for tracking or acknowledgment purposes
     * @throws IOException if an error occurs while sending the message
     */
    public short sendMessage(String message) throws IOException {
        short randomId = (short) ThreadLocalRandom.current().nextInt(0, 65536);
        Payload outgoing = new Payload(randomId, state.getSessionDiscriminator(), message);
        byte[] bytes = outgoing.toBytes();
        DatagramPacket outPacket = new DatagramPacket(bytes, bytes.length, state.getTheirAddress(), state.getTheirPort());
        state.getOurSocket().send(outPacket);
        return randomId;
    }

    /**
     * Sends an General Message Acknowledgement semaphore message.
     *
     * @param received The message to be acknowledged.
     * @throws IOException if an error occurs while sending the message
     * @see #sendMessage(String)
     */
    public void sendAck(Payload received) throws IOException {
        String ackMessage = S_ACK.token() + " " + received.getMessageId() + " " + received.getSessionDiscriminator() + " " + received.getContent();
        sendMessage(ackMessage);
    }

    /**
     * Sends a Salve semaphore to trigger a greeting / "connection wellness check".
     *
     * @throws IOException if an error occurs while sending the message
     * @see #sendMessage(String)
     */
    public void sendSalve() throws IOException {
        String message = S_SALVE.token() + state.getOurIdentityToken();
        sendMessage(message);
    }

    /**
     * Triggers a shutdown. For graceful shutdowns, the provided status code must be zero.
     *
     * @param status Status code. Must be 0 if graceful.
     */
    public void shutdown(int status) {
        running = false;
        if (kaThread != null)
            kaThread.interrupt();
        try {
            if (state.getOurSocket() != null && !state.getOurSocket().isClosed()) {
                state.getOurSocket().close();
            }
        } catch (Exception ignored) {
        }
        userInterface.handleShutdown();
        System.exit(status);
    }

    // --- PRIVATE METHODS ---

    private void startReceiverThread() {
        running = true;
        new Thread(() -> {
            byte[] buffer = new byte[state.getBufferSize()];
            try {
                sendSalve();
                kaThread = KeepAliveTransmitter.start(this);
                while (running) {
                    Arrays.fill(buffer, (byte) 0);
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    state.getOurSocket().receive(packet);

                    Payload received = Payload.fromBytes(buffer, state.getBufferSize());
                    state.setLastReceivedTransmissionTime(System.currentTimeMillis());
                    if (msgIdStore.contains(received.getMessageId())) {
                        continue;
                    } else {
                        msgIdStore.push(received.getMessageId());
                    }
                    boolean shouldDisplayMessage = semaphoreHandler.handle(received);
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
            } finally {
                System.out.println("(Communication end).");
            }
        }).start();
    }

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
