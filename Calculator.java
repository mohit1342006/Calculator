import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Calculator {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/calculate", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "Method not allowed", "text/plain; charset=UTF-8");
                return;
            }

            try {
                String query = exchange.getRequestURI().getRawQuery();
                if (query == null || !query.startsWith("expression=")) {
                    throw new IllegalArgumentException("Missing expression");
                }

                String expression = URLDecoder.decode(
                    query.substring("expression=".length()),
                    StandardCharsets.UTF_8
                );

                double answer = new ExpressionParser(expression).parse();
                if (!Double.isFinite(answer)) {
                    throw new IllegalArgumentException("Invalid result");
                }

                String result = (answer == Math.rint(answer))
                    ? String.valueOf((long) answer)
                    : String.valueOf(answer);

                send(exchange, 200, result, "text/plain; charset=UTF-8");
            } catch (RuntimeException ex) {
                send(exchange, 400, "Error", "text/plain; charset=UTF-8");
            }
        });

        server.createContext("/", exchange -> {
            String requestPath = exchange.getRequestURI().getPath();
            if (requestPath.equals("/")) requestPath = "/index.html";

            // Only serve the three known front-end files.
            if (!requestPath.equals("/index.html")
                    && !requestPath.equals("/style.css")
                    && !requestPath.equals("/script.js")) {
                send(exchange, 404, "Not found", "text/plain; charset=UTF-8");
                return;
            }

            Path file = Path.of("." + requestPath);
            if (!Files.isRegularFile(file)) {
                send(exchange, 404, "File not found: " + requestPath,
                    "text/plain; charset=UTF-8");
                return;
            }

            String contentType = requestPath.endsWith(".css")
                ? "text/css; charset=UTF-8"
                : requestPath.endsWith(".js")
                    ? "application/javascript; charset=UTF-8"
                    : "text/html; charset=UTF-8";

            byte[] data = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });

        server.start();
        System.out.println("Calculator is running at http://localhost:8080");
    }

    private static void send(HttpExchange exchange, int status, String body,
                             String contentType) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    // Small parser for +, -, *, /, %, parentheses and decimal numbers.
    // This avoids depending on the JavaScript engine removed from modern JDKs.
    private static class ExpressionParser {
        private final String input;
        private int position = 0;

        ExpressionParser(String input) {
            this.input = input.replaceAll("\\s+", "");
        }

        double parse() {
            double value = expression();
            if (position != input.length()) {
                throw new IllegalArgumentException("Unexpected character");
            }
            return value;
        }

        private double expression() {
            double value = term();
            while (position < input.length()) {
                char op = input.charAt(position);
                if (op != '+' && op != '-') break;
                position++;
                double right = term();
                value = (op == '+') ? value + right : value - right;
            }
            return value;
        }

        private double term() {
            double value = factor();
            while (position < input.length()) {
                char op = input.charAt(position);
                if (op != '*' && op != '/' && op != '%') break;
                position++;
                double right = factor();
                if ((op == '/' || op == '%') && right == 0) {
                    throw new IllegalArgumentException("Division by zero");
                }
                if (op == '*') value *= right;
                else if (op == '/') value /= right;
                else value %= right;
            }
            return value;
        }

        private double factor() {
            if (position >= input.length()) {
                throw new IllegalArgumentException("Unexpected end");
            }

            char current = input.charAt(position);

            if (current == '+' || current == '-') {
                position++;
                double value = factor();
                return current == '-' ? -value : value;
            }

            if (current == '(') {
                position++;
                double value = expression();
                if (position >= input.length() || input.charAt(position) != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                position++;
                return value;
            }

            int start = position;
            boolean dotSeen = false;
            while (position < input.length()) {
                char c = input.charAt(position);
                if (Character.isDigit(c)) {
                    position++;
                } else if (c == '.' && !dotSeen) {
                    dotSeen = true;
                    position++;
                } else {
                    break;
                }
            }

            if (start == position || input.substring(start, position).equals(".")) {
                throw new IllegalArgumentException("Number expected");
            }
            return Double.parseDouble(input.substring(start, position));
        }
    }
}
