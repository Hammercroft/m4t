package com.github.hammercroft.m4tchatprogram;

import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 *
 * @author hammercroft
 */
public class ProgramState {
    public DatagramSocket ourSocket; //our operating port & localhost address
    public InetAddress theirAddress; //message target address
    public int theirPort; //message target port
    public int bufferSize;
}
