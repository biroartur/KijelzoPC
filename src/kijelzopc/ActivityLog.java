package kijelzopc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Activity of this display for the admin dashboard (v2.0): hymns and texts sent, clears, settings
 * changes, starts and shutdowns, each with the time, where it came from (Android phone or the
 * iPhone page) and the phone's address on the local network.
 *
 * Events wait in activity-pending.jsonl until the admin heartbeat has delivered them, so nothing is
 * lost while the internet is down or across the nightly shutdown. Each event has a random id, so
 * the dashboard ignores one that is sent twice (e.g. when an answer got lost). At most MAX events
 * are kept; when the dashboard is unreachable for a long time the oldest ones are dropped.
 */
public final class ActivityLog {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final File FILE = new File("activity-pending.jsonl");
    private static final int MAX = 5000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final LinkedList<String> pending = new LinkedList<String>();

    static {
        load();
    }

    private ActivityLog() {
    }

    /** Records one event; a and b are its details (e.g. book and number), source/client may be null. */
    public static void record(String type, String a, String b, String source, String client) {
        StringBuilder sb = new StringBuilder("{");
        field(sb, "id", Long.toHexString(RANDOM.nextLong() & Long.MAX_VALUE));
        sb.append(",\"t\":").append(System.currentTimeMillis() / 1000);
        sb.append(',');
        field(sb, "type", type);
        if (a != null) { sb.append(','); field(sb, "a", cut(a)); }
        if (b != null) { sb.append(','); field(sb, "b", cut(b)); }
        if (source != null) { sb.append(','); field(sb, "src", source); }
        if (client != null) { sb.append(','); field(sb, "client", client); }
        String json = sb.append('}').toString();
        synchronized (ActivityLog.class) {
            pending.add(json);
            while (pending.size() > MAX) {
                pending.removeFirst();
            }
            save();
        }
    }

    /** Up to n of the oldest undelivered events, as a JSON array. */
    static synchronized String peekJson(int n) {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (String e : pending) {
            if (i == n) break;
            if (i > 0) sb.append(',');
            sb.append(e);
            i++;
        }
        return sb.append(']').toString();
    }

    static synchronized int size() {
        return pending.size();
    }

    /** Forgets the n oldest events once the dashboard has stored them. */
    static synchronized void delivered(int n) {
        if (n <= 0) return;
        for (int i = 0; i < n && !pending.isEmpty(); i++) {
            pending.removeFirst();
        }
        save();
    }

    private static String cut(String s) {
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private static void field(StringBuilder sb, String name, String value) {
        sb.append('"').append(name).append("\":\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
    }

    private static void load() {
        if (!FILE.isFile()) return;
        try {
            InputStream in = new FileInputStream(FILE);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                for (String line : new String(out.toByteArray(), UTF8).split("\n")) {
                    line = line.trim();
                    if (line.startsWith("{") && line.endsWith("}")) pending.add(line);
                }
            } finally {
                in.close();
            }
            while (pending.size() > MAX) pending.removeFirst();
        } catch (IOException e) {
            System.out.println("Activity log could not be read: " + e);
        }
    }

    private static void save() {
        StringBuilder sb = new StringBuilder();
        List<String> copy = new ArrayList<String>(pending);
        for (String e : copy) sb.append(e).append('\n');
        try {
            utils.SafeFile.write(FILE, sb.toString().getBytes(UTF8));
        } catch (IOException e) {
            System.out.println("Activity log could not be saved: " + e);
        }
    }
}
