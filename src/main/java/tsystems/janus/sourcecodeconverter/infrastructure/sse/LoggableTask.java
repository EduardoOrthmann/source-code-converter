package tsystems.janus.sourcecodeconverter.infrastructure.sse;

import java.util.function.Consumer;

@FunctionalInterface
public interface LoggableTask {
    void execute(Consumer<String> logger) throws Exception;
}
