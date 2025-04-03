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

            // Message must begin with BB and contain exactly 7 tokens
            String[] tokens = input.split(" ");
            if (tokens.length != 7 || !tokens[0].equals("BB")) {
                System.out.println("❌ [SKIP] Not a CRN-formatted message.");
                continue;
            }

            try {
                String type = tokens[1];
                int ttl = Integer.parseInt(tokens[2]);
                String sender = tokens[3];
                int hops = Integer.parseInt(tokens[4]);
                String ip = tokens[5];
                int port = Integer.parseInt(tokens[6]);

                if (ttl <= 0) {
                    System.out.println("⏱️ [DROP] TTL expired for message from " + sender);
                    continue;
                }

                switch (type) {
                    case "W":
                        String key = sender.split(":")[1] + "-" + hops;
                        String value = "D:" + key;
                        write(key, value);
                        System.out.println("✅ [STORE] " + key + " = " + value);

                        // Send back a B reply
                        String reply = String.join(" ", "BB", "B", "0", nodeName, "0", "127.0.0.1", Integer.toString(socket.getLocalPort()));
                        DatagramPacket response = new DatagramPacket(
                                reply.getBytes(), reply.length(), InetAddress.getByName(ip), port
                        );
                        socket.send(response);
                        System.out.println("📩 [SENT] B message to " + sender + " (port " + port + ")");

                        // Forward W to a random other port
                        if (ttl > 1) {
                            int forwardPort;
                            do {
                                forwardPort = FORWARD_MIN_PORT + rand.nextInt(FORWARD_MAX_PORT - FORWARD_MIN_PORT + 1);
                            } while (forwardPort == socket.getLocalPort());

                            String forwardMsg = String.join(" ", "BB", "W",
                                    Integer.toString(ttl - 1),
                                    sender,
                                    Integer.toString(hops + 1),
                                    "127.0.0.1",
                                    Integer.toString(port)
                            );

                            DatagramPacket forwardPacket = new DatagramPacket(
                                    forwardMsg.getBytes(), forwardMsg.length(), InetAddress.getByName("127.0.0.1"), forwardPort
                            );
                            socket.send(forwardPacket);
                            System.out.println("🚀 [FORWARD] W message to port " + forwardPort);
                        }
                        break;

                    case "B":
                        System.out.println("🔁 [RECEIVED] B message from " + sender);
                        break;

                    default:
                        System.out.println("❓ [ERROR] Unknown message type: " + type);
                }
            } catch (Exception e) {
                System.out.println("❌ [ERROR] Failed to parse or handle message: " + e.getMessage());
            }
        }

        // Final state of key-value store
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
