import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.Map;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
/**
 * @author hammercroft
 */
public class M4TChatHub {

    public static final int BUFFER_SIZE = 800;

    //  CONFIGURATION
    static DatagramSocket ourRecievingSocket = null;
    static DatagramSocket ourSendingSocket = null;

    //  PROGRAM FIELDS
    static Scanner scn = new Scanner(System.in);
    static boolean active = true;
    static Map<InetAddress, Session> sessions = new HashMap<>();
    static BlockingQueue<DatagramPacket> backlog = new LinkedBlockingQueue();

    public static void resolveConfig() {
        System.out.println("Please enter your desired receiving port (0-65535):");
        while (ourRecievingSocket == null) {
            try {
                int port = Integer.parseInt(scn.nextLine().trim());
                if (port < 0 || port > 65535) {
                    System.out.println("Port must be between 0 and 65535. Please try again.");
                    continue;
                }
                // Create the DatagramSocket on the user-specified port
                ourRecievingSocket = new DatagramSocket(port);
                if (port == 0) { // Port 0 is a request for automatic assignment, so lets print what we actually got.
                    System.out.println("Using port " + ourRecievingSocket.getLocalPort());
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a numeric port.");
            } catch (SocketException e) {
                System.out.println("Failed to bind to port. It may be in use or restricted. Please try another port.");
            }
        }
        
        ourSendingSocket = ourRecievingSocket; //TODO separate these
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
            e.printStackTrace();
        }
    }
    
    public static void send(String line, Session session) throws IOException {
        byte[] data = line.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, session.ipAddress, session.receivingPort);
        ourRecievingSocket.send(packet);
    }

    public static void main(String args[]) {
        active = true;
        printYourAddresses(); // TODO dont print on headless mode

        System.out.println(); // TODO dont print on headless mode
        resolveConfig();

        System.out.println("Hub is now active");
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                // This call blocks until a packet is received
                ourRecievingSocket.receive(packet);

                // Extract data
                String received = new String(packet.getData(), 0, packet.getLength());
                InetAddress senderAddress = packet.getAddress();
                
                // Check if IP matches a known session
                Session session = sessions.get(senderAddress);
                if (session == null) { // If not known, create a new session
                    session = new Session(
                            senderAddress.getHostAddress(),
                            senderAddress,
                            packet.getPort() // for now, assume that their recieving port is the port they used to send this packet
                    );
                    sessions.put(senderAddress, session);
                }
                
                session.lastTransmissionTime = System.currentTimeMillis();
                
                // TODO command and semaphore handling
                // Rebroadcast msg to all sessions except this session
                for (Entry<InetAddress,Session> entry : sessions.entrySet()){
                    if (entry.getKey().equals(senderAddress)) continue; // do not send to sender
                    send("["+senderAddress.getHostAddress()+"]:"+received,entry.getValue());
                }
                
                //int senderPort = packet.getPort();
                //System.out.print(" (+) ");
                //System.out.print(received);
                //if (!received.isEmpty() && received.charAt(received.length() - 1) == '\n') {
                //    // if message ended with a newline, do nothing.
                //} else {
                //    // if message did not end with a newline, print a newline.
                //    System.out.print('\n');
                //}
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static class Session {
        public String username;
        public InetAddress ipAddress;
        public int receivingPort;
        public long lastTransmissionTime; // Unix clock time in millis

        public Session(String their_uname, InetAddress their_addr, int their_port) {
            username = their_uname;
            ipAddress = their_addr;
            receivingPort = their_port;
            lastTransmissionTime = System.currentTimeMillis(); // TODO use this to automatically throw away sessions after AFK'ing for a long time
        }
    }
}
