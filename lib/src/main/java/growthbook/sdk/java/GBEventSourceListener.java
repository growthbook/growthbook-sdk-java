package growthbook.sdk.java;

import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

class GBEventSourceListener extends EventSourceListener {
    public GBEventSourceListener() {
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        System.out.printf("\n\nonClosed %s \n\n", eventSource);
        super.onClosed(eventSource);
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        System.out.printf("\n\n eventsource = %s - id = %s - type = %s - data = %s \n\n", eventSource, id, type, data);
        super.onEvent(eventSource, id, type, data);
    }

    @Override
    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
        System.out.printf("\n\n eventsource = %s , error = %s , response = %s\n\n", eventSource, t, response);
        super.onFailure(eventSource, t, response);
    }

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        System.out.printf("\n\n eventsource = %s , response = %s\n\n", eventSource, response);
        super.onOpen(eventSource, response);
    }
}
