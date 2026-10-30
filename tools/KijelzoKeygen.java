import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Usage:
 *   java tools/KijelzoKeygen.java --init           create the signing keypair (once)
 *   java tools/KijelzoKeygen.java <DEVICE-ID>      print the activation key for a device (Pi CPU serial or Windows MachineGuid)
 * The private key lives in ~/kijelzo/kijelzo-license/private.key and must never be committed.
 */
public class KijelzoKeygen {

    private static final Path KEY_DIR = Paths.get(System.getProperty("user.home"), "kijelzo", "kijelzo-license");
    private static final Path PRIVATE_KEY = KEY_DIR.resolve("private.key");
    private static final String MESSAGE_PREFIX = "KIJELZO-PI:";
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java tools/KijelzoKeygen.java --init | <DEVICE-ID>");
            System.exit(1);
        }
        if (args[0].equals("--init")) {
            init();
        } else {
            System.out.println(sign(args[0].trim().toUpperCase()));
        }
    }

    private static void init() throws Exception {
        if (Files.exists(PRIVATE_KEY)) {
            System.err.println("Refusing to overwrite existing " + PRIVATE_KEY);
            System.exit(1);
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = gen.generateKeyPair();
        Files.createDirectories(KEY_DIR);
        Files.write(PRIVATE_KEY, Base64.getEncoder().encode(pair.getPrivate().getEncoded()));
        Files.setPosixFilePermissions(PRIVATE_KEY, PosixFilePermissions.fromString("rw-------"));
        System.out.println("Private key written to " + PRIVATE_KEY);
        System.out.println("Public key (paste into Activation.PUBLIC_KEY):");
        System.out.println(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    }

    private static String sign(String deviceId) throws Exception {
        byte[] pkcs8 = Base64.getDecoder().decode(Files.readAllBytes(PRIVATE_KEY));
        PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initSign(key);
        sig.update((MESSAGE_PREFIX + deviceId).getBytes(StandardCharsets.UTF_8));
        return group(base32(sig.sign()));
    }

    private static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    private static String group(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += 5) {
            if (i > 0) sb.append('-');
            sb.append(s, i, Math.min(i + 5, s.length()));
        }
        return sb.toString();
    }
}
