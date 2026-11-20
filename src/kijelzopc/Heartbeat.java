package kijelzopc;

import utils.WSServer;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports this device's status to the central admin dashboard once a minute.
 * Failures (no internet, dashboard down) are logged once and otherwise ignored.
 */
public final class Heartbeat implements Runnable {

    public static final String ADMIN_URL = "admin.url";
    public static final String DEFAULT_ADMIN_URL = "https://kijelzoszerver.szeretet.ro/admin/api/heartbeat.php";
    // The dashboard moved here from kijelzoadmin.szeretet.ro (2026-11); devices installed before
    // still have the old address in server.properties, so it is replaced on the fly.
    private static final String OLD_ADMIN_HOST = "kijelzoadmin.szeretet.ro";
    private static final long INTERVAL_MS = 60 * 1000L;

    private final String url;
    private final String localIp;
    private final String shutdownTime;
    private boolean lastFailed;

    private Heartbeat(String url, String localIp, String shutdownTime) {
        this.url = url;
        this.localIp = localIp;
        this.shutdownTime = shutdownTime;
    }

    public static void start(java.util.Properties properties, String publishUrl) {
        String url = properties.getProperty(ADMIN_URL, DEFAULT_ADMIN_URL).trim();
        if (url.isEmpty() || url.equalsIgnoreCase("off")) {
            System.out.println("Admin heartbeat disabled (" + ADMIN_URL + "=off)");
            return;
        }
        if (url.contains(OLD_ADMIN_HOST)) {
            url = DEFAULT_ADMIN_URL;
        }
        String localIp = "";
        try {
            localIp = new URL(publishUrl).getHost();
        } catch (IOException ignored) {
        }
        String shutdownTime = properties.getProperty(WSServer.SHUTDOWN_TIME, "22:00").trim();
        ActivityLog.record("start", versionText(), System.getProperty("os.name"), null, null);
        Thread thread = new Thread(new Heartbeat(url, localIp, shutdownTime), "admin-heartbeat");
        thread.setDaemon(true);
        thread.start();
    }

    public void run() {
        try {
            Thread.sleep(10 * 1000L);
        } catch (InterruptedException e) {
            return;
        }
        while (true) {
            send();
            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // Events sent with one report; more wait for the next minute
    private static final int EVENTS_PER_REPORT = 200;

    private void send() {
        try {
            int eventCount = Math.min(EVENTS_PER_REPORT, ActivityLog.size());
            String json = toJson(collect());
            // "events": raw JSON array appended to the report (toJson only writes plain values)
            json = json.substring(0, json.length() - 1) + ",\"events\":" + ActivityLog.peekJson(eventCount) + "}";
            byte[] body = json.getBytes("UTF-8");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            OutputStream out = conn.getOutputStream();
            try {
                out.write(body);
            } finally {
                out.close();
            }
            int status = conn.getResponseCode();
            String answer = status < 400 ? readAll(conn.getInputStream()) : "";
            conn.disconnect();
            if (status >= 400) {
                throw new IOException("HTTP " + status);
            }
            // The dashboard answers how many of the sent events it stored ({"events":n}); older ones don't
            java.util.regex.Matcher stored = java.util.regex.Pattern.compile("\"events\"\\s*:\\s*(\\d+)").matcher(answer);
            if (stored.find()) {
                ActivityLog.delivered(Math.min(eventCount, Integer.parseInt(stored.group(1))));
            }
            if (lastFailed) {
                System.out.println("Admin heartbeat working again");
            }
            lastFailed = false;
        } catch (Exception e) {
            if (!lastFailed) {
                System.out.println("Admin heartbeat to " + url + " failed: " + e);
            }
            lastFailed = true;
        }
    }

    private Map<String, Object> collect() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        m.put("deviceId", Activation.deviceId());
        m.put("key", Activation.activeKey());
        m.put("platform", windows ? "windows" : "linux");
        m.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        m.put("javaVersion", System.getProperty("java.version"));
        // Lets the dashboard tell a Raspberry Pi from a PC: processor type and hardware model
        m.put("arch", System.getProperty("os.arch"));
        m.put("model", windows ? null : linuxModel());
        m.put("version", versionText());
        m.put("hostname", hostname());
        m.put("localIp", localIp);
        m.put("shutdownTime", shutdownTime);
        m.put("appUptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        long last = WSServer.lastCommandAt;
        m.put("lastCommandAgo", last == 0 ? null : (Object) ((System.currentTimeMillis() - last) / 1000));

        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sun = (com.sun.management.OperatingSystemMXBean) os;
            double cpu = sun.getSystemCpuLoad();
            if (cpu >= 0) {
                m.put("cpuLoad", Math.round(cpu * 1000) / 10.0);
            }
            m.put("memTotal", sun.getTotalPhysicalMemorySize());
            m.put("memAvailable", sun.getFreePhysicalMemorySize());
        }
        // On Linux "free" memory excludes reclaimable cache; MemAvailable is the meaningful figure
        Long available = meminfoAvailable();
        if (available != null) {
            m.put("memAvailable", available);
        }

        File root = new File(new File(".").getAbsolutePath()).toPath().getRoot().toFile();
        m.put("diskTotal", root.getTotalSpace());
        m.put("diskFree", root.getUsableSpace());

        String temp = firstLine("/sys/class/thermal/thermal_zone0/temp");
        if (temp != null && temp.matches("-?\\d+")) {
            m.put("temperature", Long.parseLong(temp) / 1000.0);
        }
        String uptime = firstLine("/proc/uptime");
        if (uptime != null) {
            m.put("systemUptime", (long) Double.parseDouble(uptime.split("\\s+")[0]));
        }
        return m;
    }

    private static String versionText() {
        String version = Heartbeat.class.getPackage().getImplementationVersion();
        if (version == null) {
            version = "dev";
        }
        String release = Updater.installedVersion();
        return release == null ? version : release + " (" + version + ")";
    }

    private static String readAll(java.io.InputStream in) throws IOException {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }

    private static Long meminfoAvailable() {
        try {
            BufferedReader in = new BufferedReader(new FileReader("/proc/meminfo"));
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("MemAvailable:")) {
                        return Long.parseLong(line.replaceAll("\\D", "")) * 1024;
                    }
                }
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // "Raspberry Pi 5 Model B Rev 1.1" on a Pi, "<vendor> <product>" from a PC's firmware, null if unknown
    private static String linuxModel() {
        String pi = firstLine("/proc/device-tree/model");
        if (pi != null && pi.length() > 0) {
            return pi;
        }
        String vendor = firstLine("/sys/class/dmi/id/sys_vendor");
        String product = firstLine("/sys/class/dmi/id/product_name");
        String model = ((vendor == null ? "" : vendor) + " " + (product == null ? "" : product)).trim();
        return model.length() == 0 ? null : model;
    }

    private static String firstLine(String path) {
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

    static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append(quote(e.getKey())).append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append(quote(v.toString()));
            }
        }
        return sb.append('}').toString();
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
