package com.aivora.system.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@org.springframework.core.annotation.Order(100)
public class AdminBootstrap implements CommandLineRunner {

  private final JdbcTemplate db;
  private final String email;

  public AdminBootstrap(JdbcTemplate db, @Value("${aivora.bootstrap-admin-email:}") String email) {
    this.db = db;
    this.email = email;
  }

  @Override
  public void run(String... args) {
    if (
      !email.isBlank() &&
      db.queryForObject("select count(*) from app_user where role='ADMIN'", Integer.class) == 0
    ) db.update(
      "update app_user set role='ADMIN',admin_super=true where email=?",
      email.toLowerCase()
    );
  }
}
