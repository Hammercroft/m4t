package com.github.hammercroft.m4tchatprogram;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;

/*
 * TODO:
 * Review ourSocket, theirAddress, and theirPort.
 * They could be made final and initialised via a constructor.
 *
 * However, using such a constructor would require rethinking
 * how and where these values are obtained.
 */

/**
 * Represents the shared runtime state of an M4TChatProgram instance.
 * <p>
 * This class holds mutable state information such as socket references,
 * peer address and port, buffer settings, and session metadata.
 * Thread safety is provided through the use of {@code volatile} fields.
 * </p>
 * 
 * @author hammercroft
 */
public class ProgramState {
    private volatile String ourIdentityToken = TokenMaker.generateAnonId("behindU");
    private volatile DatagramSocket ourSocket;
    private volatile InetAddress theirAddress;
    private volatile int theirPort;
    private volatile int bufferSize;
    private volatile short sessionDiscriminator = 0;
    private volatile long lastReceivedTransmissionTime = 0;
    
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                .withZone(ZoneId.systemDefault());


    // GETTERS AND SETTERS

    /**
     * Returns this program's current identity token.
     *
     * @return the identity token string
     */
    public String getOurIdentityToken() { return ourIdentityToken; }

    /**
     * Updates this program's identity token.
     *
     * @param token the new identity token to assign
     */
    public void setOurIdentityToken(String token) { this.ourIdentityToken = token; }

    /**
     * Returns the local {@link DatagramSocket} used for communication.
     *
     * @return the local socket, or {@code null} if uninitialised
     */
    public DatagramSocket getOurSocket() { return ourSocket; }

    /**
     * Sets the local {@link DatagramSocket} used for communication.
     *
     * @param socket the socket to assign
     */
    public void setOurSocket(DatagramSocket socket) { this.ourSocket = socket; }

    /**
     * Returns the remote peer's {@link InetAddress}.
     *
     * @return the remote address, or {@code null} if unknown
     */
    public InetAddress getTheirAddress() { return theirAddress; }

    /**
     * Sets the remote peer's {@link InetAddress}.
     *
     * @param address the remote address to assign
     */
    public void setTheirAddress(InetAddress address) { this.theirAddress = address; }

    /**
     * Returns the remote peer's UDP port number.
     *
     * @return the remote port number
     */
    public int getTheirPort() { return theirPort; }

    /**
     * Sets the remote peer's UDP port number.
     *
     * @param port the port number to assign
     */
    public void setTheirPort(int port) { this.theirPort = port; }

    /**
     * Returns the size of the communication buffer.
     *
     * @return the buffer size in bytes
     */
    public int getBufferSize() { return bufferSize; }

    /**
     * Sets the size of the communication buffer.
     *
     * @param bufferSize the buffer size in bytes
     */
    public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }

    /**
     * Returns the session discriminator value.
     * <p>
     * This short value is typically used to differentiate concurrent
     * or sequential communication sessions.
     * </p>
     *
     * @return the current session discriminator
     */
    public short getSessionDiscriminator() { return sessionDiscriminator; }

    /**
     * Updates the session discriminator value.
     *
     * @param sessionDiscriminator the new session discriminator
     */
    public void setSessionDiscriminator(short sessionDiscriminator) { 
        this.sessionDiscriminator = sessionDiscriminator; 
    }

    /**
     * Returns the timestamp (in milliseconds since the epoch)
     * of the most recently received transmission.
     *
     * @return the last received transmission time, or {@code 0} if none
     */
    public long getLastReceivedTransmissionTime() { return lastReceivedTransmissionTime; }

    /**
     * Updates the timestamp (in milliseconds since the epoch)
     * of the most recently received transmission.
     *
     * @param lastReceivedTransmissionTime the timestamp to assign
     */
    public void setLastReceivedTransmissionTime(long lastReceivedTransmissionTime) {
        this.lastReceivedTransmissionTime = lastReceivedTransmissionTime;
    }

    /**
     * Returns a human-readable summary of the current program state,
     * including socket information, peer details, and session metadata.
     *
     * @return a formatted string representing the current program state
     */
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
        sb.append("  lastReceivedTransmissionTime: ").append(this.lastReceivedTransmissionTime).append("\n");
        sb.append("  lastReceivedTransmissionTime (formatted): ");
        if (this.lastReceivedTransmissionTime == 0) {
            sb.append("null\n");
        } else {
            sb.append(FORMATTER.format(Instant.ofEpochMilli(this.lastReceivedTransmissionTime))).append("\n");
        }
        return sb.toString();
    }
}