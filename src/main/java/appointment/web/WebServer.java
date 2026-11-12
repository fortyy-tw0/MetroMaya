package appointment.web;

import appointment.service.AppointmentService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Wraps the JDK's built-in HTTP server: /api/* goes to the API, everything else to static files. */
public class WebServer {

    private final HttpServer server;

    public WebServer(int port, AppointmentService appointments) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/", new ApiHandler(appointments));
        server.setExecutor(Executors.newFixedThreadPool(16));
    }

    public void start() {
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
    }
}
