package com.aivora.common;

public record ChatFailure(String code, String message) {
  public static ChatFailure describe(Throwable failure) {
    for (Throwable e = failure; e != null; e = e.getCause()) {
      if (
        e instanceof java.util.concurrent.TimeoutException ||
        e instanceof java.net.http.HttpTimeoutException
      ) return new ChatFailure("AI_TIMEOUT", "模型响应超时，请稍后重试");
      if (
        e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException
      ) return new ChatFailure(
        "AI_UNREACHABLE",
        "模型服务暂时无法连接，请联系管理员检查供应商配置"
      );
      String m = e.getMessage() == null ? "" : e.getMessage();
      if (m.contains("HTTP 401") || m.contains("HTTP 403")) return new ChatFailure(
        "AI_AUTH_FAILED",
        "供应商密钥或模型权限不可用，请联系管理员"
      );
      if (m.contains("HTTP 429")) return new ChatFailure(
        "AI_RATE_LIMITED",
        "模型服务繁忙或额度不足，请稍后重试"
      );
    }
    return new ChatFailure("AI_FAILED", "模型暂时无法生成回复，请重试或联系管理员");
  }
}
