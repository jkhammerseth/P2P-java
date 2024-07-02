package org.example;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class P2P {
    private static final int PORT = 8888;
    private final List<File> sharedFiles = new CopyOnWriteArrayList<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private NetworkDiscovery networkDiscovery;
    private volatile boolean running = true;

    public void startServer() throws IOException {
        serverSocket = new ServerSocket(PORT);
        networkDiscovery = new NetworkDiscovery();
        networkDiscovery.start();
        System.out.println("Server started on port " + PORT);
        Thread serverThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                } catch (SocketException e) {
                    if (!running) {
                        break;
                    }
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        serverThread.start();
    }

    public void stopServer() {
        running = false;
        try {
            if (networkDiscovery != null) {
                networkDiscovery.stop();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<InetAddress> getDiscoveredPeers() {
        return networkDiscovery.getDiscoveredPeers();
    }

    private void handleClient(Socket clientSocket) {
        executorService.submit(() -> {
            try (
                    ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())
            ) {
                String request = (String) in.readObject();
                if ("LIST".equals(request)) {
                    out.writeObject(sharedFiles);
                } else if ("GET_SHARED_FILES".equals(request)) {
                    out.writeObject(sharedFiles);
                } else if (request.startsWith("GET:")) {
                    String fileName = request.substring(4);
                    File file = sharedFiles.stream()
                            .filter(f -> f.getName().equals(fileName))
                            .findFirst()
                            .orElse(null);
                    if (file != null) {
                        out.writeObject(Files.readAllBytes(file.toPath()));
                    } else {
                        out.writeObject(null);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void addSharedFile(File file) {
        sharedFiles.add(file);
    }

    public void removeSharedFile(File file) {
        sharedFiles.remove(file);
    }

    public List<File> getSharedFiles() {
        return new ArrayList<>(sharedFiles);
    }

    public String getMyIp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("google.com", 80));
            return (socket.getLocalAddress().toString()).substring(1);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<File> getSharedPeerFiles(String peerIp) {
        List<File> peerFiles = new ArrayList<>();

        // Ensure a peer is selected
        if (peerIp == null || peerIp.isEmpty()) {
            System.out.println("No peer selected");
            return peerFiles;
        }

        try {
            // Connect to the peer
            Socket socket = new Socket(peerIp, P2P.PORT);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // Send a request for shared files
            out.writeObject("GET_SHARED_FILES");
            out.flush();

            // Receive the list of shared files
            Object response = in.readObject();
            if (response instanceof List<?>) {
                peerFiles = (List<File>) response;
            }

            // Close the connection
            socket.close();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error getting shared files from peer: " + e.getMessage());
        }

        return peerFiles;
    }

    public byte[] downloadFile(String serverAddress, String fileName) throws IOException, ClassNotFoundException {
        try (
                Socket socket = new Socket(serverAddress, PORT);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            out.writeObject("GET:" + fileName);
            return (byte[]) in.readObject();
        }
    }

    public void saveFile(File file, byte[] content) throws IOException {
        Files.write(file.toPath(), content);
    }
}