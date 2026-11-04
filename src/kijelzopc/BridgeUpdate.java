package kijelzopc;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the iPhone web app (PWA: Node bridge in {@code bridge/}) up to date on Linux and the Pi.
 *
 * The self-update only replaces out/KijelzoPC.jar, so the Pi/Linux build bundles the bridge files
 * inside the JAR ({@code pwa-bundle/}, written by build-raspberry.sh; the JAR is signed, so these
 * files are covered by the release signature). At start, when the PWA is installed here and the
 * bundled files differ from the installed ones, they are copied over, npm packages are reinstalled
 * only if package-lock.json changed, and the kijelzo-bridge service is restarted.
 * Runs in the background so the display starts as usual; Windows builds have no bundle.
 */
final class BridgeUpdate implements Runnable {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String BUNDLE = "pwa-bundle/";
    private static final File BRIDGE = new File("bridge");
    private static final File INSTALLED_HASH = new File(BRIDGE, ".bundle-sha256");

    private BridgeUpdate() {
    }

    static void startInBackground() {
        if (System.getProperty("os.name", "").startsWith("Windows")) return;
        Thread t = new Thread(new BridgeUpdate(), "pwa-update");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        try {
            update();
        } catch (Exception e) {
            Updater.log("PWA update failed: " + e);
        }
    }

    private static void update() throws IOException, InterruptedException {
        String bundledHash = resourceText("HASH");
        if (bundledHash == null) return;                         // build without a PWA bundle
        if (!new File(BRIDGE, "src").isDirectory()) return;       // PWA not installed on this device
        if (bundledHash.equals(readText(INSTALLED_HASH))) return; // already up to date

        List<String> files = new ArrayList<String>();
        for (String line : resourceText("FILES").split("\n")) {
            String path = line.trim();
            if (path.isEmpty()) continue;
            if (path.startsWith("/") || path.contains("..")) throw new IOException("bad path " + path);
            files.add(path);
        }

        // Unpack into a fresh folder first, so a broken copy never replaces the working one
        File staging = new File(BRIDGE, ".update");
        deleteTree(staging);
        for (String path : files) {
            File target = new File(staging, path);
            target.getParentFile().mkdirs();
            InputStream in = resource(path);
            if (in == null) throw new IOException("missing in bundle: " + path);
            OutputStream out = new FileOutputStream(target);
            try {
                copy(in, out);
            } finally {
                out.close();
                in.close();
            }
        }

        boolean packagesChanged = !sameContent(new File(staging, "package-lock.json"),
                new File(BRIDGE, "package-lock.json")) || !new File(BRIDGE, "node_modules").isDirectory();

        for (String dir : new String[]{"src", "public"}) {
            File old = new File(BRIDGE, dir);
            File fresh = new File(staging, dir);
            File aside = new File(BRIDGE, "." + dir + ".old");
            deleteTree(aside);
            if (old.exists() && !old.renameTo(aside)) throw new IOException("cannot move " + old);
            if (!fresh.renameTo(old)) {
                aside.renameTo(old);
                throw new IOException("cannot install " + dir);
            }
            deleteTree(aside);
        }
        for (String name : new String[]{"package.json", "package-lock.json"}) {
            File fresh = new File(staging, name);
            if (fresh.exists()) {
                File old = new File(BRIDGE, name);
                old.delete();
                if (!fresh.renameTo(old)) throw new IOException("cannot install " + name);
            }
        }
        deleteTree(staging);

        if (packagesChanged) {
            int code = run(BRIDGE, 600, "npm", "ci", "--omit=dev", "--no-audit", "--no-fund", "--loglevel=error");
            if (code != 0) {
                Updater.log("PWA update: npm ci failed (" + code + "), will retry at next start");
                return;   // hash not written: tried again next time
            }
        }
        int code = run(BRIDGE, 60, "sudo", "-n", "systemctl", "restart", "kijelzo-bridge");
        writeText(INSTALLED_HASH, bundledHash);
        Updater.log("PWA updated to bundle " + bundledHash.substring(0, Math.min(12, bundledHash.length()))
                + (packagesChanged ? " (packages reinstalled)" : "")
                + (code == 0 ? ", bridge restarted" : ", bridge restart failed (" + code + ")"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static InputStream resource(String path) {
        return BridgeUpdate.class.getClassLoader().getResourceAsStream(BUNDLE + path);
    }

    private static String resourceText(String path) throws IOException {
        InputStream in = resource(path);
        if (in == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return new String(out.toByteArray(), UTF8).trim();
        } finally {
            in.close();
        }
    }

    private static String readText(File f) {
        if (!f.isFile()) return null;
        try {
            InputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                copy(in, out);
                return new String(out.toByteArray(), UTF8).trim();
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeText(File f, String text) throws IOException {
        utils.SafeFile.write(f, (text + "\n").getBytes(UTF8));
    }

    private static boolean sameContent(File a, File b) {
        if (!a.isFile() || !b.isFile() || a.length() != b.length()) return false;
        String x = readText(a), y = readText(b);
        return x != null && x.equals(y);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static void deleteTree(File f) {
        if (!f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        f.delete();
    }

    private static int run(File dir, int timeoutSeconds, String... command) throws IOException, InterruptedException {
        Process p;
        try {
            p = new ProcessBuilder(command).directory(dir).redirectErrorStream(true)
                    .redirectOutput(new File(System.getProperty("java.io.tmpdir"), "kijelzo-pwa-update.out"))
                    .start();
        } catch (IOException e) {
            return -2;   // command not available (e.g. no sudo)
        }
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroy();
            return -1;
        }
        return p.exitValue();
    }
}
