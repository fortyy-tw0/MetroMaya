package appointment.web;

import appointment.service.AppointmentService;
import appointment.service.EgovVerifier;
import appointment.service.IdScanner;
import appointment.service.VerificationPass;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Wraps the JDK's built-in HTTP server: /api/* goes to the API, everything else to static files. */
public class WebServer {

    private final HttpServer server;

    public WebServer(int port, AppointmentService appointments, EgovVerifier egov, IdScanner idScanner,
                     VerificationPass passes, String officeAccessKey) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/", new ApiHandler(appointments, egov, idScanner, passes, officeAccessKey));
        server.setExecutor(Executors.newFixedThreadPool(16));
    }

    public void start() {
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
    }
}
