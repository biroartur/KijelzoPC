/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package kijelzopc;

import gui.kijelzoGUI;
import org.xml.sax.SAXException;
import utils.WSServer;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.Endpoint;

/**
 *
 * @author jaraics
 */
public class Main {
    Timer hideTimer;
    Timer shutDownTimer;

    public static final String WS_PROPERTY_FILE = "server.properties";

    public static final String WS_PUBLISH_WSDL = "publish.wsdl.address";
    public static final String WS_PUBLISH_HTML = "publish.html.address";
    public static final String WS_CONTENT_ROOT = "content.root.folder";
    public static final String WS_MODULES_PATH = "modules.wsdl.folder";


    public static final String LOGFILE = "log.txt";

    public static Properties properties;

    static {
        try {
            properties = new Properties();
            properties.load(new FileInputStream(WS_PROPERTY_FILE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws UnsupportedEncodingException {
        new Main();
    }

    public Main() throws UnsupportedEncodingException
    {
        //final kijelzoGUI gui = new gui.kijelzoGUI();
        String wsdlPublish = properties.getProperty(WS_PUBLISH_WSDL);
        String modulesPath = properties.getProperty(WS_MODULES_PATH);


        try {
            publishWsdlJs(wsdlPublish,/* modulesPath,*/ new WSServer());
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ParserConfigurationException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (SAXException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


        //Bluetooth bt = new Bluetooth();
        //String[] books = readBooks("konyvek.txt");
        /*hideTimer = new Timer(20*60*1000, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                gui.hide(true);
               ((Timer)e.getSource()).stop();
            }
        });*/

        //6 hours of inactivity
        /*shutDownTimer= new Timer(6*60*60*1000, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                try {
                    //shutdown (-s) with timeout(-t seconds) comment (-c)
                    Runtime.getRuntime().exec("shutdown -s -t 60 -c \"Shutdown due to inactivity, by kijelzoPC\"");
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        shutDownTimer.start();*/

        byte[] b = new byte[1024];
        int count=0;
       /* while (true) {
            /*try{
                try {
                    count = bt.read(b);
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
              *//*
            shutDownTimer.restart();

            log("data:"+new String(b,0,count));
            //String data = new String(b,0,count);
            if(b[0]>=0)
            {
                if(b[0]==0){ //ASCII code of 0
                    log("hide");
                    gui.hide(true);
                }
                else{
                books = readBooks("konyvek.txt");
                gui.setBook(books[b[0]-1]); //the first byte is the book, as string, 0 based
                gui.setNumber(new String(b,1,count-1,"US-ASCII")); // the next bytes are the number
                gui.show(true); //show with fade

                hideTimer.start();
                }
            }else
            {
              // bt =  new Bluetooth();
            }
                 /*   } catch(Exception ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }*//*
        }*/


    }
    
    private String[] readBooks(String filename) {
        ArrayList<String> result = new ArrayList<String>();
        try {
            BufferedReader input = new BufferedReader(new FileReader(filename));
            try {
                String line = null;

                while ((line = input.readLine()) != null) {
                    result.add(line);
                }

            } finally {
                input.close();
            }
        } catch (FileNotFoundException ex) {
            log(ex);
        } catch (IOException ex) {
            log(ex);
        }
        return result.toArray(new String[result.size()]);
    }

    private void log(String message)
    {
        String dt =  Calendar.getInstance().getTime().toString();
        System.out.println(dt + " @ " +message);
    }

    private void log(Throwable ex)
    {
        log(ex.getMessage());
    }

    private static void publishWsdlJs(String wsdlURL, /*String jsPath,*/ Object module) throws IOException, ParserConfigurationException, SAXException {
        /*String moduleName = module.getClass().getSimpleName();
        wsdlURL += moduleName;
        jsPath += moduleName + ".js";*/

        Endpoint.publish(wsdlURL, module);

        /*URL u = new URL(wsdlURL + "?xsd=1");
        InputStream xsd = u.openStream();
        FileOutputStream js = new FileOutputStream(jsPath);*/

        /*Wsdl2JS wsdl2js = new Wsdl2JS(wsdlURL, moduleName);
        wsdl2js.parseXml(xsd);
        wsdl2js.printJs(js);*/
    }
}

