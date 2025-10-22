package com.github.hammercroft.m4tchatprogram;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents a M4T network payload consisting of a message ID and string content.
 * <p>
 * Provides utilities to convert to/from raw byte arrays for transmission.
 */
public class Payload {

    /** The message ID of this payload. */
    private final short messageId;

    /** The UTF-8 string content of this payload. */
    private final String content;

    /**
     * Creates a new Payload instance.
     *
     * @param messageId the identifier for this message
     * @param content the UTF-8 content of the message
     */
    public Payload(short messageId, String content) {
        this.messageId = messageId;
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
     * Returns the UTF-8 content of this payload.
     *
     * @return the message content
     */
    public String getContent() {
        return content;
    }

    /**
     * Parses raw bytes into a Payload.
     *
     * @param data the raw data bytes
     * @param length the number of valid bytes in {@code data}
     * @return a new Payload instance containing the parsed message ID and content
     */
    public static Payload fromBytes(byte[] data, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);

        short messageId = buffer.getShort();
        byte[] contentBytes = new byte[length - 2];
        buffer.get(contentBytes);

        String content = new String(contentBytes, StandardCharsets.UTF_8);

        return new Payload(messageId, content);
    }

    /**
     * Converts this Payload into a raw byte array suitable for transmission.
     *
     * @return a byte array containing the message ID followed by UTF-8 encoded content
     */
    public byte[] toBytes() {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(2 + contentBytes.length);

        buffer.putShort(messageId);
        buffer.put(contentBytes);

        return buffer.array();
    }
}