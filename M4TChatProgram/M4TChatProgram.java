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
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Scanner;

/**
 * A simple UDP-based, two-participant chat program.
 * @author org.nekoweb.hammercroft
 */
public class M4TChatProgram {
    // CONSTANTS
    final static int BUFFER_SIZE = 800; // well within the average MTU of old cellular internet.
    
    // TRANSMISSION CONFIGURATION
    static DatagramSocket ourSocket = null; // Port of which we receive messages (not necessarily coming from our target peer...)
    static InetAddress theirAddress = null; // Target peer's address
    static int theirPort = -1; // Target peer's receiving port. This field needs re-initialization with a valid port number.
    
    // OTHER PROGRAM FIELDS
    static Scanner scn = new Scanner(System.in);
    
    public static void resolveTransmissionConfig(){
        // TODO resolve on headless launches
        
        // [ Resolve transmission config via user input ]
        System.out.println("Please enter your desired receiving port (0–65535):");
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
                    System.out.println("Using port "+ ourSocket.getLocalPort());
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a numeric port.");
            } catch (SocketException e) {
                System.out.println("Failed to bind to port. It may be in use or restricted. Please try another port.");
            }
        }
        System.out.println();
        
        System.out.println("Please enter your target's IP address.");
        while (theirAddress == null){
            try {
                theirAddress = InetAddress.getByName(scn.nextLine().trim());
            } catch (UnknownHostException ex) {
                System.out.println("Unknown host / invalid IP address. Please try another hostname / valid IP address.");
            }
        }
        System.out.println();
        
        System.out.println("Please enter your target's recieving port (1-65535). Leave empty to reuse your recieving port."); // our recieving port in question is in DatagramSocket ourSocket
        while (theirPort == -1) {
            String line = scn.nextLine().trim();
            if (line.isEmpty()) {
                // Reuse our own receiving port
                theirPort = ourSocket.getLocalPort();
                System.out.println("Targeting "+ theirPort + "");
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
            e.printStackTrace();
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        boolean communicating = true;
        printYourAddresses(); // TODO dont print on headless mode

        System.out.println(); // TODO dont print on headless mode
        resolveTransmissionConfig();

        // Separate thread for recieving messages.
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    // This call blocks until a packet is received
                    ourSocket.receive(packet);

                    // Extract data
                    String received = new String(packet.getData(), 0, packet.getLength());
                    InetAddress senderAddress = packet.getAddress();
                    int senderPort = packet.getPort();
                    System.out.print(" (+) ");
                    System.out.print(received);
                    if (!received.isEmpty() && received.charAt(received.length() - 1) == '\n') {
                        // if message ended with a newline, do nothing.
                    } else {
                        // if message did not end with a newline, print a newline.
                        System.out.print('\n');
                    }
                } catch (IOException ex) {
                    // Crash this thread silently. Quick and dirty way to end this thread after closing ourSocket in the main thread.
                }
            }
        }).start();
        
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Starting communication to "+theirAddress+":"+theirPort);
        System.out.println("Due to the nature of delivery via UDP, the delivery of messages between you and your peer is not guaranteed.");
        System.out.println();
        System.out.println("RECEIVED MESSAGES ARE NOT GUARANTEED TO ORIGINATE FROM YOUR INTENDED COMMUNICATION TARGET.");
        System.out.println("YOUR MESSAGES ARE NOT ENCRYPTED.");
        System.out.println();
        System.out.println("To stop communication, do enter .exit");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("(Communication start.)");
        
        // Transmission of messages to target
        while (true) {
            String message = scn.nextLine();
            if (message.equalsIgnoreCase(".exit")) {
                break;
            }
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, theirAddress, theirPort);
            try {
                ourSocket.send(packet); // send the packet
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        ourSocket.close();
        System.out.println("(Communication end).");
    }
}
