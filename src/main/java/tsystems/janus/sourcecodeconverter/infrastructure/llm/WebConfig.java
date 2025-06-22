package tsystems.janus.sourcecodeconverter.infrastructure.llm;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AIEContextInterceptor aieContextInterceptor;

  public WebConfig(AIEContextInterceptor aieContextInterceptor) {
    this.aieContextInterceptor = aieContextInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(aieContextInterceptor)
        .addPathPatterns("/api/**"); // Apply to all API endpoints
  }
}