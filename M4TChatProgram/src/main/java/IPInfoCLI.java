import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.Callable;

@Command(
        name = "ipinfo",
        mixinStandardHelpOptions = true,
        version = "ipinfo 1.0",
        description = "Prints the local and public IP addresses of this machine."
)
public class IPInfoCLI implements Callable<Integer> {

    @Override
    public Integer call() {
        printLocalAddresses();
        printPublicAddresses();
        return 0;
    }

    private void printLocalAddresses() {
        System.out.println("Your Local IP addresses:");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    System.out.println(" - " + addr.getHostAddress() + " (" +
                            (addr instanceof Inet4Address ? "IPv4" : "IPv6") + ")");
                }
            }
        } catch (SocketException e) {
            System.err.println("Failed to enumerate local interfaces: " + e.getMessage());
        }
    }

    private void printPublicAddresses() {
        System.out.println("\nYour Public IP addresses as seen by ipify.org:");
        printPublicIP("https://api.ipify.org", "IPv4");
        printPublicIP("https://api6.ipify.org", "IPv6");
    }

    private void printPublicIP(String url, String type) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
            String publicIP = in.readLine();
            System.out.println(" - Public " + type + ": " + publicIP);
        } catch (Exception e) {
            System.out.println(" - Public " + type + ": Could not determine (" + e.getMessage() + ")");
        }
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new IPInfoCLI()).execute(args);
        System.exit(exitCode);
    }
}
