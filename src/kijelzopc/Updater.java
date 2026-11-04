package kijelzopc;

import utils.SafeFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs signed GitHub releases at startup. The start script runs it from a copy of the JAR,
 * before the app starts ("update") and again after the app has ended ("rollback").
 *
 * A release carries update-pi.txt / update-windows.txt (version, SHA-256 of the JAR and an ECDSA
 * signature over both, made with the activation private key) next to KijelzoPC-pi.jar /
 * KijelzoPC-windows.jar. Only a newer version with a valid signature and matching JAR is installed.
 * The new version counts as good once the app has run for CONFIRM_SECONDS; if the app ends before
 * that, "rollback" restores the previous JAR and that version is never installed again.
 */
public final class Updater {

    public static final String UPDATE_URL = "update.url";
    private static final String DEFAULT_UPDATE_URL =
            "https://github.com/biroartur/KijelzoPC/releases/latest/download/";
    private static final String MESSAGE_PREFIX = "KIJELZO-UPDATE:";
    private static final int CONFIRM_SECONDS = 30;
    private static final int MAX_JAR_BYTES = 64 * 1024 * 1024;

    private static final File JAR = new File("out", "KijelzoPC.jar");
    private static final File PREVIOUS_JAR = new File("out", "KijelzoPC.jar.prev");
    private static final File STATE_FILE = new File("update.state");
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Updater() {
    }

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "";
        try {
            if (command.equals("update")) {
                update();
            } else if (command.equals("rollback")) {
                System.exit(rollback() ? 0 : 1);
            } else {
                System.err.println("usage: java -cp KijelzoPC.jar kijelzopc.Updater update|rollback");
                System.exit(2);
            }
        } catch (Exception e) {
            // An update problem must never keep the display from starting
            log("update check failed: " + e);
            if (command.equals("rollback")) {
                System.exit(1);
            }
        }
    }

    /** Release version currently installed (e.g. "v1.2"), or null if installed by hand. */
    public static String installedVersion() {
        String version = loadState().getProperty("version", "");
        return version.length() == 0 ? null : version;
    }

    /** Marks a freshly installed version as good once the app has run for CONFIRM_SECONDS. */
    public static void confirmAfterStartup() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(CONFIRM_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    return;
                }
                confirm();
            }
        }, "update-confirm");
        t.setDaemon(true);
        t.start();
    }

    /** Marks a freshly installed version as good (also used when the user quits on purpose). */
    public static synchronized void confirm() {
        Properties state = loadState();
        String pending = state.getProperty("pending", "");
        if (pending.length() == 0) {
            return;
        }
        state.remove("pending");
        try {
            saveState(state);
            log("update " + pending + " confirmed");
        } catch (IOException e) {
            log("could not confirm update " + pending + ": " + e);
        }
    }

    private static void update() throws Exception {
        Properties settings = new Properties();
        try {
            InputStream in = new FileInputStream("server.properties");
            try {
                settings.load(in);
            } finally {
                in.close();
            }
        } catch (IOException ignored) {
        }
        String base = settings.getProperty(UPDATE_URL, DEFAULT_UPDATE_URL).trim();
        if (base.equalsIgnoreCase("off")) {
            return;
        }
        if (!base.endsWith("/")) {
            base += "/";
        }
        Properties state = loadState();
        if (state.getProperty("pending", "").length() > 0) {
            // Last start with the new version was cut short (e.g. power cut): give it another run first
            return;
        }

        String platform = System.getProperty("os.name", "").startsWith("Windows") ? "windows" : "pi";
        Properties info = new Properties();
        info.load(new ByteArrayInputStream(fetchWithRetry(base + "update-" + platform + ".txt", 16 * 1024)));
        String version = info.getProperty("version", "").trim();
        String sha256 = info.getProperty("sha256", "").trim().toLowerCase();
        String signature = info.getProperty("signature", "").trim();

        byte[] message = (MESSAGE_PREFIX + platform + ":" + version + ":" + sha256).getBytes(UTF8);
        if (!version.matches("v\\d+(\\.\\d+){0,3}") || !sha256.matches("[0-9a-f]{64}")
                || !Activation.verify(message, Base64.getDecoder().decode(signature))) {
            log("update " + version + " rejected: invalid signature");
            return;
        }
        String installed = state.getProperty("version", "");
        if (compareVersions(version, installed) <= 0) {
            return;
        }
        if ((" " + state.getProperty("skip", "") + " ").contains(" " + version + " ")) {
            return;
        }

        log("installing update " + version + " (installed: " + (installed.length() == 0 ? "none" : installed) + ")");
        byte[] jar = fetch(base + "KijelzoPC-" + platform + ".jar", MAX_JAR_BYTES);
        if (!hex(MessageDigest.getInstance("SHA-256").digest(jar)).equals(sha256)) {
            log("update " + version + " rejected: downloaded file does not match the signature");
            return;
        }
        if (!containsEntry(jar, "kijelzopc/Main.class")) {
            log("update " + version + " rejected: not a KijelzoPC JAR");
            return;
        }

        SafeFile.write(PREVIOUS_JAR, Files.readAllBytes(JAR.toPath()));
        SafeFile.write(JAR, jar);
        if (installed.length() > 0) {
            state.setProperty("previous", installed);
        } else {
            state.remove("previous");
        }
        state.setProperty("version", version);
        state.setProperty("pending", version);
        saveState(state);
        log("update " + version + " installed, starting it");
    }

    /** Restores the previous JAR if the app ended before confirming a new version. */
    private static boolean rollback() throws IOException {
        Properties state = loadState();
        String pending = state.getProperty("pending", "");
        if (pending.length() == 0 || !PREVIOUS_JAR.isFile()) {
            return false;
        }
        SafeFile.write(JAR, Files.readAllBytes(PREVIOUS_JAR.toPath()));
        String skip = (state.getProperty("skip", "") + " " + pending).trim();
        state.setProperty("skip", skip);
        String previous = state.getProperty("previous", "");
        if (previous.length() > 0) {
            state.setProperty("version", previous);
        } else {
            state.remove("version");
        }
        state.remove("previous");
        state.remove("pending");
        saveState(state);
        log("update " + pending + " stopped right after starting: restored the previous version");
        return true;
    }

    /** Compares "v1.10.2"-style versions numerically; an empty version is older than any other. */
    static int compareVersions(String a, String b) {
        String[] x = a.replaceFirst("^v", "").split("\\.");
        String[] y = b.replaceFirst("^v", "").split("\\.");
        if (b.length() == 0) {
            return a.length() == 0 ? 0 : 1;
        }
        if (a.length() == 0) {
            return -1;
        }
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) {
                return p < q ? -1 : 1;
            }
        }
        return 0;
    }

    // The network may still be coming up right after boot, so the first request is retried
    private static byte[] fetchWithRetry(String url, int maxBytes) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return fetch(url, maxBytes);
            } catch (FileNotFoundException e) {
                throw e;
            } catch (IOException e) {
                if (attempt == 3) {
                    throw e;
                }
                Thread.sleep(5000);
            }
        }
    }

    private static byte[] fetch(String url, int maxBytes) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "KijelzoPC-Updater");
        c.setInstanceFollowRedirects(true);
        int status = c.getResponseCode();
        if (status == 404) {
            throw new FileNotFoundException(url + " (no release)");
        }
        if (status != 200) {
            throw new IOException("HTTP " + status + " for " + url);
        }
        InputStream in = c.getInputStream();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                if (out.size() > maxBytes) {
                    throw new IOException(url + " is too large");
                }
            }
            return out.toByteArray();
        } finally {
            in.close();
            c.disconnect();
        }
    }

    private static boolean containsEntry(byte[] jar, String name) throws IOException {
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return true;
                }
            }
            return false;
        } finally {
            zip.close();
        }
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static Properties loadState() {
        Properties state = new Properties();
        try {
            InputStream in = new FileInputStream(STATE_FILE);
            try {
                state.load(in);
            } finally {
                in.close();
            }
        } catch (IOException ignored) {
        }
        return state;
    }

    private static void saveState(Properties state) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        state.store(out, "KijelzoPC release updates (written by kijelzopc.Updater)");
        SafeFile.write(STATE_FILE, out.toByteArray());
    }

    // Same daily log files as the app (logs/kijelzopc-YYYY-MM-DD.log); the app prunes old ones
    static void log(String message) {
        String line = new Date() + " @ " + message;
        System.out.println(line);
        File dir = new File("logs");
        dir.mkdirs();
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(
                    new File(dir, "kijelzopc-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log"), true), UTF8);
            try {
                w.write(line + "\n");
            } finally {
                w.close();
            }
        } catch (IOException ignored) {
        }
    }
}
