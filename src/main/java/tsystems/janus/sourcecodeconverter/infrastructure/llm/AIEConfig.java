package tsystems.janus.sourcecodeconverter.infrastructure.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tsystems.aiecommon.config.ProxyConfig;
import com.tsystems.aiecommon.service.auth.AIEAuthService;
import com.tsystems.aiecommon.service.chat.AIEChatService;
import com.tsystems.aiecommon.service.workflow.AIEWorkflowFactoryService;
import com.tsystems.aiecommon.service.workflow.AIEWorkflowService;

@Configuration
public class AIEConfig {

  @Bean
  public AIEAuthService aieAuthService() {
    return AIEAuthService.getInstance();
  }

  @Bean
  public AIEWorkflowService aieWorkflowService(AIEAuthService authService) {
    return new AIEWorkflowService(new ProxyConfig(), new AIEWorkflowFactoryService(), authService);
  }

  @Bean
  public AIEChatService aieChatService(AIEAuthService authService) {
    return new AIEChatService(authService);
  }
}
