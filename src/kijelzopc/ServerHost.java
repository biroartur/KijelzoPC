package kijelzopc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.xml.ws.Endpoint;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;

/**
 * One HTTP server on the SOAP port: GET /activation answers from startup (even while the
 * activation dialog is open), and the SOAP endpoint is added once the device is activated.
 */
public final class ServerHost {

    private ServerHost() {
    }

    public static HttpServer start(String wsdlUrl) throws IOException {
        URL url = new URL(wsdlUrl);
        HttpServer server = HttpServer.create(new InetSocketAddress(url.getHost(), url.getPort()), 0);
        server.createContext("/activation", new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = activationJson().getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, body.length);
                OutputStream out = exchange.getResponseBody();
                try {
                    out.write(body);
                } finally {
                    out.close();
                }
            }
        });
        server.start();
        return server;
    }

    public static void publish(HttpServer server, String wsdlUrl, Object module) throws IOException {
        Endpoint.create(module).publish(server.createContext(new URL(wsdlUrl).getPath()));
    }

    static String activationJson() {
        String platform = System.getProperty("os.name", "").startsWith("Windows") ? "windows" : "linux";
        return "{\"activated\":" + Activation.isActivated()
                + ",\"deviceId\":\"" + Activation.deviceId().replaceAll("[^A-Za-z0-9-]", "") + "\""
                + ",\"platform\":\"" + platform + "\"}";
    }
}
