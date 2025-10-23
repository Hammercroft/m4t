package com.github.hammercroft.m4tchatprogram;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents a M4T network payload consisting of a message ID, session discriminator, and string content.
 * <p>
 * Provides utilities to convert to/from raw byte arrays for transmission.
 */
public class Payload {

    /** The message ID of this payload. */
    private final short messageId;

    /** The session discriminator of this payload. */
    private final short sessionDiscriminator;

    /** The UTF-8 string content of this payload. */
    private final String content;

    /**
     * Creates a new Payload instance.
     *
     * @param messageId the identifier for this message
     * @param sessionDiscriminator the session identifier
     * @param content the UTF-8 content of the message
     */
    public Payload(short messageId, short sessionDiscriminator, String content) {
        this.messageId = messageId;
        this.sessionDiscriminator = sessionDiscriminator;
        this.content = content;
    }

    /**
     * Returns the message ID of this payload.
     *
     * @return the message ID
     */
    public short getMessageId() {
        return messageId;
    }

    /**
     * Returns the session discriminator of this payload.
     *
     * @return the session discriminator
     */
    public short getSessionDiscriminator() {
        return sessionDiscriminator;
    }

    /**
     * Returns the UTF-8 content of this payload.
     *
     * @return the message content
     */
    public String getContent() {
        return content;
    }

    /**
     * Parses raw bytes into a Payload. The transient message ID and the session discriminator should be in big-endian order.
     *
     * @param data the raw data bytes
     * @param length the number of valid bytes in {@code data}
     * @return a new Payload instance containing the parsed message ID, session discriminator, and content
     */
    public static Payload fromBytes(byte[] data, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);

        // Transient message ID (BE 2 bytes)
        short messageId = buffer.getShort();
        
        // Session Discriminator (BE 2 bytes)
        short sessionDiscriminator = buffer.getShort();
        
        // Content (remaining bytes)
        // Total overhead for short fields is 4 bytes (2 for ID + 2 for discriminator)
        byte[] contentBytes = new byte[length - 4];
        buffer.get(contentBytes);

        String content = new String(contentBytes, StandardCharsets.UTF_8);

        return new Payload(messageId, sessionDiscriminator, content);
    }

    /**
     * Converts this Payload into a raw byte array suitable for transmission.
     *
     * @return a byte array containing the message ID, session discriminator, followed by UTF-8 encoded content
     */
    public byte[] toBytes() {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        
        // Total size = 2 bytes (ID) + 2 bytes (Session) + content length
        ByteBuffer buffer = ByteBuffer.allocate(4 + contentBytes.length);

        buffer.putShort(messageId);
        buffer.putShort(sessionDiscriminator);
        buffer.put(contentBytes);

        return buffer.array();
    }
}