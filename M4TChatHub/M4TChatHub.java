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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class M4TChatHub {

    ///////////////////////
    // CONSTANTS & FIELDS
    ///////////////////////

    public static final int BUFFER_SIZE = 800;

    static InetSocketAddress ourSocketAddr = null;
    static final Scanner scn = new Scanner(System.in);
    static final ExecutorService jobs = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    static final SessionManager sessions = new SessionManager(300_000); // 5 min timeout
    static boolean active = true;

    /////////////////////
    // UTILITY METHODS
    /////////////////////

    public static void printYourAddresses() {
        try {
            // Local
            System.out.println("Your Local IP addresses:");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    System.out.println(" - " + addr.getHostAddress()
                            + " (" + (addr instanceof Inet4Address ? "IPv4" : "IPv6") + ")");
                }
            }

            // Public IPv4
            System.out.println("\nYour Public IP addresses as seen by ipify.org:");
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(new URL("https://api.ipify.org").openStream()))) {
                System.out.println(" - Public IPv4: " + in.readLine());
            } catch (Exception e) {
                System.out.println(" - Public IPv4: Could not determine (" + e.getMessage() + ")");
            }

            // Public IPv6
            try (BufferedReader in6 = new BufferedReader(
                    new InputStreamReader(new URL("https://api6.ipify.org").openStream()))) {
                System.out.println(" - Public IPv6: " + in6.readLine());
            } catch (Exception e) {
                System.out.println(" - Public IPv6: Could not determine (" + e.getMessage() + ")");
            }
        } catch (SocketException e) {
            System.getLogger("M4TChatProgram")
                  .log(System.Logger.Level.ERROR, "Exception during address checks", e);
        }
    }

    public static void resolveTransmissionConfig() {
        System.out.println("Please enter the hub's operating port (0–65535):");

        while (ourSocketAddr == null) {
            try {
                int port = Integer.parseInt(scn.nextLine().trim());
                if (port < 0 || port > 65535) {
                    System.out.println("Port must be between 0 and 65535.");
                    continue;
                }

                try (DatagramChannel testCh = DatagramChannel.open()) {
                    testCh.bind(new InetSocketAddress(port));
                    ourSocketAddr = (InetSocketAddress) testCh.getLocalAddress();
                    System.out.println("Using port " + ourSocketAddr.getPort());
                } catch (IOException e) {
                    System.out.println("Port " + port + " unavailable. Try another.");
                }

            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
        System.out.println();
    }
    
    /// sends a message.
    /// returns a later substring of characters that couldn't fit in the payload.
    static String sendMsg(DatagramChannel chan, String msg, SocketAddress target){
        try {
            byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
            int maxLen = BUFFER_SIZE - 2; //accounting for the two byte message id (short)
            
            int len = Math.min(msgBytes.length, maxLen);
            byte[] sendablePart = Arrays.copyOfRange(msgBytes, 0, len);
            
            short randomId = (short)ThreadLocalRandom.current().nextInt(0,65536);
            Payload outbound = new Payload (randomId, new String(sendablePart,StandardCharsets.UTF_8));
            
            ByteBuffer buffer = ByteBuffer.wrap(outbound.toBytes());
            chan.send(buffer, target);
            
            // return leftovers if any
            if (len < msgBytes.length){
                return new String(Arrays.copyOfRange(msgBytes, len, msgBytes.length), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            Logger.getLogger(M4TChatHub.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    ///////////////////////
    // NETWORKING LOGIC
    ///////////////////////
    private static void handlePayload(DatagramChannel channel, ByteBuffer data, SocketAddress client) { //to be ran on a worker thread
        Payload received = Payload.fromBytes(data.array(), BUFFER_SIZE);
        Session sender = sessions.getOrCreate(client.toString(), (InetSocketAddress) client); //supposedly a safe cast
        sessions.touch(sender.socketAddr);
        // Semaphore handling
        if (received.content.startsWith("|^~")) {
            String tokens[] = received.content.split(" ");
            switch (tokens[0].trim()){ //we need the trim so that we dispose newlines
                case "|^~SALVE": //should be the first case, else side effects
                    sendMsg(channel,"|^~E2SALVE",sender.socketAddr);
                    // intentional switch fallthrough; no break should be here
                case "|^~E2SALVE":
                    System.out.println("(SALVE from "+sender.username+")");
                    // hmm, but how would clients even get the chance to respond with this exact main token?
                    break;
                case "|^~ACK":
                    //do nothing for now
                    break;
            }
            return;
        }
        
        System.out.println(sender.username + ": "+received.content);
        
        // Command handling
        if (received.content.startsWith("/")){
            String tokens[] = received.content.split(" ");
            switch (tokens[0].trim()){
                case "/nickname":
                    if (tokens[1] == null || tokens[1].isBlank())
                        break;
                    sender.username = tokens[1].trim(); //without the trim, newlines wouldve been included...
                    sendMsg(channel,"You will now be visible to other chatters as "+sender.username+".",sender.socketAddr);
                    break;
            }
            // no return intended
        }
        
        // Rebroadcast msg to all sessions except this sender
        String message = "[" + (sender.username) + "]: " + received.content;
        for (Session recipient : sessions) {
            if (recipient.socketAddr == sender.socketAddr) {
                continue; // do not send to sender
            }
            
            sendMsg(channel, message, recipient.socketAddr);
        }
        // Let sender know that their message is acknowledged
        String ackMsg = "|^~ACK "+received.messageId+" "+received.content; //TODO modify acknowledged content to include username if they are in GMA Display mode
        sendMsg(channel, ackMsg, sender.socketAddr);
    }

    private static void runSelectorLoop(Selector selector) { //to be executed on main thread
        try {
            while (true) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (key.isReadable()) {
                        DatagramChannel dc = (DatagramChannel) key.channel();
                        ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);

                        SocketAddress clientAddr = dc.receive(buf);
                        if (clientAddr != null) {
                            buf.flip();
                            jobs.submit(() -> handlePayload(dc, buf, clientAddr));
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    ///////////////////
    // MAIN METHOD
    ///////////////////

    public static void main(String[] args) throws IOException {
        active = true;
        printYourAddresses();
        System.out.println();
        resolveTransmissionConfig();

        Selector selector = Selector.open();
        DatagramChannel channel = DatagramChannel.open();
        channel.bind(ourSocketAddr);
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);

        System.out.println("Hub active on port " + ourSocketAddr.getPort());
        runSelectorLoop(selector);
    }

    /////////////////////
    // INNER CLASSES
    /////////////////////

    public static class Session {
        public String username;
        public InetSocketAddress socketAddr;
        public long lastTransmissionTime;

        public Session(String their_uname, InetSocketAddress their_socketAddr) {
            username = their_uname;
            socketAddr = their_socketAddr;
            lastTransmissionTime = System.currentTimeMillis();
        }
    }

    public static class SessionManager implements Iterable<Session> {
        private final Map<InetSocketAddress, Session> sessions = new ConcurrentHashMap<>();
        private final ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor();
        private final long timeoutMillis;

        public SessionManager(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            reaper.scheduleAtFixedRate(this::reap, timeoutMillis, timeoutMillis, TimeUnit.MILLISECONDS);
        }

        public Session getOrCreate(String username, InetSocketAddress addr) {
            return sessions.compute(addr, (key, existing) -> {
                if (existing != null) {
                    existing.lastTransmissionTime = System.currentTimeMillis();
                    return existing;
                }
                return new Session(username, addr);
            });
        }

        public Session getByAddress(InetSocketAddress addr) {
            return sessions.get(addr);
        }

        public Session getByUsername(String username) {
            return sessions.values().stream()
                           .filter(s -> s.username.equals(username))
                           .findFirst().orElse(null);
        }
        
        @Override
        public Iterator<Session> iterator() {
            return sessions.values().iterator();
        }

        public void touch(InetSocketAddress addr) {
            Session s = sessions.get(addr);
            if (s != null) s.lastTransmissionTime = System.currentTimeMillis();
        }

        private void reap() {
            long now = System.currentTimeMillis();
            sessions.values().removeIf(s -> (now - s.lastTransmissionTime) > timeoutMillis);
        }

        public void shutdown() {
            reaper.shutdown();
        }
    }

    public static class Payload {
        private final short messageId;
        private final String content;

        public Payload(short messageId, String content) {
            this.messageId = messageId;
            this.content = content;
        }

        public short getMessageId() { return messageId; }
        public String getContent() { return content; }

        public static Payload fromBytes(byte[] data, int length) {
            ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
            short messageId = buffer.getShort();
            byte[] contentBytes = new byte[length - 2];
            buffer.get(contentBytes);
            return new Payload(messageId, new String(contentBytes, StandardCharsets.UTF_8));
        }

        public byte[] toBytes() {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(2 + contentBytes.length);
            buffer.putShort(messageId);
            buffer.put(contentBytes);
            return buffer.array();
        }
    }
}
