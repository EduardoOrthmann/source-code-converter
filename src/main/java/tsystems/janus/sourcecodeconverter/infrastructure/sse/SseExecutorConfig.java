package tsystems.janus.sourcecodeconverter.infrastructure.sse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class SseExecutorConfig {

    @Bean(name = "sseExecutor")
    public ExecutorService sseExecutor() {
        return Executors.newCachedThreadPool();
    }
}
