package com.lootfast.backend.config;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class DatabaseLockTimeout {
  private final EntityManager em;

  public DatabaseLockTimeout(EntityManager em) {
    this.em = em;
  }

  public void apply() {
    var session = em.unwrap(org.hibernate.Session.class);
    session.doWork(
        connection -> {
          if (connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
            try (var statement = connection.createStatement()) {
              statement.execute("SET LOCAL lock_timeout = '3s'");
            }
          }
        });
  }
}
