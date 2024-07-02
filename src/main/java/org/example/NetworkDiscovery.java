package org.example;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class NetworkDiscovery {
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final String BROADCAST_MESSAGE = "FILE_SHARE_APP";
    private static final int BROADCAST_INTERVAL = 5000; // 5 seconds

    private final List<InetAddress> discoveredPeers = new ArrayList<>();
    private MulticastSocket socket;
    private InetAddress group;
    private volatile boolean running = true;

    public void start() throws IOException {
        socket = new MulticastSocket(MULTICAST_PORT);
        group = InetAddress.getByName(MULTICAST_ADDRESS);
        socket.joinGroup(group);

        // Start broadcasting
        new Thread(this::broadcast).start();

        // Start listening
        new Thread(this::listen).start();
    }

    private void broadcast() {
        while (running) {
            try {
                byte[] buffer = BROADCAST_MESSAGE.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, MULTICAST_PORT);
                socket.send(packet);
                Thread.sleep(BROADCAST_INTERVAL);
            } catch (IOException e) {
                if (!running) break;
                e.printStackTrace();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void listen() {
        byte[] buffer = new byte[256];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                if (BROADCAST_MESSAGE.equals(message)) {
                    InetAddress peerAddress = packet.getAddress();
                    if (!discoveredPeers.contains(peerAddress)) {
                        discoveredPeers.add(peerAddress);
                        System.out.println("Discovered peer: " + peerAddress.getHostAddress());
                    }
                }
            } catch (SocketException e) {
                if (!running) break;
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<InetAddress> getDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers);
    }

    public void stop() {
        running = false;
        socket.close();
    }
}