package tsystems.janus.sourcecodeconverter.infrastructure.sse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Service
public class SseLogExecutor {

    private final ExecutorService sseExecutor;

    public SseLogExecutor(@Qualifier("sseExecutor") ExecutorService sseExecutor) {
        this.sseExecutor = sseExecutor;
    }

    public SseEmitter streamConversionLogs(LoggableTask task) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 minutes

        sseExecutor.execute(() -> {
            try {
                Consumer<String> logConsumer = logMessage -> {
                    try {
                        emitter.send(SseEmitter.event().name("log").data(logMessage));
                    } catch (IOException e) {
                        System.err.println("Error sending SSE log. Client might have disconnected. " + e.getMessage());
                        throw new RuntimeException(e);
                    }
                };

                task.execute(logConsumer);

                emitter.send(SseEmitter.event().name("completion").data("Conversion process finished."));
                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("Conversion failed: " + e.getMessage()));
                } catch (IOException ioException) {
                    System.err.println("Error sending the final SSE error event: " + ioException.getMessage());
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
