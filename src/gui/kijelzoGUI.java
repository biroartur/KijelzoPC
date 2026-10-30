/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.border.Border;

import static java.lang.System.out;

/**
 * @author jaraics <jaraics+code at google.com>
 */


public class kijelzoGUI {

    private JLabel number;
    private JLabel book;
    private JLabel time;
    private FadingPanel panel;
    private JPanel timepanel;
    private int componentWidth;
    private int componentHeight;
    private String oldstring;
    private Font font;
    private JFrame f;
    private volatile boolean contentVisible = false;

    public static final String SCREEN_ID = "content.screen";
    public static final String WS_PROPERTY_FILE = "server.properties";
    public static final String SHOWTIME = "content.time";

    public static Properties properties;

    static {
        try {
            properties = new Properties();
            properties.load(new FileInputStream(WS_PROPERTY_FILE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public kijelzoGUI() {
        GraphicsConfiguration gc = null;
        GraphicsDevice displayDevice;
        GraphicsConfiguration[] gcss = new GraphicsConfiguration[100];
        {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] gs = ge.getScreenDevices();

//            int k=0;
//
//            for (int j = 0; j < gs.length; j++) {
//                GraphicsDevice gd = gs[j];
//                GraphicsConfiguration[] gcs =
//                        gd.getConfigurations();
//
//                for (int i = 0; i < gcs.length; i++) {
//                    System.out.println(gcs[i].getBounds().toString());
//                    gcss[k++] = gcs[i];
//                    gc = gcs[i];
//                    }
//                }
            displayDevice = gs[Integer.parseInt(properties.getProperty(SCREEN_ID))];
            gc = displayDevice.getDefaultConfiguration();
        }


        f = new JFrame(gc);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setUndecorated(true);  //no window bars and buttons
        f.setBackground(Color.BLACK);

        Rectangle gcBounds = gc.getBounds();


        f.setBounds(gcBounds);
        // int xoffs = gcBounds.x;
        // int yoffs = gcBounds.y;

        f.setLocation(gcBounds.getLocation().x, gcBounds.getLocation().y);


        number = new JLabel("");
        book = new JLabel("");
        time = new JLabel("10:20");

        /*time.setHorizontalAlignment(SwingConstants.RIGHT);
        time.setHorizontalTextPosition(SwingConstants.RIGHT);
        time.setVerticalAlignment(SwingConstants.BOTTOM);
        time.setVerticalTextPosition(SwingConstants.BOTTOM);*/

        number.setAlignmentX(Component.CENTER_ALIGNMENT);
        book.setAlignmentX(Component.CENTER_ALIGNMENT);

        Border border = BorderFactory.createEmptyBorder(0, 0, 0, 0);
        number.setBorder(null);
        book.setBorder(border);

        panel = new FadingPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.BLACK);
        // Glue above and below centres the text vertically instead of starting at the top edge
        panel.add(Box.createVerticalGlue());
        panel.add(book);
        panel.add(number);
        panel.add(time);
        panel.add(Box.createVerticalGlue());

        //panel.set
        //timepanel = new JPanel(  new FlowLayout(FlowLayout.RIGHT));
        //timepanel.add(time);


        //JPanel jpnl = new JPanel();
        //jpnl.show();

        f.getContentPane().add(panel);
        //f.add(timepanel);
        //Font font = number.getFont().deriveFont(220f); //getFont must be called after a fnt has been set, or the Component has a parent
        //timepanel.setAlignmentX(f.getWidth()-200);
        //timepanel.setAlignmentY(f.getHeight()-50);
        //timepanel.setVisible(false);

        //Font font = number.getFont();
        font = new Font("Serif", Font.PLAIN, 220);
        componentWidth = f.getWidth() - 50;
        // Two lines share the height, keeping a margin of ~4% of the screen above and below
        int verticalMargin = f.getHeight() / 25;
        componentHeight = (f.getHeight() - 2 * verticalMargin) / 2;
        oldstring = new String("");

        out.println("f's width: " + f.getWidth());

        number.setForeground(Color.ORANGE);
        number.setFont(font);

        book.setForeground(Color.ORANGE);
        book.setFont(font);

        Font timefont = new Font("Serif", Font.PLAIN, 200);
        time.setForeground(Color.ORANGE);
        time.setFont(timefont);


        f.setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
                new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                new Point(0, 0), "hidden"));

        f.setVisible(true);

        // On Linux desktops a screen-sized window stays below the taskbar; real fullscreen covers it.
        // Not on Windows: exclusive fullscreen there minimizes the window whenever it loses focus.
        if (!System.getProperty("os.name", "").startsWith("Windows") && displayDevice.isFullScreenSupported()) {
            displayDevice.setFullScreenWindow(f);
        }

        TimeThread tt = new TimeThread();
        tt.start();

        if ("1".equals(properties.getProperty(SHOWTIME))) {
            javax.swing.Timer startupClock = new javax.swing.Timer(10000, e -> {
                ((javax.swing.Timer) e.getSource()).stop();
                if (!contentVisible) {
                    time.setVisible(true);
                    panel.myShow(true);
                }
            });
            startupClock.setRepeats(false);
            startupClock.start();
        }

    }

    public void setTime(String time) {
        //timepanel.setVisible(true);
    }

    public void setNumber(String number) {
        //this.number.setText(number);
        SetTextAndWait(this.number, number);
        time.setVisible(false);
    }

    public void setBook(String book) {
        time.setVisible(false);
        SetTextAndWait(this.book, book);
    }

    public void show() {
        panel.myShow(false);
        // panel.setVisible(true);
    }

    public void show(boolean animate) {
        contentVisible = true;
        time.setVisible(false);
        panel.myShow(animate);
    }

    public void hide() {
        //panel.myHide(false);
        panel.setVisible(false);
    }

    public void hide(final boolean animate) {
        contentVisible = false;
        panel.hideNow(); // volatile write + repaint — executes immediately on calling thread
        SwingUtilities.invokeLater(() -> {
            setNumber("");
            setBook("");
            if ("1".equals(properties.getProperty(SHOWTIME))) {
                javax.swing.Timer clockTimer = new javax.swing.Timer(10000, e -> {
                    ((javax.swing.Timer) e.getSource()).stop();
                    if (!contentVisible) {
                        time.setVisible(true);
                        panel.myShow(true);
                    }
                });
                clockTimer.setRepeats(false);
                clockTimer.start();
            }
        });
    }

    private void SetTextAndWait(JLabel label, String text) {
        label.setText("");

        if ((label == number) && (text.length() < 4)) {
            // Short number / digit: keep big fixed font
            label.setFont(new Font("Serif", Font.PLAIN, 350));
            label.setText(text);
        } else if (text.length() > 15 && text.indexOf(" ") != -1) {
            // Long text: split into 2 rows at the space nearest the middle
            int mid = text.length() / 2;
            int splitPos = text.indexOf(" "); // fallback: first space
            int minDist = Math.abs(splitPos - mid);
            for (int i = splitPos + 1; i < text.length(); i++) {
                if (text.charAt(i) == ' ') {
                    int dist = Math.abs(i - mid);
                    if (dist < minDist) {
                        minDist = dist;
                        splitPos = i;
                    }
                }
            }
            String line1 = text.substring(0, splitPos);
            String line2 = text.substring(splitPos + 1);

            // Width: constrained by the longer of the two lines
            int stringWidth = Math.max(
                label.getFontMetrics(font).stringWidth(line1),
                label.getFontMetrics(font).stringWidth(line2)
            );
            int newFontSize = (int) (font.getSize() * ((double) componentWidth / stringWidth));

            // Height: 4 visual rows total (2 per label) must fit the screen
            int fontSizeToUse = Math.min(newFontSize, f.getHeight() / 5);

            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(new Font("Serif", Font.PLAIN, fontSizeToUse));
            label.setText("<html><center>" + line1 + "<br>" + line2 + "</center></html>");
        } else {
            // Short text (≤ 15 chars) or long text without spaces: scale by width
            out.println("oldstring: " + oldstring);
            out.println("newstring: " + text);
            int stringWidth = label.getFontMetrics(font).stringWidth(text);
            if (stringWidth > 0) {
                if (!oldstring.equals(text)) {
                    oldstring = text;
                    double widthRatio = (double) componentWidth / (double) stringWidth;
                    int newFontSize = (int) (font.getSize() * widthRatio);
                    int fontSizeToUse = Math.min(newFontSize, componentHeight);
                    label.setFont(new Font("Serif", Font.PLAIN, Math.min(fontSizeToUse, 350)));
                }
            } else {
                label.setFont(new Font("Serif", Font.PLAIN, 220));
            }
            label.setText(text);
        }

        out.println(label.getFont().getSize());

        /*try {
            //if (text != null && text.length() > 10)
                //Thread.sleep(50);
            //Thread.sleep(50); //wait for those two labels to set their text
        } catch (InterruptedException ex) {
            Logger.getLogger(kijelzoGUI.class.getName()).log(Level.SEVERE, null, ex);
        }*/
    }

    public int getClock() {
        try {
            properties.load(new FileInputStream(WS_PROPERTY_FILE));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(" Clock value: " + properties.getProperty(SHOWTIME));
        return properties.getProperty(SHOWTIME).equals("1") ? 1 : 0;
    }

    public void setClock(String value) {
        properties.setProperty(SHOWTIME, value);
        try (FileOutputStream fos = new FileOutputStream(WS_PROPERTY_FILE)) {
            properties.store(fos, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Clock value: " + properties.getProperty(SHOWTIME));
    }

    public class TimeThread extends Thread {

        public void run() {
            int i = 0;
            DateFormat dateFormat = new SimpleDateFormat("HH:mm");
            //Date date = new Date();
            while (true) {

                //if(i%1000 == 0) {
                time.setText(dateFormat.format(new Date()));
                //String timeString = date.getHours() + ":" + date.getMinutes();
                //}
                try {
                    TimeThread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }
    }

}

   
