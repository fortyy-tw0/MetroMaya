package appointment.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Serves the HTML, CSS and JS files packaged under /static on the classpath. */
class StaticHandler implements HttpHandler {

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "webp", "image/webp",
            "ico", "image/x-icon");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
                send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            if (path.contains("..") || path.contains("\\")) {
                send(exchange, 400, "text/plain; charset=utf-8", "Bad request".getBytes(StandardCharsets.UTF_8));
                return;
            }
            try (InputStream in = StaticHandler.class.getResourceAsStream("/static" + path)) {
                if (in == null) {
                    send(exchange, 404, "text/html; charset=utf-8",
                            "<h1>404</h1><p>Page not found. <a href=\"/\">Go home</a></p>".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
                send(exchange, 200, TYPES.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
            }
        }
    }

    private void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        boolean head = exchange.getRequestMethod().equals("HEAD");
        exchange.sendResponseHeaders(status, head ? -1 : body.length);
        if (!head) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
