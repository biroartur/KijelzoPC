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

    // X.509 EC P-256 public key, hex so it decodes on Java 7 (no java.util.Base64)
    private static final String PUBLIC_KEY_HEX =
            "3059301306072a8648ce3d020106082a8648ce3d030107034200041e18b60196a37c72e4e531a4"
            + "f45337a22e676909f5581195f30578be274e4333e700adf85484f4418ef9fa0df1a6eb1ae6055b"
            + "1bb33ec0ad83ca64cab8eb96f5";

    public static final String MESSAGE_PREFIX = "KIJELZO-PI:";
    private static final String KEY_FILE = "activation.key";
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static volatile boolean activated;
    private static volatile String activeKey;
    private static String cachedDeviceId;
    private static final java.util.concurrent.CountDownLatch activationDone =
            new java.util.concurrent.CountDownLatch(1);
    private static JDialog dialog;

    private Activation() {
    }

    public static boolean isActivated() {
        return activated;
    }

    /** The valid key this device was activated with, or null while not activated. */
    public static String activeKey() {
        return activeKey;
    }

    /** Blocks until a valid key is entered in the on-screen dialog or submitted over HTTP. */
    public static void ensureActivated() {
        String stored = readKeyFile();
        if (stored != null && submitKey(stored)) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                showDialog();
            }
        });
        try {
            activationDone.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Accepts the key if it is valid for this device; safe to call from any thread. */
    public static synchronized boolean submitKey(String key) {
        if (activated) {
            return true;
        }
        if (key == null || !isValid(deviceId(), key)) {
            return false;
        }
        writeKeyFile(key.trim());
        activeKey = key.trim();
        activated = true;
        activationDone.countDown();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (dialog != null) {
                    dialog.dispose();
                }
            }
        });
        return true;
    }

    private static void showDialog() {
        if (activated) {
            return;
        }
        final JTextField idField = new JTextField(deviceId());
        idField.setEditable(false);
        final JTextField keyField = new JTextField(50);
        JButton ok = new JButton("Activate");
        JButton cancel = new JButton("Quit");

        JPanel fields = new JPanel(new GridLayout(0, 1, 4, 4));
        fields.add(new JLabel("KijelzoPC is not activated."));
        fields.add(new JLabel("Device ID (send this to get a key):"));
        fields.add(idField);
        fields.add(new JLabel("Activation key (or enter it on the admin page):"));
        fields.add(keyField);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(fields, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        dialog = new JDialog((Frame) null, "KijelzoPC activation", false);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                System.exit(0);
            }
        });
        ok.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!submitKey(keyField.getText())) {
                    JOptionPane.showMessageDialog(dialog, "Invalid activation key for this device.",
                            "KijelzoPC activation", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        cancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                System.exit(0);
            }
        });
        dialog.getRootPane().setDefaultButton(ok);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
    }

    public static boolean isValid(String deviceId, String key) {
        try {
            byte[] raw = base32Decode(key);
            if (raw.length != 64) {
                return false;
            }
            byte[] der = rawToDer(raw);
            PublicKey pub = KeyFactory.getInstance("EC").generatePublic(
                    new X509EncodedKeySpec(hexDecode(PUBLIC_KEY_HEX)));
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(pub);
            sig.update((MESSAGE_PREFIX + deviceId).getBytes(UTF8));
            return sig.verify(der);
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized String deviceId() {
        if (cachedDeviceId == null) {
            cachedDeviceId = readDeviceId();
        }
        return cachedDeviceId;
    }

    private static String readDeviceId() {
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            String guid = readWindowsMachineGuid();
            if (guid != null) {
                return guid;
            }
        }
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

    private static String readWindowsMachineGuid() {
        // /reg:64 so a 32-bit JVM on 64-bit Windows isn't redirected to the WOW64 view, which lacks MachineGuid
        String[][] commands = {
                {"reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid", "/reg:64"},
                {"reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"}
        };
        for (String[] command : commands) {
            try {
                Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("MachineGuid")) {
                            String[] parts = line.split("\\s+");
                            return parts[parts.length - 1].toUpperCase();
                        }
                    }
                } finally {
                    in.close();
                    p.destroy();
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static byte[] hexDecode(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
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
