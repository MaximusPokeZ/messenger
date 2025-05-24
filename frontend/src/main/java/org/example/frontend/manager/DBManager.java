package org.example.frontend.manager;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Slf4j
public class DBManager {
  private final String username;
  private Connection connection;

  public DBManager(String username) {
    this.username = username;
  }

  public void init() {
    try {
      String home = System.getProperty("user.home");
      File dbDir = new File(home, ".securechat");
      if (!dbDir.exists()) {
        dbDir.mkdirs();
      }

      String dbFilePath = new File(dbDir, username + ".db").getAbsolutePath();
      String url = "jdbc:sqlite:" + dbFilePath;

      connection = DriverManager.getConnection(url);
      log.info("Database connection initialized successfully at {}", dbFilePath);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public Connection getConnection() {
    return connection;
  }

  public void close() {
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException ignored) {}
    }
  }

}
