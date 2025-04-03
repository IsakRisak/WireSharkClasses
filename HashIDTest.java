public class HashIDTest {
    public static void main(String[] args) throws Exception {
        // Create a HashID object and test its methods
        byte[] hash = HashID.computeHashID("testInput");

        // Print hash as hexadecimal
        System.out.print("HashID (hex): ");
        for (byte b : hash) {
            System.out.printf("%02x", b); // hex format with leading zero
        }
        System.out.println(); // newline
    }
}

