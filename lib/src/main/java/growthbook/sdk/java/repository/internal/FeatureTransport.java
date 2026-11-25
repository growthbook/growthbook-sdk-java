package growthbook.sdk.java.repository.internal;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

/**
 * The HTTP a repository needs: a GET for features, a JSON POST for remote evaluation and a
 * server-sent events stream. Implementations are thread-safe.
 */
public interface FeatureTransport {

    /**
     * @param url     the URL to fetch
     * @param headers request headers to add
     * @return the response read in full
     * @throws IOException when there is no response
     */
    TransportResponse get(String url, Map<String, String> headers) throws IOException;

    /**
     * @param url  the URL to post to
     * @param json the request body, sent as {@code application/json}
     * @return the response read in full
     * @throws IOException when there is no response
     */
    TransportResponse postJson(String url, String json) throws IOException;

    /**
     * Opens one server-sent events connection; it is not reopened when it ends.
     *
     * @param url      the events URL
     * @param listener receives the connection's events, on a transport thread
     * @return a handle to cancel the connection
     */
    SseConnection openEvents(String url, SseListener listener);

    /**
     * Releases the resources held for server-sent events. Feature fetches keep working.
     */
    void closeEvents();

    /**
     * A server-sent events connection.
     */
    interface SseConnection {
        void cancel();
    }

    /**
     * Receives the events of one server-sent events connection.
     */
    interface SseListener {
        void onOpen();

        void onEvent(@Nullable String type, String data);

        /**
         * The server ended the stream.
         */
        void onClosed();

        /**
         * The connection failed or was lost.
         */
        void onFailure(@Nullable Throwable error);
    }
}
