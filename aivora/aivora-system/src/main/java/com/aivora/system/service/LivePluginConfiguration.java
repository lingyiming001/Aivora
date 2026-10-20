package com.aivora.system.service;

import java.util.function.UnaryOperator;
import org.springframework.context.annotation.*;

@Configuration
public class LivePluginConfiguration {

  record TextPlugin(
    String id,
    String name,
    String description,
    UnaryOperator<String> transform
  ) implements LiveMessagePlugin {
    public String process(String content) {
      return transform.apply(content);
    }
  }

  @Bean
  LiveMessagePlugin trimLiveMessage() {
    return new TextPlugin("trim", "清理空白", "去掉首尾空白并合并连续空白", text ->
      LiveWorkspaceService.apply("trim", text)
    );
  }

  @Bean
  LiveMessagePlugin hideLiveLinks() {
    return new TextPlugin("hide-links", "隐藏链接", "将 HTTP 链接替换为 [链接]", text ->
      LiveWorkspaceService.apply("hide-links", text)
    );
  }
}
