package com.github.hammercroft.m4tchatprogram;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

public final class TokenMaker {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int OUT_CHARS = 32;

    private TokenMaker() {} // prevent instantiation

    /**
     * Generates a 32-character strictly alphanumeric identifier
     * based on machine MAC, OS username, current nanosecond timestamp, and a salt.
     *
     * @param salt constant phrase for additional entropy
     * @return 32-character alphanumeric string (UTF-8 bytes = 32)
     */
    public static String generateAnonId(String salt) {
        byte[] mac = getMacAddressOrRandom();
        String osUser = System.getProperty("user.name");
        long nano = System.nanoTime();

        byte[] input = concat(mac,
                              ByteBuffer.allocate(Long.BYTES).putLong(nano).array(),
                              osUser.getBytes(StandardCharsets.UTF_8),
                              salt.getBytes(StandardCharsets.UTF_8));

        byte[] hash = sha256(input);
        return encodeBase62(hash).substring(0, OUT_CHARS);
    }

    // ---- helpers ----

    private static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] arr : arrays) len += arr.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, out, pos, arr.length);
            pos += arr.length;
        }
        return out;
    }

    private static byte[] getMacAddressOrRandom() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                if (nif == null || nif.isLoopback() || nif.isVirtual() || !nif.isUp()) continue;
                byte[] mac = nif.getHardwareAddress();
                if (mac != null && mac.length >= 6) return mac;
            }
        } catch (SocketException ignored) { }
        // fallback pseudo-MAC
        long fallback = System.currentTimeMillis();
        ByteBuffer b = ByteBuffer.allocate(8);
        b.putLong(fallback);
        return b.array();
    }

    private static String encodeBase62(byte[] data) {
        StringBuilder sb = new StringBuilder();
        java.math.BigInteger value = new java.math.BigInteger(1, data); // unsigned

        while (value.compareTo(java.math.BigInteger.ZERO) > 0) {
            java.math.BigInteger[] divmod = value.divideAndRemainder(java.math.BigInteger.valueOf(62));
            sb.append(BASE62.charAt(divmod[1].intValue()));
            value = divmod[0];
        }
        return sb.reverse().toString();
    }
}
