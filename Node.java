import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Random;

public class Node {
    private String nodeName;
    private DatagramSocket socket;
    private HashMap<String, String> keyValueStore = new HashMap<>();
    private final int FORWARD_MIN_PORT = 20110;
    private final int FORWARD_MAX_PORT = 20129;
    private final Random rand = new Random();

    public void setNodeName(String name) {
        this.nodeName = name;
        System.out.println("💻 CRN Node running as: " + nodeName);
    }

    public void openPort(int port) throws Exception {
        socket = new DatagramSocket(port);
        System.out.println("🔌 Socket opened on port: " + port);
    }

    public void handleIncomingMessages(int durationMillis) throws Exception {
        long endTime = System.currentTimeMillis() + durationMillis;
        byte[] buffer = new byte[1024];

        while (System.currentTimeMillis() < endTime) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String input = new String(packet.getData(), 0, packet.getLength()).trim();
            System.out.println("\n📥 [RECEIVED] " + input);

            // Skip clearly invalid formats
            if (!input.startsWith("BB")) {
                System.out.println("❌ [SKIP] Not a CRN-formatted message.");
                continue;
            }

            String[] tokens = input.split(" ");
            if (tokens.length != 7) {
                System.out.println("❌ [ERROR] Invalid CRN message format.");
                continue;
            }

            try {
                String magic = tokens[0];
                String type = tokens[1];
                int ttl = Integer.parseInt(tokens[2]);
                String sender = tokens[3];
                int hops = Integer.parseInt(tokens[4]);
                String ip = tokens[5];
                int port = Integer.parseInt(tokens[6]);

                if (!magic.equals("BB")) {
                    System.out.println("❌ [ERROR] Invalid magic header.");
                    continue;
                }

                if (ttl <= 0) {
                    System.out.println("⏱️ [DROP] TTL expired for message from " + sender);
                    continue;
                }

                if (type.equals("W")) {
                    String key = sender.split(":")[1] + "-" + hops;
                    String value = "D:" + key;
                    write(key, value);
                    System.out.println("✅ [STORE] " + key + " = " + value);

                    // Send B reply
                    String backMessage = String.join(" ",
                            "BB", "B", "0", nodeName, "0", "127.0.0.1", Integer.toString(socket.getLocalPort())
                    );
                    byte[] responseBytes = backMessage.getBytes();
                    DatagramPacket response = new DatagramPacket(
                            responseBytes,
                            responseBytes.length,
                            InetAddress.getByName(ip),
                            port
                    );
                    socket.send(response);
                    System.out.println("📩 [SENT] B message to " + sender + " (port " + port + ")");

                    // Forward W message to another random port
                    if (ttl > 1) {
                        int forwardPort;
                        do {
                            forwardPort = FORWARD_MIN_PORT + rand.nextInt(FORWARD_MAX_PORT - FORWARD_MIN_PORT + 1);
                        } while (forwardPort == socket.getLocalPort());

                        String forwardMessage = String.join(" ",
                                "BB", "W", Integer.toString(ttl - 1), sender, Integer.toString(hops + 1), "127.0.0.1", Integer.toString(port)
                        );
                        byte[] forwardBytes = forwardMessage.getBytes();
                        DatagramPacket forwardPacket = new DatagramPacket(
                                forwardBytes,
                                forwardBytes.length,
                                InetAddress.getByName("127.0.0.1"),
                                forwardPort
                        );
                        socket.send(forwardPacket);
                        System.out.println("🚀 [FORWARD] W message to port " + forwardPort);
                    }

                } else if (type.equals("B")) {
                    System.out.println("🔁 [RECEIVED] B message from " + sender);

                } else {
                    System.out.println("❓ [ERROR] Unknown message type: " + type);
                }

            } catch (NumberFormatException e) {
                System.out.println("❌ [ERROR] Number format issue: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("❌ [ERROR] General error: " + e.getMessage());
            }
        }

        System.out.println("\n📦 [FINAL STORE CONTENTS]");
        if (keyValueStore.isEmpty()) {
            System.out.println("❌ No entries stored.");
        } else {
            keyValueStore.forEach((k, v) -> System.out.println("🔑 " + k + " ➡️ " + v));
        }
    }

    public String read(String key) {
        return keyValueStore.get(key);
    }

    public boolean write(String key, String value) {
        keyValueStore.put(key, value);
        return true;
    }
}
