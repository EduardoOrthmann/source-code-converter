package tsystems.janus.sourcecodeconverter.infrastructure.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.tsystems.aiecommon.auth.AIEAuthContextWrapper;
import com.tsystems.aiecommon.auth.AIEContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AIEContextInterceptor implements HandlerInterceptor {

  @Value("${aie.project}")
  private String aieProject;

  @Value("${aie.username}")
  private String aieUsername;

  @Value("${aie.password}")
  private String aiePassword;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    // Set AIE context for this request thread
    AIEContext.setContext(new AIEAuthContextWrapper(
        aieProject,
        aieUsername,
        aiePassword.toCharArray()));
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
      throws Exception {
    // Clear AIE context after request completion
    AIEContext.clear();
  }
}