package com.aivora.ai.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModelService {

  private final JdbcTemplate db;

  public ModelService(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> listEnabled() {
    return db.queryForList(
      "select m.id,m.model_name,m.capabilities,m.is_default,m.star_per_request,p.name as provider from ai_model m join ai_provider p on p.id=m.provider_id where m.enabled and p.enabled order by m.is_default desc,p.name,m.model_name"
    );
  }
}
