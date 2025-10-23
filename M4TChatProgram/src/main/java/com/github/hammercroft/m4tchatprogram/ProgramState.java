package com.github.hammercroft.m4tchatprogram;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;

/**
 *
 * @author hammercroft
 */
public class ProgramState {

    public DatagramSocket ourSocket;           // our operating port & localhost address
    public InetAddress theirAddress;           // message target address
    public int theirPort;                      // message target port
    public int bufferSize;
    public short sessionDiscriminator = 0;
    public String ourIdentityToken = TokenMaker.generateAnonId("behindU");
    public long lastRecievedTransmissionTime = 0;

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                         .withZone(ZoneId.systemDefault());

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Program State:\n");
        sb.append("  ourSocket: ").append(ourSocket != null ? ourSocket.getLocalSocketAddress() : "null").append("\n");
        sb.append("  theirAddress: ").append(theirAddress != null ? theirAddress.getHostAddress() : "null").append("\n");
        sb.append("  theirPort: ").append(theirPort).append("\n");
        sb.append("  bufferSize: ").append(bufferSize).append("\n");
        sb.append("  sessionDiscriminator: ").append(sessionDiscriminator).append("\n");
        sb.append("  ourIdentityToken: ").append(ourIdentityToken).append("\n");
        sb.append("  lastRecievedTransmissionTime: ").append(lastRecievedTransmissionTime).append("\n");
        sb.append("  lastRecievedTransmissionTime (formatted): ");
        if (lastRecievedTransmissionTime == 0) {
            sb.append("null\n");
        } else {
            sb.append(FORMATTER.format(Instant.ofEpochMilli(lastRecievedTransmissionTime))).append("\n");
        }
        return sb.toString();
    }
}