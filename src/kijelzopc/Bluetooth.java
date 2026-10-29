package kijelzopc;

import com.intel.bluetooth.BlueCoveImpl;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import sun.java2d.pipe.BufferedBufImgOps;

/**
 * A class that demonstrates Bluetooth communication between server mode PC and
 * client mode device through serial port profile.
 * 
 * @see <a href="http://sourceforge.net/projects/bluecove/">BlueCove - JSR-82
 *      implementation</a>
 */
public class Bluetooth {


    /*-
     *
     * ---- Bluetooth attributes ----
     */

    // serial port profile
    protected String UUID = new UUID("1101", true).toString();
    //modified as of 2010.10.30 by centauri to LIAC(was GIAC) in the hope that it will handle correctly the case when the app is shut down on the mobile cleint, after which it doesn't worked any longer....
    protected int discoveryMode = DiscoveryAgent.LIAC; // no paring needed(ture for GIAC), for LIAC...who knows?!
//    protected StreamConnection streamConnection;
    protected InputStream in;
    protected final Boolean streamConnectionReady = new Boolean(false);

    /*-
     *
     * ---- Connection handling attributes ----
     */
//
    protected int endToken = 255;

    private void initBT() throws BluetoothStateException, IOException {
        LocalDevice device = LocalDevice.getLocalDevice();
        // device.setDiscoverable(discoveryMode); in our application we don't need to be dicoverable, since the MAC is hardcoded in the client

        String url = "btspp://localhost:" + UUID + ";name=Kijelzo";

        log("Create server by uri: " + url);
        StreamConnectionNotifier notifier = null;
        boolean retry = true;
        while (retry) {
            try {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Bluetooth.class.getName()).log(Level.SEVERE, null, ex);
                }
                BlueCoveImpl.instance().getLocalDeviceFeature(0);
                notifier = (StreamConnectionNotifier) Connector.open(url);
                retry = false;
            } catch (IOException ex) {
                retry = true;
                log("ex:"+ex.getMessage());
            }
        }
        serverLoop(notifier);
    }

    public Bluetooth() {
        try {
            initBT();
        } catch (Throwable e) {
            log(e);
        }
    }
    StreamConnectionNotifier previousNotifier = null;
    Thread serverThread;

    private void serverLoop(final StreamConnectionNotifier notifier) throws IOException {
        if (serverThread != null) {
            if (previousNotifier != null) {
                try{
                previousNotifier.close();
                }catch(Exception ex){log(ex);}
            }
            serverThread.interrupt();
        }
        previousNotifier = notifier;
        serverThread = new Thread() {

            public void run() {
                try {
                    log("Waiting for connection...");
                    handleConnection(notifier.acceptAndOpen());
                } catch (Exception e) {
                    log(e);
                }
            }
        };
        serverThread.start();
    }

    private void handleConnection(StreamConnection conn) throws IOException {
        log("New connection...");
        synchronized (streamConnectionReady) {
            log("stream available");
            //this.streamConnection = conn;
            this.in = conn.openDataInputStream();
            streamConnectionReady.notifyAll();
        }
    }

    /**
     * Reads from the connection. Blocks until there is something to read.
     * (If a connection is not available wait for one)
     * @return
     */
    public int read(byte[] b) throws IOException {
        int result = -1;
        boolean retry = true;
        while (retry) {
            //only retry if specified later
            retry = false;
            while (in == null) {
                synchronized (streamConnectionReady) {
                    try {

                        streamConnectionReady.wait();

                        log("reading stream");
                    } catch (InterruptedException ex) {
                        log(ex);
                    }
                }
            }

            do {
                b[++result] = (byte) in.read();
                log("d " + b[result]);
            } while (!((b[result] == 255) || (b[result] < 0)));

            //we lost connection
            if ((result == 0) && (b[0] == -1)) {
                in = null;
                retry = true;
                initBT();
            }
        }//while(retry)
        //there is a problem after reconnecting, that a -1 is left on the stream,
        //we must skip that(it is the first byte in the array)
        if ((result > 0) && (b[0] == -1)) {
            for (int i = 0; i < result - 1; i++) {
                b[i] = b[i + 1];
            }
            //now we have one less byte
            result--;
        }
        log("stream read");

        return result; //the last byte is the sentinel, we skip that
    }

//	private void handleConnection(StreamConnection conn) throws IOException {
//		OutputStream out = conn.openOutputStream();
//		InputStream in = conn.openInputStream();
//		startReadThread(in);
//
//		try { // to write some tokens through the bluetooth link
//			int[] tokens = new int[] { 1, 3, 5, 7, endToken };
//			for (int i = 0; i < tokens.length; i++) {
//				out.write(tokens[i]);
//				out.flush();
//				log("write:" + tokens[i]);
//				// wait a sec before next write.
//				Thread.sleep(1 * 1000);
//			}
//		} catch (Exception e) {
//			log(e);
//		} finally {
//			log("Close connection.");
//			if (conn != null) {
//				try {
//					conn.close();
//				} catch (IOException e) {
//				}
//			}
//		}
//	}
//
//	private void startReadThread(final InputStream in) {
//		Thread reader = new Thread() {
//			public void run() {
//				try {
//					while (true) {
//						int r = in.read();
//						log("read:" + r);
//						if (r == endToken)
//							break;
//					}
//				} catch (Throwable e) {
//					log(e);
//				} finally {
//					if (in != null) {
//						try {
//							in.close();
//						} catch (IOException e) {
//						}
//					}
//				}
//			}
//		};
//		reader.start();
//	}

    /*-
     *   -------  Utility section -------
     */
    private void log(String msg) {
        String dt =  Calendar.getInstance().getTime().toString();
        System.out.println(dt+" @ "+ msg);
    }

    private void log(Throwable e) {
        log(e.getMessage());
        e.printStackTrace();
    }
}
