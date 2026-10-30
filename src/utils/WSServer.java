package utils;

import gui.kijelzoGUI;
import kijelzopc.Main;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created with IntelliJ IDEA.
 * User: Andor
 * Date: 4/29/13
 * Time: 12:27 PM
 * To change this template use File | Settings | File Templates.
 */



@WebService(targetNamespace = "http://modules.ws.server.com/")
public class WSServer {

    public static final String WS_PROPERTY_FILE = "server.properties";
    public static final String LOG = "content.log";

    public static Properties properties;

    static {
        try {
            properties = new Properties();
            properties.load(new FileInputStream(WS_PROPERTY_FILE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    Timer hideTimer;
    // When text was last put on screen (0 = never); read by the admin heartbeat. Static fields aren't web methods.
    public static volatile long lastCommandAt;
    public static final String SHUTDOWN_TIME = "shutdown.time";
    private static final String DEFAULT_SHUTDOWN_TIME = "22:00";
    private final java.util.Timer shutdownScheduler = new java.util.Timer("daily-shutdown", true);

    final kijelzoGUI gui = new gui.kijelzoGUI();

    public WSServer() {
        hideTimer = new Timer(30*60*1000, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                gui.hide(true);
                gui.setTime("12:20");

                ((Timer)e.getSource()).stop();
            }
        });

        scheduleDailyShutdown();
        //gui.setBook("<html>elso <br/> masodik </html>");
        //gui.show(true);

    }

    // Returns {hour, minute} from shutdown.time ("HH:mm"), null for "off"; missing or invalid means 22:00.
    private int[] shutdownTime() {
        String value = properties.getProperty(SHUTDOWN_TIME, DEFAULT_SHUTDOWN_TIME).trim();
        if (value.equalsIgnoreCase("off")) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(value);
        if (m.matches()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            if (hour < 24 && minute < 60) {
                return new int[]{hour, minute};
            }
        }
        log("invalid " + SHUTDOWN_TIME + "=\"" + value + "\" in " + WS_PROPERTY_FILE
                + " (use HH:mm or off), using " + DEFAULT_SHUTDOWN_TIME);
        return new int[]{22, 0};
    }

    // Shuts the computer down every day at shutdown.time local time. Started after that time,
    // the first shutdown is the next day's, so a late start isn't switched off immediately.
    private void scheduleDailyShutdown() {
        int[] time = shutdownTime();
        if (time == null) {
            log("daily shutdown disabled (" + SHUTDOWN_TIME + "=off)");
            return;
        }
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, time[0]);
        next.set(Calendar.MINUTE, time[1]);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(Calendar.getInstance())) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        log("daily shutdown scheduled for " + next.getTime());
        shutdownScheduler.schedule(new java.util.TimerTask() {
            public void run() {
                shutdownComputer();
                scheduleDailyShutdown();
            }
        }, next.getTime());
    }

    private static void shutdownComputer() {
        String[] command = System.getProperty("os.name", "").startsWith("Windows")
                ? new String[]{"shutdown", "-s", "-t", "60", "-c", "Daily shutdown, by kijelzoPC"}
                : new String[]{"sudo", "shutdown", "-h", "+1"};
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    public static final String FILENAME = "konyvek.txt";

    @WebMethod
    public String[] getList() {
        ArrayList<String> result = new ArrayList<String>();
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(FILENAME), "UTF-8"));
            try {
                String line = null;

                while ((line = input.readLine()) != null) {
                    if(line.length() != 0) {
                        result.add(line);
                    }
                }

            } finally {
                input.close();
            }
        } catch (FileNotFoundException ex) {
            //log(ex);
        } catch (IOException ex) {
            //log(ex);
        }

        for(int i=0; i<result.size(); ++i) {
            System.out.println(result.get(i));
        }

        log("getList requested");
        return result.toArray(new String[result.size()]);



    }

    @WebMethod
    public void setTitle(
            @WebParam(name = "title") String title
    ) {
        System.out.println(title);
        gui.setBook(title);
        gui.show(true);
        hideTimer.restart();
        lastCommandAt = System.currentTimeMillis();
        log("setTitle requested to" + title );
    }

    @WebMethod
    public void setBlank() {
        log("hide");
        //gui.setBook(" ");
        //gui.setNumber(" ");
        gui.hide(true);
        log("setBlank requested");
    }

    @WebMethod
    public void setTitleAndNumber(
            @WebParam(name = "title") String title,
            @WebParam(name = "number") int number
    ) {
        System.out.println(title + number);
        gui.setBook(title);
        gui.setNumber(String.valueOf(number));
        gui.show(true);
        hideTimer.restart();
        lastCommandAt = System.currentTimeMillis();
        log("setTitleAndNumber requested to " + title + " and " + number);
    }

    @WebMethod
    public void setStrings(
            @WebParam(name = "string1") String string1,
            @WebParam(name = "string2") String string2
    ) {
        //System.out.println(title + number);
        gui.setBook(string1);
        gui.setNumber(string2);
        gui.show(true);
        hideTimer.restart();
        lastCommandAt = System.currentTimeMillis();
        log("setStrings requested to " + string1 + " and " + string2);
    }

    @WebMethod
    public int getClock() {
        log("getClock requested");
        return gui.getClock();
    }

    @WebMethod
    public void setClock(
            @WebParam(name = "value") String value
    ) {
        gui.setClock(value);
        log("setClock requested to " + value);
    }

    @WebMethod
    public void deleteBook(
            @WebParam(name = "title") String title
    ) {

        StringBuilder kept = new StringBuilder();
        for (String line : readBookLines()) {
            String trimmedLine = line.trim();
            if (trimmedLine.equals(title) || trimmedLine.length() == 0) continue;
            kept.append(line).append("\n");
        }
        boolean successful = writeBooks(kept.toString());
        log("deleteBook requested to " + title + " " + successful);
    }

    private static java.util.List<String> readBookLines() {
        java.util.List<String> lines = new ArrayList<String>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(FILENAME), "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    private static boolean writeBooks(String content) {
        try {
            SafeFile.write(new File(FILENAME), content.getBytes("UTF-8"));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @WebMethod
    public void addBook(
            @WebParam(name = "title") String title
    ) {
        StringBuilder content = new StringBuilder();
        for (String line : readBookLines()) {
            if (line.trim().length() > 0) content.append(line).append("\n");
        }
        content.append(title).append("\n");
        boolean successful = writeBooks(content.toString());
        log("addBook requested to " + title + " " + successful);
    }

    public void log(String message)
    {
        String dt =  Calendar.getInstance().getTime().toString();
        System.out.println(dt + " @ " +message);
        if (Integer.parseInt(properties.getProperty(LOG)) == 1) {
            writeLogLine(dt + " @ " + message);
        }

    }

    // One log file per day in logs/; files older than LOG_KEEP_DAYS are deleted so the logs can't grow forever
    private static final File LOG_DIR = new File("logs");
    private static final int LOG_KEEP_DAYS = 90;
    private static String lastPruneDay = "";

    private static synchronized void writeLogLine(String line) {
        String day = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        LOG_DIR.mkdirs();
        try {
            Writer writer = new OutputStreamWriter(
                    new FileOutputStream(new File(LOG_DIR, "kijelzopc-" + day + ".log"), true), "UTF-8");
            try {
                writer.write(line + "\n");
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!day.equals(lastPruneDay)) {
            lastPruneDay = day;
            long cutoff = System.currentTimeMillis() - LOG_KEEP_DAYS * 86400000L;
            File[] files = LOG_DIR.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().matches("kijelzopc-\\d{4}-\\d{2}-\\d{2}\\.log") && f.lastModified() < cutoff) {
                        f.delete();
                    }
                }
            }
        }
    }

    /*private void log(Throwable ex)
    {
        log(ex.getMessage());
    }*/

}
