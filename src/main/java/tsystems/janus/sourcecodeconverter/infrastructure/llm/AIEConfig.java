package tsystems.janus.sourcecodeconverter.infrastructure.llm;

import com.tsystems.aiecommon.AIEFacade;
import com.tsystems.aiecommon.auth.AIEAuthContextWrapper;
import com.tsystems.aiecommon.auth.AIEContext;
import com.tsystems.aiecommon.config.ProxyConfig;
import com.tsystems.aiecommon.handler.AIEGenericWorkflowHandler;
import com.tsystems.aiecommon.handler.WorkflowHandler;
import com.tsystems.aiecommon.service.auth.AIEAuthService;
import com.tsystems.aiecommon.service.chat.AIEChatService;
import com.tsystems.aiecommon.service.memory.MemoryService;
import com.tsystems.aiecommon.service.workflow.AIEWorkflowFactoryService;
import com.tsystems.aiecommon.service.workflow.AIEWorkflowService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIEConfig {

    @Value("${aie.project}")
    private String aieProject;

    @Value("${aie.username}")
    private String aieUsername;

    @Value("${aie.password}")
    private String aiePassword;

    @PostConstruct
    public void initializeAieContext() {
        AIEContext.setContext(new AIEAuthContextWrapper(
                aieProject,
                aieUsername,
                aiePassword.toCharArray()
        ));
    }

    @Bean
    public AIEAuthService aieAuthService() {
        return AIEAuthService.getInstance();
    }

    @Bean
    public ProxyConfig aieProxyConfig() {
        return new ProxyConfig();
    }

    @Bean
    public AIEWorkflowFactoryService aieWorkflowFactoryService() {
        return new AIEWorkflowFactoryService();
    }

    @Bean
    public AIEWorkflowService aieWorkflowService(ProxyConfig aieProxyConfig, AIEWorkflowFactoryService aieWorkflowFactoryService, AIEAuthService authService) {
        return new AIEWorkflowService(aieProxyConfig, aieWorkflowFactoryService, authService);
    }

    @Bean
    public AIEFacade aieFacade(AIEWorkflowService workflowService) {
        return new AIEFacade(workflowService);
    }

    @Bean
    public MemoryService memoryService() {
        return new MemoryService();
    }

    @Bean
    public AIEGenericWorkflowHandler aieGenericWorkflowHandler(AIEFacade facade, MemoryService memoryService) {
        return new AIEGenericWorkflowHandler(facade, memoryService);
    }

    @Bean
    public WorkflowHandler workflowHandler(AIEGenericWorkflowHandler genericHandler) {
        return new WorkflowHandler(genericHandler);
    }

    @Bean
    public AIEChatService aieChatService(AIEAuthService authService) {
        return new AIEChatService(authService);
    }
}
