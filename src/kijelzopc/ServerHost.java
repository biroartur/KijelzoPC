package kijelzopc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.xml.ws.Endpoint;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;

/**
 * One HTTP server on the SOAP port: GET /activation answers from startup (even while the
 * activation dialog is open), POST /activation with the key as body activates remotely,
 * and the SOAP endpoint is added once the device is activated.
 */
public final class ServerHost {

    private ServerHost() {
    }

    public static HttpServer start(String wsdlUrl) throws IOException {
        URL url = new URL(wsdlUrl);
        HttpServer server = HttpServer.create(new InetSocketAddress(url.getHost(), url.getPort()), 0);
        server.createContext("/activation", new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                int status = 200;
                String method = exchange.getRequestMethod();
                if ("POST".equals(method)) {
                    status = Activation.submitKey(readBody(exchange.getRequestBody())) ? 200 : 400;
                } else if (!"GET".equals(method)) {
                    status = 405;
                }
                byte[] body = activationJson().getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(status, body.length);
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

    private static String readBody(InputStream in) throws IOException {
        byte[] buf = new byte[1024];
        int total = 0;
        int n;
        while (total < buf.length && (n = in.read(buf, total, buf.length - total)) > 0) {
            total += n;
        }
        in.close();
        return new String(buf, 0, total, "UTF-8");
    }

    static String activationJson() {
        String platform = System.getProperty("os.name", "").startsWith("Windows") ? "windows" : "linux";
        return "{\"activated\":" + Activation.isActivated()
                + ",\"deviceId\":\"" + Activation.deviceId().replaceAll("[^A-Za-z0-9-]", "") + "\""
                + ",\"platform\":\"" + platform + "\"}";
    }
}
