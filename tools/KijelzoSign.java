import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Signs a release JAR for the KijelzoPC updater (used by tools/release.sh).
 *
 *   java tools/KijelzoSign.java <pi|windows> <version> <jar>   prints update-<platform>.txt
 *
 * Uses the activation private key in ~/kijelzo/kijelzo-license/private.key (never commit it).
 */
public class KijelzoSign {

    private static final Path PRIVATE_KEY =
            Paths.get(System.getProperty("user.home"), "kijelzo", "kijelzo-license", "private.key");

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[0].matches("pi|windows") || !args[1].matches("v\\d+(\\.\\d+){0,3}")) {
            System.err.println("usage: java tools/KijelzoSign.java <pi|windows> <vX.Y> <jar>");
            System.exit(2);
        }
        String sha256 = hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get(args[2]))));
        byte[] pkcs8 = Base64.getMimeDecoder().decode(Files.readAllBytes(PRIVATE_KEY));
        PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        // Must match kijelzopc.Updater: platform and version are signed so a file can't be relabelled
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(key);
        sig.update(("KIJELZO-UPDATE:" + args[0] + ":" + args[1] + ":" + sha256).getBytes(StandardCharsets.UTF_8));
        System.out.println("version=" + args[1]);
        System.out.println("sha256=" + sha256);
        System.out.println("signature=" + Base64.getEncoder().encodeToString(sig.sign()));
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
