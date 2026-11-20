package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.Version;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.model.HttpMethods;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link FeatureTransport} over {@link HttpURLConnection}, with no HTTP library. Bodies are read
 * as UTF-8; the body of an error status is not read, since no read timeout bounds it. Each events connection reads on its own daemon thread until it ends or is cancelled.
 */
@Slf4j
public final class UrlConnectionFeatureTransport implements FeatureTransport {

    private final Set<EventStream> openStreams = ConcurrentHashMap.newKeySet();

    private static final String USER_AGENT = "growthbook-sdk-java/" + Version.SDK_VERSION;

    @Override
    public TransportResponse get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = open(url, HttpMethods.GET.getMethod());
        try {
            headers.forEach(connection::setRequestProperty);
            return read(connection);
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public TransportResponse postJson(String url, String json) throws IOException {
        HttpURLConnection connection = open(url, "POST");
        try {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty(HttpHeaders.ACCEPT.getHeader(), HttpHeaders.APPLICATION_JSON.getHeader());
            try (OutputStream out = connection.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return read(connection);
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public SseConnection openEvents(String url, SseListener listener) {
        EventStream stream = new EventStream(url, listener);
        openStreams.add(stream);
        Thread thread = new Thread(stream, "growthbook-sse");
        thread.setDaemon(true);
        thread.start();
        return stream::cancel;
    }

    @Override
    public void closeEvents() {
        for (EventStream stream : openStreams) {
            stream.cancel();
        }
        openStreams.clear();
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private static TransportResponse read(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        String body = code < HttpURLConnection.HTTP_BAD_REQUEST ? readFully(connection.getInputStream()) : null;
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if (header.getKey() != null && !header.getValue().isEmpty()) {
                headers.put(header.getKey(), header.getValue().get(0));
            }
        }
        return TransportResponse.of(code, connection.getResponseMessage(), body, headers);
    }

    private static String readFully(InputStream stream) throws IOException {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder body = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                body.append(buffer, 0, read);
            }
            return body.toString();
        }
    }

    /**
     * The event being read: its type and data lines until the blank line that dispatches it.
     */
    private static final class PendingEvent {
        @Nullable
        private String type;
        private final StringBuilder data = new StringBuilder();
        private boolean hasData;

        /**
         * Takes one {@code field: value} line; fields other than {@code event} and {@code data}
         * are ignored.
         */
        void accept(String line) {
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            if ("event".equals(field)) {
                type = value;
            } else if ("data".equals(field)) {
                data.append(value).append('\n');
                hasData = true;
            }
        }

        /**
         * Hands the event over when it has data, then starts the next one.
         */
        void dispatchTo(SseListener listener) {
            if (hasData) {
                listener.onEvent(type, data.substring(0, data.length() - 1));
            }
            type = null;
            data.setLength(0);
            hasData = false;
        }
    }

    /**
     * One events connection, parsed per the server-sent events format: {@code event:} and
     * {@code data:} fields, comment lines starting with {@code :}, and an event dispatched at each
     * blank line once it has data.
     */
    private final class EventStream implements Runnable {
        private final String url;
        private final SseListener listener;
        private volatile boolean cancelled;
        @Nullable
        private volatile HttpURLConnection connection;

        EventStream(String url, SseListener listener) {
            this.url = url;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                HttpURLConnection opened = open(url, HttpMethods.GET.getMethod());
                connection = opened;
                opened.setRequestProperty(HttpHeaders.ACCEPT.getHeader(), HttpHeaders.SSE_HEADER.getHeader());
                opened.setDoInput(true);
                int code = opened.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("SSE connection responded with status " + code);
                }
                if (!cancelled) {
                    listener.onOpen();
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(opened.getInputStream(), StandardCharsets.UTF_8))) {
                    readEvents(reader);
                }
                if (!cancelled) {
                    listener.onClosed();
                }
            } catch (IOException | RuntimeException e) {
                if (!cancelled) {
                    listener.onFailure(e);
                }
            } finally {
                openStreams.remove(this);
                disconnect();
            }
        }

        private void readEvents(BufferedReader reader) throws IOException {
            PendingEvent event = new PendingEvent();
            String line;
            while (!cancelled && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    dispatch(event);
                } else if (!line.startsWith(":")) {
                    event.accept(line);
                }
            }
        }

        private void dispatch(PendingEvent event) {
            if (!cancelled) {
                event.dispatchTo(listener);
            }
        }

        /**
         * Stops the stream without blocking the caller: {@link HttpURLConnection#disconnect()}
         * waits for a lock the reader thread holds while blocked reading an idle stream, so it
         * runs on its own daemon thread. No listener method is called after this returns.
         */
        void cancel() {
            cancelled = true;
            HttpURLConnection current = connection;
            if (current != null) {
                Thread closer = new Thread(current::disconnect, "growthbook-sse-close");
                closer.setDaemon(true);
                closer.start();
            }
        }

        private void disconnect() {
            HttpURLConnection current = connection;
            if (current != null) {
                current.disconnect();
            }
        }
    }
}
