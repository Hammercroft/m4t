package com.github.hammercroft.m4tchatprogram;

import static com.github.hammercroft.m4tchatprogram.Semaphore.*;

import java.io.IOException;

/**
 *
 * @author hammercroft
 */
public class KeepAliveTransmitter {
    public static Thread start(M4TChatProgram chatProgram) {
        Thread keepAliveThread = new Thread(() -> {
            final long intervalNs = 3_000_000_000L;
            long nextSendTime = System.nanoTime() + intervalNs;

            while (!Thread.currentThread().isInterrupted()) {
                long now = System.nanoTime();

                if (now >= nextSendTime) {
                    try {
                        chatProgram.sendMessage(S_KA.token());
                        nextSendTime += intervalNs;
                    } catch (IOException ex) {
                        System.getLogger(M4TChatProgram.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                    }
                } else {
                    long sleepNs = nextSendTime - now;
                    try {
                        long sleepMs = sleepNs / 1_000_000;
                        int sleepNano = (int) (sleepNs % 1_000_000);
                        Thread.sleep(sleepMs, sleepNano);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "KeepAliveThread");

        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
        return keepAliveThread;
    }
}
