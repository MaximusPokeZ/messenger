package org.example.frontend.model.DAO.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.frontend.model.DAO.MessageDao;
import org.example.frontend.model.main.Message;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MessageDaoImpl implements MessageDao {
  private final Connection connection;

  public MessageDaoImpl(Connection connection) {
    this.connection = connection;
    createTable();
  }

  @Override
  public void createTable() {
    String sql = """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                room_id TEXT NOT NULL,
                sender TEXT NOT NULL,
                content TEXT,
                file_path TEXT,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE CASCADE
            );
            """;
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(sql);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to create messages table", e);
    }
  }

  @Override
  public void insert(Message message) {
    String sql = "INSERT INTO messages (room_id, sender, content, file_path, timestamp) VALUES (?, ?, ?, ?, ?)";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setString(1, message.getRoomId());
      stmt.setString(2, message.getSender());
      stmt.setString(3, message.getContent());
      stmt.setString(4, message.getFilePath());
      stmt.setLong(5, message.getTimestamp());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert message", e);
    }
  }

  @Override
  public List<Message> findByRoomId(String roomId) {
    String sql = "SELECT * FROM messages WHERE room_id = ? ORDER BY timestamp";
    List<Message> messages = new ArrayList<>();
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setString(1, roomId);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          messages.add(new Message(
                  rs.getLong("id"),
                  rs.getString("room_id"),
                  rs.getString("sender"),
                  rs.getString("content"),
                  rs.getString("file_path"),
                  rs.getLong("timestamp")
          ));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to retrieve messages", e);
    }
    return messages;
  }

  @Override
  public void deleteAll() {
    String sql = "DELETE FROM messages";
    try (Statement stmt = connection.createStatement()) {
      int affectedRows = stmt.executeUpdate(sql);
      log.info("Messages removed: {}", affectedRows);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete all messages", e);
    }
  }
}
