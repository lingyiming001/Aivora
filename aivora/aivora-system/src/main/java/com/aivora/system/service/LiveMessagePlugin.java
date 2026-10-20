package com.aivora.system.service;

/** Trusted server-side extension. Implement as a Spring bean; no uploaded code is executed. */
public interface LiveMessagePlugin {
  String id();
  String name();
  String description();
  String process(String content);
}
