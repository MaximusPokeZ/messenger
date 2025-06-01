package org.example.frontend.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public class DBManager {
  private static DBManager instance;

  private final String username;
  @Getter
  private String dbFilePath;
  @Getter
  private Connection connection;

  public DBManager(String username) {
    this.username = username;
  }

  public static void initInstance(String username) {
    if (instance == null) {
      instance = new DBManager(username);
      instance.init();
      instance.initSchema();
    }
  }

  public static void resetInstance() {
    if (instance != null) {
      instance.close();
    }
    instance = null;
  }

  public void init() {
    try {
      String home = System.getProperty("user.home");//user.downloads
      File dbDir = new File(home, ".securechat");//.tmpStorage
      if (!dbDir.exists()) {
        boolean created = dbDir.mkdirs();
        if (!created) {
          throw new RuntimeException("Failed to create .securechat directory");
        }
      }

      dbFilePath = new File(dbDir, username + ".db").getAbsolutePath();
      String url = "jdbc:sqlite:" + dbFilePath;

      connection = DriverManager.getConnection(url);
      log.info("Database connection initialized successfully at {}", dbFilePath);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void initSchema() {
    try (Statement stmt = connection.createStatement()) {
      stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS chat_rooms (
                  room_id TEXT PRIMARY KEY,
                  owner TEXT NULL,
                  other_user TEXT NULL,
                  last_message TEXT,
                  last_message_time INTEGER,
                  cipher TEXT,
                  cipher_mode TEXT,
                  padding_mode TEXT,
                  iv TEXT,
                  key_bit_length TEXT
              );
            """);

      stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    room_id TEXT NOT NULL,
                    sender TEXT NOT NULL,
                    content TEXT,
                    file_path TEXT,
                    timestamp INTEGER,
                    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id)
                );
            """);

      log.info("Database schema initialized");
    } catch (SQLException e) {
      log.error("Failed to initialize schema", e);
      throw new RuntimeException(e);
    }
  }

  public static DBManager getInstance() {
    if (instance == null) {
      throw new IllegalStateException("DBManager is not initialized. Call initInstance(username) first.");
    }
    return instance;
  }

  private void close() {
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException ignored) {}
    }
  }

}
