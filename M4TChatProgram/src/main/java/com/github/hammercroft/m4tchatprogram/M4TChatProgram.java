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

        /**
         * Default buffer size for receiving packets (well within MTU of old
         * cellular internet).
         */
        public static final int DEFAULT_BUFFER_SIZE = 800;

        /**
         * General message acknowledgment semaphore.
         */
        private static final String SEM_ACK = "|^~ACK "; //whitespace intentional
        /**
         * Online notification semaphores.
         */
        private static final String SEM_SALVE = "|^~SALVE";
        private static final String SEM_ETTUSALVE = "|^~E2SALVE";

        /**
         * Local commands.
         */
        private static final String LCOM_EXIT = ".EXIT";

        /**
         * Variables
         */
        public volatile boolean running = true;

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
         * Sends a UTF-8 encoded message to this M4TChatProgram's target peer.
         *
         * @param message the message content to send
         * @return the randomly generated message ID
         * @throws IOException if an I/O error occurs while sending
         * @see Payload
         */
        public short sendMessage(String message) throws IOException {
            short randomId = (short) ThreadLocalRandom.current().nextInt(0, 65536);
            Payload outgoing = new Payload(randomId, message);
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
            String ackMessage = SEM_ACK + received.getMessageId() + " " + received.getContent();
            sendMessage(ackMessage);
        }

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

        @SuppressWarnings("CallToPrintStackTrace")
        private void startReceiverThread() {
            running = true;
            new Thread(() -> {
                byte[] buffer = new byte[state.bufferSize];
                try {
                    this.sendMessage(SEM_SALVE);
                    while (running) {
                        Arrays.fill(buffer, (byte) 0); // clear previous data
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        state.ourSocket.receive(packet);

                        Payload received = Payload.fromBytes(buffer, state.bufferSize);
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

        /**
         * Handles special semaphore messages like ACKs and SALVE notifications.
         *
         * <p>
         * If the payload is an ACK message, it processes it (TODO: mark as
         * delivered) and returns {@code false}, indicating the message should not
         * be displayed. For SALVE notifications, it triggers the appropriate
         * program notification.
         *
         * @param received the payload to examine
         * @return {@code true} if the message should be displayed normally,
         * {@code false} if it is a semaphore message that should be suppressed
         * @throws IOException if sending a SALVE response fails
         */
        private boolean handleSemaphores(Payload received) throws IOException {
            String content = received.getContent();

            if (content.startsWith(SEM_ACK)) {
                String ackData = content.substring(SEM_ACK.length());
                int firstSpace = ackData.indexOf(' ');
                if (firstSpace == -1) {
                    //System.err.println("Malformed ACK: missing space separator");
                    userInterface.handleProgramNotification(Map.of("Topic", "MALFORMED_ACK_MISSING_SPACE_SEPARATOR"));
                    return false;
                }
                String idPart = ackData.substring(0, firstSpace);
                try {
                    // TODO: handle ACK logic (mark message as delivered, etc.)
                } catch (NumberFormatException e) {
                    //System.err.println("Malformed ACK: invalid message ID \"" + idPart + "\"");
                    userInterface.handleProgramNotification(Map.of("Topic", "MALFORMED_ACK_INVALID_ID", "MessageId", idPart));
                }
                return false;
            }

            if (content.startsWith(SEM_SALVE) || content.startsWith(SEM_ETTUSALVE)) {
                userInterface.handleProgramNotification(Map.of("Topic", "TARGET_PEER_ONLINE"));
                if (content.startsWith(SEM_SALVE)) {
                    sendMessage(SEM_ETTUSALVE);
                }
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
            if (dotCommand.equalsIgnoreCase(LCOM_EXIT)) {
                shutdown(0); // exit code 0 - graceful shutdown
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
