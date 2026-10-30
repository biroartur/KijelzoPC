package kijelzopc;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;

public final class Activation {

    private static final String PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEHhi2AZajfHLk5TGk9FM3oi5naQn1WBGV8wV4vidOQzPnAK34VIT0QY75+g3xpusa5gVbG7M+wK2DymTKuOuW9Q==";

    public static final String MESSAGE_PREFIX = "KIJELZO-PI:";
    private static final String KEY_FILE = "activation.key";
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Activation() {
    }

    public static void ensureActivated() {
        String deviceId = deviceId();
        String stored = readKeyFile();
        if (stored != null && isValid(deviceId, stored)) {
            return;
        }

        JTextField idField = new JTextField(deviceId);
        idField.setEditable(false);
        JTextField keyField = new JTextField(50);

        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("KijelzoPC is not activated."));
        panel.add(new JLabel("Device ID (send this to get a key):"));
        panel.add(idField);
        panel.add(new JLabel("Activation key:"));
        panel.add(keyField);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(null, panel, "KijelzoPC activation",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                System.exit(0);
            }
            String key = keyField.getText();
            if (isValid(deviceId, key)) {
                writeKeyFile(key.trim());
                return;
            }
            JOptionPane.showMessageDialog(null, "Invalid activation key for this device.",
                    "KijelzoPC activation", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static boolean isValid(String deviceId, String key) {
        try {
            byte[] raw = base32Decode(key);
            if (raw.length != 64) {
                return false;
            }
            byte[] der = rawToDer(raw);
            PublicKey pub = KeyFactory.getInstance("EC").generatePublic(
                    new X509EncodedKeySpec(java.util.Base64.getDecoder().decode(PUBLIC_KEY)));
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(pub);
            sig.update((MESSAGE_PREFIX + deviceId).getBytes(UTF8));
            return sig.verify(der);
        } catch (Exception e) {
            return false;
        }
    }

    public static String deviceId() {
        String serial = readCpuSerial();
        if (serial != null) {
            return serial;
        }
        String machineId = readFirstLine("/etc/machine-id");
        if (machineId != null && !machineId.isEmpty()) {
            return machineId.toUpperCase();
        }
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                byte[] mac = nif.getHardwareAddress();
                if (mac != null && mac.length == 6 && !nif.isLoopback()) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return "UNKNOWN";
    }

    private static String readCpuSerial() {
        try {
            BufferedReader in = new BufferedReader(new FileReader("/proc/cpuinfo"));
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("Serial")) {
                        String value = line.substring(line.indexOf(':') + 1).trim().toUpperCase();
                        if (!value.isEmpty() && !value.matches("0+")) {
                            return value;
                        }
                    }
                }
            } finally {
                in.close();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String readFirstLine(String path) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(path));
            try {
                String line = in.readLine();
                return line == null ? null : line.trim();
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String readKeyFile() {
        return readFirstLine(KEY_FILE);
    }

    private static void writeKeyFile(String key) {
        try {
            Writer out = new OutputStreamWriter(new FileOutputStream(KEY_FILE), UTF8);
            try {
                out.write(key + "\n");
            } finally {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static byte[] base32Decode(String text) {
        String s = text.toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < s.length(); i++) {
            buffer = (buffer << 5) | BASE32.indexOf(s.charAt(i));
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static byte[] rawToDer(byte[] raw) {
        byte[] r = derInt(raw, 0);
        byte[] s = derInt(raw, 32);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30);
        out.write(r.length + s.length);
        out.write(r, 0, r.length);
        out.write(s, 0, s.length);
        return out.toByteArray();
    }

    private static byte[] derInt(byte[] raw, int offset) {
        int start = offset;
        int end = offset + 32;
        while (start < end - 1 && raw[start] == 0) {
            start++;
        }
        boolean pad = (raw[start] & 0x80) != 0;
        int len = end - start + (pad ? 1 : 0);
        byte[] out = new byte[len + 2];
        out[0] = 0x02;
        out[1] = (byte) len;
        System.arraycopy(raw, start, out, 2 + (pad ? 1 : 0), end - start);
        return out;
    }
}
